package com.notmugil.uta.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// Pure neutral base tokens for dark mode (AMOLED / deep black)
val NeutralDarkBackground = Color(0xFF000000)
val NeutralDarkOnBackground = Color(0xFFE2E2E6)
val NeutralDarkSurface = Color(0xFF000000)
val NeutralDarkOnSurface = Color(0xFFE2E2E6)
val NeutralDarkSurfaceVariant = Color(0xFF14161C)
val NeutralDarkOnSurfaceVariant = Color(0xFFC4C6D0)
val NeutralDarkSurfaceContainerLowest = Color(0xFF000000)
val NeutralDarkSurfaceContainerLow = Color(0xFF0A0B0F)
val NeutralDarkSurfaceContainer = Color(0xFF101217)
val NeutralDarkSurfaceContainerHigh = Color(0xFF171920)
val NeutralDarkSurfaceContainerHighest = Color(0xFF1F2129)
val NeutralDarkSurfaceBright = Color(0xFF282A33)
val NeutralDarkSurfaceDim = Color(0xFF000000)
val NeutralDarkOutline = Color(0xFF6E717D)
val NeutralDarkOutlineVariant = Color(0xFF2B2D38)

// Pure neutral base tokens for light mode
val NeutralLightBackground = Color(0xFFF9F9FF)
val NeutralLightOnBackground = Color(0xFF191C20)
val NeutralLightSurface = Color(0xFFF9F9FF)
val NeutralLightOnSurface = Color(0xFF191C20)
val NeutralLightSurfaceVariant = Color(0xFFE1E2EC)
val NeutralLightOnSurfaceVariant = Color(0xFF44474F)
val NeutralLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val NeutralLightSurfaceContainerLow = Color(0xFFF3F3F9)
val NeutralLightSurfaceContainer = Color(0xFFEDEEF4)
val NeutralLightSurfaceContainerHigh = Color(0xFFE7E8EE)
val NeutralLightSurfaceContainerHighest = Color(0xFFE1E2E8)
val NeutralLightSurfaceBright = Color(0xFFF9F9FF)
val NeutralLightSurfaceDim = Color(0xFFD9D9E0)
val NeutralLightOutline = Color(0xFF74777F)
val NeutralLightOutlineVariant = Color(0xFFC4C6D0)

private fun hslColor(h: Float, s: Float, l: Float): Color {
    val out = FloatArray(3)
    out[0] = (h % 360f + 360f) % 360f
    out[1] = s.coerceIn(0f, 1f)
    out[2] = l.coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(out))
}

fun dynamicDarkColorSchemeFromSeed(seedColor: Color): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seedColor.toArgb(), hsl)
    val h = hsl[0]
    val s = hsl[1].coerceIn(0.35f, 0.85f)

    val sPri = s
    val sSec = (s * 0.45f).coerceIn(0.12f, 0.40f)
    val sTer = (s * 0.70f).coerceIn(0.25f, 0.65f)
    val hSec = h
    val hTer = (h + 60f) % 360f

    return darkColorScheme(
        primary = hslColor(h, sPri, 0.80f),
        onPrimary = hslColor(h, sPri, 0.20f),
        primaryContainer = hslColor(h, sPri, 0.30f),
        onPrimaryContainer = hslColor(h, sPri, 0.90f),
        inversePrimary = hslColor(h, sPri, 0.40f),
        secondary = hslColor(hSec, sSec, 0.80f),
        onSecondary = hslColor(hSec, sSec, 0.20f),
        secondaryContainer = hslColor(hSec, sSec, 0.30f),
        onSecondaryContainer = hslColor(hSec, sSec, 0.90f),
        tertiary = hslColor(hTer, sTer, 0.80f),
        onTertiary = hslColor(hTer, sTer, 0.20f),
        tertiaryContainer = hslColor(hTer, sTer, 0.30f),
        onTertiaryContainer = hslColor(hTer, sTer, 0.90f),
        background = NeutralDarkBackground,
        onBackground = NeutralDarkOnBackground,
        surface = NeutralDarkSurface,
        onSurface = NeutralDarkOnSurface,
        surfaceVariant = NeutralDarkSurfaceVariant,
        onSurfaceVariant = NeutralDarkOnSurfaceVariant,
        surfaceTint = hslColor(h, sPri, 0.80f),
        inverseSurface = Color(0xFFE2E2E6),
        inverseOnSurface = Color(0xFF2F3036),
        outline = NeutralDarkOutline,
        outlineVariant = NeutralDarkOutlineVariant,
        scrim = Color.Black,
        surfaceBright = NeutralDarkSurfaceBright,
        surfaceDim = NeutralDarkSurfaceDim,
        surfaceContainerLowest = NeutralDarkSurfaceContainerLowest,
        surfaceContainerLow = NeutralDarkSurfaceContainerLow,
        surfaceContainer = NeutralDarkSurfaceContainer,
        surfaceContainerHigh = NeutralDarkSurfaceContainerHigh,
        surfaceContainerHighest = NeutralDarkSurfaceContainerHighest,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
}

