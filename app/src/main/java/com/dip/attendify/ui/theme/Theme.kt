package com.dip.attendify.ui.theme

import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────
// Static dark color scheme — indigo seed
// Used on Android < 12 where dynamic color
// isn't available.
//
// Surface hierarchy refined:
//   background / surface       → #111114  (near-black, deeper than before)
//   surfaceContainerLow        → #1A1A1F  (cards at rest)
//   surfaceContainer           → #1F1F25  (elevated cards)
//   surfaceContainerHigh       → #26262D  (sheets, dialogs)
//   surfaceContainerHighest    → #2D2D35  (top-most overlays)
// ─────────────────────────────────────────────

private val StaticDarkColorScheme = darkColorScheme(
    primary              = Color(0xFFBBC4FF),
    onPrimary            = Color(0xFF1E2F6E),
    primaryContainer     = Color(0xFF354686),
    onPrimaryContainer   = Color(0xFFDDE1FF),

    secondary            = Color(0xFFC3C5DD),
    onSecondary          = Color(0xFF2C2F42),
    secondaryContainer   = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFDFE1F9),

    tertiary             = Color(0xFFE5BAD8),
    onTertiary           = Color(0xFF44263E),
    tertiaryContainer    = Color(0xFF5D3C56),
    onTertiaryContainer  = Color(0xFFFFD7F3),

    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),

    // Refined surface tier — deeper blacks, more separation between layers
    background                = Color(0xFF111114),
    onBackground              = Color(0xFFE5E1E6),
    surface                   = Color(0xFF111114),
    onSurface                 = Color(0xFFE5E1E6),
    surfaceVariant             = Color(0xFF3A3A44),
    onSurfaceVariant           = Color(0xFFC7C5D0),
    surfaceContainerLowest     = Color(0xFF0D0D10),
    surfaceContainerLow        = Color(0xFF1A1A1F),
    surfaceContainer           = Color(0xFF1F1F25),
    surfaceContainerHigh       = Color(0xFF26262D),
    surfaceContainerHighest    = Color(0xFF2D2D35),

    outline                    = Color(0xFF8A8A96),
    outlineVariant             = Color(0xFF3A3A44),

    inverseSurface             = Color(0xFFE5E1E6),
    inverseOnSurface           = Color(0xFF2F2F37),
    inversePrimary             = Color(0xFF4B5CB9),

    scrim                      = Color(0xFF000000),
)

// ─────────────────────────────────────────────
// Vibrancy tokens (C5)
//
// M3's `error` role is deliberately desaturated for
// dark-theme accessibility — good for form validation,
// but too gentle for "you're about to fall below your
// attendance requirement." AtRiskRed is a separate,
// punchier token used ONLY for attendance at-risk states,
// so it stays consistent across dynamic-color devices
// instead of drifting with the wallpaper-derived palette.
//
// AttendanceGreen is the "safe" end of the gradient
// progress bars/ring (green → primary), reusing the same
// green already used for "Present" stat pills.
// ─────────────────────────────────────────────

val AtRiskRed          = Color(0xFFFF453A)
val AtRiskRedContainer = Color(0xFF4A1512)
val OnAtRiskRedContainer = Color(0xFFFFDAD4)

val AttendanceGreen = Color(0xFF4CAF50)

// ─────────────────────────────────────────────
// Typography — system default, tightened scale
// ─────────────────────────────────────────────

val AttendifyTypography = Typography(
    displayLarge   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 36.sp, lineHeight = 44.sp),

    headlineLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),

    titleLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),

    bodyLarge      = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall      = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),

    labelLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

// ─────────────────────────────────────────────
// Subject color palette
// Vibrant but dark-mode-aware — higher saturation
// so colors pop against the deep background
// ─────────────────────────────────────────────

val subjectColorPalette = listOf(
    "#EF5350",  // red
    "#EC407A",  // pink
    "#AB47BC",  // purple
    "#5C6BC0",  // indigo
    "#42A5F5",  // blue
    "#26C6DA",  // cyan
    "#26A69A",  // teal
    "#66BB6A",  // green
    "#D4E157",  // lime
    "#FFCA28",  // amber
    "#FFA726",  // orange
    "#8D6E63",  // brown
)

// ─────────────────────────────────────────────
// Theme composable
//
// Dark mode only — always uses dark scheme.
// Dynamic color (Material You) on Android 12+:
//   extracts palette from user's wallpaper.
// Falls back to static indigo scheme on <12.
// ─────────────────────────────────────────────

@Composable
fun AttendifyTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ — dynamic color from wallpaper, always dark variant
        dynamicDarkColorScheme(LocalContext.current).let { dynamic ->
            // Preserve our refined surface hierarchy even in dynamic mode
            // by overriding the surface tier with our deeper blacks
            dynamic.copy(
                background             = Color(0xFF111114),
                surface                = Color(0xFF111114),
                surfaceContainerLowest = Color(0xFF0D0D10),
                surfaceContainerLow    = Color(0xFF1A1A1F),
                surfaceContainer       = Color(0xFF1F1F25),
                surfaceContainerHigh   = Color(0xFF26262D),
                surfaceContainerHighest = Color(0xFF2D2D35),
            )
        }
    } else {
        StaticDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AttendifyTypography,
        content     = content,
    )
}