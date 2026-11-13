package com.example.ui.components

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraEnhance
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.example.R
import com.example.camera.CaptureMode
import com.example.camera.PhotoFilter
import com.example.geo.GeoLocationInfo
import com.example.media.CapturedMediaRepository
import com.example.ui.theme.LocalHudColors
import kotlin.math.roundToInt

@Composable
fun TopTelemetryBar(
  geoInfo: GeoLocationInfo,
  hasLocationPermission: Boolean,
  geoTaggingEnabled: Boolean,
  timestampOnly: Boolean,
  isDarkTheme: Boolean,
  photoFilter: PhotoFilter,
  flashMode: Int,
  showGridLines: Boolean,
  onRefreshLocation: () -> Unit,
  onToggleGeoTagging: () -> Unit,
  onToggleTheme: () -> Unit,
  onCycleFilter: () -> Unit,
  onToggleFlash: () -> Unit,
  onToggleGrid: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hud = LocalHudColors.current

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Live GPS / Mode status badge
      val statusColor by
        animateColorAsState(
          targetValue =
            when {
              timestampOnly -> hud.accent
              !geoTaggingEnabled -> hud.textMuted
              !hasLocationPermission -> hud.alertRed
              geoInfo.hasCoordinates -> hud.signalGreen
              else -> hud.warningAmber
            },
          label = "gpsStatusColor",
        )

      Surface(
        shape = RoundedCornerShape(50),
        color = hud.pillBackground,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f)),
        modifier =
          Modifier.weight(1f, fill = false)
            .padding(end = 8.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(
              onClick = {
                if (geoTaggingEnabled && !timestampOnly) onRefreshLocation()
                else onOpenSettings()
              }
            )
            .semantics(mergeDescendants = true) {}
            .testTag("gps_status_pill"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor)
          )
          val label =
            when {
              timestampOnly -> stringResource(R.string.time_only)
              !geoTaggingEnabled -> stringResource(R.string.geo_off)
              !hasLocationPermission -> stringResource(R.string.gps_unavailable)
              geoInfo.hasCoordinates -> {
                val acc = geoInfo.accuracyMeters?.let { " ±${it.roundToInt()}m" }.orEmpty()
                "${stringResource(R.string.gps_locked)}$acc"
              }
              else -> stringResource(R.string.gps_locating)
            }
          Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = hud.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (geoTaggingEnabled && !timestampOnly) {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = stringResource(R.string.cd_refresh_location),
              tint = hud.accent,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      // Camera quick controls pill
      Surface(
        shape = RoundedCornerShape(50),
        color = hud.pillBackground,
        border = BorderStroke(1.dp, hud.pillBorder),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(0.dp),
          modifier = Modifier.padding(horizontal = 2.dp),
        ) {
          IconButton(
            onClick = onToggleGeoTagging,
            modifier = Modifier.testTag("geo_toggle_button"),
          ) {
            Icon(
              imageVector =
                if (geoTaggingEnabled) Icons.Rounded.LocationOn else Icons.Rounded.LocationOff,
              contentDescription = stringResource(R.string.cd_toggle_geo),
              tint = if (geoTaggingEnabled) hud.accent else hud.textMuted,
            )
          }

          val flashIcon =
            when (flashMode) {
              ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
              ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
              else -> Icons.Rounded.FlashOff
            }
          IconButton(
            onClick = onToggleFlash,
            modifier = Modifier.testTag("flash_toggle_button"),
          ) {
            Icon(
              imageVector = flashIcon,
              contentDescription = stringResource(R.string.cd_toggle_flash),
              tint = if (flashMode == ImageCapture.FLASH_MODE_ON) hud.warningAmber else hud.textPrimary,
            )
          }

          IconButton(
            onClick = onToggleGrid,
            modifier = Modifier.testTag("grid_toggle_button"),
          ) {
            Icon(
              imageVector = if (showGridLines) Icons.Rounded.GridOn else Icons.Rounded.GridOff,
              contentDescription = stringResource(R.string.cd_toggle_grid),
              tint = if (showGridLines) hud.accent else hud.textPrimary,
            )
          }

          IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("settings_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Tune,
              contentDescription = stringResource(R.string.cd_overlay_settings),
              tint = hud.textPrimary,
            )
          }
        }
      }
    }

    // Quick Filter & Light/Dark Mode toggle buttons tucked neatly at TopEnd
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val hasActiveFilter = photoFilter != PhotoFilter.NONE
      Surface(
        onClick = onCycleFilter,
        shape = RoundedCornerShape(50),
        color = hud.pillBackground,
        border =
          BorderStroke(
            1.dp,
            if (hasActiveFilter) hud.accent.copy(alpha = 0.7f) else hud.pillBorder,
          ),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("filter_cycle_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = stringResource(R.string.cd_cycle_filter),
            tint = if (hasActiveFilter) hud.accent else hud.textMuted,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = photoFilter.shortLabel,
            style = MaterialTheme.typography.labelMedium,
            color = if (hasActiveFilter) hud.accent else hud.textPrimary,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      Surface(
        onClick = onToggleTheme,
        shape = RoundedCornerShape(50),
        color = hud.pillBackground,
        border = BorderStroke(1.dp, hud.pillBorder),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("theme_toggle_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            contentDescription = stringResource(R.string.cd_toggle_theme),
            tint = if (isDarkTheme) hud.warningAmber else hud.accent,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = if (isDarkTheme) "LIGHT" else "DARK",
            style = MaterialTheme.typography.labelMedium,
            color = hud.textPrimary,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

@Composable
fun CaptureModePill(
  captureMode: CaptureMode,
  isRecording: Boolean,
  recordingDurationMillis: Long,
  onModeSelected: (CaptureMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hud = LocalHudColors.current

  if (isRecording) {
    RecordingTimerPill(
      durationMillis = recordingDurationMillis,
      modifier = modifier,
    )
    return
  }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(50),
    color = hud.pillBackground,
    border = BorderStroke(1.dp, hud.pillBorder),
  ) {
    Row(
      modifier = Modifier.padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CaptureModeChip(
        label = stringResource(R.string.mode_photo),
        icon = Icons.Rounded.PhotoCamera,
        selected = captureMode == CaptureMode.PHOTO,
        onClick = { onModeSelected(CaptureMode.PHOTO) },
        testTag = "mode_photo_button",
      )
      CaptureModeChip(
        label = stringResource(R.string.mode_pro),
        icon = Icons.Rounded.CameraEnhance,
        selected = captureMode == CaptureMode.PRO,
        onClick = { onModeSelected(CaptureMode.PRO) },
        testTag = "mode_pro_button",
      )
      CaptureModeChip(
        label = stringResource(R.string.mode_video),
        icon = Icons.Rounded.Videocam,
        selected = captureMode == CaptureMode.VIDEO,
        onClick = { onModeSelected(CaptureMode.VIDEO) },
        testTag = "mode_video_button",
      )
    }
  }
}

@Composable
private fun CaptureModeChip(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val hud = LocalHudColors.current
  val bg = if (selected) hud.accent else Color.Transparent
  val fg =
    if (selected) {
      if (hud.isDark) Color(0xFF06131F) else Color.White
    } else {
      hud.textSecondary
    }

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(50),
    color = bg,
    modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(15.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun RecordingTimerPill(
  durationMillis: Long,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.testTag("recording_timer_pill"),
    shape = RoundedCornerShape(50),
    color = Color(0xCC991B1B),
    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFEF4444))
      )
      Text(
        text = "${stringResource(R.string.recording_live)} ${CapturedMediaRepository.formatDuration(durationMillis)}",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun GalleryThumbnailButton(
  lastCapturedUri: Uri?,
  lastCapturedIsVideo: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hud = LocalHudColors.current
  val context = LocalContext.current
  val thumbLoader =
    remember(context) {
      ImageLoader.Builder(context)
        .components { add(VideoFrameDecoder.Factory()) }
        .build()
    }
  DisposableEffect(thumbLoader) {
    onDispose { thumbLoader.shutdown() }
  }

  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.size(52.dp).testTag("last_photo_button"),
    shape = CircleShape,
    color = hud.pillBackground,
    border =
      BorderStroke(
        width = 1.5.dp,
        color = if (lastCapturedUri != null) hud.accent else hud.pillBorder,
      ),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      if (lastCapturedUri != null) {
        AsyncImage(
          model = lastCapturedUri,
          imageLoader = thumbLoader,
          contentDescription = stringResource(R.string.cd_open_gallery),
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
        if (lastCapturedIsVideo) {
          Box(
            modifier =
              Modifier.size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.PlayArrow,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      } else {
        Icon(
          imageVector = Icons.Rounded.PhotoLibrary,
          contentDescription = stringResource(R.string.cd_open_gallery),
          tint = hud.textPrimary,
          modifier = Modifier.size(22.dp),
        )
      }
    }
  }
}

@Composable
fun BottomShutterDock(
  captureMode: CaptureMode,
  lastCapturedUri: Uri?,
  lastCapturedIsVideo: Boolean,
  isCapturing: Boolean,
  isRecording: Boolean,
  recordingDurationMillis: Long,
  countdownSecondsRemaining: Int = 0,
  onModeSelected: (CaptureMode) -> Unit,
  onCaptureClick: () -> Unit,
  onSwitchCamera: () -> Unit,
  onOpenGallery: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hud = LocalHudColors.current

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    CaptureModePill(
      captureMode = captureMode,
      isRecording = isRecording,
      recordingDurationMillis = recordingDurationMillis,
      onModeSelected = onModeSelected,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      GalleryThumbnailButton(
        lastCapturedUri = lastCapturedUri,
        lastCapturedIsVideo = lastCapturedIsVideo,
        enabled = !isRecording && countdownSecondsRemaining == 0,
        onClick = onOpenGallery,
      )

      // Tactile Shutter / Video Record Button
      ShutterButton(
        captureMode = captureMode,
        isCapturing = isCapturing,
        isRecording = isRecording,
        countdownSecondsRemaining = countdownSecondsRemaining,
        onClick = onCaptureClick,
      )

      // Switch front/back camera
      Surface(
        onClick = onSwitchCamera,
        enabled = !isRecording && countdownSecondsRemaining == 0,
        modifier = Modifier.size(52.dp).testTag("switch_camera_button"),
        shape = CircleShape,
        color = hud.pillBackground,
        border = BorderStroke(1.5.dp, hud.pillBorder),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
          Icon(
            imageVector = Icons.Rounded.Cameraswitch,
            contentDescription = stringResource(R.string.cd_switch_camera),
            tint = if (isRecording || countdownSecondsRemaining > 0) hud.textMuted else hud.textPrimary,
            modifier = Modifier.size(24.dp),
          )
        }
      }
    }
  }
}

@Composable
fun ShutterButton(
  captureMode: CaptureMode,
  isCapturing: Boolean,
  isRecording: Boolean,
  countdownSecondsRemaining: Int = 0,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hud = LocalHudColors.current
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed) 0.9f else 1f,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "shutterScale",
    )

  val ringColor =
    when {
      isRecording -> Color(0xFFEF4444)
      countdownSecondsRemaining > 0 || captureMode == CaptureMode.PRO -> hud.accent
      hud.isDark -> Color.White
      else -> hud.textPrimary
    }
  val innerFill =
    when {
      isCapturing -> hud.accent
      countdownSecondsRemaining > 0 -> hud.warningAmber
      captureMode == CaptureMode.VIDEO && !isRecording -> Color(0xFFEF4444)
      captureMode == CaptureMode.VIDEO && isRecording -> Color(0xFF1F2937)
      else -> Color.White
    }

  Box(
    modifier =
      modifier
        .size(76.dp)
        .scale(scale)
        .clip(CircleShape)
        .border(3.5.dp, ringColor, CircleShape)
        .padding(6.dp)
        .clip(CircleShape)
        .background(innerFill)
        .clickable(
          enabled = !isCapturing,
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
        )
        .testTag(if (captureMode == CaptureMode.VIDEO) "record_video_button" else "capture_shutter_button"),
    contentAlignment = Alignment.Center,
  ) {
    when {
      isCapturing -> {
        CircularProgressIndicator(
          modifier = Modifier.size(26.dp),
          color = Color(0xFF070B12),
          strokeWidth = 3.dp,
        )
      }
      countdownSecondsRemaining > 0 -> {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = stringResource(R.string.cancel_action),
          tint = Color(0xFF070B12),
          modifier = Modifier.size(26.dp),
        )
      }
      captureMode == CaptureMode.VIDEO && isRecording -> {
        Box(
          modifier =
            Modifier.size(24.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Color(0xFFEF4444))
        )
      }
      captureMode == CaptureMode.VIDEO -> {
        Box(
          modifier =
            Modifier.size(16.dp)
              .clip(CircleShape)
              .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
        )
      }
      captureMode == CaptureMode.PRO -> {
        Text(
          text = stringResource(R.string.mode_pro),
          style = MaterialTheme.typography.labelSmall,
          color = Color(0xFF070B12),
          fontWeight = FontWeight.ExtraBold,
        )
      }
      else -> {
        Box(
          modifier =
            Modifier.size(18.dp)
              .clip(CircleShape)
              .border(1.5.dp, hud.accent.copy(alpha = 0.85f), CircleShape)
        )
      }
    }
  }
}

@Composable
fun CameraGridOverlay(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier.fillMaxSize()) {
    val lineColor = Color.White.copy(alpha = 0.26f)
    val stroke = 1.dp.toPx()

    val thirdW = size.width / 3f
    val thirdH = size.height / 3f

    drawLine(lineColor, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = stroke)
    drawLine(
      lineColor,
      Offset(thirdW * 2f, 0f),
      Offset(thirdW * 2f, size.height),
      strokeWidth = stroke,
    )
    drawLine(lineColor, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = stroke)
    drawLine(
      lineColor,
      Offset(0f, thirdH * 2f),
      Offset(size.width, thirdH * 2f),
      strokeWidth = stroke,
    )
  }
}
