package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.RenderEffect
import android.location.Location
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.camera.CaptureMode
import com.example.camera.GeoCamEvent
import com.example.camera.GeoCamModal
import com.example.camera.GeoCamRoute
import com.example.camera.GeoCamUiState
import com.example.camera.GeoCamViewModel
import com.example.camera.PhotoFilter
import com.example.camera.ProCameraCapabilities
import com.example.camera.ProCameraController
import com.example.camera.ProControlTab
import com.example.camera.ProWhiteBalance
import com.example.camera.VideoOverlayController
import com.example.geo.CompassAccuracy
import com.example.media.CapturedMediaRepository
import com.example.media.MediaType
import com.example.ui.components.BottomShutterDock
import com.example.ui.components.CameraGridOverlay
import com.example.ui.components.CaptureModePill
import com.example.ui.components.CompassDirectionOverlay
import com.example.ui.components.GalleryThumbnailButton
import com.example.ui.components.GeoOverlayCard
import com.example.ui.components.OverlaySettingsSheet
import com.example.ui.components.PhotoPreviewDialog
import com.example.ui.components.ProControlPanel
import com.example.ui.components.QrExpandedDialog
import com.example.ui.components.ShutterButton
import com.example.ui.components.TimestampOnlyOverlayPill
import com.example.ui.components.TopTelemetryBar
import com.example.ui.theme.LocalHudColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun GeoCamScreen(
  viewModel: GeoCamViewModel = viewModel(),
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val shutterFlashAlpha = remember { Animatable(0f) }
  val shutterSound = remember {
    runCatching {
      MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }
    }.getOrNull()
  }

  DisposableEffect(shutterSound) {
    onDispose {
      runCatching { shutterSound?.release() }
    }
  }

  var requestedOnce by remember { mutableStateOf(false) }
  var requestedAudioOnce by remember { mutableStateOf(false) }

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
      val camGranted = hasCameraPermission(context)
      val locGranted = hasAnyLocationPermission(context)
      viewModel.onPermissionsChanged(cameraGranted = camGranted, locationGranted = locGranted)
    }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

  fun launchPermissionRequest() {
    requestedOnce = true
    permissionLauncher.launch(
      arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
      )
    )
  }

  // Sync permissions on start & resume
  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          val camGranted = hasCameraPermission(context)
          val locGranted = hasAnyLocationPermission(context)
          viewModel.onPermissionsChanged(cameraGranted = camGranted, locationGranted = locGranted)
          viewModel.onAppResume()
        }
        Lifecycle.Event.ON_PAUSE -> viewModel.onAppPause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Request permissions automatically once on initial launch if not yet granted
  LaunchedEffect(Unit) {
    val camGranted = hasCameraPermission(context)
    val locGranted = hasAnyLocationPermission(context)
    viewModel.onPermissionsChanged(cameraGranted = camGranted, locationGranted = locGranted)
    if (!camGranted && !requestedOnce) {
      launchPermissionRequest()
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is GeoCamEvent.ShowMessage -> {
          snackbarHostState.currentSnackbarData?.dismiss()
          snackbarHostState.showSnackbar(event.message)
        }
        is GeoCamEvent.TriggerShutterFlash -> {
          if (event.playSound) {
            runCatching { shutterSound?.play(MediaActionSound.SHUTTER_CLICK) }
          }
          shutterFlashAlpha.snapTo(0.75f)
          shutterFlashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 220))
        }
      }
    }
  }

  BackHandler(enabled = uiState.activeModal != GeoCamModal.None) {
    viewModel.dismissModal()
  }

  BackHandler(
    enabled =
      uiState.activeModal == GeoCamModal.None &&
        uiState.currentRoute == GeoCamRoute.CAMERA &&
        uiState.captureMode == CaptureMode.PRO &&
        uiState.activeProTab != ProControlTab.NONE
  ) {
    viewModel.setActiveProTab(ProControlTab.NONE)
  }

  val copiedMsg = stringResource(R.string.copied_location)
  val savedStampedMsg = stringResource(R.string.photo_saved_stamped)
  val savedTimestampMsg = stringResource(R.string.photo_saved_timestamp)
  val savedPlainMsg = stringResource(R.string.photo_saved_plain)
  val failedMsg = stringResource(R.string.capture_failed)
  val videoStampedMsg = stringResource(R.string.video_saved_stamped)
  val videoTimestampMsg = stringResource(R.string.video_saved_timestamp)
  val videoPlainMsg = stringResource(R.string.video_saved_plain)
  val videoFailedMsg = stringResource(R.string.video_failed)
  val deletedMsg = stringResource(R.string.deleted_item)
  val deleteFailedMsg = stringResource(R.string.delete_failed)

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    when {
      !uiState.hasCameraPermission -> {
        PermissionGateScreen(
          onGrantClick = { launchPermissionRequest() },
          onOpenSettingsClick = { openAppSettings(context) },
        )
      }
      uiState.currentRoute == GeoCamRoute.GALLERY -> {
        GalleryScreen(
          items = uiState.galleryItems,
          filter = uiState.galleryFilter,
          selectedUri = uiState.selectedGalleryUri,
          isLoading = uiState.isLoadingGallery,
          onSelectFilter = { viewModel.setGalleryFilter(it) },
          onSelectItem = { viewModel.selectGalleryItem(it) },
          onRefresh = { viewModel.refreshGallery() },
          onShareItem = { item ->
            val mime = if (item.type == MediaType.VIDEO) "video/mp4" else "image/jpeg"
            val title =
              context.getString(
                if (item.type == MediaType.VIDEO) R.string.share_video else R.string.share_photo
              )
            shareMediaUri(context = context, uri = item.uri, mimeType = mime, chooserTitle = title)
          },
          onDeleteItem = { item ->
            viewModel.deleteGalleryItem(
              item = item,
              deletedMessage = deletedMsg,
              failedMessage = deleteFailedMsg,
            )
          },
          onBackToCamera = { viewModel.closeGallery() },
        )
      }
      else -> {
        CameraViewfinderContent(
          uiState = uiState,
          shutterFlashAlpha = shutterFlashAlpha.value,
          onRefreshLocation = { viewModel.refreshLocation() },
          onToggleGeoTagging = { viewModel.toggleGeoTagging() },
          onToggleTheme = { viewModel.toggleTheme() },
          onCycleFilter = { viewModel.cyclePhotoFilter() },
          onToggleFlash = { viewModel.cycleFlashMode() },
          onToggleGrid = { viewModel.toggleGridLines() },
          onOpenSettings = { viewModel.openModal(GeoCamModal.OverlaySettings) },
          onSwitchCamera = { viewModel.toggleCameraLens() },
          onModeSelected = { mode ->
            viewModel.setCaptureMode(mode)
            if (mode == CaptureMode.VIDEO && !hasAudioPermission(context) && !requestedAudioOnce) {
              requestedAudioOnce = true
              audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
          },
          onCaptureStart = { viewModel.setCapturing(true) },
          onImageCaptured = { proxy ->
            viewModel.processCapturedImage(
              imageProxy = proxy,
              savedMessageStamped = savedStampedMsg,
              savedMessageTimestamp = savedTimestampMsg,
              savedMessagePlain = savedPlainMsg,
              failedMessage = failedMsg,
            )
          },
          onCaptureError = { viewModel.onCaptureError(failedMsg) },
          onRecordingChanged = { recording, durationMs ->
            viewModel.setRecordingState(recording = recording, durationMillis = durationMs)
          },
          onRecordingDurationUpdate = { durationMs ->
            viewModel.updateRecordingDuration(durationMs)
          },
          onVideoFinalized = { uri ->
            viewModel.onVideoFinalized(
              uri = uri,
              savedMessageStamped = videoStampedMsg,
              savedMessageTimestamp = videoTimestampMsg,
              savedMessagePlain = videoPlainMsg,
              failedMessage = videoFailedMsg,
            )
          },
          onCopySnippet = {
            copyGeoSnippetToClipboard(
              context = context,
              text = uiState.geoInfo.clipboardSummary(uiState.preferences.useDmsCoordinates),
            )
            scope.launch {
              snackbarHostState.currentSnackbarData?.dismiss()
              snackbarHostState.showSnackbar(copiedMsg)
            }
          },
          onMapClick = {
            val lat = uiState.geoInfo.latitude
            val lng = uiState.geoInfo.longitude
            if (lat != null && lng != null) {
              openLocationInMaps(context, lat, lng, uiState.geoInfo.addressLine)
            }
          },
          onQrClick = { viewModel.openModal(GeoCamModal.QrExpanded) },
          onEnableLocationClick = { launchPermissionRequest() },
          onOpenGallery = { viewModel.openGallery() },
          onCompassClick = {
            val compass = uiState.compassState
            if (compass.hasSensor) {
              val base =
                if (compass.isTrueNorth) {
                  context.getString(
                    R.string.compass_info_true,
                    compass.cardinalLabel,
                    compass.roundedDegrees,
                  )
                } else {
                  context.getString(
                    R.string.compass_info_mag,
                    compass.cardinalLabel,
                    compass.roundedDegrees,
                  )
                }
              val msg =
                if (compass.accuracy == CompassAccuracy.LOW) {
                  base + context.getString(R.string.compass_calibrate_hint)
                } else {
                  base
                }
              scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(msg)
              }
            }
          },
          onProCapabilitiesRead = { viewModel.setProCapabilities(it) },
          onSelectProTab = { viewModel.setActiveProTab(it) },
          onProIsoChanged = { viewModel.setProIso(it) },
          onProShutterChanged = { viewModel.setProShutter(it) },
          onProEvChanged = { viewModel.setProEv(it) },
          onProFocusChanged = { viewModel.setProFocus(it) },
          onProWbChanged = { viewModel.setProWhiteBalance(it) },
          onProTimerChanged = { viewModel.setProTimer(it) },
          onResetPro = { viewModel.resetProSettings() },
        )
      }
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier =
        Modifier.align(Alignment.TopCenter)
          .statusBarsPadding()
          .padding(top = 64.dp, start = 16.dp, end = 16.dp),
    )

    // Active Modals
    when (val modal = uiState.activeModal) {
      GeoCamModal.None -> Unit
      GeoCamModal.QrExpanded -> {
        QrExpandedDialog(
          geoInfo = uiState.geoInfo,
          preferences = uiState.preferences,
          qrBitmap = uiState.qrBitmap,
          qrPayload = uiState.qrPayload,
          onSelectFormat = { viewModel.setQrPayloadFormat(it) },
          onCopyInfo = {
            copyGeoSnippetToClipboard(
              context = context,
              text = uiState.geoInfo.clipboardSummary(uiState.preferences.useDmsCoordinates),
            )
            scope.launch {
              snackbarHostState.currentSnackbarData?.dismiss()
              snackbarHostState.showSnackbar(copiedMsg)
            }
          },
          onShareLocation = {
            shareText(
              context = context,
              text = uiState.geoInfo.clipboardSummary(uiState.preferences.useDmsCoordinates),
              chooserTitle = context.getString(R.string.share_location),
            )
          },
          onDismiss = { viewModel.dismissModal() },
        )
      }
      GeoCamModal.OverlaySettings -> {
        OverlaySettingsSheet(
          preferences = uiState.preferences,
          onDarkThemeChanged = { viewModel.setDarkTheme(it) },
          onShutterSoundChanged = { viewModel.setShutterSoundEnabled(it) },
          onGeoTaggingChanged = { viewModel.setGeoTaggingEnabled(it) },
          onTimestampOnlyChanged = { viewModel.setTimestampOnly(it) },
          onStampChanged = { viewModel.setStampOnCapture(it) },
          onDmsChanged = { viewModel.setUseDmsCoordinates(it) },
          onTimestampChanged = { viewModel.setShowTimestamp(it) },
          onGridChanged = { viewModel.toggleGridLines() },
          onCompassChanged = { viewModel.setShowCompassOverlay(it) },
          onMapZoomChanged = { viewModel.setMapZoomLevel(it) },
          onPhotoFilterChanged = { viewModel.setPhotoFilter(it) },
          onFormatChanged = { viewModel.setQrPayloadFormat(it) },
          onDismiss = { viewModel.dismissModal() },
        )
      }
      is GeoCamModal.PhotoPreview -> {
        PhotoPreviewDialog(
          photoUri = modal.uri,
          onSharePhoto = { uri ->
            shareImageUri(
              context = context,
              uri = uri,
              chooserTitle = context.getString(R.string.share_photo),
            )
          },
          onDismiss = { viewModel.dismissModal() },
        )
      }
    }
  }
}

