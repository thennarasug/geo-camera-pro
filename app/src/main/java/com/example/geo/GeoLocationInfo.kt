package com.example.geo

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class QrPayloadFormat {
  MAPS_URL,
  GEO_URI,
  FULL_SNIPPET,
}

data class GeoLocationInfo(
  val latitude: Double? = null,
  val longitude: Double? = null,
  val altitudeMeters: Double? = null,
  val accuracyMeters: Float? = null,
  val addressLine: String = "",
  val area: String = "",
  val locationSummary: String = "",
  val timestampMillis: Long = System.currentTimeMillis(),
  val isLoading: Boolean = true,
  val hasPermission: Boolean = false,
  val providerLabel: String = "",
  val statusNote: String? = null,
) {
  val hasCoordinates: Boolean
    get() = latitude != null && longitude != null

  fun formattedCoordinates(useDms: Boolean): String {
    val lat = latitude ?: return "--.------°, --.------°"
    val lng = longitude ?: return "--.------°, --.------°"
    return if (useDms) {
      "${toDms(lat, isLat = true)}  ${toDms(lng, isLat = false)}"
    } else {
      val latDir = if (lat >= 0) "N" else "S"
      val lngDir = if (lng >= 0) "E" else "W"
      String.format(Locale.US, "%.6f° %s, %.6f° %s", abs(lat), latDir, abs(lng), lngDir)
    }
  }

  fun formattedTimestamp(): String {
    val formatter = SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestampMillis))
  }

  fun qrPayload(format: QrPayloadFormat): String? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    val latStr = String.format(Locale.US, "%.6f", lat)
    val lngStr = String.format(Locale.US, "%.6f", lng)

    return when (format) {
      QrPayloadFormat.MAPS_URL -> "https://maps.google.com/?q=$latStr,$lngStr"
      QrPayloadFormat.GEO_URI -> {
        val label =
          addressLine.ifBlank { locationSummary }.ifBlank { "$latStr,$lngStr" }
        "geo:$latStr,$lngStr?q=$latStr,$lngStr(${Uri.encode(label)})"
      }
      QrPayloadFormat.FULL_SNIPPET ->
        buildString {
          if (addressLine.isNotBlank()) appendLine("Address: $addressLine")
          if (area.isNotBlank()) appendLine("Area: $area")
          if (locationSummary.isNotBlank()) appendLine("Location: $locationSummary")
          appendLine("Coordinates: ${formattedCoordinates(useDms = false)}")
          appendLine("Captured: ${formattedTimestamp()}")
          append("Map: https://maps.google.com/?q=$latStr,$lngStr")
        }
    }
  }

  fun clipboardSummary(useDms: Boolean): String = buildString {
    if (addressLine.isNotBlank()) appendLine("Address: $addressLine")
    if (area.isNotBlank()) appendLine("Area: $area")
    if (locationSummary.isNotBlank()) appendLine("Location: $locationSummary")
    appendLine("Coordinates: ${formattedCoordinates(useDms)}")
    val lat = latitude
    val lng = longitude
    if (lat != null && lng != null) {
      append(
        String.format(Locale.US, "Map: https://maps.google.com/?q=%.6f,%.6f", lat, lng)
      )
    }
  }.trim()

  companion object {
    private fun toDms(decimal: Double, isLat: Boolean): String {
      val direction =
        if (isLat) {
          if (decimal >= 0) "N" else "S"
        } else {
          if (decimal >= 0) "E" else "W"
        }
      val absVal = abs(decimal)
      val degrees = absVal.toInt()
      val minutesFull = (absVal - degrees) * 60.0
      val minutes = minutesFull.toInt()
      val seconds = (minutesFull - minutes) * 60.0
      return String.format(Locale.US, "%d°%02d'%04.1f\"%s", degrees, minutes, seconds, direction)
    }
  }
}
