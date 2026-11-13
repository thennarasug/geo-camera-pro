package com.example.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import java.util.Locale
import kotlin.math.roundToInt

enum class ProControlTab {
  NONE,
  ISO,
  SHUTTER,
  EV,
  FOCUS,
  WB,
  TIMER,
}

enum class ProWhiteBalance(
  val shortLabel: String,
  val displayName: String,
  val camera2Mode: Int,
) {
  AUTO("AUTO", "Auto", CameraMetadata.CONTROL_AWB_MODE_AUTO),
  DAYLIGHT("DAY", "Daylight", CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT),
  CLOUDY("CLD", "Cloudy", CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
  INCANDESCENT("INC", "Tungsten", CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT),
  FLUORESCENT("FLR", "Fluorescent", CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT),
  SHADE("SHD", "Shade", CameraMetadata.CONTROL_AWB_MODE_SHADE),
}

data class ProCameraSettings(
  val iso: Int? = null,
  val shutterSpeedNs: Long? = null,
  val exposureCompIndex: Int = 0,
  val focusDistance: Float? = null,
  val whiteBalance: ProWhiteBalance = ProWhiteBalance.AUTO,
  val timerSeconds: Int = 0,
) {
  val isManualExposure: Boolean
    get() = iso != null || shutterSpeedNs != null

  val hasAnyOverride: Boolean
    get() =
      iso != null ||
        shutterSpeedNs != null ||
        exposureCompIndex != 0 ||
        focusDistance != null ||
        whiteBalance != ProWhiteBalance.AUTO ||
        timerSeconds != 0
}

data class ProCameraCapabilities(
  val isManualSensorSupported: Boolean = false,
  val isoRange: IntRange = 100..3200,
  val exposureTimeNsRange: LongRange = 250_000L..500_000_000L,
  val exposureCompRange: IntRange = -4..4,
  val exposureCompStep: Float = 0.5f,
  val minFocusDistance: Float = 0f,
  val availableAwbModes: List<ProWhiteBalance> = ProWhiteBalance.entries,
) {
  val isManualFocusSupported: Boolean
    get() = minFocusDistance > 0f

  val isExposureCompSupported: Boolean
    get() = exposureCompRange.first < exposureCompRange.last

  val defaultManualIso: Int
    get() = 200.coerceIn(isoRange.first, isoRange.last)

  val defaultManualShutterNs: Long
    get() = 16_666_667L.coerceIn(exposureTimeNsRange.first, exposureTimeNsRange.last)

  fun availableIsoStops(): List<Int> {
    val standardStops = listOf(50, 100, 160, 200, 320, 400, 640, 800, 1250, 1600, 3200, 6400)
    val filtered = standardStops.filter { it in isoRange }
    return if (filtered.isNotEmpty()) filtered else listOf(isoRange.first, (isoRange.first + isoRange.last) / 2, isoRange.last).distinct()
  }

  fun availableShutterStopsNs(): List<Long> {
    val standardStops =
      listOf(
        250_000L, // 1/4000s
        500_000L, // 1/2000s
        1_000_000L, // 1/1000s
        2_000_000L, // 1/500s
        4_000_000L, // 1/250s
        8_000_000L, // 1/125s
        16_666_667L, // 1/60s
        33_333_333L, // 1/30s
        66_666_667L, // 1/15s
        125_000_000L, // 1/8s
        250_000_000L, // 1/4s
        500_000_000L, // 1/2s
        1_000_000_000L, // 1s
      )
    val filtered = standardStops.filter { it in exposureTimeNsRange }
    return if (filtered.isNotEmpty()) {
      filtered
    } else {
      listOf(
        exposureTimeNsRange.first,
        (exposureTimeNsRange.first + exposureTimeNsRange.last) / 2L,
        exposureTimeNsRange.last,
      ).distinct()
    }
  }
}

object ProCameraController {

  @OptIn(ExperimentalCamera2Interop::class)
  fun readCapabilities(camera: Camera): ProCameraCapabilities {
    val exposureState = camera.cameraInfo.exposureState
    val compRange =
      if (exposureState.isExposureCompensationSupported) {
        exposureState.exposureCompensationRange.lower..exposureState.exposureCompensationRange.upper
      } else {
        0..0
      }
    val compStep =
      if (exposureState.isExposureCompensationSupported) {
        exposureState.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 0.5f
      } else {
        0.5f
      }

    return runCatching {
      val c2Info = Camera2CameraInfo.from(camera.cameraInfo)
      val caps =
        c2Info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
          ?: IntArray(0)
      val manualSensor =
        caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)

      val rawIso = c2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
      val isoRange =
        if (rawIso != null && rawIso.lower <= rawIso.upper) {
          rawIso.lower..rawIso.upper
        } else {
          100..3200
        }

      val rawExpTime =
        c2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
      val expTimeRange =
        if (rawExpTime != null && rawExpTime.lower <= rawExpTime.upper) {
          rawExpTime.lower..rawExpTime.upper
        } else {
          250_000L..500_000_000L
        }

      val minFocus =
        c2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

      val rawAwb =
        c2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
          ?: intArrayOf(CameraMetadata.CONTROL_AWB_MODE_AUTO)
      val awbList =
        ProWhiteBalance.entries.filter { it.camera2Mode in rawAwb }.ifEmpty {
          listOf(ProWhiteBalance.AUTO)
        }

      ProCameraCapabilities(
        isManualSensorSupported = manualSensor,
        isoRange = isoRange,
        exposureTimeNsRange = expTimeRange,
        exposureCompRange = compRange,
        exposureCompStep = compStep,
        minFocusDistance = minFocus.coerceAtLeast(0f),
        availableAwbModes = awbList,
      )
    }.getOrElse {
      ProCameraCapabilities(
        exposureCompRange = compRange,
        exposureCompStep = compStep,
      )
    }
  }

  @OptIn(ExperimentalCamera2Interop::class)
  fun applySettings(
    camera: Camera,
    isProMode: Boolean,
    settings: ProCameraSettings,
    capabilities: ProCameraCapabilities,
  ) {
    runCatching {
      val c2Control = Camera2CameraControl.from(camera.cameraControl)
      if (!isProMode) {
        c2Control.clearCaptureRequestOptions()
        if (capabilities.isExposureCompSupported) {
          camera.cameraControl.setExposureCompensationIndex(0)
        }
        return
      }

      val optionsBuilder = CaptureRequestOptions.Builder()

      if (settings.isManualExposure && capabilities.isManualSensorSupported) {
        val targetIso =
          (settings.iso ?: capabilities.defaultManualIso)
            .coerceIn(capabilities.isoRange.first, capabilities.isoRange.last)
        val targetShutter =
          (settings.shutterSpeedNs ?: capabilities.defaultManualShutterNs)
            .coerceIn(
              capabilities.exposureTimeNsRange.first,
              capabilities.exposureTimeNsRange.last,
            )
        optionsBuilder
          .setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CameraMetadata.CONTROL_AE_MODE_OFF,
          )
          .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, targetIso)
          .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, targetShutter)
      } else {
        optionsBuilder.setCaptureRequestOption(
          CaptureRequest.CONTROL_AE_MODE,
          CameraMetadata.CONTROL_AE_MODE_ON,
        )
        if (capabilities.isExposureCompSupported) {
          val clampedEv =
            settings.exposureCompIndex.coerceIn(
              capabilities.exposureCompRange.first,
              capabilities.exposureCompRange.last,
            )
          camera.cameraControl.setExposureCompensationIndex(clampedEv)
        }
      }

      if (settings.focusDistance != null && capabilities.isManualFocusSupported) {
        val clampedFocus = settings.focusDistance.coerceIn(0f, capabilities.minFocusDistance)
        optionsBuilder
          .setCaptureRequestOption(
            CaptureRequest.CONTROL_AF_MODE,
            CameraMetadata.CONTROL_AF_MODE_OFF,
          )
          .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, clampedFocus)
      } else {
        optionsBuilder.setCaptureRequestOption(
          CaptureRequest.CONTROL_AF_MODE,
          CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
        )
      }

      optionsBuilder.setCaptureRequestOption(
        CaptureRequest.CONTROL_AWB_MODE,
        settings.whiteBalance.camera2Mode,
      )

      c2Control.setCaptureRequestOptions(optionsBuilder.build())
    }
  }

  fun formatShutterSpeed(ns: Long): String {
    if (ns <= 0L) return "AUTO"
    val seconds = ns / 1_000_000_000.0
    return if (seconds >= 0.4) {
      String.format(Locale.US, "%.1fs", seconds)
    } else {
      val denominator = (1_000_000_000.0 / ns).roundToInt().coerceAtLeast(1)
      "1/$denominator"
    }
  }

  fun formatEv(index: Int, step: Float): String {
    val ev = index * step
    return when {
      index == 0 -> "0.0"
      ev > 0f -> String.format(Locale.US, "+%.1f", ev)
      else -> String.format(Locale.US, "%.1f", ev)
    }
  }

  fun formatFocusLabel(focusDistance: Float?, minFocusDistance: Float): String {
    if (focusDistance == null || minFocusDistance <= 0f) return "AF"
    val ratio = (focusDistance / minFocusDistance).coerceIn(0f, 1f)
    return when {
      ratio >= 0.85f -> "MACRO"
      ratio <= 0.1f -> "INF"
      else -> "${(ratio * 100).roundToInt()}%"
    }
  }
}
