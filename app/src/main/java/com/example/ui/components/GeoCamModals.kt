package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.camera.PhotoFilter
import com.example.data.OverlayPreferences
import com.example.geo.GeoLocationInfo
import com.example.geo.MiniMapTileLoader
import com.example.geo.QrPayloadFormat
import com.example.ui.theme.LocalHudColors
import kotlin.math.roundToInt

@Composable
fun QrExpandedDialog(
  geoInfo: GeoLocationInfo,
  preferences: OverlayPreferences,
  qrBitmap: Bitmap?,
  qrPayload: String?,
  onSelectFormat: (QrPayloadFormat) -> Unit,
  onCopyInfo: () -> Unit,
  onShareLocation: () -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val hud = LocalHudColors.current

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.padding(horizontal = 20.dp)
          .widthIn(max = 460.dp)
          .fillMaxWidth()
          .testTag("qr_expanded_dialog"),
      shape = RoundedCornerShape(28.dp),
      color = colors.surface,
      border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
    ) {
      Column(
        modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.qr_dialog_title),
              style = MaterialTheme.typography.titleLarge,
              color = colors.onSurface,
            )
            Text(
              text = stringResource(R.string.qr_dialog_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = colors.onSurfaceVariant,
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.testTag("qr_dialog_close")) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.close),
              tint = colors.onSurfaceVariant,
            )
          }
        }

        // Format selector chips
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          QrFormatChip(
            label = stringResource(R.string.qr_format_maps),
            selected = preferences.qrPayloadFormat == QrPayloadFormat.MAPS_URL,
            onClick = { onSelectFormat(QrPayloadFormat.MAPS_URL) },
            modifier = Modifier.weight(1f).testTag("qr_chip_maps"),
          )
          QrFormatChip(
            label = stringResource(R.string.qr_format_geo),
            selected = preferences.qrPayloadFormat == QrPayloadFormat.GEO_URI,
            onClick = { onSelectFormat(QrPayloadFormat.GEO_URI) },
            modifier = Modifier.weight(1f).testTag("qr_chip_geo"),
          )
          QrFormatChip(
            label = stringResource(R.string.qr_format_text),
            selected = preferences.qrPayloadFormat == QrPayloadFormat.FULL_SNIPPET,
            onClick = { onSelectFormat(QrPayloadFormat.FULL_SNIPPET) },
            modifier = Modifier.weight(1f).testTag("qr_chip_text"),
          )
        }

        // High-contrast QR container
        val imageBitmap = remember(qrBitmap) { qrBitmap?.asImageBitmap() }
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = Color.White,
          border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.4f)),
          modifier = Modifier.size(230.dp),
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(14.dp)) {
            if (imageBitmap != null) {
              Image(
                bitmap = imageBitmap,
                contentDescription = stringResource(R.string.cd_qr_code),
                modifier = Modifier.size(202.dp),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
              )
            } else {
              Text(
                text = stringResource(R.string.unknown_location),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF0F172A),
              )
            }
          }
        }

        // Encoded location snippet box
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = colors.surfaceVariant,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (geoInfo.addressLine.isNotBlank()) {
              Text(
                text = geoInfo.addressLine,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
              )
            }
            if (geoInfo.area.isNotBlank() || geoInfo.locationSummary.isNotBlank()) {
              Text(
                text = listOf(geoInfo.area, geoInfo.locationSummary)
                  .filter { it.isNotBlank() }
                  .joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
              )
            }
            Text(
              text = geoInfo.formattedCoordinates(preferences.useDmsCoordinates),
              style = MaterialTheme.typography.labelMedium,
              color = colors.primary,
              fontWeight = FontWeight.Bold,
            )
            if (!qrPayload.isNullOrBlank()) {
              Text(
                text = qrPayload,
                style = MaterialTheme.typography.labelSmall,
                color = hud.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onCopyInfo,
            modifier = Modifier.weight(1f).height(48.dp).testTag("qr_copy_button"),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
          ) {
            Icon(
              imageVector = Icons.Rounded.ContentCopy,
              contentDescription = null,
              tint = colors.primary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = stringResource(R.string.copy_coordinates), color = colors.primary)
          }

          Button(
            onClick = onShareLocation,
            modifier = Modifier.weight(1f).height(48.dp).testTag("qr_share_button"),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
              ),
          ) {
            Icon(
              imageVector = Icons.Rounded.Share,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
              text = stringResource(R.string.share_location),
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun QrFormatChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  FilterChip(
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    label = {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = colors.primary.copy(alpha = 0.18f),
        selectedLabelColor = colors.primary,
        labelColor = colors.onSurfaceVariant,
      ),
    border =
      FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = colors.onSurface.copy(alpha = 0.16f),
        selectedBorderColor = colors.primary,
      ),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySettingsSheet(
  preferences: OverlayPreferences,
  onDarkThemeChanged: (Boolean) -> Unit,
  onShutterSoundChanged: (Boolean) -> Unit,
  onGeoTaggingChanged: (Boolean) -> Unit,
  onTimestampOnlyChanged: (Boolean) -> Unit,
  onStampChanged: (Boolean) -> Unit,
  onDmsChanged: (Boolean) -> Unit,
  onTimestampChanged: (Boolean) -> Unit,
  onGridChanged: (Boolean) -> Unit,
  onCompassChanged: (Boolean) -> Unit,
  onMapZoomChanged: (Int) -> Unit,
  onPhotoFilterChanged: (PhotoFilter) -> Unit,
  onFormatChanged: (QrPayloadFormat) -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = colors.surface,
    contentColor = colors.onSurface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp)
          .testTag("overlay_settings_sheet"),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.titleLarge,
        color = colors.onSurface,
      )

      SettingToggleRow(
        title = stringResource(R.string.setting_dark_theme),
        subtitle = stringResource(R.string.setting_dark_theme_desc),
        checked = preferences.isDarkTheme,
        onCheckedChange = onDarkThemeChanged,
        testTag = "switch_dark_theme",
      )
      SettingToggleRow(
        title = stringResource(R.string.setting_shutter_sound),
        subtitle = stringResource(R.string.setting_shutter_sound_desc),
        checked = preferences.shutterSoundEnabled,
        onCheckedChange = onShutterSoundChanged,
        testTag = "switch_shutter_sound",
      )
      SettingToggleRow(
        title = stringResource(R.string.setting_geo_tagging),
        subtitle = stringResource(R.string.setting_geo_tagging_desc),
        checked = preferences.geoTaggingEnabled,
        onCheckedChange = onGeoTaggingChanged,
        testTag = "switch_geo_tagging",
      )
      SettingToggleRow(
        title = stringResource(R.string.setting_timestamp_only),
        subtitle = stringResource(R.string.setting_timestamp_only_desc),
        checked = preferences.timestampOnly,
        onCheckedChange = onTimestampOnlyChanged,
        testTag = "switch_timestamp_only",
      )
      SettingToggleRow(
        title = stringResource(R.string.setting_stamp_capture),
        subtitle = stringResource(R.string.setting_stamp_capture_desc),
        checked = preferences.stampOverlayOnCapture,
        onCheckedChange = onStampChanged,
        testTag = "switch_stamp_capture",
      )
      SettingToggleRow(
        title = stringResource(R.string.setting_dms_coords),
        subtitle = stringResource(R.string.setting_dms_coords_desc),
        checked = preferences.useDmsCoordinates,
        onCheckedChange = onDmsChanged,
        testTag = "switch_dms_coords",
      )
      SettingToggleRow(
        title = stringResource(R.string.setting_show_timestamp),
        subtitle = stringResource(R.string.setting_show_timestamp_desc),
        checked = preferences.showTimestamp,
        onCheckedChange = onTimestampChanged,
        testTag = "switch_show_timestamp",
      )
      SettingToggleRow(
        title = stringResource(R.string.setting_grid_lines),
        subtitle = stringResource(R.string.setting_grid_lines_desc),
        checked = preferences.showGridLines,
        onCheckedChange = onGridChanged,
        testTag = "switch_grid_lines",
      )
      SettingToggleRow(
        title = stringResource(R.string.setting_compass_overlay),
        subtitle = stringResource(R.string.setting_compass_overlay_desc),
        checked = preferences.showCompassOverlay,
        onCheckedChange = onCompassChanged,
        testTag = "switch_compass_overlay",
      )

      HorizontalDivider(color = colors.onSurface.copy(alpha = 0.12f))

      PhotoFilterSettingSection(
        selectedFilter = preferences.photoFilter,
        onFilterSelected = onPhotoFilterChanged,
      )

      HorizontalDivider(color = colors.onSurface.copy(alpha = 0.12f))

      MapZoomSettingSection(
        zoomLevel = preferences.mapZoomLevel,
        onZoomChanged = onMapZoomChanged,
      )

      HorizontalDivider(color = colors.onSurface.copy(alpha = 0.12f))

      Text(
        text = stringResource(R.string.setting_qr_payload),
        style = MaterialTheme.typography.titleMedium,
        color = colors.onSurface,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        QrFormatChip(
          label = stringResource(R.string.qr_format_maps),
          selected = preferences.qrPayloadFormat == QrPayloadFormat.MAPS_URL,
          onClick = { onFormatChanged(QrPayloadFormat.MAPS_URL) },
          modifier = Modifier.weight(1f),
        )
        QrFormatChip(
          label = stringResource(R.string.qr_format_geo),
          selected = preferences.qrPayloadFormat == QrPayloadFormat.GEO_URI,
          onClick = { onFormatChanged(QrPayloadFormat.GEO_URI) },
          modifier = Modifier.weight(1f),
        )
        QrFormatChip(
          label = stringResource(R.string.qr_format_text),
          selected = preferences.qrPayloadFormat == QrPayloadFormat.FULL_SNIPPET,
          onClick = { onFormatChanged(QrPayloadFormat.FULL_SNIPPET) },
          modifier = Modifier.weight(1f),
        )
      }

      HorizontalDivider(color = colors.onSurface.copy(alpha = 0.12f))

      CreditsSection()
    }
  }
}

