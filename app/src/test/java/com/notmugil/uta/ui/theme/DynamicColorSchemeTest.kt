package com.notmugil.uta.ui.theme

import androidx.compose.ui.graphics.Color
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
}
