package com.notmugil.uta.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DynamicColorSchemeTest {

    @Test
    fun `dynamicDarkColorSchemeFromSeed generates valid scheme with neutral AMOLED dark background`() {
        val seed = Color(0xFF6200EE)
        val scheme = dynamicDarkColorSchemeFromSeed(seed)

        assertNotNull(scheme)
        assertEquals(NeutralDarkBackground, scheme.background)
        assertEquals(NeutralDarkSurface, scheme.surface)
        assertEquals(NeutralDarkSurfaceContainer, scheme.surfaceContainer)
        assertNotNull(scheme.primary)
        assertNotNull(scheme.secondary)
        assertNotNull(scheme.tertiary)
    }

    @Test
    fun `dynamicLightColorSchemeFromSeed generates valid scheme with neutral light background`() {
        val seed = Color(0xFF03DAC5)
        val scheme = dynamicLightColorSchemeFromSeed(seed)

        assertNotNull(scheme)
        assertEquals(NeutralLightBackground, scheme.background)
        assertEquals(NeutralLightSurface, scheme.surface)
        assertEquals(NeutralLightSurfaceContainer, scheme.surfaceContainer)
        assertNotNull(scheme.primary)
    }

    @Test
    fun `accentOnlyDarkColorScheme locks background and surfaces to AMOLED dark`() {
        val seedScheme = dynamicDarkColorSchemeFromSeed(Color(0xFFE91E63))
        val accentOnly = accentOnlyDarkColorScheme(seedScheme)

        assertEquals(NeutralDarkBackground, accentOnly.background)
        assertEquals(NeutralDarkSurface, accentOnly.surface)
        assertEquals(NeutralDarkSurfaceVariant, accentOnly.surfaceVariant)
        assertEquals(NeutralDarkSurfaceContainerHighest, accentOnly.surfaceContainerHighest)
    }

    @Test
    fun `dynamicDarkColorSchemeFromSeed with achromatic seed produces neutral primary without red tint`() {
        val graySeed = Color(0xFF757575)
        val scheme = dynamicDarkColorSchemeFromSeed(graySeed)

        assertNotNull(scheme)
        val primaryHsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(scheme.primary.toArgb(), primaryHsl)
        assertEquals(0.0f, primaryHsl[1], 0.01f)
    }

    @Test
    fun `dynamicLightColorSchemeFromSeed with achromatic seed produces neutral primary without red tint`() {
        val graySeed = Color(0xFF888888)
        val scheme = dynamicLightColorSchemeFromSeed(graySeed)

        assertNotNull(scheme)
        val primaryHsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(scheme.primary.toArgb(), primaryHsl)
        assertEquals(0.0f, primaryHsl[1], 0.01f)
    }

    @Test
    fun `buildDynamicColorScheme uses accent for tertiary color`() {
        val albumColors = AlbumColors(
            seed = Color(0xFF1E88E5),
            accent = Color(0xFFFB8C00),
            isNeutral = false
        )
        val scheme = buildDynamicColorScheme(albumColors, isDark = true)

        assertNotNull(scheme)
        val tertiaryHsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(scheme.tertiary.toArgb(), tertiaryHsl)
        val accentHsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(albumColors.accent!!.toArgb(), accentHsl)

        assertEquals(accentHsl[0], tertiaryHsl[0], 2.0f)
    }
}
