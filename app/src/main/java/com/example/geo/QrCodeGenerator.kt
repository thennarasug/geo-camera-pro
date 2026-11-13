package com.example.geo

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QrCodeGenerator {

  suspend fun generateBitmap(content: String, sizePx: Int = 512): Bitmap? =
    withContext(Dispatchers.Default) {
      generateSync(content, sizePx)
    }

  fun generateSync(content: String, sizePx: Int = 512): Bitmap? {
    if (content.isBlank() || sizePx <= 0) return null
    return runCatching {
      val hints =
        mapOf(
          EncodeHintType.CHARACTER_SET to "UTF-8",
          EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
          EncodeHintType.MARGIN to 1,
        )
      val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val width = bitMatrix.width
      val height = bitMatrix.height
      val pixels = IntArray(width * height)
      for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
          pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
        }
      }
      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
      }
    }.getOrNull()
  }
}
