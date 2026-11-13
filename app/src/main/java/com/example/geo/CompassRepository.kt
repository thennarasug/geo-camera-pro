package com.example.geo

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class CompassAccuracy {
  HIGH,
  MEDIUM,
  LOW,
  UNAVAILABLE,
}

data class CompassState(
  val headingDegrees: Float = 0f,
  val cardinalLabel: String = "N",
  val hasSensor: Boolean = true,
  val accuracy: CompassAccuracy = CompassAccuracy.MEDIUM,
  val isTrueNorth: Boolean = false,
) {
  val roundedDegrees: Int
    get() = (((headingDegrees % 360f) + 360f) % 360f).roundToInt() % 360
}

class CompassRepository(private val context: Context) {

  private val sensorManager =
    context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

  private val _compassState =
    MutableStateFlow(
      CompassState(
        hasSensor = hasOrientationSensors(),
        accuracy = if (hasOrientationSensors()) CompassAccuracy.MEDIUM else CompassAccuracy.UNAVAILABLE,
      )
    )
  val compassState: StateFlow<CompassState> = _compassState.asStateFlow()

  private var isListening = false
  private var isFrontCamera = false
  private var declinationDegrees = 0f
  private var hasDeclination = false

  private val rotationMatrix = FloatArray(9)
  private val accelerometerReading = FloatArray(3)
  private val magnetometerReading = FloatArray(3)
  private var hasAccel = false
  private var hasMag = false
  private var hasSmoothedHeading = false
  private var smoothedHeading = 0f
  private var currentAccuracy = CompassAccuracy.MEDIUM

