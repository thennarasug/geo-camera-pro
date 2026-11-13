package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExploreOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.geo.CompassAccuracy
import com.example.geo.CompassRepository
import com.example.geo.CompassState
import com.example.ui.theme.LocalHudColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun CompassDirectionOverlay(
  compassState: CompassState,
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val hud = LocalHudColors.current

  if (!compassState.hasSensor) {
    Surface(
      shape = RoundedCornerShape(50),
      color = hud.pillBackground,
      border = BorderStroke(1.dp, hud.pillBorder),
      modifier = modifier.testTag("compass_overlay_unavailable"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.ExploreOff,
          contentDescription = stringResource(R.string.cd_compass_overlay),
          tint = hud.textMuted,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = stringResource(R.string.compass_no_sensor),
          style = MaterialTheme.typography.labelSmall,
          color = hud.textMuted,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    return
  }

  // Unwrap angle continuously so crossing 359° -> 0° animates across shortest arc.
  var continuousHeading by remember { mutableFloatStateOf(compassState.headingDegrees) }
  LaunchedEffect(compassState.headingDegrees) {
    val currentWrapped = CompassRepository.normalizeDegrees(continuousHeading)
    val delta = ((compassState.headingDegrees - currentWrapped + 540f) % 360f) - 180f
    continuousHeading += delta
  }

  val animatedHeading by
    animateFloatAsState(
      targetValue = continuousHeading,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f),
      label = "compassHeading",
    )

  val displayHeading = CompassRepository.normalizeDegrees(animatedHeading)
  val displayDegInt = displayHeading.roundToInt() % 360
  val displayCardinal = CompassRepository.cardinalDirection(displayHeading)

  val accuracyColor =
    when (compassState.accuracy) {
      CompassAccuracy.HIGH -> hud.signalGreen
      CompassAccuracy.MEDIUM -> hud.accent
      CompassAccuracy.LOW -> hud.warningAmber
      CompassAccuracy.UNAVAILABLE -> hud.alertRed
    }

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = hud.pillBackground,
    border = BorderStroke(1.dp, hud.pillBorder),
    modifier =
      modifier
        .widthIn(max = 336.dp)
        .fillMaxWidth(0.92f)
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .semantics(mergeDescendants = true) {}
        .testTag("compass_direction_overlay"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Mini rotating North-pointer rose
      CompassMiniDial(
        headingDegrees = displayHeading,
        accentColor = hud.accent,
        northColor = hud.alertRed,
        ringColor = hud.pillBorder,
        southColor = hud.textMuted,
        modifier = Modifier.size(34.dp),
      )

      // Scrolling azimuth degree tape
      CompassAzimuthTape(
        headingDegrees = displayHeading,
        accentColor = hud.accent,
        northColor = hud.alertRed,
        tickColor = hud.textSecondary,
        labelColor = hud.textPrimary,
        modifier = Modifier.weight(1f).height(34.dp),
      )

      // Live digital bearing readout
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.width(58.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Box(
            modifier = Modifier.size(6.dp).clip(CircleShape).background(accuracyColor)
          )
          Text(
            text = displayCardinal,
            style = MaterialTheme.typography.labelMedium,
            color = if (displayCardinal == "N") hud.alertRed else hud.accent,
            fontWeight = FontWeight.ExtraBold,
          )
        }
        Text(
          text = String.format(Locale.US, "%03d° %s", displayDegInt, if (compassState.isTrueNorth) "T" else "M"),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = hud.textPrimary,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun CompassMiniDial(
  headingDegrees: Float,
  accentColor: Color,
  northColor: Color,
  ringColor: Color,
  southColor: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)

    // Outer compass housing
    drawCircle(
      color = ringColor,
      radius = radius - 1.dp.toPx(),
      center = center,
      style = Stroke(width = 1.2.dp.toPx()),
    )

    // Fixed camera forward index at top
    drawLine(
      color = accentColor,
      start = Offset(center.x, 0f),
      end = Offset(center.x, 4.dp.toPx()),
      strokeWidth = 2.dp.toPx(),
      cap = StrokeCap.Round,
    )

    // Rotate needle opposite to camera heading so red tip always points toward real-world North
    rotate(degrees = -headingDegrees, pivot = center) {
      val needleLen = radius * 0.62f
      val needleHalfW = radius * 0.2f

      val northPath =
        Path().apply {
          moveTo(center.x, center.y - needleLen)
          lineTo(center.x - needleHalfW, center.y)
          lineTo(center.x + needleHalfW, center.y)
          close()
        }
      drawPath(northPath, color = northColor)

      val southPath =
        Path().apply {
          moveTo(center.x, center.y + needleLen)
          lineTo(center.x - needleHalfW, center.y)
          lineTo(center.x + needleHalfW, center.y)
          close()
        }
      drawPath(southPath, color = southColor.copy(alpha = 0.65f))
    }

    drawCircle(
      color = Color.White,
      radius = 2.dp.toPx(),
      center = center,
    )
  }
}

@Composable
private fun CompassAzimuthTape(
  headingDegrees: Float,
  accentColor: Color,
  northColor: Color,
  tickColor: Color,
  labelColor: Color,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()

  Box(modifier = modifier) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      val centerX = w / 2f
      val visibleHalfSpanDeg = 55f
      val pxPerDeg = centerX / visibleHalfSpanDeg

      // Center lubber triangle & vertical hairline
      val triW = 5.dp.toPx()
      val triH = 5.dp.toPx()
      val markerPath =
        Path().apply {
          moveTo(centerX, triH)
          lineTo(centerX - triW, 0f)
          lineTo(centerX + triW, 0f)
          close()
        }
      drawPath(markerPath, color = accentColor)
      drawLine(
        color = accentColor.copy(alpha = 0.7f),
        start = Offset(centerX, triH),
        end = Offset(centerX, h * 0.45f),
        strokeWidth = 1.5.dp.toPx(),
      )

      val startDeg = floor((headingDegrees - visibleHalfSpanDeg) / 5f).toInt() * 5
      val endDeg = floor((headingDegrees + visibleHalfSpanDeg) / 5f).toInt() * 5 + 5

      for (degStep in startDeg..endDeg step 5) {
        val diff = degStep - headingDegrees
        val x = centerX + diff * pxPerDeg
        if (x < 0f || x > w) continue

        val edgeFade = (1f - (abs(x - centerX) / centerX).pow2()).coerceIn(0f, 1f)
        val wrappedDeg = ((degStep % 360) + 360) % 360
        val isCardinal = wrappedDeg % 90 == 0
        val isIntercardinal = wrappedDeg % 45 == 0 && !isCardinal
        val isMajor = wrappedDeg % 15 == 0

        val tickTop = h * 0.22f
        val tickBottom =
          when {
            isCardinal -> h * 0.56f
            isIntercardinal -> h * 0.50f
            isMajor -> h * 0.44f
            else -> h * 0.36f
          }

        val tickTint =
          when {
            wrappedDeg == 0 -> northColor
            isCardinal -> accentColor
            else -> tickColor
          }.copy(alpha = (if (isMajor) 0.9f else 0.45f) * edgeFade)

        drawLine(
          color = tickTint,
          start = Offset(x, tickTop),
          end = Offset(x, tickBottom),
          strokeWidth = if (isCardinal) 1.8.dp.toPx() else 1.1.dp.toPx(),
          cap = StrokeCap.Round,
        )

        val label =
          when (wrappedDeg) {
            0 -> "N"
            45 -> "NE"
            90 -> "E"
            135 -> "SE"
            180 -> "S"
            225 -> "SW"
            270 -> "W"
            315 -> "NW"
            else -> if (wrappedDeg % 30 == 0) wrappedDeg.toString() else null
          }

        if (label != null) {
          val style =
            TextStyle(
              fontSize = if (isCardinal) 11.sp else if (isIntercardinal) 10.sp else 9.sp,
              fontWeight = if (isCardinal || isIntercardinal) FontWeight.ExtraBold else FontWeight.SemiBold,
              color =
                when {
                  wrappedDeg == 0 -> northColor
                  isCardinal -> accentColor
                  else -> labelColor
                }.copy(alpha = edgeFade),
            )
          val measured = textMeasurer.measure(text = label, style = style)
          val textX = x - measured.size.width / 2f
          val textY = h - measured.size.height - 1.dp.toPx()
          if (textX >= 0f && textX + measured.size.width <= w) {
            drawText(measured, topLeft = Offset(textX, textY))
          }
        }
      }
    }
  }
}

private fun Float.pow2(): Float = this * this