@SuppressLint("ClickableViewAccessibility", "MissingPermission")
@Composable
private fun CameraViewfinderContent(
  uiState: GeoCamUiState,
  shutterFlashAlpha: Float,
  onRefreshLocation: () -> Unit,
  onToggleGeoTagging: () -> Unit,
  onToggleTheme: () -> Unit,
  onCycleFilter: () -> Unit,
  onToggleFlash: () -> Unit,
  onToggleGrid: () -> Unit,
  onOpenSettings: () -> Unit,
  onSwitchCamera: () -> Unit,
  onModeSelected: (CaptureMode) -> Unit,
  onCaptureStart: () -> Unit,
  onImageCaptured: (ImageProxy) -> Unit,
  onCaptureError: () -> Unit,
  onRecordingChanged: (Boolean, Long) -> Unit,
  onRecordingDurationUpdate: (Long) -> Unit,
  onVideoFinalized: (Uri?) -> Unit,
  onCopySnippet: () -> Unit,
  onMapClick: () -> Unit,
  onQrClick: () -> Unit,
  onEnableLocationClick: () -> Unit,
  onOpenGallery: () -> Unit,
  onCompassClick: () -> Unit,
  onProCapabilitiesRead: (ProCameraCapabilities) -> Unit,
  onSelectProTab: (ProControlTab) -> Unit,
  onProIsoChanged: (Int?) -> Unit,
  onProShutterChanged: (Long?) -> Unit,
  onProEvChanged: (Int) -> Unit,
  onProFocusChanged: (Float?) -> Unit,
  onProWbChanged: (ProWhiteBalance) -> Unit,
  onProTimerChanged: (Int) -> Unit,
  onResetPro: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val hud = LocalHudColors.current
  val colors = MaterialTheme.colorScheme

  val previewView = remember {
    PreviewView(context).apply {
      scaleType = PreviewView.ScaleType.FILL_CENTER
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
  }

  val videoOverlayController = remember { VideoOverlayController() }
  LaunchedEffect(uiState.geoInfo, uiState.preferences, uiState.miniMapBitmap) {
    videoOverlayController.update(
      geoInfo = uiState.geoInfo,
      prefs = uiState.preferences,
      miniMapBitmap = uiState.miniMapBitmap,
    )
  }
  DisposableEffect(videoOverlayController) {
    onDispose { videoOverlayController.release() }
  }

  var camera by remember { mutableStateOf<Camera?>(null) }
  var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
  var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
  var activeRecording by remember { mutableStateOf<Recording?>(null) }
  var cameraError by remember { mutableStateOf<String?>(null) }
  var focusPoint by remember { mutableStateOf<Offset?>(null) }
  var countdownRemaining by remember { mutableIntStateOf(0) }

  LaunchedEffect(focusPoint) {
    if (focusPoint != null) {
      delay(900L)
      focusPoint = null
    }
  }

  val isVideoMode = uiState.captureMode == CaptureMode.VIDEO

  // Bind CameraX whenever lens or photo/video pipeline changes
  DisposableEffect(uiState.isFrontCamera, isVideoMode, lifecycleOwner) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    val executor = ContextCompat.getMainExecutor(context)

    val listener = Runnable {
      runCatching {
        val provider = providerFuture.get()
        val desiredSelector =
          if (uiState.isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
          else CameraSelector.DEFAULT_BACK_CAMERA
        val selector =
          when {
            provider.hasCamera(desiredSelector) -> desiredSelector
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
              CameraSelector.DEFAULT_BACK_CAMERA
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
              CameraSelector.DEFAULT_FRONT_CAMERA
            else -> null
          }

        if (selector == null) {
          cameraError = "No camera hardware detected"
          return@runCatching
        }

        val preview =
          Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
          }

        provider.unbindAll()

        if (isVideoMode) {
          val qualitySelector =
            QualitySelector.fromOrderedList(
              listOf(Quality.FHD, Quality.HD, Quality.SD),
              FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
            )
          val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
          val vidCap =
            VideoCapture.Builder(recorder)
              .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
              .build()

          val boundCamera =
            runCatching {
              val overlayEffect = videoOverlayController.createOverlayEffect()
              val group =
                UseCaseGroup.Builder()
                  .addUseCase(preview)
                  .addUseCase(vidCap)
                  .addEffect(overlayEffect)
                  .build()
              provider.bindToLifecycle(lifecycleOwner, selector, group)
            }.getOrElse {
              provider.unbindAll()
              provider.bindToLifecycle(lifecycleOwner, selector, preview, vidCap)
            }

          camera = boundCamera
          videoCapture = vidCap
          imageCapture = null
          onProCapabilitiesRead(ProCameraController.readCapabilities(boundCamera))
        } else {
          val capture =
            ImageCapture.Builder()
              .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
              .setFlashMode(uiState.flashMode)
              .build()
          val boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
          camera = boundCamera
          imageCapture = capture
          videoCapture = null
          onProCapabilitiesRead(ProCameraController.readCapabilities(boundCamera))
        }
        cameraError = null
      }.onFailure { err ->
        cameraError = err.localizedMessage ?: "Unable to open camera"
      }
    }

    providerFuture.addListener(listener, executor)
    onDispose {
      runCatching {
        activeRecording?.stop()
        activeRecording = null
        if (providerFuture.isDone) {
          providerFuture.get().unbindAll()
        }
      }
    }
  }

  // Keep flash mode synchronized without rebinding camera
  LaunchedEffect(uiState.flashMode, imageCapture) {
    imageCapture?.flashMode = uiState.flashMode
  }

  // Synchronize Camera2 Pro manual controls with active Camera instance
  LaunchedEffect(camera, uiState.captureMode, uiState.proSettings, uiState.proCapabilities) {
    val activeCam = camera ?: return@LaunchedEffect
    ProCameraController.applySettings(
      camera = activeCam,
      isProMode = uiState.captureMode == CaptureMode.PRO,
      settings = uiState.proSettings,
      capabilities = uiState.proCapabilities,
    )
  }

  // Apply active PhotoFilter to live CameraX PreviewView
  LaunchedEffect(uiState.preferences.photoFilter, previewView) {
    applyPreviewFilter(previewView, uiState.preferences.photoFilter)
  }

  // Cancel active countdown if mode leaves PRO
  LaunchedEffect(uiState.captureMode) {
    if (uiState.captureMode != CaptureMode.PRO) {
      countdownRemaining = 0
    }
  }

  // Tap-to-focus & pinch-to-zoom on PreviewView
  DisposableEffect(previewView, uiState.captureMode, uiState.proSettings.focusDistance) {
    val scaleDetector =
      ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
          override fun onScale(detector: ScaleGestureDetector): Boolean {
            val cam = camera ?: return false
            val zoomState = cam.cameraInfo.zoomState.value ?: return false
            val targetZoom =
              (zoomState.zoomRatio * detector.scaleFactor).coerceIn(
                zoomState.minZoomRatio,
                zoomState.maxZoomRatio,
              )
            cam.cameraControl.setZoomRatio(targetZoom)
            return true
          }
        },
      )

    previewView.setOnTouchListener { view, event ->
      scaleDetector.onTouchEvent(event)
      if (!scaleDetector.isInProgress && event.action == MotionEvent.ACTION_UP) {
        val manualFocusLocked =
          uiState.captureMode == CaptureMode.PRO && uiState.proSettings.focusDistance != null
        val cam = camera
        if (cam != null && !manualFocusLocked) {
          val factory = previewView.meteringPointFactory
          val point = factory.createPoint(event.x, event.y)
          val action = FocusMeteringAction.Builder(point).build()
          cam.cameraControl.startFocusAndMetering(action)
          focusPoint = Offset(event.x, event.y)
        }
        view.performClick()
      }
      true
    }
    onDispose { previewView.setOnTouchListener(null) }
  }

  fun triggerPhotoCapture() {
    val capture = imageCapture ?: return
    if (uiState.isCapturing) return
    onCaptureStart()
    capture.takePicture(
      ContextCompat.getMainExecutor(context),
      object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
          onImageCaptured(image)
        }

        override fun onError(exception: ImageCaptureException) {
          onCaptureError()
        }
      },
    )
  }

  fun toggleVideoRecording() {
    val currentRec = activeRecording
    if (currentRec != null) {
      currentRec.stop()
      activeRecording = null
      return
    }
    val vidCap = videoCapture ?: return
    val prefs = uiState.preferences
    val geo = uiState.geoInfo
    val gpsLocation =
      if (prefs.geoTaggingEnabled && !prefs.timestampOnly && geo.latitude != null && geo.longitude != null) {
        Location("GeoCam").apply {
          latitude = geo.latitude
          longitude = geo.longitude
          time = System.currentTimeMillis()
        }
      } else {
        null
      }

    var pendingRecording =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val mediaStoreOpts = CapturedMediaRepository.createVideoMediaStoreOutput(context, gpsLocation)
        vidCap.output.prepareRecording(context, mediaStoreOpts)
      } else {
        val fileOpts = CapturedMediaRepository.createVideoFileOutput(context, gpsLocation)
        vidCap.output.prepareRecording(context, fileOpts)
      }

    if (hasAudioPermission(context)) {
      pendingRecording = pendingRecording.withAudioEnabled()
    }

    onRecordingChanged(true, 0L)
    activeRecording =
      pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
        when (event) {
          is VideoRecordEvent.Start -> {
            onRecordingChanged(true, 0L)
          }
          is VideoRecordEvent.Status -> {
            val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000L
            onRecordingDurationUpdate(durationMs)
          }
          is VideoRecordEvent.Finalize -> {
            activeRecording = null
            if (event.hasError()) {
              onVideoFinalized(null)
            } else {
              onVideoFinalized(event.outputResults.outputUri)
            }
          }
        }
      }
  }

  LaunchedEffect(countdownRemaining) {
    if (countdownRemaining > 0) {
      delay(1000L)
      if (countdownRemaining == 1) {
        countdownRemaining = 0
        triggerPhotoCapture()
      } else if (countdownRemaining > 1) {
        countdownRemaining -= 1
      }
    }
  }

  fun handleShutterPress() {
    when {
      uiState.captureMode == CaptureMode.VIDEO -> toggleVideoRecording()
      countdownRemaining > 0 -> countdownRemaining = 0
      uiState.captureMode == CaptureMode.PRO && uiState.proSettings.timerSeconds > 0 -> {
        countdownRemaining = uiState.proSettings.timerSeconds
      }
      else -> triggerPhotoCapture()
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isLandscapeWide = maxWidth > maxHeight && maxWidth >= 600.dp

    // Live CameraX PreviewView
    AndroidView(
      factory = { previewView },
      modifier = Modifier.fillMaxSize().testTag("camera_preview_view"),
    )

    if (cameraError != null) {
      Box(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = cameraError.orEmpty(),
          style = MaterialTheme.typography.bodyLarge,
          color = colors.onSurfaceVariant,
        )
      }
    }

    // Optional Rule-of-Thirds Grid
    if (uiState.preferences.showGridLines) {
      CameraGridOverlay()
    }

    // Subtle top/bottom vignette scrims for guaranteed HUD contrast
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(140.dp)
          .align(Alignment.TopCenter)
          .background(
            Brush.verticalGradient(
              listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
            )
          )
    )
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(260.dp)
          .align(Alignment.BottomCenter)
          .background(
            Brush.verticalGradient(
              listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
            )
          )
    )

    // Tap-to-focus reticle ring
    AnimatedVisibility(
      visible = focusPoint != null,
      enter = fadeIn(tween(120)),
      exit = fadeOut(tween(240)),
    ) {
      focusPoint?.let { pt ->
        val sizePx = with(LocalDensity.current) { 64.dp.toPx() }
        Box(
          modifier =
            Modifier.offset {
                IntOffset(
                  (pt.x - sizePx / 2f).roundToInt(),
                  (pt.y - sizePx / 2f).roundToInt(),
                )
              }
              .size(64.dp)
              .border(2.dp, hud.accent, CircleShape)
        )
      }
    }

    // Shutter flash visual feedback
    if (shutterFlashAlpha > 0f) {
      Box(
        modifier =
          Modifier.fillMaxSize().alpha(shutterFlashAlpha).background(Color.White)
      )
    }

    // Pro countdown timer overlay
    if (countdownRemaining > 0) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.6f),
          border = BorderStroke(2.dp, hud.accent),
          modifier = Modifier.size(116.dp).testTag("pro_countdown_badge"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = countdownRemaining.toString(),
              style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
              color = Color.White,
              fontWeight = FontWeight.ExtraBold,
            )
          }
        }
      }
    }

    if (isLandscapeWide) {
      // Adaptive wide/landscape layout: Top telemetry + compass + bottom-left Geo QR overlay + right shutter column
      Column(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TopTelemetryBar(
          geoInfo = uiState.geoInfo,
          hasLocationPermission = uiState.hasLocationPermission,
          geoTaggingEnabled = uiState.preferences.geoTaggingEnabled,
          timestampOnly = uiState.preferences.timestampOnly,
          isDarkTheme = uiState.preferences.isDarkTheme,
          photoFilter = uiState.preferences.photoFilter,
          flashMode = uiState.flashMode,
          showGridLines = uiState.preferences.showGridLines,
          onRefreshLocation = onRefreshLocation,
          onToggleGeoTagging = onToggleGeoTagging,
          onToggleTheme = onToggleTheme,
          onCycleFilter = onCycleFilter,
          onToggleFlash = onToggleFlash,
          onToggleGrid = onToggleGrid,
          onOpenSettings = onOpenSettings,
        )
        if (uiState.preferences.showCompassOverlay) {
          CompassDirectionOverlay(
            compassState = uiState.compassState,
            onClick = onCompassClick,
          )
        }
      }

      val showTimestampOnly =
        uiState.preferences.timestampOnly ||
          (!uiState.preferences.geoTaggingEnabled && uiState.preferences.showTimestamp)
      Column(
        modifier =
          Modifier.align(Alignment.BottomStart)
            .safeDrawingPadding()
            .padding(start = 16.dp, bottom = 16.dp, end = 120.dp)
            .widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (uiState.captureMode == CaptureMode.PRO) {
          ProControlPanel(
            settings = uiState.proSettings,
            capabilities = uiState.proCapabilities,
            activeTab = uiState.activeProTab,
            onSelectTab = onSelectProTab,
            onIsoChanged = onProIsoChanged,
            onShutterChanged = onProShutterChanged,
            onEvChanged = onProEvChanged,
            onFocusChanged = onProFocusChanged,
            onWhiteBalanceChanged = onProWbChanged,
            onTimerChanged = onProTimerChanged,
            onResetPro = onResetPro,
          )
        }
        when {
          showTimestampOnly -> TimestampOnlyOverlayPill()
          uiState.preferences.geoTaggingEnabled -> {
            GeoOverlayCard(
              geoInfo = uiState.geoInfo,
              preferences = uiState.preferences,
              qrBitmap = uiState.qrBitmap,
              miniMapBitmap = uiState.miniMapBitmap,
              hasLocationPermission = uiState.hasLocationPermission,
              onCopySnippet = onCopySnippet,
              onMapClick = onMapClick,
              onQrClick = onQrClick,
              onEnableLocationClick = onEnableLocationClick,
            )
          }
        }
      }

      val lastCapturedIsVideo =
        uiState.galleryItems.firstOrNull { it.uri == uiState.lastCapturedUri }?.type == MediaType.VIDEO

      Column(
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .safeDrawingPadding()
            .fillMaxHeight()
            .padding(end = 18.dp, top = 72.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        CaptureModePill(
          captureMode = uiState.captureMode,
          isRecording = uiState.isRecording,
          recordingDurationMillis = uiState.recordingDurationMillis,
          onModeSelected = onModeSelected,
        )

        Surface(
          onClick = onSwitchCamera,
          enabled = !uiState.isRecording && countdownRemaining == 0,
          modifier = Modifier.size(52.dp).testTag("switch_camera_button"),
          shape = CircleShape,
          color = hud.pillBackground,
          border = BorderStroke(1.5.dp, hud.pillBorder),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.Cameraswitch,
              contentDescription = stringResource(R.string.cd_switch_camera),
              tint = if (uiState.isRecording || countdownRemaining > 0) hud.textMuted else hud.textPrimary,
            )
          }
        }

        ShutterButton(
          captureMode = uiState.captureMode,
          isCapturing = uiState.isCapturing,
          isRecording = uiState.isRecording,
          countdownSecondsRemaining = countdownRemaining,
          onClick = { handleShutterPress() },
        )

        GalleryThumbnailButton(
          lastCapturedUri = uiState.lastCapturedUri,
          lastCapturedIsVideo = lastCapturedIsVideo,
          enabled = !uiState.isRecording && countdownRemaining == 0,
          onClick = onOpenGallery,
        )
      }
    } else {
      // Portrait / Compact layout: Top telemetry + compass + Bottom Geo-QR Overlay + Pro Controls + Shutter dock
      Column(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TopTelemetryBar(
          geoInfo = uiState.geoInfo,
          hasLocationPermission = uiState.hasLocationPermission,
          geoTaggingEnabled = uiState.preferences.geoTaggingEnabled,
          timestampOnly = uiState.preferences.timestampOnly,
          isDarkTheme = uiState.preferences.isDarkTheme,
          photoFilter = uiState.preferences.photoFilter,
          flashMode = uiState.flashMode,
          showGridLines = uiState.preferences.showGridLines,
          onRefreshLocation = onRefreshLocation,
          onToggleGeoTagging = onToggleGeoTagging,
          onToggleTheme = onToggleTheme,
          onCycleFilter = onCycleFilter,
          onToggleFlash = onToggleFlash,
          onToggleGrid = onToggleGrid,
          onOpenSettings = onOpenSettings,
        )
        if (uiState.preferences.showCompassOverlay) {
          CompassDirectionOverlay(
            compassState = uiState.compassState,
            onClick = onCompassClick,
          )
        }
      }

      Column(
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (uiState.captureMode == CaptureMode.PRO) {
          ProControlPanel(
            settings = uiState.proSettings,
            capabilities = uiState.proCapabilities,
            activeTab = uiState.activeProTab,
            onSelectTab = onSelectProTab,
            onIsoChanged = onProIsoChanged,
            onShutterChanged = onProShutterChanged,
            onEvChanged = onProEvChanged,
            onFocusChanged = onProFocusChanged,
            onWhiteBalanceChanged = onProWbChanged,
            onTimerChanged = onProTimerChanged,
            onResetPro = onResetPro,
          )
        }

        val showTimestampOnly =
          uiState.preferences.timestampOnly ||
            (!uiState.preferences.geoTaggingEnabled && uiState.preferences.showTimestamp)
        when {
          showTimestampOnly -> TimestampOnlyOverlayPill()
          uiState.preferences.geoTaggingEnabled -> {
            GeoOverlayCard(
              geoInfo = uiState.geoInfo,
              preferences = uiState.preferences,
              qrBitmap = uiState.qrBitmap,
              miniMapBitmap = uiState.miniMapBitmap,
              hasLocationPermission = uiState.hasLocationPermission,
              onCopySnippet = onCopySnippet,
              onMapClick = onMapClick,
              onQrClick = onQrClick,
              onEnableLocationClick = onEnableLocationClick,
            )
          }
        }

        val lastCapturedIsVideo =
          uiState.galleryItems.firstOrNull { it.uri == uiState.lastCapturedUri }?.type == MediaType.VIDEO

        BottomShutterDock(
          captureMode = uiState.captureMode,
          lastCapturedUri = uiState.lastCapturedUri,
          lastCapturedIsVideo = lastCapturedIsVideo,
          isCapturing = uiState.isCapturing,
          isRecording = uiState.isRecording,
          recordingDurationMillis = uiState.recordingDurationMillis,
          countdownSecondsRemaining = countdownRemaining,
          onModeSelected = onModeSelected,
          onCaptureClick = { handleShutterPress() },
          onSwitchCamera = onSwitchCamera,
          onOpenGallery = onOpenGallery,
        )
      }
    }
  }
}

