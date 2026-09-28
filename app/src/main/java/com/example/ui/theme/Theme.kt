package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val CodeOneDarkColorScheme =
  darkColorScheme(
    primary = CodeOneElectricBlue,
    secondary = CodeOneNeonCyan,
    tertiary = CodeOneBorderGlow,
    background = CodeOneDarkNavy,
    surface = CodeOneSurfaceDark,
    surfaceVariant = CodeOneSurfaceVariantDark,
    onPrimary = Color.White,
    onSecondary = Color(0xFF0A0F1D),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF94A3B8)
  )

private val CodeOneLightColorScheme =
  lightColorScheme(
    primary = CodeOneBlueGradientStart,
    secondary = Color(0xFF0284C7),
    tertiary = CodeOneBorderGlow,
    background = CodeOneLightBg,
    surface = CodeOneLightSurface,
    surfaceVariant = CodeOneLightSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569)
  )

private val EmeraldDarkColorScheme =
  darkColorScheme(
    primary = GptGreen,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = GptDarkBg,
    surface = GptDarkSurface,
    surfaceVariant = GptDarkSurfaceVariant,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.LightGray
  )

private val EmeraldLightColorScheme =
  lightColorScheme(
    primary = GptGreen,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF9F9F9),
    surfaceVariant = Color(0xFFF0F0F0),
    onPrimary = Color.White,
    onBackground = Color(0xFF171717),
    onSurface = Color(0xFF171717),
    onSurfaceVariant = Color(0xFF555555)
  )

private val AzureDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF7A60FF),
    secondary = Color(0xFF4285F4),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF0F1016),
    surface = Color(0xFF181A24),
    surfaceVariant = Color(0xFF242736),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF8E92A6)
  )

private val AzureLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF5E43F3),
    secondary = Color(0xFF4285F4),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFFF4F5FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEBEFFB),
    onPrimary = Color.White,
    onBackground = Color(0xFF0F1016),
    onSurface = Color(0xFF0F1016),
    onSurfaceVariant = Color(0xFF5C6073)
  )

private val OceanDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF00ACC1),
    secondary = Color(0xFF26A69A),
    background = Color(0xFF0E1A1E),
    surface = Color(0xFF142429),
    surfaceVariant = Color(0xFF1C343B),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF7E979C)
  )

private val OceanLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF00838F),
    secondary = Color(0xFF00ACC1),
    background = Color(0xFFEEF5F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFD6E7E9),
    onPrimary = Color.White,
    onBackground = Color(0xFF0E1A1E),
    onSurface = Color(0xFF0E1A1E),
    onSurfaceVariant = Color(0xFF485D61)
  )

private val SunsetDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFFFA726),
    secondary = Color(0xFFFF7043),
    background = Color(0xFF1B1512),
    surface = Color(0xFF261D1A),
    surfaceVariant = Color(0xFF332722),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB19B91)
  )

private val SunsetLightColorScheme =
  lightColorScheme(
    primary = Color(0xFFE65100),
    secondary = Color(0xFFF57C00),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5EAE4),
    onPrimary = Color.White,
    onBackground = Color(0xFF2E1C14),
    onSurface = Color(0xFF2E1C14),
    onSurfaceVariant = Color(0xFF705E56)
  )

@Composable
fun MyApplicationTheme(
  themeName: String = "codeone",
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when (themeName) {
      "codeone" -> if (darkTheme) CodeOneDarkColorScheme else CodeOneLightColorScheme
      "azure" -> if (darkTheme) AzureDarkColorScheme else AzureLightColorScheme
      "ocean" -> if (darkTheme) OceanDarkColorScheme else OceanLightColorScheme
      "sunset" -> if (darkTheme) SunsetDarkColorScheme else SunsetLightColorScheme
      "emerald" -> if (darkTheme) EmeraldDarkColorScheme else EmeraldLightColorScheme
      else -> {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val context = LocalContext.current
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
          if (darkTheme) CodeOneDarkColorScheme else CodeOneLightColorScheme
        }
      }
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
