package com.example.geo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class LocationRepository(private val context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
  private val locationManager =
    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

  private val _locationInfo = MutableStateFlow(GeoLocationInfo())
  val locationInfo: StateFlow<GeoLocationInfo> = _locationInfo.asStateFlow()

  private var isListening = false
  private var lastGeocodedLocation: Location? = null
  private var geocodeJob: Job? = null

  private val fusedCallback =
    object : LocationCallback() {
      override fun onLocationResult(result: LocationResult) {
        val latest = result.lastLocation ?: return
        handleNewLocation(latest, provider = "GPS")
      }
    }

  private val legacyListener =
    object : LocationListener {
      override fun onLocationChanged(location: Location) {
        handleNewLocation(location, provider = location.provider ?: "GPS")
      }

      @Deprecated("Deprecated in Java")
      override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

      override fun onProviderEnabled(provider: String) = Unit

      override fun onProviderDisabled(provider: String) = Unit
    }

  fun hasLocationPermission(): Boolean {
    val fine =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  @SuppressLint("MissingPermission")
  fun startTracking() {
    if (!hasLocationPermission()) {
      _locationInfo.update {
        it.copy(
          hasPermission = false,
          isLoading = false,
          statusNote = "Location permission required",
        )
      }
      return
    }

    _locationInfo.update { it.copy(hasPermission = true, isLoading = !it.hasCoordinates, statusNote = null) }
    if (isListening) return
    isListening = true

    // Fetch last known location immediately for instant UX
    fusedClient.lastLocation
      .addOnSuccessListener { location ->
        if (location != null) {
          handleNewLocation(location, provider = "Fused")
        }
      }
      .addOnFailureListener {
        tryLegacyLastKnownLocation()
      }

    val request =
      LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
        .setMinUpdateIntervalMillis(2000L)
        .setMinUpdateDistanceMeters(3f)
        .build()

    fusedClient
      .requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
      .addOnFailureListener {
        startLegacyLocationUpdates()
      }

    // Also attach LocationManager GPS listener on emulators / AOSP builds without Play Services
    startLegacyLocationUpdates()
  }

  fun stopTracking() {
    if (!isListening) return
    isListening = false
    runCatching { fusedClient.removeLocationUpdates(fusedCallback) }
    runCatching { locationManager?.removeUpdates(legacyListener) }
  }

  @SuppressLint("MissingPermission")
  fun refreshLocation() {
    if (!hasLocationPermission()) {
      _locationInfo.update {
        it.copy(hasPermission = false, isLoading = false, statusNote = "Location permission required")
      }
      return
    }
    _locationInfo.update { it.copy(isLoading = true, statusNote = null) }
    lastGeocodedLocation = null

    fusedClient
      .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
      .addOnSuccessListener { location ->
        if (location != null) {
          handleNewLocation(location, provider = "GPS", forceGeocode = true)
        } else {
          tryLegacyLastKnownLocation(forceGeocode = true)
        }
      }
      .addOnFailureListener {
        tryLegacyLastKnownLocation(forceGeocode = true)
      }
  }

  @SuppressLint("MissingPermission")
  private fun tryLegacyLastKnownLocation(forceGeocode: Boolean = false) {
    if (!hasLocationPermission()) return
    val manager = locationManager ?: return
    val gpsLoc = runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
    val netLoc = runCatching { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
    val best =
      when {
        gpsLoc != null && netLoc != null -> if (gpsLoc.time >= netLoc.time) gpsLoc else netLoc
        gpsLoc != null -> gpsLoc
        else -> netLoc
      }
    if (best != null) {
      handleNewLocation(best, provider = best.provider ?: "GPS", forceGeocode = forceGeocode)
    }
  }

  @SuppressLint("MissingPermission")
  private fun startLegacyLocationUpdates() {
    if (!hasLocationPermission()) return
    val manager = locationManager ?: return
    runCatching {
      if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        manager.requestLocationUpdates(
          LocationManager.GPS_PROVIDER,
          4000L,
          3f,
          legacyListener,
          Looper.getMainLooper(),
        )
      }
    }
    runCatching {
      if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
        manager.requestLocationUpdates(
          LocationManager.NETWORK_PROVIDER,
          6000L,
          5f,
          legacyListener,
          Looper.getMainLooper(),
        )
      }
    }
  }

  private fun handleNewLocation(
    location: Location,
    provider: String,
    forceGeocode: Boolean = false,
  ) {
    val now = System.currentTimeMillis()
    _locationInfo.update { current ->
      current.copy(
        latitude = location.latitude,
        longitude = location.longitude,
        altitudeMeters = if (location.hasAltitude()) location.altitude else current.altitudeMeters,
        accuracyMeters = if (location.hasAccuracy()) location.accuracy else current.accuracyMeters,
        timestampMillis = now,
        isLoading = false,
        hasPermission = true,
        providerLabel = provider.uppercase(Locale.US),
        statusNote = null,
      )
    }

    val prev = lastGeocodedLocation
    val movedEnough = prev == null || prev.distanceTo(location) >= 15f
    if (forceGeocode || movedEnough) {
      lastGeocodedLocation = location
      reverseGeocode(location.latitude, location.longitude)
    }
  }

  private fun reverseGeocode(latitude: Double, longitude: Double) {
    if (!Geocoder.isPresent()) {
      _locationInfo.update {
        it.copy(statusNote = "Geocoder unavailable • Coordinates active")
      }
      return
    }
    geocodeJob?.cancel()
    geocodeJob =
      scope.launch {
        val address = fetchAddress(latitude, longitude)
        if (address != null) {
          val parsed = parseAddress(address)
          _locationInfo.update { current ->
            current.copy(
              addressLine = parsed.addressLine,
              area = parsed.area,
              locationSummary = parsed.locationSummary,
              statusNote = null,
            )
          }
        } else {
          _locationInfo.update { current ->
            if (current.addressLine.isBlank()) {
              current.copy(statusNote = "Offline address • Coordinates locked")
            } else {
              current
            }
          }
        }
      }
  }

  private suspend fun fetchAddress(latitude: Double, longitude: Double): Address? {
    val geocoder = Geocoder(context, Locale.getDefault())
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      suspendCancellableCoroutine { continuation ->
        runCatching {
          geocoder.getFromLocation(
            latitude,
            longitude,
            1,
            object : Geocoder.GeocodeListener {
              override fun onGeocode(addresses: MutableList<Address>) {
                if (continuation.isActive) {
                  continuation.resume(addresses.firstOrNull())
                }
              }

              override fun onError(errorMessage: String?) {
                if (continuation.isActive) {
                  continuation.resume(null)
                }
              }
            },
          )
        }.onFailure {
          if (continuation.isActive) continuation.resume(null)
        }
      }
    } else {
      withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
      }
    }
  }

  private data class ParsedGeoAddress(
    val addressLine: String,
    val area: String,
    val locationSummary: String,
  )

  private fun parseAddress(address: Address): ParsedGeoAddress {
    val streetParts =
      listOfNotNull(address.subThoroughfare, address.thoroughfare)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val street =
      when {
        streetParts.isNotEmpty() -> streetParts.joinToString(" ")
        !address.featureName.isNullOrBlank() && address.featureName != address.locality ->
          address.featureName.orEmpty().trim()
        else -> address.getAddressLine(0).orEmpty().substringBefore(",").trim()
      }

    val areaCandidate =
      listOfNotNull(
          address.subLocality,
          address.subAdminArea,
          address.premises,
        )
        .map { it.trim() }
        .firstOrNull {
          it.isNotBlank() &&
            !it.equals(street, ignoreCase = true) &&
            !it.equals(address.locality, ignoreCase = true)
        }
        .orEmpty()

    val locationParts =
      listOfNotNull(
          address.locality,
          address.adminArea,
          address.postalCode,
          address.countryName,
        )
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    val locationSummary =
      if (locationParts.isNotEmpty()) {
        locationParts.joinToString(", ")
      } else {
        address.getAddressLine(0).orEmpty().substringAfter(",", "").trim()
      }

    return ParsedGeoAddress(
      addressLine = street.ifBlank { address.getAddressLine(0).orEmpty() },
      area = areaCandidate,
      locationSummary = locationSummary,
    )
  }
}
