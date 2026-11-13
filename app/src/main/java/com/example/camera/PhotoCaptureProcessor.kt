package com.example.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.camera.core.ImageProxy
import androidx.core.content.FileProvider
import com.example.data.OverlayPreferences
import com.example.geo.GeoLocationInfo
import com.example.geo.MiniMapTileLoader
import com.example.geo.QrCodeGenerator
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhotoCaptureProcessor {

  suspend fun processAndSave(
    context: Context,
    imageProxy: ImageProxy,
    isFrontCamera: Boolean,
    geoInfo: GeoLocationInfo,
    prefs: OverlayPreferences,
    miniMapBitmap: Bitmap? = null,
  ): Uri? =
    withContext(Dispatchers.IO) {
      try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()

        val orientedBitmap =
          if (rotation != 0f || isFrontCamera) {
            val matrix =
              Matrix().apply {
                if (rotation != 0f) postRotate(rotation)
                if (isFrontCamera) postScale(-1f, 1f)
              }
            val transformed =
              Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                matrix,
                true,
              )
            if (transformed !== decoded) decoded.recycle()
            transformed
          } else {
            decoded
          }

        val shouldStampTimestampOnly =
          prefs.stampOverlayOnCapture &&
            (prefs.timestampOnly || (!prefs.geoTaggingEnabled && prefs.showTimestamp))
        val shouldStampFullGeo =
          prefs.stampOverlayOnCapture && prefs.geoTaggingEnabled && !prefs.timestampOnly

        val resolvedMiniMap =
          if (shouldStampFullGeo) {
            miniMapBitmap
              ?: if (geoInfo.latitude != null && geoInfo.longitude != null) {
                MiniMapTileLoader.loadMiniMapBitmap(
                  latitude = geoInfo.latitude,
                  longitude = geoInfo.longitude,
                  zoom = prefs.mapZoomLevel,
                  outputSizePx = 256,
                )
              } else {
                null
              }
          } else {
            null
          }

        val filteredBitmap =
          if (prefs.photoFilter != PhotoFilter.NONE) {
            val target =
              Bitmap.createBitmap(
                orientedBitmap.width,
                orientedBitmap.height,
                Bitmap.Config.ARGB_8888,
              )
            val filterCanvas = Canvas(target)
            val filterPaint =
              Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(prefs.photoFilter.toColorMatrix())
              }
            filterCanvas.drawBitmap(orientedBitmap, 0f, 0f, filterPaint)
            orientedBitmap.recycle()
            target
          } else {
            orientedBitmap
          }

        val finalBitmap =
          when {
            shouldStampTimestampOnly -> {
              val stamped = filteredBitmap.copy(Bitmap.Config.ARGB_8888, true)
              if (stamped !== filteredBitmap) filteredBitmap.recycle()
              drawTimestampOnlyOverlay(Canvas(stamped), stamped.width, stamped.height)
              stamped
            }
            shouldStampFullGeo -> {
              val stamped = filteredBitmap.copy(Bitmap.Config.ARGB_8888, true)
              if (stamped !== filteredBitmap) filteredBitmap.recycle()
              drawGeoOverlay(Canvas(stamped), stamped.width, stamped.height, geoInfo, prefs, resolvedMiniMap)
              stamped
            }
            else -> filteredBitmap
          }

        val savedUri = saveBitmapToGallery(context, finalBitmap)
        finalBitmap.recycle()
        savedUri
      } finally {
        imageProxy.close()
      }
    }

  fun drawTimestampOnlyOverlay(
    canvas: Canvas,
    width: Int,
    height: Int,
    timestampMillis: Long = System.currentTimeMillis(),
  ) {
    val w = width.toFloat()
    val h = height.toFloat()
    val unit = minOf(w, h) / 1080f

    val outerMargin = 28f * unit
    val brandText = "Geo Camera Pro by Thennarasu Gopalsamy"
    val timestampStr =
      SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))

    val timePaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f * unit
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      }
    val brandPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        textSize = 23f * unit
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      }

    val timeWidth = timePaint.measureText(timestampStr)
    val brandWidth = brandPaint.measureText(brandText)

    val timeFm = timePaint.fontMetrics
    val brandFm = brandPaint.fontMetrics
    val timeHeight = timeFm.descent - timeFm.ascent
    val brandHeight = brandFm.descent - brandFm.ascent
    val dividerGap = 14f * unit

    val padX = 28f * unit
    val padY = 18f * unit
    val pillWidth = w - outerMargin * 2f
    val pillHeight = timeHeight + dividerGap * 2f + brandHeight + padY * 2f
    val left = outerMargin
    val bottom = h - outerMargin
    val top = bottom - pillHeight
    val rect = RectF(left, top, left + pillWidth, bottom)

    val bgPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(214, 9, 16, 28)
        style = Paint.Style.FILL
      }
    val borderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 34, 211, 238)
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * unit
      }
    val dividerPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(65, 34, 211, 238)
        strokeWidth = 1.5f * unit
      }

    val radius = 26f * unit
    canvas.drawRoundRect(rect, radius, radius, bgPaint)
    canvas.drawRoundRect(rect, radius, radius, borderPaint)

    val timeX = rect.centerX() - timeWidth / 2f
    val timeBaseline = rect.top + padY - timeFm.ascent
    canvas.drawText(timestampStr, timeX.coerceAtLeast(rect.left + padX), timeBaseline, timePaint)

    val dividerY = rect.top + padY + timeHeight + dividerGap
    canvas.drawLine(rect.left + padX, dividerY, rect.right - padX, dividerY, dividerPaint)

    val brandX = rect.centerX() - brandWidth / 2f
    val brandBaseline = dividerY + dividerGap - brandFm.ascent
    canvas.drawText(brandText, brandX.coerceAtLeast(rect.left + padX), brandBaseline, brandPaint)
  }

  fun drawGeoOverlay(
    canvas: Canvas,
    width: Int,
    height: Int,
    geoInfo: GeoLocationInfo,
    prefs: OverlayPreferences,
    miniMapBitmap: Bitmap?,
    timestampMillis: Long? = null,
  ) {
    val w = width.toFloat()
    val h = height.toFloat()
    val unit = minOf(w, h) / 1080f

    val outerMargin = 28f * unit
    val cardPadding = 24f * unit
    val cornerRadius = 26f * unit
    val colGap = 20f * unit

    val mapBoxSize = if (miniMapBitmap != null) 168f * unit else 0f
    val qrPayload = geoInfo.qrPayload(prefs.qrPayloadFormat)
    val qrSizePx = (156f * unit).toInt().coerceIn(90, 320)
    val qrBitmap = qrPayload?.let { QrCodeGenerator.generateSync(it, qrSizePx) }
    val qrContainerSize = if (qrBitmap != null) qrSizePx + (16f * unit) else 0f

    val leftWidth = if (miniMapBitmap != null) mapBoxSize + colGap else 0f
    val rightWidth = if (qrBitmap != null) qrContainerSize + colGap else 0f
    val fullInnerWidth = (w - outerMargin * 2f - cardPadding * 2f).toInt().coerceAtLeast(240)
    val textAreaWidth =
      (fullInnerWidth - leftWidth - rightWidth)
        .toInt()
        .coerceAtLeast(180)

    val areaPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        textSize = 22f * unit
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
    val addressPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 31f * unit
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
    val locationPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        textSize = 24f * unit
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
      }
    val coordsPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#67E8F9")
        textSize = 22f * unit
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      }
    val timestampPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 20f * unit
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
      }
    val brandFooterPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        textSize = 23f * unit
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      }

    val areaText = geoInfo.area.ifBlank { "Local Area" }
    val addressText =
      geoInfo.addressLine.ifBlank {
        if (geoInfo.hasCoordinates) "Pinned Geo Location" else "Location Unavailable"
      }
    val locationText =
      geoInfo.locationSummary.ifBlank {
        geoInfo.statusNote ?: "Coordinates Embedded"
      }
    val coordsText = geoInfo.formattedCoordinates(prefs.useDmsCoordinates)
    val timestampText =
      if (prefs.showTimestamp) {
        if (timestampMillis != null) {
          SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
        } else {
          geoInfo.formattedTimestamp()
        }
      } else {
        ""
      }

    val areaLayout = buildLayout(areaText, areaPaint, textAreaWidth)
    val addressLayout = buildLayout(addressText, addressPaint, textAreaWidth)
    val locationLayout = buildLayout(locationText, locationPaint, textAreaWidth)
    val coordsLayout = buildLayout(coordsText, coordsPaint, textAreaWidth)
    val timestampLayout =
      if (timestampText.isNotBlank()) buildLayout(timestampText, timestampPaint, textAreaWidth)
      else null
    val brandFooterLayout =
      buildLayout(
        "Geo Camera Pro by Thennarasu Gopalsamy",
        brandFooterPaint,
        fullInnerWidth,
        alignment = Layout.Alignment.ALIGN_CENTER,
      )

    val gap = 8f * unit
    val textBlockHeight =
      areaLayout.height +
        gap * 0.7f +
        addressLayout.height +
        gap * 0.6f +
        locationLayout.height +
        gap * 0.8f +
        coordsLayout.height +
        (if (timestampLayout != null) gap * 0.6f + timestampLayout.height else 0f)

    val mapBlockHeight = if (miniMapBitmap != null) mapBoxSize + 24f * unit else 0f
    val qrBlockHeight = if (qrBitmap != null) qrContainerSize + 24f * unit else 0f
    val topRowHeight = maxOf(textBlockHeight, mapBlockHeight, qrBlockHeight)
    val footerGap = 14f * unit
    val cardHeight =
      cardPadding + topRowHeight + footerGap * 2f + brandFooterLayout.height + cardPadding * 0.85f
    val cardTop = (h - outerMargin - cardHeight).coerceAtLeast(outerMargin)
    val cardRect = RectF(outerMargin, cardTop, w - outerMargin, h - outerMargin)

    val bgPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 9, 16, 28)
        style = Paint.Style.FILL
      }
    val borderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 34, 211, 238)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * unit
      }

    canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)
    canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

    val subLabelPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = 18f * unit
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
      }

    val topSectionTop = cardRect.top + cardPadding

    // Left: Mini Map
    if (miniMapBitmap != null) {
      val mapLeft = cardRect.left + cardPadding
      val mapTop = topSectionTop + (topRowHeight - mapBlockHeight) / 2f
      val mapRect = RectF(mapLeft, mapTop, mapLeft + mapBoxSize, mapTop + mapBoxSize)
      val clipPath =
        Path().apply {
          addRoundRect(mapRect, 14f * unit, 14f * unit, Path.Direction.CW)
        }
      canvas.save()
      canvas.clipPath(clipPath)
      canvas.drawBitmap(miniMapBitmap, null, mapRect, null)
      canvas.restore()

      val mapBorder =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.argb(140, 34, 211, 238)
          style = Paint.Style.STROKE
          strokeWidth = 2f * unit
        }
      canvas.drawRoundRect(mapRect, 14f * unit, 14f * unit, mapBorder)
      canvas.drawText("MAP", mapRect.centerX(), mapRect.bottom + 20f * unit, subLabelPaint)
    }

    // Center: Text Snippet (Area, Address, Location, Coordinates, Timestamp)
    val textX = cardRect.left + cardPadding + leftWidth
    var textY = topSectionTop + (topRowHeight - textBlockHeight) / 2f

    canvas.save()
    canvas.translate(textX, textY)
    areaLayout.draw(canvas)
    textY += areaLayout.height + gap * 0.7f
    canvas.restore()

    canvas.save()
    canvas.translate(textX, textY)
    addressLayout.draw(canvas)
    textY += addressLayout.height + gap * 0.6f
    canvas.restore()

    canvas.save()
    canvas.translate(textX, textY)
    locationLayout.draw(canvas)
    textY += locationLayout.height + gap * 0.8f
    canvas.restore()

    canvas.save()
    canvas.translate(textX, textY)
    coordsLayout.draw(canvas)
    textY += coordsLayout.height
    canvas.restore()

    if (timestampLayout != null) {
      textY += gap * 0.6f
      canvas.save()
      canvas.translate(textX, textY)
      timestampLayout.draw(canvas)
      canvas.restore()
    }

    // Right: Location QR Code
    if (qrBitmap != null) {
      val qrLeft = cardRect.right - cardPadding - qrContainerSize
      val qrTop = topSectionTop + (topRowHeight - qrBlockHeight) / 2f
      val qrBgRect = RectF(qrLeft, qrTop, qrLeft + qrContainerSize, qrTop + qrContainerSize)
      val whitePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.WHITE
          style = Paint.Style.FILL
        }
      canvas.drawRoundRect(qrBgRect, 14f * unit, 14f * unit, whitePaint)
      val inset = 8f * unit
      val destRect =
        RectF(
          qrBgRect.left + inset,
          qrBgRect.top + inset,
          qrBgRect.right - inset,
          qrBgRect.bottom - inset,
        )
      canvas.drawBitmap(qrBitmap, null, destRect, null)
      canvas.drawText(
        "SCAN GEO",
        qrBgRect.centerX(),
        qrBgRect.bottom + 20f * unit,
        subLabelPaint,
      )
      qrBitmap.recycle()
    }

    // Bottom Full-Width Centered Branding Footer
    val dividerY = topSectionTop + topRowHeight + footerGap
    val dividerPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(65, 34, 211, 238)
        strokeWidth = 1.5f * unit
      }
    canvas.drawLine(
      cardRect.left + cardPadding,
      dividerY,
      cardRect.right - cardPadding,
      dividerY,
      dividerPaint,
    )

    canvas.save()
    canvas.translate(cardRect.left + cardPadding, dividerY + footerGap * 0.7f)
    brandFooterLayout.draw(canvas)
    canvas.restore()
  }

  private fun buildLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
  ): StaticLayout {
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
      .setAlignment(alignment)
      .setLineSpacing(0f, 1.06f)
      .setIncludePad(false)
      .build()
  }

  private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "GeoCam_$timestamp.jpg"

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val values =
        ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
          put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
          put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GeoCam")
          put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
      val resolver = context.contentResolver
      val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
      val success =
        runCatching {
          resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)
          } ?: false
        }.getOrDefault(false)

      if (success) {
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
      } else {
        resolver.delete(uri, null, null)
        null
      }
    } else {
      val dir =
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "GeoCam").apply {
          if (!exists()) mkdirs()
        }
      val file = File(dir, fileName)
      val ok =
        runCatching {
          FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)
          }
        }.getOrDefault(false)
      if (ok) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      } else {
        null
      }
    }
  }
}