fun dynamicLightColorSchemeFromSeed(seedColor: Color): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seedColor.toArgb(), hsl)
    val h = hsl[0]
    val s = hsl[1].coerceIn(0.35f, 0.85f)

    val sPri = s
    val sSec = (s * 0.45f).coerceIn(0.12f, 0.40f)
    val sTer = (s * 0.70f).coerceIn(0.25f, 0.65f)
    val hSec = h
    val hTer = (h + 60f) % 360f

    return lightColorScheme(
        primary = hslColor(h, sPri, 0.40f),
        onPrimary = Color.White,
        primaryContainer = hslColor(h, sPri, 0.90f),
        onPrimaryContainer = hslColor(h, sPri, 0.10f),
        inversePrimary = hslColor(h, sPri, 0.80f),
        secondary = hslColor(hSec, sSec, 0.40f),
        onSecondary = Color.White,
        secondaryContainer = hslColor(hSec, sSec, 0.90f),
        onSecondaryContainer = hslColor(hSec, sSec, 0.10f),
        tertiary = hslColor(hTer, sTer, 0.40f),
        onTertiary = Color.White,
        tertiaryContainer = hslColor(hTer, sTer, 0.90f),
        onTertiaryContainer = hslColor(hTer, sTer, 0.10f),
        background = NeutralLightBackground,
        onBackground = NeutralLightOnBackground,
        surface = NeutralLightSurface,
        onSurface = NeutralLightOnSurface,
        surfaceVariant = NeutralLightSurfaceVariant,
        onSurfaceVariant = NeutralLightOnSurfaceVariant,
        surfaceTint = hslColor(h, sPri, 0.40f),
        inverseSurface = Color(0xFF2F3036),
        inverseOnSurface = Color(0xFFF1F0F7),
        outline = NeutralLightOutline,
        outlineVariant = NeutralLightOutlineVariant,
        scrim = Color.Black,
        surfaceBright = NeutralLightSurfaceBright,
        surfaceDim = NeutralLightSurfaceDim,
        surfaceContainerLowest = NeutralLightSurfaceContainerLowest,
        surfaceContainerLow = NeutralLightSurfaceContainerLow,
        surfaceContainer = NeutralLightSurfaceContainer,
        surfaceContainerHigh = NeutralLightSurfaceContainerHigh,
        surfaceContainerHighest = NeutralLightSurfaceContainerHighest,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
}

fun accentOnlyDarkColorScheme(baseDynamicScheme: ColorScheme): ColorScheme = baseDynamicScheme.copy(
    background = NeutralDarkBackground,
    onBackground = NeutralDarkOnBackground,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkOnSurface,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = NeutralDarkOnSurfaceVariant,
    surfaceContainerLowest = NeutralDarkSurfaceContainerLowest,
    surfaceContainerLow = NeutralDarkSurfaceContainerLow,
    surfaceContainer = NeutralDarkSurfaceContainer,
    surfaceContainerHigh = NeutralDarkSurfaceContainerHigh,
    surfaceContainerHighest = NeutralDarkSurfaceContainerHighest,
    surfaceBright = NeutralDarkSurfaceBright,
    surfaceDim = NeutralDarkSurfaceDim,
    outline = NeutralDarkOutline,
    outlineVariant = NeutralDarkOutlineVariant,
    onPrimaryContainer = NeutralDarkOnSurface,
    onSecondaryContainer = NeutralDarkOnSurface,
    onTertiaryContainer = NeutralDarkOnSurface
)

fun accentOnlyLightColorScheme(baseDynamicScheme: ColorScheme): ColorScheme = baseDynamicScheme.copy(
    background = NeutralLightBackground,
    onBackground = NeutralLightOnBackground,
    surface = NeutralLightSurface,
    onSurface = NeutralLightOnSurface,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralLightOnSurfaceVariant,
    surfaceContainerLowest = NeutralLightSurfaceContainerLowest,
    surfaceContainerLow = NeutralLightSurfaceContainerLow,
    surfaceContainer = NeutralLightSurfaceContainer,
    surfaceContainerHigh = NeutralLightSurfaceContainerHigh,
    surfaceContainerHighest = NeutralLightSurfaceContainerHighest,
    surfaceBright = NeutralLightSurfaceBright,
    surfaceDim = NeutralLightSurfaceDim,
    outline = NeutralLightOutline,
    outlineVariant = NeutralLightOutlineVariant
)
