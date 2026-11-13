package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.OverlayPreferences
import com.example.geo.GeoLocationInfo
import com.example.ui.theme.LocalHudColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun TimestampOnlyOverlayPill(modifier: Modifier = Modifier) {
  val hud = LocalHudColors.current
  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      nowMillis = System.currentTimeMillis()
      delay(1000L)
    }
  }
  val formattedTime =
    remember(nowMillis) {
      SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale.getDefault()).format(Date(nowMillis))
    }

  Surface(
    modifier = modifier.fillMaxWidth().testTag("timestamp_only_overlay"),
    shape = RoundedCornerShape(18.dp),
    color = hud.glassBackground,
    border = BorderStroke(1.dp, hud.glassBorder),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Schedule,
          contentDescription = null,
          tint = hud.accent,
          modifier = Modifier.size(15.dp),
        )
        Text(
          text = formattedTime,
          style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
          color = hud.textPrimary,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      HorizontalDivider(color = hud.accent.copy(alpha = 0.24f), thickness = 1.dp)
      Text(
        text = stringResource(R.string.geo_snippet_brand),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
        color = hud.accent,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun GeoOverlayCard(
  geoInfo: GeoLocationInfo,
  preferences: OverlayPreferences,
  qrBitmap: Bitmap?,
  miniMapBitmap: Bitmap?,
  hasLocationPermission: Boolean,
  onCopySnippet: () -> Unit,
  onMapClick: () -> Unit,
  onQrClick: () -> Unit,
  onEnableLocationClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hud = LocalHudColors.current

  Surface(
    modifier = modifier.fillMaxWidth().testTag("geo_overlay_card"),
    shape = RoundedCornerShape(20.dp),
    color = hud.glassBackground,
    border = BorderStroke(1.dp, hud.glassBorder),
    tonalElevation = 0.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Left Column: Actual Mini Map
        MiniMapBadge(
          miniMapBitmap = miniMapBitmap,
          mapZoomLevel = preferences.mapZoomLevel,
          hasLocationPermission = hasLocationPermission,
          isLoading = geoInfo.isLoading,
          onClick = if (hasLocationPermission) onMapClick else onEnableLocationClick,
        )

        // Center Column: Text Snippet (Area, Address, Location, Coordinates, Timestamp)
        Column(
          modifier =
            Modifier.weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .clickable(onClick = if (hasLocationPermission) onCopySnippet else onEnableLocationClick)
              .padding(vertical = 2.dp)
              .semantics(mergeDescendants = true) {}
              .testTag("geo_snippet_area"),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            val topTagText =
              when {
                !hasLocationPermission -> stringResource(R.string.location_permission_needed)
                geoInfo.area.isNotBlank() -> geoInfo.area
                geoInfo.hasCoordinates -> stringResource(R.string.unknown_area)
                else -> stringResource(R.string.gps_locating)
              }
            Surface(
              modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp),
              color =
                if (hasLocationPermission) hud.accent.copy(alpha = 0.14f)
                else hud.alertRed.copy(alpha = 0.16f),
              shape = RoundedCornerShape(6.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                if (!hasLocationPermission) {
                  Icon(
                    imageVector = Icons.Rounded.LocationOff,
                    contentDescription = null,
                    tint = hud.alertRed,
                    modifier = Modifier.size(11.dp),
                  )
                }
                Text(
                  text = topTagText,
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                  color = if (hasLocationPermission) hud.accent else hud.alertRed,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            if (hasLocationPermission && geoInfo.hasCoordinates) {
              Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.copy_coordinates),
                tint = hud.textMuted,
                modifier = Modifier.size(13.dp),
              )
            }
          }

          // Address line
          val addressText =
            when {
              !hasLocationPermission -> stringResource(R.string.grant_permissions)
              geoInfo.addressLine.isNotBlank() -> geoInfo.addressLine
              else -> stringResource(R.string.resolving_address)
            }
          Text(
            text = addressText,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp, lineHeight = 17.sp),
            color = hud.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          // Location summary (City, State, Postal, Country)
          val locationText =
            when {
              !hasLocationPermission -> stringResource(R.string.unknown_location)
              geoInfo.locationSummary.isNotBlank() -> geoInfo.locationSummary
              geoInfo.statusNote != null -> geoInfo.statusNote
              else -> stringResource(R.string.unknown_location)
            }
          Text(
            text = locationText,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
            color = hud.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          // Coordinates
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.GpsFixed,
              contentDescription = null,
              tint = hud.accent,
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = geoInfo.formattedCoordinates(preferences.useDmsCoordinates),
              style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
              color = hud.accent,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          if (preferences.showTimestamp || geoInfo.accuracyMeters != null) {
            val metaLine = buildString {
              if (preferences.showTimestamp) {
                append(geoInfo.formattedTimestamp())
              }
              val acc = geoInfo.accuracyMeters
              if (acc != null) {
                if (isNotEmpty()) append(" • ")
                append("±${acc.roundToInt()}m")
              }
            }
            Text(
              text = metaLine,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
              color = hud.textMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        // Right Column: Scannable Location QR Code Overlay
        QrOverlayBadge(
          qrBitmap = qrBitmap,
          hasLocationPermission = hasLocationPermission,
          isLoading = geoInfo.isLoading,
          onClick = if (hasLocationPermission) onQrClick else onEnableLocationClick,
        )
      }

      HorizontalDivider(color = hud.accent.copy(alpha = 0.24f), thickness = 1.dp)

      // Bottom Full-Width Centered Branding Text
      Text(
        text = stringResource(R.string.geo_snippet_brand),
        modifier = Modifier.fillMaxWidth().testTag("geo_brand_footer"),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
        color = hud.accent,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun MiniMapBadge(
  miniMapBitmap: Bitmap?,
  mapZoomLevel: Int,
  hasLocationPermission: Boolean,
  isLoading: Boolean,
  onClick: () -> Unit,
) {
  val hud = LocalHudColors.current
  val imageBitmap = remember(miniMapBitmap) { miniMapBitmap?.asImageBitmap() }

  Column(
    modifier =
      Modifier.clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .semantics(mergeDescendants = true) {}
        .testTag("mini_map_overlay"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Surface(
      modifier = Modifier.size(72.dp),
      shape = RoundedCornerShape(12.dp),
      color = hud.mapPlaceholderBg,
      border = BorderStroke(1.dp, hud.accent.copy(alpha = 0.55f)),
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        when {
          imageBitmap != null -> {
            Image(
              bitmap = imageBitmap,
              contentDescription = stringResource(R.string.cd_mini_map),
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
          }
          isLoading && hasLocationPermission -> {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = hud.accent,
              strokeWidth = 2.dp,
            )
          }
          else -> {
            Icon(
              imageVector = Icons.Rounded.Map,
              contentDescription = stringResource(R.string.cd_mini_map),
              tint = hud.textMuted,
              modifier = Modifier.size(30.dp),
            )
          }
        }
      }
    }

    Text(
      text =
        if (imageBitmap != null) "${stringResource(R.string.map_label)} Z$mapZoomLevel"
        else if (!hasLocationPermission) "ENABLE"
        else "WAIT GPS",
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
      color = if (imageBitmap != null) hud.accent else hud.textMuted,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun QrOverlayBadge(
  qrBitmap: Bitmap?,
  hasLocationPermission: Boolean,
  isLoading: Boolean,
  onClick: () -> Unit,
) {
  val hud = LocalHudColors.current
  val imageBitmap = remember(qrBitmap) { qrBitmap?.asImageBitmap() }

  Column(
    modifier =
      Modifier.clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .semantics(mergeDescendants = true) {}
        .testTag("location_qr_overlay"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Surface(
      modifier = Modifier.size(72.dp),
      shape = RoundedCornerShape(12.dp),
      color = if (imageBitmap != null) Color.White else hud.mapPlaceholderBg,
      border = BorderStroke(1.dp, hud.accent.copy(alpha = 0.5f)),
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
        when {
          imageBitmap != null -> {
            Image(
              bitmap = imageBitmap,
              contentDescription = stringResource(R.string.cd_qr_code),
              modifier = Modifier.size(64.dp),
              contentScale = ContentScale.Fit,
              filterQuality = FilterQuality.None,
            )
          }
          isLoading && hasLocationPermission -> {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = hud.accent,
              strokeWidth = 2.dp,
            )
          }
          else -> {
            Icon(
              imageVector = Icons.Rounded.QrCode2,
              contentDescription = stringResource(R.string.cd_qr_code),
              tint = hud.textMuted,
              modifier = Modifier.size(32.dp),
            )
          }
        }
      }
    }

    Text(
      text =
        if (imageBitmap != null) stringResource(R.string.scan_geo)
        else if (!hasLocationPermission) "ENABLE"
        else "WAIT GPS",
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
      color = if (imageBitmap != null) hud.accent else hud.textMuted,
      fontWeight = FontWeight.Bold,
    )
  }
}
