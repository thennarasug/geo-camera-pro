package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.camera.ProCameraCapabilities
import com.example.camera.ProCameraController
import com.example.camera.ProCameraSettings
import com.example.camera.ProControlTab
import com.example.camera.ProWhiteBalance
import com.example.ui.theme.LocalHudColors
import kotlin.math.roundToInt

@Composable
fun ProControlPanel(
  settings: ProCameraSettings,
  capabilities: ProCameraCapabilities,
  activeTab: ProControlTab,
  onSelectTab: (ProControlTab) -> Unit,
  onIsoChanged: (Int?) -> Unit,
  onShutterChanged: (Long?) -> Unit,
  onEvChanged: (Int) -> Unit,
  onFocusChanged: (Float?) -> Unit,
  onWhiteBalanceChanged: (ProWhiteBalance) -> Unit,
  onTimerChanged: (Int) -> Unit,
  onResetPro: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hud = LocalHudColors.current

  Column(
    modifier = modifier.fillMaxWidth().testTag("pro_control_panel"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    AnimatedVisibility(
      visible = activeTab != ProControlTab.NONE,
      enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
      exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = hud.glassBackground,
        border = BorderStroke(1.dp, hud.accent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().testTag("pro_adjustment_tray"),
      ) {
        when (activeTab) {
          ProControlTab.ISO -> {
            IsoAdjustmentTray(
              selectedIso = settings.iso,
              capabilities = capabilities,
              onSelectIso = onIsoChanged,
            )
          }
          ProControlTab.SHUTTER -> {
            ShutterAdjustmentTray(
              selectedShutterNs = settings.shutterSpeedNs,
              capabilities = capabilities,
              onSelectShutter = onShutterChanged,
            )
          }
          ProControlTab.EV -> {
            EvAdjustmentTray(
              evIndex = settings.exposureCompIndex,
              isOverriddenByManual = settings.isManualExposure && capabilities.isManualSensorSupported,
              capabilities = capabilities,
              onEvChanged = onEvChanged,
            )
          }
          ProControlTab.FOCUS -> {
            FocusAdjustmentTray(
              focusDistance = settings.focusDistance,
              capabilities = capabilities,
              onFocusChanged = onFocusChanged,
            )
          }
          ProControlTab.WB -> {
            WhiteBalanceAdjustmentTray(
              selectedWb = settings.whiteBalance,
              availableModes = capabilities.availableAwbModes,
              onSelectWb = onWhiteBalanceChanged,
            )
          }
          ProControlTab.TIMER -> {
            TimerAdjustmentTray(
              timerSeconds = settings.timerSeconds,
              onSelectTimer = onTimerChanged,
            )
          }
          ProControlTab.NONE -> Unit
        }
      }
    }

    // Parameter dial pills strip
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = hud.pillBackground,
      border = BorderStroke(1.dp, hud.pillBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ProParamPill(
          label = stringResource(R.string.pro_iso),
          value = settings.iso?.toString() ?: stringResource(R.string.pro_auto),
          isModified = settings.iso != null,
          isSelected = activeTab == ProControlTab.ISO,
          onClick = {
            onSelectTab(if (activeTab == ProControlTab.ISO) ProControlTab.NONE else ProControlTab.ISO)
          },
          testTag = "pro_tab_iso",
        )
        ProParamPill(
          label = stringResource(R.string.pro_shutter_short),
          value =
            settings.shutterSpeedNs?.let { ProCameraController.formatShutterSpeed(it) }
              ?: stringResource(R.string.pro_auto),
          isModified = settings.shutterSpeedNs != null,
          isSelected = activeTab == ProControlTab.SHUTTER,
          onClick = {
            onSelectTab(
              if (activeTab == ProControlTab.SHUTTER) ProControlTab.NONE else ProControlTab.SHUTTER
            )
          },
          testTag = "pro_tab_shutter",
        )
        ProParamPill(
          label = stringResource(R.string.pro_ev),
          value = ProCameraController.formatEv(settings.exposureCompIndex, capabilities.exposureCompStep),
          isModified = settings.exposureCompIndex != 0,
          isSelected = activeTab == ProControlTab.EV,
          onClick = {
            onSelectTab(if (activeTab == ProControlTab.EV) ProControlTab.NONE else ProControlTab.EV)
          },
          testTag = "pro_tab_ev",
        )
        ProParamPill(
          label = if (settings.focusDistance == null) stringResource(R.string.pro_af) else stringResource(R.string.pro_mf),
          value = ProCameraController.formatFocusLabel(settings.focusDistance, capabilities.minFocusDistance),
          isModified = settings.focusDistance != null,
          isSelected = activeTab == ProControlTab.FOCUS,
          onClick = {
            onSelectTab(
              if (activeTab == ProControlTab.FOCUS) ProControlTab.NONE else ProControlTab.FOCUS
            )
          },
          testTag = "pro_tab_focus",
        )
        ProParamPill(
          label = stringResource(R.string.pro_wb),
          value = settings.whiteBalance.shortLabel,
          isModified = settings.whiteBalance != ProWhiteBalance.AUTO,
          isSelected = activeTab == ProControlTab.WB,
          onClick = {
            onSelectTab(if (activeTab == ProControlTab.WB) ProControlTab.NONE else ProControlTab.WB)
          },
          testTag = "pro_tab_wb",
        )
        ProParamPill(
          label = stringResource(R.string.pro_timer),
          value = if (settings.timerSeconds == 0) stringResource(R.string.pro_off) else "${settings.timerSeconds}s",
          isModified = settings.timerSeconds != 0,
          isSelected = activeTab == ProControlTab.TIMER,
          onClick = {
            onSelectTab(
              if (activeTab == ProControlTab.TIMER) ProControlTab.NONE else ProControlTab.TIMER
            )
          },
          testTag = "pro_tab_timer",
        )

        if (settings.hasAnyOverride) {
          Surface(
            onClick = onResetPro,
            shape = RoundedCornerShape(12.dp),
            color = hud.alertRed.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, hud.alertRed.copy(alpha = 0.45f)),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("pro_reset_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = stringResource(R.string.cd_pro_reset),
                tint = hud.alertRed,
                modifier = Modifier.size(15.dp),
              )
              Text(
                text = stringResource(R.string.pro_reset),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = hud.alertRed,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ProParamPill(
  label: String,
  value: String,
  isModified: Boolean,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val hud = LocalHudColors.current
  val bgColor =
    when {
      isSelected -> hud.accent
      isModified -> hud.accent.copy(alpha = 0.18f)
      else -> Color.Transparent
    }
  val borderColor =
    when {
      isSelected -> hud.accent
      isModified -> hud.accent.copy(alpha = 0.55f)
      else -> Color.Transparent
    }
  val labelColor =
    when {
      isSelected -> if (hud.isDark) Color(0xFF06131F) else Color.White
      isModified -> hud.accent
      else -> hud.textMuted
    }
  val valueColor =
    when {
      isSelected -> if (hud.isDark) Color(0xFF06131F) else Color.White
      else -> hud.textPrimary
    }

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = bgColor,
    border = BorderStroke(1.dp, borderColor),
    modifier =
      Modifier.minimumInteractiveComponentSize()
        .semantics(mergeDescendants = true) {}
        .testTag(testTag),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.7.sp),
        color = labelColor,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
        color = valueColor,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun TrayHeader(
  title: String,
  valueText: String,
  note: String? = null,
) {
  val hud = LocalHudColors.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = hud.accent,
        fontWeight = FontWeight.Bold,
      )
      if (!note.isNullOrBlank()) {
        Text(
          text = note,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = hud.textMuted,
        )
      }
    }
    Text(
      text = valueText,
      style = MaterialTheme.typography.titleSmall,
      color = hud.textPrimary,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun TrayChoiceChip(
  label: String,
  selected: Boolean,
  enabled: Boolean = true,
  onClick: () -> Unit,
  testTag: String,
) {
  val hud = LocalHudColors.current
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(10.dp),
    color =
      when {
        selected -> hud.accent
        !enabled -> hud.pillBackground.copy(alpha = 0.4f)
        else -> hud.pillBackground
      },
    border =
      BorderStroke(
        1.dp,
        if (selected) hud.accent else hud.pillBorder,
      ),
    modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
  ) {
    Box(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color =
          when {
            selected -> if (hud.isDark) Color(0xFF06131F) else Color.White
            !enabled -> hud.textMuted
            else -> hud.textPrimary
          },
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun IsoAdjustmentTray(
  selectedIso: Int?,
  capabilities: ProCameraCapabilities,
  onSelectIso: (Int?) -> Unit,
) {
  val stops = remember(capabilities.isoRange) { capabilities.availableIsoStops() }
  val note =
    if (!capabilities.isManualSensorSupported) {
      stringResource(R.string.pro_manual_sensor_unsupported)
    } else {
      null
    }

  Column(
    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TrayHeader(
      title = stringResource(R.string.pro_iso_title),
      valueText = selectedIso?.toString() ?: stringResource(R.string.pro_auto),
      note = note,
    )
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      TrayChoiceChip(
        label = stringResource(R.string.pro_auto),
        selected = selectedIso == null,
        onClick = { onSelectIso(null) },
        testTag = "pro_iso_auto",
      )
      stops.forEach { iso ->
        TrayChoiceChip(
          label = iso.toString(),
          selected = selectedIso == iso,
          enabled = capabilities.isManualSensorSupported,
          onClick = { onSelectIso(iso) },
          testTag = "pro_iso_$iso",
        )
      }
    }
  }
}

@Composable
private fun ShutterAdjustmentTray(
  selectedShutterNs: Long?,
  capabilities: ProCameraCapabilities,
  onSelectShutter: (Long?) -> Unit,
) {
  val stops = remember(capabilities.exposureTimeNsRange) { capabilities.availableShutterStopsNs() }
  val note =
    if (!capabilities.isManualSensorSupported) {
      stringResource(R.string.pro_manual_sensor_unsupported)
    } else {
      null
    }

  Column(
    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TrayHeader(
      title = stringResource(R.string.pro_shutter_title),
      valueText =
        selectedShutterNs?.let { ProCameraController.formatShutterSpeed(it) }
          ?: stringResource(R.string.pro_auto),
      note = note,
    )
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      TrayChoiceChip(
        label = stringResource(R.string.pro_auto),
        selected = selectedShutterNs == null,
        onClick = { onSelectShutter(null) },
        testTag = "pro_shutter_auto",
      )
      stops.forEach { ns ->
        val label = ProCameraController.formatShutterSpeed(ns)
        TrayChoiceChip(
          label = label,
          selected = selectedShutterNs == ns,
          enabled = capabilities.isManualSensorSupported,
          onClick = { onSelectShutter(ns) },
          testTag = "pro_shutter_$ns",
        )
      }
    }
  }
}

@Composable
private fun EvAdjustmentTray(
  evIndex: Int,
  isOverriddenByManual: Boolean,
  capabilities: ProCameraCapabilities,
  onEvChanged: (Int) -> Unit,
) {
  val hud = LocalHudColors.current
  val range = capabilities.exposureCompRange
  val note =
    when {
      !capabilities.isExposureCompSupported -> stringResource(R.string.pro_ev_unsupported)
      isOverriddenByManual -> stringResource(R.string.pro_ev_manual_override_hint)
      else -> null
    }

  Column(
    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    TrayHeader(
      title = stringResource(R.string.pro_ev_title),
      valueText = "${ProCameraController.formatEv(evIndex, capabilities.exposureCompStep)} EV",
      note = note,
    )
    if (capabilities.isExposureCompSupported) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = ProCameraController.formatEv(range.first, capabilities.exposureCompStep),
          style = MaterialTheme.typography.labelSmall,
          color = hud.textMuted,
        )
        Slider(
          value = evIndex.toFloat(),
          onValueChange = { onEvChanged(it.roundToInt()) },
          valueRange = range.first.toFloat()..range.last.toFloat(),
          steps = (range.last - range.first - 1).coerceAtLeast(0),
          modifier = Modifier.weight(1f).testTag("pro_ev_slider"),
          colors =
            SliderDefaults.colors(
              thumbColor = hud.accent,
              activeTrackColor = hud.accent,
              inactiveTrackColor = hud.pillBorder,
            ),
        )
        Text(
          text = ProCameraController.formatEv(range.last, capabilities.exposureCompStep),
          style = MaterialTheme.typography.labelSmall,
          color = hud.textMuted,
        )
        Spacer(modifier = Modifier.width(2.dp))
        TrayChoiceChip(
          label = "0.0",
          selected = evIndex == 0 && !isOverriddenByManual,
          onClick = { onEvChanged(0) },
          testTag = "pro_ev_reset",
        )
      }
    }
  }
}

@Composable
private fun FocusAdjustmentTray(
  focusDistance: Float?,
  capabilities: ProCameraCapabilities,
  onFocusChanged: (Float?) -> Unit,
) {
  val hud = LocalHudColors.current
  val note =
    if (!capabilities.isManualFocusSupported) {
      stringResource(R.string.pro_mf_unsupported)
    } else {
      null
    }

  Column(
    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    TrayHeader(
      title = stringResource(R.string.pro_focus_title),
      valueText = ProCameraController.formatFocusLabel(focusDistance, capabilities.minFocusDistance),
      note = note,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      TrayChoiceChip(
        label = stringResource(R.string.pro_af),
        selected = focusDistance == null,
        onClick = { onFocusChanged(null) },
        testTag = "pro_focus_auto",
      )
      if (capabilities.isManualFocusSupported) {
        Text(
          text = stringResource(R.string.pro_inf),
          style = MaterialTheme.typography.labelSmall,
          color = hud.textMuted,
        )
        Slider(
          value = focusDistance ?: 0f,
          onValueChange = { onFocusChanged(it) },
          valueRange = 0f..capabilities.minFocusDistance,
          modifier = Modifier.weight(1f).testTag("pro_focus_slider"),
          colors =
            SliderDefaults.colors(
              thumbColor = hud.accent,
              activeTrackColor = hud.accent,
              inactiveTrackColor = hud.pillBorder,
            ),
        )
        Text(
          text = stringResource(R.string.pro_macro),
          style = MaterialTheme.typography.labelSmall,
          color = hud.textMuted,
        )
      }
    }
  }
}

@Composable
private fun WhiteBalanceAdjustmentTray(
  selectedWb: ProWhiteBalance,
  availableModes: List<ProWhiteBalance>,
  onSelectWb: (ProWhiteBalance) -> Unit,
) {
  Column(
    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TrayHeader(
      title = stringResource(R.string.pro_wb_title),
      valueText = selectedWb.displayName,
    )
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      availableModes.forEach { wb ->
        TrayChoiceChip(
          label = wb.displayName,
          selected = selectedWb == wb,
          onClick = { onSelectWb(wb) },
          testTag = "pro_wb_${wb.name.lowercase()}",
        )
      }
    }
  }
}

@Composable
private fun TimerAdjustmentTray(
  timerSeconds: Int,
  onSelectTimer: (Int) -> Unit,
) {
  val options = listOf(0, 3, 10)
  Column(
    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    TrayHeader(
      title = stringResource(R.string.pro_timer_title),
      valueText = if (timerSeconds == 0) stringResource(R.string.pro_off) else "${timerSeconds}s",
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      options.forEach { sec ->
        TrayChoiceChip(
          label = if (sec == 0) stringResource(R.string.pro_off) else "${sec}s",
          selected = timerSeconds == sec,
          onClick = { onSelectTimer(sec) },
          testTag = "pro_timer_$sec",
        )
      }
    }
  }
}
