package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class HudColors(
  val isDark: Boolean,
  val glassBackground: Color,
  val glassBorder: Color,
  val pillBackground: Color,
  val pillBorder: Color,
  val accent: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textMuted: Color,
  val signalGreen: Color,
  val warningAmber: Color,
  val alertRed: Color,
  val mapPlaceholderBg: Color,
)

private val DarkHudColors =
  HudColors(
    isDark = true,
    glassBackground = HudGlassBackground,
    glassBorder = HudGlassBorder,
    pillBackground = HudTopPillBackground,
    pillBorder = Color.White.copy(alpha = 0.16f),
    accent = TelemetryCyan,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textMuted = TextMutedLight,
    signalGreen = SignalEmerald,
    warningAmber = ShutterAmber,
    alertRed = AlertRose,
    mapPlaceholderBg = Color(0xFF142236),
  )

private val LightHudColors =
  HudColors(
    isDark = false,
    glassBackground = HudLightGlassBackground,
    glassBorder = HudLightGlassBorder,
    pillBackground = HudLightPillBackground,
    pillBorder = TextPrimaryDark.copy(alpha = 0.14f),
    accent = TelemetryCyanDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textMuted = TextMutedDark,
    signalGreen = SignalEmeraldDark,
    warningAmber = ShutterAmberDark,
    alertRed = AlertRoseDark,
    mapPlaceholderBg = LightSurfaceVariant,
  )

val LocalHudColors = staticCompositionLocalOf { DarkHudColors }

private val DarkColorScheme =
  darkColorScheme(
    primary = TelemetryCyan,
    onPrimary = OnTelemetryCyan,
    primaryContainer = TelemetryCyanContainer,
    onPrimaryContainer = TextPrimaryLight,
    secondary = ShutterAmber,
    onSecondary = ObsidianBase,
    secondaryContainer = ShutterAmberContainer,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = SignalEmerald,
    background = ObsidianBase,
    onBackground = TextPrimaryLight,
    surface = SlateSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    error = AlertRose,
    outline = HudGlassBorder,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = TelemetryCyanDark,
    onPrimary = LightSurface,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = TextPrimaryDark,
    secondary = ShutterAmberDark,
    onSecondary = LightSurface,
    tertiary = SignalEmeraldDark,
    background = LightBackground,
    onBackground = TextPrimaryDark,
    surface = LightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    error = AlertRoseDark,
    outline = HudLightGlassBorder,
  )

@Composable
fun GeoCamTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val hudColors = if (darkTheme) DarkHudColors else LightHudColors

  CompositionLocalProvider(LocalHudColors provides hudColors) {
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
  }
}
