package com.combadge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ //
// LCARS Color Palette
// ------------------------------------------------------------------ //

val DeepSpace       = Color(0xFF1A1A2E)
val DeepSpaceDark   = Color(0xFF0F0F1E)
val LcarsAmber      = Color(0xFFFF9900)
val LcarsAmberDim   = Color(0xFF996600)
val LcarsLavender   = Color(0xFFCC99CC)
val LcarsPeriwinkle = Color(0xFF9999FF)
val LcarsAlert      = Color(0xFFFF3333)
val LcarsTeal       = Color(0xFF33CC99)
val LcarsGold       = Color(0xFFFFCC44)
val LcarsText       = Color(0xFFDDDDEE)
val LcarsTextDim    = Color(0xFF888899)

val LcarsDarkColorScheme = darkColorScheme(
    primary          = LcarsAmber,
    onPrimary        = DeepSpace,
    primaryContainer = LcarsAmberDim,
    secondary        = LcarsLavender,
    onSecondary      = DeepSpace,
    tertiary         = LcarsPeriwinkle,
    background       = DeepSpace,
    onBackground     = LcarsText,
    surface          = DeepSpaceDark,
    onSurface        = LcarsText,
    error            = LcarsAlert,
    onError          = Color.White
)

// ------------------------------------------------------------------ //
// Typography
// ------------------------------------------------------------------ //

val LcarsTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        color = LcarsAmber,
        letterSpacing = 4.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        color = LcarsAmber,
        letterSpacing = 3.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        color = LcarsText,
        letterSpacing = 2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = LcarsText,
        letterSpacing = 1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = LcarsTextDim,
        letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        color = LcarsTextDim,
        letterSpacing = 1.5.sp
    )
)

// ------------------------------------------------------------------ //
// Theme Composable
// ------------------------------------------------------------------ //

@Composable
fun LcarsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LcarsDarkColorScheme,
        typography = LcarsTypography,
        content = content
    )
}
