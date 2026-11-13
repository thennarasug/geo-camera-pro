package com.example.camera

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OverlayPreferences
import com.example.data.UserPreferencesRepository
import com.example.geo.CompassRepository
import com.example.geo.CompassState
import com.example.geo.GeoLocationInfo
import com.example.geo.LocationRepository
import com.example.geo.MiniMapTileLoader
import com.example.geo.QrCodeGenerator
import com.example.geo.QrPayloadFormat
import com.example.media.CapturedMediaItem
import com.example.media.CapturedMediaRepository
import com.example.media.GalleryFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CaptureMode {
  PHOTO,
  PRO,
  VIDEO,
}

enum class GeoCamRoute {
  CAMERA,
  GALLERY,
}

sealed interface GeoCamModal {
  data object None : GeoCamModal
  data object QrExpanded : GeoCamModal
  data object OverlaySettings : GeoCamModal
  data class PhotoPreview(val uri: Uri) : GeoCamModal
}

sealed interface GeoCamEvent {
  data class ShowMessage(val message: String) : GeoCamEvent
  data class TriggerShutterFlash(val playSound: Boolean) : GeoCamEvent
}

data class GeoCamUiState(
  val hasCameraPermission: Boolean = false,
  val hasLocationPermission: Boolean = false,
  val captureMode: CaptureMode = CaptureMode.PHOTO,
  val currentRoute: GeoCamRoute = GeoCamRoute.CAMERA,
  val isFrontCamera: Boolean = false,
  val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
  val isCapturing: Boolean = false,
  val isRecording: Boolean = false,
  val recordingDurationMillis: Long = 0L,
  val lastCapturedUri: Uri? = null,
  val galleryItems: List<CapturedMediaItem> = emptyList(),
  val galleryFilter: GalleryFilter = GalleryFilter.ALL,
  val selectedGalleryUri: Uri? = null,
  val isLoadingGallery: Boolean = false,
  val proSettings: ProCameraSettings = ProCameraSettings(),
  val proCapabilities: ProCameraCapabilities = ProCameraCapabilities(),
  val activeProTab: ProControlTab = ProControlTab.NONE,
  val compassState: CompassState = CompassState(),
  val geoInfo: GeoLocationInfo = GeoLocationInfo(),
  val preferences: OverlayPreferences = OverlayPreferences(),
  val qrBitmap: Bitmap? = null,
  val qrPayload: String? = null,
  val miniMapBitmap: Bitmap? = null,
  val activeModal: GeoCamModal = GeoCamModal.None,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GeoCamViewModel(application: Application) : AndroidViewModel(application) {

  private val locationRepository = LocationRepository(application.applicationContext)
  private val compassRepository = CompassRepository(application.applicationContext)
  private val preferencesRepository = UserPreferencesRepository(application.applicationContext)

  private val controlState = MutableStateFlow(ControlState())
  private val isAppForeground = MutableStateFlow(false)
  private val _events = MutableSharedFlow<GeoCamEvent>(extraBufferCapacity = 8)
  val events: SharedFlow<GeoCamEvent> = _events.asSharedFlow()

  private data class ControlState(
    val hasCameraPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val currentRoute: GeoCamRoute = GeoCamRoute.CAMERA,
    val isFrontCamera: Boolean = false,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val isCapturing: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDurationMillis: Long = 0L,
    val lastCapturedUri: Uri? = null,
    val galleryItems: List<CapturedMediaItem> = emptyList(),
    val galleryFilter: GalleryFilter = GalleryFilter.ALL,
    val selectedGalleryUri: Uri? = null,
    val isLoadingGallery: Boolean = false,
    val proSettings: ProCameraSettings = ProCameraSettings(),
    val proCapabilities: ProCameraCapabilities = ProCameraCapabilities(),
    val activeProTab: ProControlTab = ProControlTab.NONE,
    val activeModal: GeoCamModal = GeoCamModal.None,
  )

  init {
    refreshGallery()
    viewModelScope.launch {
      combine(controlState, preferencesRepository.preferences) { ctrl, prefs ->
        ctrl.hasLocationPermission && prefs.geoTaggingEnabled && !prefs.timestampOnly
      }
        .distinctUntilChanged()
        .collect { shouldTrack ->
          if (shouldTrack) {
            locationRepository.startTracking()
          } else {
            locationRepository.stopTracking()
          }
        }
    }
    viewModelScope.launch {
      combine(
        isAppForeground,
        controlState,
        preferencesRepository.preferences,
      ) { fg, ctrl, prefs ->
        Triple(
          fg && ctrl.hasCameraPermission && ctrl.currentRoute == GeoCamRoute.CAMERA && prefs.showCompassOverlay,
          ctrl.isFrontCamera,
          Unit,
        )
      }
        .distinctUntilChanged()
        .collect { (shouldRunCompass, isFront, _) ->
          compassRepository.setFrontCamera(isFront)
          if (shouldRunCompass) {
            compassRepository.start()
          } else {
            compassRepository.stop()
          }
        }
    }
    viewModelScope.launch {
      locationRepository.locationInfo.collect { geo ->
        compassRepository.updateLocationDeclination(geo.latitude, geo.longitude)
      }
    }
  }

  private val qrDataFlow =
    combine(locationRepository.locationInfo, preferencesRepository.preferences) { geo, prefs ->
      if (prefs.geoTaggingEnabled && !prefs.timestampOnly) {
        geo.qrPayload(prefs.qrPayloadFormat)
      } else {
        null
      }
    }
      .distinctUntilChanged()
      .mapLatest { payload ->
        val bitmap = payload?.let { QrCodeGenerator.generateBitmap(it, sizePx = 512) }
        payload to bitmap
      }

  private val miniMapFlow =
    combine(locationRepository.locationInfo, preferencesRepository.preferences) { geo, prefs ->
      if (!prefs.geoTaggingEnabled || prefs.timestampOnly) return@combine null
      val lat = geo.latitude
      val lng = geo.longitude
      if (lat != null && lng != null) {
        Triple(
          (lat * 10000.0).toInt() / 10000.0,
          (lng * 10000.0).toInt() / 10000.0,
          prefs.mapZoomLevel,
        )
      } else {
        null
      }
    }
      .distinctUntilChanged()
      .mapLatest { params ->
        params?.let { (lat, lng, zoom) ->
          MiniMapTileLoader.loadMiniMapBitmap(
            latitude = lat,
            longitude = lng,
            zoom = zoom,
            outputSizePx = 256,
          )
        }
      }

  val uiState: StateFlow<GeoCamUiState> =
    combine(
      combine(controlState, compassRepository.compassState) { ctrl, compass -> ctrl to compass },
      locationRepository.locationInfo,
      preferencesRepository.preferences,
      qrDataFlow,
      miniMapFlow,
    ) { (ctrl, compass), geo, prefs, (payload, qrBitmap), miniMap ->
      GeoCamUiState(
        hasCameraPermission = ctrl.hasCameraPermission,
        hasLocationPermission = ctrl.hasLocationPermission,
        captureMode = ctrl.captureMode,
        currentRoute = ctrl.currentRoute,
        isFrontCamera = ctrl.isFrontCamera,
        flashMode = ctrl.flashMode,
        isCapturing = ctrl.isCapturing,
        isRecording = ctrl.isRecording,
        recordingDurationMillis = ctrl.recordingDurationMillis,
        lastCapturedUri = ctrl.lastCapturedUri ?: ctrl.galleryItems.firstOrNull()?.uri,
        galleryItems = ctrl.galleryItems,
        galleryFilter = ctrl.galleryFilter,
        selectedGalleryUri = ctrl.selectedGalleryUri,
        isLoadingGallery = ctrl.isLoadingGallery,
        proSettings = ctrl.proSettings,
        proCapabilities = ctrl.proCapabilities,
        activeProTab = ctrl.activeProTab,
        compassState = compass,
        geoInfo = geo,
        preferences = prefs,
        qrBitmap = qrBitmap,
        qrPayload = payload,
        miniMapBitmap = miniMap,
        activeModal = ctrl.activeModal,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = GeoCamUiState(),
    )

  fun onPermissionsChanged(cameraGranted: Boolean, locationGranted: Boolean) {
    controlState.update {
      it.copy(
        hasCameraPermission = cameraGranted,
        hasLocationPermission = locationGranted,
      )
    }
  }

  fun onAppResume() {
    isAppForeground.value = true
    val locGranted = locationRepository.hasLocationPermission()
    controlState.update { it.copy(hasLocationPermission = locGranted) }
    val prefs = uiState.value.preferences
    if (locGranted && prefs.geoTaggingEnabled && !prefs.timestampOnly) {
      locationRepository.startTracking()
    }
  }

  fun onAppPause() {
    isAppForeground.value = false
    locationRepository.stopTracking()
    compassRepository.stop()
  }

  fun refreshLocation() {
    val prefs = uiState.value.preferences
    if (prefs.geoTaggingEnabled && !prefs.timestampOnly) {
      locationRepository.refreshLocation()
    }
  }

  fun toggleCameraLens() {
    controlState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
  }

  fun cycleFlashMode() {
    controlState.update { current ->
      val next =
        when (current.flashMode) {
          ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
          ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
          else -> ImageCapture.FLASH_MODE_OFF
        }
      current.copy(flashMode = next)
    }
  }

  fun toggleTheme() {
    val next = !uiState.value.preferences.isDarkTheme
    viewModelScope.launch { preferencesRepository.setDarkTheme(next) }
  }

  fun setDarkTheme(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setDarkTheme(enabled) }
  }

  fun toggleGeoTagging() {
    val next = !uiState.value.preferences.geoTaggingEnabled
    viewModelScope.launch { preferencesRepository.setGeoTaggingEnabled(next) }
  }

  fun setGeoTaggingEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setGeoTaggingEnabled(enabled) }
  }

  fun setTimestampOnly(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setTimestampOnly(enabled) }
  }

  fun toggleGridLines() {
    val next = !uiState.value.preferences.showGridLines
    viewModelScope.launch { preferencesRepository.setShowGridLines(next) }
  }

  fun setShowCompassOverlay(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setShowCompassOverlay(enabled) }
  }

  fun setStampOnCapture(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setStampOnCapture(enabled) }
  }

  fun setUseDmsCoordinates(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setUseDmsCoordinates(enabled) }
  }

  fun setShowTimestamp(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setShowTimestamp(enabled) }
  }

  fun setMapZoomLevel(zoom: Int) {
    viewModelScope.launch { preferencesRepository.setMapZoomLevel(zoom) }
  }

  fun setShutterSoundEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setShutterSoundEnabled(enabled) }
  }

  fun setPhotoFilter(filter: PhotoFilter) {
    viewModelScope.launch { preferencesRepository.setPhotoFilter(filter) }
  }

  fun cyclePhotoFilter() {
    val entries = PhotoFilter.entries
    val current = uiState.value.preferences.photoFilter
    val next = entries[(entries.indexOf(current) + 1) % entries.size]
    viewModelScope.launch { preferencesRepository.setPhotoFilter(next) }
  }

  fun setQrPayloadFormat(format: QrPayloadFormat) {
    viewModelScope.launch { preferencesRepository.setQrPayloadFormat(format) }
  }

  fun openModal(modal: GeoCamModal) {
    controlState.update { it.copy(activeModal = modal) }
  }

  fun dismissModal() {
    controlState.update { it.copy(activeModal = GeoCamModal.None) }
  }

  fun setCaptureMode(mode: CaptureMode) {
    controlState.update { current ->
      if (current.isRecording || current.isCapturing) current
      else current.copy(
        captureMode = mode,
        activeProTab = if (mode == CaptureMode.PRO) current.activeProTab else ProControlTab.NONE,
      )
    }
  }

  fun setProCapabilities(capabilities: ProCameraCapabilities) {
    controlState.update { current ->
      val clampedIso =
        current.proSettings.iso?.let {
          if (capabilities.isManualSensorSupported) {
            it.coerceIn(capabilities.isoRange.first, capabilities.isoRange.last)
          } else null
        }
      val clampedShutter =
        current.proSettings.shutterSpeedNs?.let {
          if (capabilities.isManualSensorSupported) {
            it.coerceIn(
              capabilities.exposureTimeNsRange.first,
              capabilities.exposureTimeNsRange.last,
            )
          } else null
        }
      val clampedEv =
        if (capabilities.isExposureCompSupported) {
          current.proSettings.exposureCompIndex.coerceIn(
            capabilities.exposureCompRange.first,
            capabilities.exposureCompRange.last,
          )
        } else 0
      val clampedFocus =
        current.proSettings.focusDistance?.let {
          if (capabilities.isManualFocusSupported) {
            it.coerceIn(0f, capabilities.minFocusDistance)
          } else null
        }
      val validWb =
        if (current.proSettings.whiteBalance in capabilities.availableAwbModes) {
          current.proSettings.whiteBalance
        } else {
          ProWhiteBalance.AUTO
        }

      current.copy(
        proCapabilities = capabilities,
        proSettings =
          current.proSettings.copy(
            iso = clampedIso,
            shutterSpeedNs = clampedShutter,
            exposureCompIndex = clampedEv,
            focusDistance = clampedFocus,
            whiteBalance = validWb,
          ),
      )
    }
  }

  fun setActiveProTab(tab: ProControlTab) {
    controlState.update { it.copy(activeProTab = tab) }
  }

  fun setProIso(iso: Int?) {
    controlState.update {
      it.copy(proSettings = it.proSettings.copy(iso = iso))
    }
  }

  fun setProShutter(shutterNs: Long?) {
    controlState.update {
      it.copy(proSettings = it.proSettings.copy(shutterSpeedNs = shutterNs))
    }
  }

  fun setProEv(evIndex: Int) {
    controlState.update {
      it.copy(
        proSettings =
          it.proSettings.copy(
            exposureCompIndex = evIndex,
            iso = null,
            shutterSpeedNs = null,
          )
      )
    }
  }

  fun setProFocus(focusDistance: Float?) {
    controlState.update {
      it.copy(proSettings = it.proSettings.copy(focusDistance = focusDistance))
    }
  }

  fun setProWhiteBalance(wb: ProWhiteBalance) {
    controlState.update {
      it.copy(proSettings = it.proSettings.copy(whiteBalance = wb))
    }
  }

  fun setProTimer(seconds: Int) {
    controlState.update {
      it.copy(proSettings = it.proSettings.copy(timerSeconds = seconds))
    }
  }

  fun resetProSettings() {
    controlState.update {
      it.copy(
        proSettings = ProCameraSettings(),
        activeProTab = ProControlTab.NONE,
      )
    }
  }

  fun setRecordingState(recording: Boolean, durationMillis: Long = 0L) {
    controlState.update {
      it.copy(
        isRecording = recording,
        recordingDurationMillis = durationMillis,
      )
    }
  }

  fun updateRecordingDuration(durationMillis: Long) {
    controlState.update { it.copy(recordingDurationMillis = durationMillis) }
  }

  fun onVideoFinalized(
    uri: Uri?,
    savedMessageStamped: String,
    savedMessageTimestamp: String,
    savedMessagePlain: String,
    failedMessage: String,
  ) {
    val prefs = uiState.value.preferences
    controlState.update {
      it.copy(
        isRecording = false,
        recordingDurationMillis = 0L,
        lastCapturedUri = uri ?: it.lastCapturedUri,
      )
    }
    if (uri != null) {
      refreshGallery()
      val msg =
        when {
          !prefs.stampOverlayOnCapture -> savedMessagePlain
          prefs.timestampOnly || (!prefs.geoTaggingEnabled && prefs.showTimestamp) ->
            savedMessageTimestamp
          prefs.geoTaggingEnabled -> savedMessageStamped
          else -> savedMessagePlain
        }
      _events.tryEmit(GeoCamEvent.ShowMessage(msg))
    } else {
      _events.tryEmit(GeoCamEvent.ShowMessage(failedMessage))
    }
  }

  fun openGallery(initialUri: Uri? = null) {
    controlState.update {
      it.copy(
        currentRoute = GeoCamRoute.GALLERY,
        selectedGalleryUri = initialUri,
        activeModal = GeoCamModal.None,
      )
    }
    refreshGallery()
  }

  fun closeGallery() {
    controlState.update {
      it.copy(
        currentRoute = GeoCamRoute.CAMERA,
        selectedGalleryUri = null,
      )
    }
  }

  fun setGalleryFilter(filter: GalleryFilter) {
    controlState.update { it.copy(galleryFilter = filter) }
  }

  fun selectGalleryItem(uri: Uri?) {
    controlState.update { it.copy(selectedGalleryUri = uri) }
  }

  fun refreshGallery() {
    viewModelScope.launch {
      controlState.update { it.copy(isLoadingGallery = true) }
      val items = CapturedMediaRepository.loadCapturedMedia(getApplication())
      controlState.update { current ->
        val selectedStillExists =
          current.selectedGalleryUri?.let { sel -> items.any { it.uri == sel } } ?: false
        current.copy(
          galleryItems = items,
          isLoadingGallery = false,
          selectedGalleryUri = if (selectedStillExists) current.selectedGalleryUri else null,
          lastCapturedUri = items.firstOrNull()?.uri ?: current.lastCapturedUri,
        )
      }
    }
  }

  fun deleteGalleryItem(
    item: CapturedMediaItem,
    deletedMessage: String,
    failedMessage: String,
  ) {
    viewModelScope.launch {
      val deleted = CapturedMediaRepository.deleteMediaItem(getApplication(), item)
      if (deleted) {
        val remaining = CapturedMediaRepository.loadCapturedMedia(getApplication())
        controlState.update { current ->
          val nextSelected =
            if (current.selectedGalleryUri == item.uri) {
              val oldFiltered =
                when (current.galleryFilter) {
                  GalleryFilter.ALL -> current.galleryItems
                  GalleryFilter.PHOTOS ->
                    current.galleryItems.filter { it.type == com.example.media.MediaType.PHOTO }
                  GalleryFilter.VIDEOS ->
                    current.galleryItems.filter { it.type == com.example.media.MediaType.VIDEO }
                }
              val oldIdx = oldFiltered.indexOfFirst { it.uri == item.uri }
              val newFiltered =
                when (current.galleryFilter) {
                  GalleryFilter.ALL -> remaining
                  GalleryFilter.PHOTOS ->
                    remaining.filter { it.type == com.example.media.MediaType.PHOTO }
                  GalleryFilter.VIDEOS ->
                    remaining.filter { it.type == com.example.media.MediaType.VIDEO }
                }
              newFiltered.getOrNull(oldIdx.coerceAtMost((newFiltered.size - 1).coerceAtLeast(0)))?.uri
            } else {
              current.selectedGalleryUri
            }
          current.copy(
            galleryItems = remaining,
            selectedGalleryUri = nextSelected,
            lastCapturedUri = remaining.firstOrNull()?.uri,
          )
        }
        _events.emit(GeoCamEvent.ShowMessage(deletedMessage))
      } else {
        _events.emit(GeoCamEvent.ShowMessage(failedMessage))
      }
    }
  }

  fun setCapturing(capturing: Boolean) {
    controlState.update { it.copy(isCapturing = capturing) }
  }

  fun processCapturedImage(
    imageProxy: ImageProxy,
    savedMessageStamped: String,
    savedMessageTimestamp: String,
    savedMessagePlain: String,
    failedMessage: String,
  ) {
    val snapshot = uiState.value
    _events.tryEmit(GeoCamEvent.TriggerShutterFlash(playSound = snapshot.preferences.shutterSoundEnabled))

    viewModelScope.launch {
      val uri =
        PhotoCaptureProcessor.processAndSave(
          context = getApplication(),
          imageProxy = imageProxy,
          isFrontCamera = snapshot.isFrontCamera,
          geoInfo = snapshot.geoInfo,
          prefs = snapshot.preferences,
          miniMapBitmap = snapshot.miniMapBitmap,
        )
      controlState.update {
        it.copy(
          isCapturing = false,
          lastCapturedUri = uri ?: it.lastCapturedUri,
        )
      }
      if (uri != null) {
        refreshGallery()
        val prefs = snapshot.preferences
        val msg =
          when {
            !prefs.stampOverlayOnCapture -> savedMessagePlain
            prefs.timestampOnly || (!prefs.geoTaggingEnabled && prefs.showTimestamp) ->
              savedMessageTimestamp
            prefs.geoTaggingEnabled -> savedMessageStamped
            else -> savedMessagePlain
          }
        _events.emit(GeoCamEvent.ShowMessage(msg))
      } else {
        _events.emit(GeoCamEvent.ShowMessage(failedMessage))
      }
    }
  }

  fun onCaptureError(message: String) {
    controlState.update { it.copy(isCapturing = false) }
    _events.tryEmit(GeoCamEvent.ShowMessage(message))
  }

  override fun onCleared() {
    super.onCleared()
    locationRepository.stopTracking()
    compassRepository.stop()
  }
}
