package com.example.geo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object MiniMapTileLoader {

  private const val TILE_SIZE = 256
  const val MIN_ZOOM = 12
  const val MAX_ZOOM = 18
  const val DEFAULT_ZOOM = 16
  private val tileCache = LruCache<String, Bitmap>(32)

  suspend fun loadMiniMapBitmap(
    latitude: Double,
    longitude: Double,
    zoom: Int = DEFAULT_ZOOM,
    outputSizePx: Int = 256,
  ): Bitmap =
    withContext(Dispatchers.IO) {
      val safeZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
      val n = 2.0.pow(safeZoom.toDouble())
      val safeLat = latitude.coerceIn(-85.0511, 85.0511)
      val xTileFloat = ((longitude + 180.0) / 360.0) * n
      val latRad = Math.toRadians(safeLat)
      val yTileFloat = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n

      val centerTileX = floor(xTileFloat).toInt()
      val centerTileY = floor(yTileFloat).toInt()

      val centerPixelX = xTileFloat * TILE_SIZE
      val centerPixelY = yTileFloat * TILE_SIZE

      val halfCrop = outputSizePx / 2.0
      val cropLeft = centerPixelX - halfCrop
      val cropTop = centerPixelY - halfCrop
      val cropRight = centerPixelX + halfCrop
      val cropBottom = centerPixelY + halfCrop

      val minTileX = floor(cropLeft / TILE_SIZE).toInt()
      val maxTileX = floor(cropRight / TILE_SIZE).toInt()
      val minTileY = floor(cropTop / TILE_SIZE).toInt()
      val maxTileY = floor(cropBottom / TILE_SIZE).toInt()

      val maxIndex = n.toInt()
      val neededCoords = buildList {
        for (ty in minTileY..maxTileY) {
          if (ty < 0 || ty >= maxIndex) continue
          for (tx in minTileX..maxTileX) {
            val wrappedX = ((tx % maxIndex) + maxIndex) % maxIndex
            add(Triple(tx, ty, wrappedX))
          }
        }
      }

      val fetchedTiles = coroutineScope {
        neededCoords
          .map { (rawX, rawY, wrappedX) ->
            async {
              val tile = fetchTile(safeZoom, wrappedX, rawY)
              Triple(rawX, rawY, tile)
            }
          }
          .awaitAll()
      }

      val result = Bitmap.createBitmap(outputSizePx, outputSizePx, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(result)

      // Base map canvas background (used if any tile is unavailable/offline)
      drawOfflineMapBase(canvas, outputSizePx, centerTileX, centerTileY)

      val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
      for ((rawX, rawY, tileBitmap) in fetchedTiles) {
        if (tileBitmap == null) continue
        val tileLeftPx = rawX * TILE_SIZE - cropLeft
        val tileTopPx = rawY * TILE_SIZE - cropTop
        val dst =
          Rect(
            tileLeftPx.toInt(),
            tileTopPx.toInt(),
            (tileLeftPx + TILE_SIZE).toInt(),
            (tileTopPx + TILE_SIZE).toInt(),
          )
        canvas.drawBitmap(tileBitmap, null, dst, tilePaint)
      }

      drawLocationPin(canvas, outputSizePx / 2f, outputSizePx / 2f, outputSizePx / 256f)
      result
    }

  private fun fetchTile(zoom: Int, x: Int, y: Int): Bitmap? {
    val key = "$zoom/$x/$y"
    tileCache.get(key)?.let { return it }

    val urls =
      listOf(
        "https://tile.openstreetmap.org/$zoom/$x/$y.png",
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/$zoom/$y/$x",
        "https://a.tile.opentopomap.org/$zoom/$x/$y.png",
      )

    for (urlStr in urls) {
      val bitmap = downloadBitmap(urlStr)
      if (bitmap != null) {
        tileCache.put(key, bitmap)
        return bitmap
      }
    }
    return null
  }

  private fun downloadBitmap(urlStr: String): Bitmap? {
    var connection: HttpURLConnection? = null
    return runCatching {
      connection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
          connectTimeout = 5000
          readTimeout = 5000
          requestMethod = "GET"
          instanceFollowRedirects = true
          setRequestProperty("User-Agent", "GeoCameraApp/1.0 (Android; com.aistudio.geocam.vkmqrx)")
          setRequestProperty("Accept", "image/png,image/jpeg,image/*")
        }
      val conn = connection!!
      val contentType = conn.contentType.orEmpty().lowercase()
      if (conn.responseCode == HttpURLConnection.HTTP_OK &&
        (contentType.isEmpty() || contentType.startsWith("image/"))
      ) {
        conn.inputStream.use { stream -> BitmapFactory.decodeStream(stream) }
      } else {
        null
      }
    }.getOrNull().also {
      connection?.disconnect()
    }
  }

  private fun drawOfflineMapBase(
    canvas: Canvas,
    size: Int,
    tileX: Int,
    tileY: Int,
  ) {
    canvas.drawColor(Color.parseColor("#142236"))
    val gridPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F3552")
        strokeWidth = 1.5f
      }
    val step = size / 4f
    for (i in 1..3) {
      val pos = step * i
      canvas.drawLine(pos, 0f, pos, size.toFloat(), gridPaint)
      canvas.drawLine(0f, pos, size.toFloat(), pos, gridPaint)
    }

    // Subtle synthetic road arcs as graceful offline fallback
    val roadPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A486E")
        strokeWidth = 4f
      }
    val offset = ((tileX + tileY) % 3) * 18f
    canvas.drawLine(0f, size * 0.35f + offset, size.toFloat(), size * 0.65f - offset, roadPaint)
    canvas.drawLine(size * 0.4f, 0f, size * 0.6f, size.toFloat(), roadPaint)
  }

  private fun drawLocationPin(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
    val haloPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(72, 34, 211, 238)
        style = Paint.Style.FILL
      }
    canvas.drawCircle(cx, cy, 24f * scale, haloPaint)

    val haloStroke =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * scale
      }
    canvas.drawCircle(cx, cy, 24f * scale, haloStroke)

    val outerDot =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
      }
    canvas.drawCircle(cx, cy, 10f * scale, outerDot)

    val innerDot =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        style = Paint.Style.FILL
      }
    canvas.drawCircle(cx, cy, 6.5f * scale, innerDot)
  }
}
