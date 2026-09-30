package com.notmugil.uta.ui.screens.player

import com.notmugil.uta.ui.screens.player.components.SongWaveformGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongWaveformGeneratorTest {

    @Test
    fun `generateProceduralProfile produces expected sample count and bounded range`() {
        val count = 256
        val profile = SongWaveformGenerator.generateProceduralProfile("track_12345", count)

        assertEquals(count, profile.size)
        for (i in profile.indices) {
            assertTrue("Sample $i (${profile[i]}) is below minimum 0.05f", profile[i] >= 0.05f)
            assertTrue("Sample $i (${profile[i]}) exceeds maximum 1.0f", profile[i] <= 1.0f)
        }
    }

    @Test
    fun `generateProceduralProfile is deterministic for same key`() {
        val p1 = SongWaveformGenerator.generateProceduralProfile("track_abc", 256)
        val p2 = SongWaveformGenerator.generateProceduralProfile("track_abc", 256)

        assertEquals(p1.size, p2.size)
        for (i in p1.indices) {
            assertEquals(p1[i], p2[i], 0.0001f)
        }
    }

    @Test
    fun `generateProceduralProfile produces distinct waveforms across different tracks`() {
        val p1 = SongWaveformGenerator.generateProceduralProfile("song_electronic_drop", 256)
        val p2 = SongWaveformGenerator.generateProceduralProfile("song_acoustic_verse", 256)
        val p3 = SongWaveformGenerator.generateProceduralProfile("song_hiphop_beat", 256)

        // Ensure differences between profiles are significant
        var diffCount12 = 0
        var diffCount13 = 0
        for (i in 0 until 256) {
            if (kotlin.math.abs(p1[i] - p2[i]) > 0.15f) diffCount12++
            if (kotlin.math.abs(p1[i] - p3[i]) > 0.15f) diffCount13++
        }
        assertTrue("Different tracks should have distinctly different waveform profiles", diffCount12 > 50)
        assertTrue("Different tracks should have distinctly different waveform profiles", diffCount13 > 50)
    }

    @Test
    fun `processEnergyProfile applies dB scaling, contrast expansion and peak preservation`() {
        val rawPeaks = FloatArray(100) { i -> (i / 100f) }
        val rawRms = FloatArray(100) { i -> (i / 100f) * 0.8f }

        val processed = SongWaveformGenerator.processEnergyProfile(rawPeaks, rawRms, 100)
        assertEquals(100, processed.size)

        // Ensure baseline starts at >= 0.05f and max reaches 1.0f
        assertTrue(processed.first() >= 0.05f)
        assertEquals(1.0f, processed.last(), 0.001f)
    }

    @Test
    fun `processEnergyProfile handles silent or zero input gracefully`() {
        val rawPeaks = FloatArray(50) { 0f }
        val processed = SongWaveformGenerator.processEnergyProfile(rawPeaks, null, 50)

        assertEquals(50, processed.size)
        for (v in processed) {
            assertTrue(v >= 0.05f)
        }
    }
}