@Composable
private fun CreditsSection() {
  val colors = MaterialTheme.colorScheme
  val context = LocalContext.current
  val websiteUrl = stringResource(R.string.credits_website_url)
  val githubUrl = stringResource(R.string.credits_github_url)

  fun openExternalUrl(url: String) {
    runCatching {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    }
  }

  Column(
    modifier = Modifier.fillMaxWidth().testTag("credits_section"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(R.string.credits_section_title),
        style = MaterialTheme.typography.titleMedium,
        color = colors.onSurface,
      )
      Surface(
        shape = RoundedCornerShape(50),
        color = colors.primary.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
      ) {
        Text(
          text = stringResource(R.string.credits_badge),
          style = MaterialTheme.typography.labelSmall,
          color = colors.primary,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }
    }

    Surface(
      shape = RoundedCornerShape(18.dp),
      color = colors.surfaceVariant,
      border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.1f)),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = stringResource(R.string.app_long_name),
          style = MaterialTheme.typography.titleSmall,
          color = colors.primary,
          fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        CreditsInfoRow(
          icon = Icons.Rounded.Person,
          label = stringResource(R.string.credits_creator_label),
          value = stringResource(R.string.credits_creator_name),
        )

        CreditsLinkRow(
          icon = Icons.Rounded.Language,
          label = stringResource(R.string.credits_website_label),
          value = stringResource(R.string.credits_website_display),
          onClick = { openExternalUrl(websiteUrl) },
          testTag = "credits_website_link",
        )

        CreditsLinkRow(
          icon = Icons.Rounded.Code,
          label = stringResource(R.string.credits_github_label),
          value = stringResource(R.string.credits_github_display),
          onClick = { openExternalUrl(githubUrl) },
          testTag = "credits_github_link",
        )
      }
    }
  }
}