@Composable
private fun PermissionGateScreen(
  onGrantClick: () -> Unit,
  onOpenSettingsClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val hud = LocalHudColors.current
  val topGradientColor = if (hud.isDark) Color(0xFF0A1628) else Color(0xFFE0F2FE)

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(topGradientColor, colors.background)
          )
        )
        .safeDrawingPadding()
        .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
      shape = RoundedCornerShape(28.dp),
      color = colors.surface,
      border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
    ) {
      Column(
        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = colors.primary.copy(alpha = 0.14f),
          modifier = Modifier.size(56.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.CameraAlt,
              contentDescription = null,
              tint = colors.primary,
              modifier = Modifier.size(28.dp),
            )
          }
        }

        Text(
          text = stringResource(R.string.permission_title),
          style = MaterialTheme.typography.headlineMedium,
          color = colors.onSurface,
        )

        Text(
          text = stringResource(R.string.permission_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = colors.onSurfaceVariant,
        )

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = colors.surfaceVariant,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            PermissionFeatureRow(
              icon = Icons.Rounded.CameraAlt,
              text = stringResource(R.string.permission_camera_benefit),
            )
            PermissionFeatureRow(
              icon = Icons.Rounded.LocationOn,
              text = stringResource(R.string.permission_location_benefit),
            )
            PermissionFeatureRow(
              icon = Icons.Rounded.QrCode2,
              text = stringResource(R.string.permission_qr_benefit),
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
          onClick = onGrantClick,
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("grant_permissions_button"),
          shape = RoundedCornerShape(14.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = colors.primary,
              contentColor = colors.onPrimary,
            ),
        ) {
          Text(
            text = stringResource(R.string.grant_permissions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }

        OutlinedButton(
          onClick = onOpenSettingsClick,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_settings_button"),
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.2f)),
        ) {
          Text(
            text = stringResource(R.string.open_app_settings),
            color = colors.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun PermissionFeatureRow(
  icon: ImageVector,
  text: String,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = colors.primary,
      modifier = Modifier.size(20.dp),
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = colors.onSurface,
    )
  }
}

private fun hasCameraPermission(context: Context): Boolean =
  ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
    PackageManager.PERMISSION_GRANTED

private fun hasAudioPermission(context: Context): Boolean =
  ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
    PackageManager.PERMISSION_GRANTED

private fun hasAnyLocationPermission(context: Context): Boolean {
  val fine =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED
  val coarse =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED
  return fine || coarse
}

private fun openAppSettings(context: Context) {
  val intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", context.packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  context.startActivity(intent)
}

private fun copyGeoSnippetToClipboard(context: Context, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
  clipboard.setPrimaryClip(ClipData.newPlainText("GeoCam Location", text))
}

private fun shareText(context: Context, text: String, chooserTitle: String) {
  val intent =
    Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
    }
  context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun shareImageUri(context: Context, uri: Uri, chooserTitle: String) {
  shareMediaUri(context = context, uri = uri, mimeType = "image/jpeg", chooserTitle = chooserTitle)
}

private fun shareMediaUri(
  context: Context,
  uri: Uri,
  mimeType: String,
  chooserTitle: String,
) {
  val intent =
    Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun openLocationInMaps(
  context: Context,
  latitude: Double,
  longitude: Double,
  label: String,
) {
  val encodedLabel = Uri.encode(label.ifBlank { "$latitude,$longitude" })
  val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
  val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
  runCatching { context.startActivity(mapIntent) }
    .onFailure {
      val webUri = Uri.parse("https://maps.google.com/?q=$latitude,$longitude")
      runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, webUri)) }
    }
}

private fun applyPreviewFilter(previewView: PreviewView, filter: PhotoFilter) {
  runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val effect =
        if (filter == PhotoFilter.NONE) {
          null
        } else {
          RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(filter.toColorMatrix()))
        }
      previewView.setRenderEffect(effect)
    } else {
      val targetView: View =
        (0 until previewView.childCount)
          .map { previewView.getChildAt(it) }
          .firstOrNull { it is TextureView }
          ?: previewView
      if (filter == PhotoFilter.NONE) {
        targetView.setLayerType(View.LAYER_TYPE_NONE, null)
      } else {
        val paint =
          AndroidPaint().apply {
            colorFilter = ColorMatrixColorFilter(filter.toColorMatrix())
          }
        targetView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
      }
    }
  }
}
