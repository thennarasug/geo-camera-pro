package com.example.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraEffect
import androidx.camera.effects.OverlayEffect
import com.example.data.OverlayPreferences
import com.example.geo.GeoLocationInfo
import java.util.concurrent.atomic.AtomicReference

class VideoOverlayController {

  data class OverlaySnapshot(
    val geoInfo: GeoLocationInfo,
    val prefs: OverlayPreferences,
    val miniMapBitmap: Bitmap?,
  )

  private val snapshotRef =
    AtomicReference(
      OverlaySnapshot(
        geoInfo = GeoLocationInfo(),
        prefs = OverlayPreferences(),
        miniMapBitmap = null,
      )
    )

  @Volatile private var cachedBitmap: Bitmap? = null
  @Volatile private var cachedSecond: Long = -1L
  @Volatile private var cachedWidth: Int = 0
  @Volatile private var cachedHeight: Int = 0
  @Volatile private var cachedSnapshot: OverlaySnapshot? = null

  fun update(
    geoInfo: GeoLocationInfo,
    prefs: OverlayPreferences,
    miniMapBitmap: Bitmap?,
  ) {
    snapshotRef.set(
      OverlaySnapshot(
        geoInfo = geoInfo,
        prefs = prefs,
        miniMapBitmap = miniMapBitmap,
      )
    )
  }

  fun createOverlayEffect(onError: (Throwable) -> Unit = {}): OverlayEffect {
    val effect =
      OverlayEffect(
        CameraEffect.VIDEO_CAPTURE,
        4,
        Handler(Looper.getMainLooper()),
      ) { throwable ->
        onError(throwable)
      }

    effect.setOnDrawListener { frame ->
      val canvas = frame.overlayCanvas
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

      val snap = snapshotRef.get()
      val prefs = snap.prefs
      val shouldStampTimestampOnly =
        prefs.stampOverlayOnCapture &&
          (prefs.timestampOnly || (!prefs.geoTaggingEnabled && prefs.showTimestamp))
      val shouldStampFullGeo =
        prefs.stampOverlayOnCapture && prefs.geoTaggingEnabled && !prefs.timestampOnly

      if (shouldStampTimestampOnly || shouldStampFullGeo) {
        val w = canvas.width
        val h = canvas.height
        if (w > 0 && h > 0) {
          val nowMillis = System.currentTimeMillis()
          val currentSec = nowMillis / 1000L
          val needsRedraw =
            cachedBitmap == null ||
              cachedWidth != w ||
              cachedHeight != h ||
              cachedSecond != currentSec ||
              cachedSnapshot != snap

          if (needsRedraw) {
            val target =
              if (cachedBitmap != null && cachedWidth == w && cachedHeight == h) {
                cachedBitmap!!.also { it.eraseColor(Color.TRANSPARENT) }
              } else {
                cachedBitmap?.recycle()
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
              }
            val offscreen = Canvas(target)
            if (shouldStampTimestampOnly) {
              PhotoCaptureProcessor.drawTimestampOnlyOverlay(
                canvas = offscreen,
                width = w,
                height = h,
                timestampMillis = nowMillis,
              )
            } else {
              PhotoCaptureProcessor.drawGeoOverlay(
                canvas = offscreen,
                width = w,
                height = h,
                geoInfo = snap.geoInfo,
                prefs = prefs,
                miniMapBitmap = snap.miniMapBitmap,
                timestampMillis = nowMillis,
              )
            }
            cachedBitmap = target
            cachedWidth = w
            cachedHeight = h
            cachedSecond = currentSec
            cachedSnapshot = snap
          }
          cachedBitmap?.let { overlayBmp ->
            canvas.drawBitmap(overlayBmp, 0f, 0f, null)
          }
        }
      }
      true
    }
    return effect
  }

  fun release() {
    cachedBitmap?.recycle()
    cachedBitmap = null
    cachedSecond = -1L
  }
}