@Composable
private fun CreditsInfoRow(
  icon: ImageVector,
  label: String,
  value: String,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = colors.primary,
      modifier = Modifier.size(18.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
private fun CreditsLinkRow(
  icon: ImageVector,
  label: String,
  value: String,
  onClick: () -> Unit,
  testTag: String,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize()
        .padding(vertical = 6.dp, horizontal = 2.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = colors.primary,
      modifier = Modifier.size(18.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.primary,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Icon(
      imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
      contentDescription = label,
      tint = colors.onSurfaceVariant,
      modifier = Modifier.size(16.dp),
    )
  }
}

@Composable
private fun PhotoFilterSettingSection(
  selectedFilter: PhotoFilter,
  onFilterSelected: (PhotoFilter) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.setting_photo_filter),
          style = MaterialTheme.typography.bodyLarge,
          color = colors.onSurface,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = stringResource(R.string.setting_photo_filter_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = colors.onSurfaceVariant,
        )
      }
      Surface(
        shape = RoundedCornerShape(50),
        color = colors.primary.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
      ) {
        Text(
          text = selectedFilter.displayName,
          style = MaterialTheme.typography.labelMedium,
          color = colors.primary,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
    }

    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(PhotoFilter.entries) { filter ->
        val isSelected = filter == selectedFilter
        FilterChip(
          selected = isSelected,
          onClick = { onFilterSelected(filter) },
          modifier = Modifier.testTag("chip_filter_${filter.name.lowercase()}"),
          leadingIcon = {
            Box(
              modifier =
                Modifier.size(12.dp)
                  .clip(CircleShape)
                  .background(filter.swatchColor)
            )
          },
          label = {
            Text(
              text = filter.displayName,
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
            )
          },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = colors.primary.copy(alpha = 0.18f),
              selectedLabelColor = colors.primary,
              labelColor = colors.onSurfaceVariant,
            ),
          border =
            FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = isSelected,
              borderColor = colors.onSurface.copy(alpha = 0.16f),
              selectedBorderColor = colors.primary,
            ),
        )
      }
    }
  }
}

