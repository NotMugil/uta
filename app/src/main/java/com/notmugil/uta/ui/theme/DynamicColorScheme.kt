package com.notmugil.uta.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

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

private data class ToneSpec(
    val primaryTone: Float,
    val onPrimaryTone: Float,
    val primaryContainerTone: Float,
    val onPrimaryContainerTone: Float,
    val inversePrimaryTone: Float,
    val secondaryTone: Float,
    val onSecondaryTone: Float,
    val secondaryContainerTone: Float,
    val onSecondaryContainerTone: Float,
    val tertiaryTone: Float,
    val onTertiaryTone: Float,
    val tertiaryContainerTone: Float,
    val onTertiaryContainerTone: Float
)

private val DarkTones = ToneSpec(
    primaryTone = 0.80f,
    onPrimaryTone = 0.20f,
    primaryContainerTone = 0.30f,
    onPrimaryContainerTone = 0.90f,
    inversePrimaryTone = 0.40f,
    secondaryTone = 0.80f,
    onSecondaryTone = 0.20f,
    secondaryContainerTone = 0.30f,
    onSecondaryContainerTone = 0.90f,
    tertiaryTone = 0.80f,
    onTertiaryTone = 0.20f,
    tertiaryContainerTone = 0.30f,
    onTertiaryContainerTone = 0.90f
)

private val LightTones = ToneSpec(
    primaryTone = 0.40f,
    onPrimaryTone = 1.00f,
    primaryContainerTone = 0.90f,
    onPrimaryContainerTone = 0.10f,
    inversePrimaryTone = 0.80f,
    secondaryTone = 0.40f,
    onSecondaryTone = 1.00f,
    secondaryContainerTone = 0.90f,
    onSecondaryContainerTone = 0.10f,
    tertiaryTone = 0.40f,
    onTertiaryTone = 1.00f,
    tertiaryContainerTone = 0.90f,
    onTertiaryContainerTone = 0.10f
)

private fun hslColor(h: Float, s: Float, l: Float): Color {
    val out = FloatArray(3)
    out[0] = (h % 360f + 360f) % 360f
    out[1] = s.coerceIn(0f, 1f)
    out[2] = l.coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(out))
}

fun buildDynamicColorScheme(
    albumColors: AlbumColors,
    isDark: Boolean
): ColorScheme = buildDynamicColorScheme(
    seedColor = albumColors.seed,
    accentColor = albumColors.accent,
    isDark = isDark,
    isNeutral = albumColors.isNeutral
)

fun buildDynamicColorScheme(
    seedColor: Color,
    accentColor: Color? = null,
    isDark: Boolean,
    isNeutral: Boolean? = null
): ColorScheme {
    val seedHsl = FloatArray(3)
    ColorUtils.colorToHSL(seedColor.toArgb(), seedHsl)
    val h = seedHsl[0]
    val rawS = seedHsl[1]
    val achromatic = isNeutral ?: (rawS < 0.12f)

    val tones = if (isDark) DarkTones else LightTones

    val sPri = if (achromatic) 0.0f else rawS.coerceIn(0.30f, 0.85f)
    val sSec = if (achromatic) 0.0f else (sPri * 0.45f).coerceIn(0.12f, 0.40f)

    val (hTer, sTer) = if (achromatic) {
        0.0f to 0.0f
    } else if (accentColor != null) {
        val accHsl = FloatArray(3)
        ColorUtils.colorToHSL(accentColor.toArgb(), accHsl)
        accHsl[0] to accHsl[1].coerceIn(0.25f, 0.75f)
    } else {
        ((h + 60f) % 360f) to ((sPri * 0.70f).coerceIn(0.25f, 0.65f))
    }

    val primaryLightness = if (achromatic) {
        if (isDark) seedHsl[2].coerceIn(0.65f, 0.78f) else seedHsl[2].coerceIn(0.32f, 0.48f)
    } else {
        tones.primaryTone
    }

    val primaryColor = hslColor(h, sPri, primaryLightness)
    val primaryContainer = hslColor(h, sPri, if (achromatic && isDark) 0.28f else if (achromatic) 0.88f else tones.primaryContainerTone)
    val secondaryColor = hslColor(h, sSec, if (achromatic && isDark) 0.70f else if (achromatic) 0.45f else tones.secondaryTone)
    val secondaryContainer = hslColor(h, sSec, if (achromatic && isDark) 0.24f else if (achromatic) 0.90f else tones.secondaryContainerTone)
    val tertiaryColor = hslColor(hTer, sTer, if (achromatic && isDark) 0.75f else if (achromatic) 0.45f else tones.tertiaryTone)
    val tertiaryContainer = hslColor(hTer, sTer, if (achromatic && isDark) 0.24f else if (achromatic) 0.90f else tones.tertiaryContainerTone)

    return if (isDark) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = hslColor(h, sPri, tones.onPrimaryTone),
            primaryContainer = primaryContainer,
            onPrimaryContainer = hslColor(h, sPri, tones.onPrimaryContainerTone),
            inversePrimary = hslColor(h, sPri, tones.inversePrimaryTone),
            secondary = secondaryColor,
            onSecondary = hslColor(h, sSec, tones.onSecondaryTone),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = hslColor(h, sSec, tones.onSecondaryContainerTone),
            tertiary = tertiaryColor,
            onTertiary = hslColor(hTer, sTer, tones.onTertiaryTone),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = hslColor(hTer, sTer, tones.onTertiaryContainerTone),
            background = NeutralDarkBackground,
            onBackground = NeutralDarkOnBackground,
            surface = NeutralDarkSurface,
            onSurface = NeutralDarkOnSurface,
            surfaceVariant = NeutralDarkSurfaceVariant,
            onSurfaceVariant = NeutralDarkOnSurfaceVariant,
            surfaceTint = primaryColor,
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
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = hslColor(h, sPri, tones.onPrimaryContainerTone),
            inversePrimary = hslColor(h, sPri, tones.inversePrimaryTone),
            secondary = secondaryColor,
            onSecondary = Color.White,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = hslColor(h, sSec, tones.onSecondaryContainerTone),
            tertiary = tertiaryColor,
            onTertiary = Color.White,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = hslColor(hTer, sTer, tones.onTertiaryContainerTone),
            background = NeutralLightBackground,
            onBackground = NeutralLightOnBackground,
            surface = NeutralLightSurface,
            onSurface = NeutralLightOnSurface,
            surfaceVariant = NeutralLightSurfaceVariant,
            onSurfaceVariant = NeutralLightOnSurfaceVariant,
            surfaceTint = primaryColor,
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
}

fun dynamicDarkColorSchemeFromSeed(seedColor: Color): ColorScheme =
    buildDynamicColorScheme(seedColor = seedColor, isDark = true)

fun dynamicLightColorSchemeFromSeed(seedColor: Color): ColorScheme =
    buildDynamicColorScheme(seedColor = seedColor, isDark = false)

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