  private val sensorListener =
    object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
          Sensor.TYPE_ROTATION_VECTOR,
          Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            publishHeadingFromMatrix(rotationMatrix)
          }
          Sensor.TYPE_ACCELEROMETER -> {
            lowPassVec(event.values, accelerometerReading, hasAccel)
            hasAccel = true
            if (hasMag && SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
              publishHeadingFromMatrix(rotationMatrix)
            }
          }
          Sensor.TYPE_MAGNETIC_FIELD -> {
            lowPassVec(event.values, magnetometerReading, hasMag)
            hasMag = true
            if (hasAccel && SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
              publishHeadingFromMatrix(rotationMatrix)
            }
          }
        }
      }

      override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD ||
          sensor.type == Sensor.TYPE_ROTATION_VECTOR ||
          sensor.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
        ) {
          currentAccuracy =
            when (accuracy) {
              SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
              SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
              else -> CompassAccuracy.LOW
            }
          _compassState.value = _compassState.value.copy(accuracy = currentAccuracy)
        }
      }
    }

  private fun hasOrientationSensors(): Boolean {
    val sm = sensorManager ?: return false
    val hasRot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    val hasGeoRot = sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) != null
    val hasAccelMag =
      sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
    return hasRot || hasGeoRot || hasAccelMag
  }

  fun setFrontCamera(front: Boolean) {
    isFrontCamera = front
  }

  fun updateLocationDeclination(latitude: Double?, longitude: Double?) {
    if (latitude != null && longitude != null) {
      val field =
        GeomagneticField(
          latitude.toFloat(),
          longitude.toFloat(),
          0f,
          System.currentTimeMillis(),
        )
      declinationDegrees = field.declination
      hasDeclination = true
    } else {
      declinationDegrees = 0f
      hasDeclination = false
    }
  }

  fun start() {
    if (isListening) return
    val sm = sensorManager ?: return

    val rotSensor =
      sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    if (rotSensor != null) {
      sm.registerListener(sensorListener, rotSensor, SensorManager.SENSOR_DELAY_UI)
      isListening = true
      return
    }

    val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    if (accel != null && mag != null) {
      sm.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_UI)
      sm.registerListener(sensorListener, mag, SensorManager.SENSOR_DELAY_UI)
      isListening = true
    } else {
      _compassState.value =
        CompassState(
          hasSensor = false,
          accuracy = CompassAccuracy.UNAVAILABLE,
        )
    }
  }

  fun stop() {
    if (!isListening) return
    sensorManager?.unregisterListener(sensorListener)
    isListening = false
    hasSmoothedHeading = false
    hasAccel = false
    hasMag = false
  }

  private fun publishHeadingFromMatrix(r: FloatArray) {
    val rawMagneticDeg =
      computeCameraAzimuthDegrees(
        rotationMatrix = r,
        isFrontCamera = isFrontCamera,
        displayRotation = currentDisplayRotation(),
      )
    val trueOrMagnetic = normalizeDegrees(rawMagneticDeg + if (hasDeclination) declinationDegrees else 0f)

    smoothedHeading =
      if (!hasSmoothedHeading) {
        hasSmoothedHeading = true
        trueOrMagnetic
      } else {
        smoothAngle(smoothedHeading, trueOrMagnetic, alpha = 0.22f)
      }

    _compassState.value =
      CompassState(
        headingDegrees = smoothedHeading,
        cardinalLabel = cardinalDirection(smoothedHeading),
        hasSensor = true,
        accuracy = currentAccuracy,
        isTrueNorth = hasDeclination,
      )
  }

  @Suppress("DEPRECATION")
  private fun currentDisplayRotation(): Int {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    return runCatching { wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0 }
      .getOrDefault(Surface.ROTATION_0)
  }

  private fun lowPassVec(input: FloatArray, output: FloatArray, initialized: Boolean) {
    if (!initialized) {
      input.copyInto(output, endIndex = minOf(input.size, output.size))
      return
    }
    val alpha = 0.2f
    for (i in 0 until minOf(input.size, output.size)) {
      output[i] = output[i] + alpha * (input[i] - output[i])
    }
  }

  companion object {
    /**
     * Computes horizontal azimuth (0° = North, 90° = East, 180° = South, 270° = West)
     * of the active camera optical axis (-Z for rear camera, +Z for front camera),
     * falling back smoothly to the screen's top edge when held nearly flat horizontally.
     */
    fun computeCameraAzimuthDegrees(
      rotationMatrix: FloatArray,
      isFrontCamera: Boolean,
      displayRotation: Int = Surface.ROTATION_0,
    ): Float {
      // Back camera points along -Z in device coordinates: East = -R[2], North = -R[5].
      val zSign = if (isFrontCamera) 1f else -1f
      val camEast = zSign * rotationMatrix[2]
      val camNorth = zSign * rotationMatrix[5]
      val camHorizMag = hypot(camEast, camNorth)

      val azimuthRad =
        if (camHorizMag >= 0.25f) {
          atan2(camEast, camNorth)
        } else {
          // Device is held nearly flat; use top edge of current display orientation.
          val (east, north) =
            when (displayRotation) {
              Surface.ROTATION_90 -> -rotationMatrix[0] to -rotationMatrix[3]
              Surface.ROTATION_180 -> -rotationMatrix[1] to -rotationMatrix[4]
              Surface.ROTATION_270 -> rotationMatrix[0] to rotationMatrix[3]
              else -> rotationMatrix[1] to rotationMatrix[4]
            }
          atan2(east, north)
        }

      return normalizeDegrees((azimuthRad * (180.0 / PI)).toFloat())
    }

    fun normalizeDegrees(degrees: Float): Float {
      val mod = degrees % 360f
      return if (mod < 0f) mod + 360f else mod
    }

    fun smoothAngle(current: Float, target: Float, alpha: Float): Float {
      val delta = ((target - current + 540f) % 360f) - 180f
      return normalizeDegrees(current + alpha * delta)
    }

    fun cardinalDirection(degrees: Float): String {
      val normalized = normalizeDegrees(degrees)
      val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
      val index = (((normalized + 22.5f) / 45f).toInt()) % 8
      return directions[index]
    }
  }
}
