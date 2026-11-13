package com.example.camera

import android.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color

enum class PhotoFilter(
  val displayName: String,
  val shortLabel: String,
  val swatchColor: Color,
) {
  NONE("Normal", "NORMAL", Color(0xFF94A3B8)),
  VIVID("Vivid", "VIVID", Color(0xFF22D3EE)),
  WARM("Warm", "WARM", Color(0xFFF59E0B)),
  COOL("Cool", "COOL", Color(0xFF38BDF8)),
  MONO("B&W", "B&W", Color(0xFFE2E8F0)),
  SEPIA("Sepia", "SEPIA", Color(0xFFD97706)),
  NOIR("Noir", "NOIR", Color(0xFF475569));

  fun toColorMatrix(): ColorMatrix {
    val matrix = ColorMatrix()
    when (this) {
      NONE -> Unit
      VIVID -> {
        matrix.setSaturation(1.38f)
        matrix.postConcat(
          ColorMatrix(
            floatArrayOf(
              1.08f, 0f, 0f, 0f, -8f,
              0f, 1.08f, 0f, 0f, -8f,
              0f, 0f, 1.08f, 0f, -8f,
              0f, 0f, 0f, 1f, 0f,
            )
          )
        )
      }
      WARM -> {
        matrix.setSaturation(1.12f)
        matrix.postConcat(
          ColorMatrix(
            floatArrayOf(
              1.14f, 0f, 0f, 0f, 8f,
              0f, 1.04f, 0f, 0f, 2f,
              0f, 0f, 0.86f, 0f, -6f,
              0f, 0f, 0f, 1f, 0f,
            )
          )
        )
      }
      COOL -> {
        matrix.setSaturation(1.06f)
        matrix.postConcat(
          ColorMatrix(
            floatArrayOf(
              0.88f, 0f, 0f, 0f, -4f,
              0f, 1.02f, 0f, 0f, 2f,
              0f, 0f, 1.16f, 0f, 10f,
              0f, 0f, 0f, 1f, 0f,
            )
          )
        )
      }
      MONO -> {
        matrix.setSaturation(0f)
        matrix.postConcat(
          ColorMatrix(
            floatArrayOf(
              1.12f, 0f, 0f, 0f, -10f,
              0f, 1.12f, 0f, 0f, -10f,
              0f, 0f, 1.12f, 0f, -10f,
              0f, 0f, 0f, 1f, 0f,
            )
          )
        )
      }
      SEPIA -> {
        matrix.set(
          floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
          )
        )
      }
      NOIR -> {
        matrix.setSaturation(0f)
        matrix.postConcat(
          ColorMatrix(
            floatArrayOf(
              1.38f, 0f, 0f, 0f, -36f,
              0f, 1.38f, 0f, 0f, -36f,
              0f, 0f, 1.38f, 0f, -36f,
              0f, 0f, 0f, 1f, 0f,
            )
          )
        )
      }
    }
    return matrix
  }
}