@Composable
private fun MapZoomSettingSection(
  zoomLevel: Int,
  onZoomChanged: (Int) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  var sliderPosition by remember(zoomLevel) { mutableFloatStateOf(zoomLevel.toFloat()) }
  val displayZoom =
    sliderPosition.roundToInt().coerceIn(MiniMapTileLoader.MIN_ZOOM, MiniMapTileLoader.MAX_ZOOM)
  val zoomScaleLabel =
    when {
      displayZoom <= 13 -> stringResource(R.string.map_zoom_city)
      displayZoom <= 15 -> stringResource(R.string.map_zoom_district)
      displayZoom <= 17 -> stringResource(R.string.map_zoom_street)
      else -> stringResource(R.string.map_zoom_closeup)
    }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.setting_map_zoom),
          style = MaterialTheme.typography.bodyLarge,
          color = colors.onSurface,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = stringResource(R.string.setting_map_zoom_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = colors.onSurfaceVariant,
        )
      }
      Surface(
        shape = RoundedCornerShape(50),
        color = colors.primary.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
      ) {
        Text(
          text = "Z$displayZoom • $zoomScaleLabel",
          style = MaterialTheme.typography.labelMedium,
          color = colors.primary,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
    }

    Slider(
      value = sliderPosition,
      onValueChange = { value ->
        sliderPosition = value
        val next = value.roundToInt().coerceIn(MiniMapTileLoader.MIN_ZOOM, MiniMapTileLoader.MAX_ZOOM)
        if (next != zoomLevel) onZoomChanged(next)
      },
      valueRange = MiniMapTileLoader.MIN_ZOOM.toFloat()..MiniMapTileLoader.MAX_ZOOM.toFloat(),
      steps = MiniMapTileLoader.MAX_ZOOM - MiniMapTileLoader.MIN_ZOOM - 1,
      modifier = Modifier.fillMaxWidth().testTag("slider_map_zoom"),
      colors =
        SliderDefaults.colors(
          thumbColor = colors.primary,
          activeTrackColor = colors.primary,
          inactiveTrackColor = colors.onSurface.copy(alpha = 0.18f),
          activeTickColor = colors.onPrimary,
          inactiveTickColor = colors.onSurface.copy(alpha = 0.35f),
        ),
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val presets = listOf(12, 14, 16, 18)
      presets.forEach { preset ->
        QrFormatChip(
          label = "Z$preset",
          selected = displayZoom == preset,
          onClick = {
            sliderPosition = preset.toFloat()
            onZoomChanged(preset)
          },
          modifier = Modifier.weight(1f).testTag("chip_map_zoom_$preset"),
        )
      }
    }
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurface,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = colors.onPrimary,
          checkedTrackColor = colors.primary,
        ),
    )
  }
}

@Composable
fun PhotoPreviewDialog(
  photoUri: Uri,
  onSharePhoto: (Uri) -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.padding(16.dp)
          .widthIn(max = 540.dp)
          .fillMaxWidth()
          .testTag("photo_preview_dialog"),
      shape = RoundedCornerShape(24.dp),
      color = colors.surface,
      border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.preview_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
          )
          IconButton(onClick = onDismiss) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.close),
              tint = colors.onSurfaceVariant,
            )
          }
        }

        Box(
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(min = 240.dp, max = 480.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(colors.background),
          contentAlignment = Alignment.Center,
        ) {
          AsyncImage(
            model = photoUri,
            contentDescription = stringResource(R.string.preview_title),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).height(48.dp).testTag("preview_back_button"),
          ) {
            Text(text = stringResource(R.string.retake), color = colors.onSurface)
          }
          Button(
            onClick = { onSharePhoto(photoUri) },
            modifier = Modifier.weight(1f).height(48.dp).testTag("preview_share_button"),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
              ),
          ) {
            Icon(
              imageVector = Icons.Rounded.Share,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = stringResource(R.string.share_photo), fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
