package com.notmugil.uta.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `formatDurationSeconds formats various durations accurately`() {
        assertEquals("0:00", Formatters.formatDurationSeconds(0L))
        assertEquals("0:00", Formatters.formatDurationSeconds(-5L))
        assertEquals("0:08", Formatters.formatDurationSeconds(8L))
        assertEquals("0:59", Formatters.formatDurationSeconds(59L))
        assertEquals("1:00", Formatters.formatDurationSeconds(60L))
        assertEquals("3:45", Formatters.formatDurationSeconds(225L))
        assertEquals("59:59", Formatters.formatDurationSeconds(3599L))
        assertEquals("1:00:00", Formatters.formatDurationSeconds(3600L))
        assertEquals("1:23:45", Formatters.formatDurationSeconds(5025L))
    }

    @Test
    fun `formatDurationMs converts milliseconds to readable string`() {
        assertEquals("0:00", Formatters.formatDurationMs(0L))
        assertEquals("0:00", Formatters.formatDurationMs(400L))
        assertEquals("0:01", Formatters.formatDurationMs(1200L))
        assertEquals("3:45", Formatters.formatDurationMs(225_000L))
    }

    @Test
    fun `formatHumanDuration formats durations into human readable strings`() {
        assertEquals("0 min", Formatters.formatHumanDuration(0L))
        assertEquals("32s", Formatters.formatHumanDuration(32L))
        assertEquals("3 min", Formatters.formatHumanDuration(225L))
        assertEquals("45 min", Formatters.formatHumanDuration(2710L))
        assertEquals("1 hr", Formatters.formatHumanDuration(3600L))
        assertEquals("1 hr 20 min", Formatters.formatHumanDuration(4800L))
    }

    @Test
    fun `formatBytes converts byte counts accurately`() {
        assertEquals("0 B", Formatters.formatBytes(0L))
        assertEquals("512 B", Formatters.formatBytes(512L))
        assertEquals("1.0 KB", Formatters.formatBytes(1024L))
        assertEquals("1.5 MB", Formatters.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.3 GB", Formatters.formatBytes((2.3 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `formatTrackAudioStats formats audio stats correctly`() {
        val mp3Track = com.notmugil.uta.domain.model.TrackItem(
            id = "1",
            title = "Song",
            artist = "Artist",
            suffix = "mp3",
            bitRate = 192
        )
        val format441 = androidx.media3.common.Format.Builder()
            .setSampleMimeType("audio/mpeg")
            .setSampleRate(44100)
            .setAverageBitrate(192000)
            .build()

        assertEquals("MP3 • 192kbps • 44.1kHz", Formatters.formatTrackAudioStats(mp3Track, format441))

        val flacTrack = com.notmugil.uta.domain.model.TrackItem(
            id = "2",
            title = "FLAC Song",
            artist = "Artist",
            suffix = "flac",
            bitRate = null
        )
        val flacFormat = androidx.media3.common.Format.Builder()
            .setSampleMimeType("audio/flac")
            .setSampleRate(44100)
            .build()

        assertEquals("FLAC • 44.1kHz", Formatters.formatTrackAudioStats(flacTrack, flacFormat))

        val hiResFlacFormat = androidx.media3.common.Format.Builder()
            .setSampleMimeType("audio/flac")
            .setSampleRate(96000)
            .setAverageBitrate(950000)
            .build()

        assertEquals("FLAC • 950kbps • 96kHz", Formatters.formatTrackAudioStats(flacTrack, hiResFlacFormat))

        val opusTrack = com.notmugil.uta.domain.model.TrackItem(
            id = "3",
            title = "Opus Song",
            artist = "Artist",
            suffix = "opus",
            bitRate = 128
        )
        val opusFormat = androidx.media3.common.Format.Builder()
            .setSampleMimeType("audio/opus")
            .setSampleRate(48000)
            .setAverageBitrate(128000)
            .build()

        val transcodedMp3Format = androidx.media3.common.Format.Builder()
            .setSampleMimeType("audio/mpeg")
            .setSampleRate(44100)
            .setAverageBitrate(320000)
            .build()
        assertEquals("OPUS → MP3 • 320kbps • 44.1kHz", Formatters.formatTrackAudioStats(opusTrack, transcodedMp3Format))

        val transcodedAacFormat = androidx.media3.common.Format.Builder()
            .setSampleMimeType("audio/mp4a-latm")
            .setSampleRate(44100)
            .setAverageBitrate(256000)
            .build()
        assertEquals("FLAC → AAC • 256kbps • 44.1kHz", Formatters.formatTrackAudioStats(flacTrack, transcodedAacFormat))

        val requestedBitrateFallbackFormat = androidx.media3.common.Format.Builder()
            .setSampleMimeType("audio/opus")
            .setSampleRate(48000)
            .build()
        assertEquals("FLAC → OPUS • 192kbps • 48kHz", Formatters.formatTrackAudioStats(flacTrack, requestedBitrateFallbackFormat, requestedBitrate = 192))

        val noSuffixTrack = com.notmugil.uta.domain.model.TrackItem(
            id = "4",
            title = "Unknown Suffix",
            artist = "Artist",
            suffix = null,
            bitRate = 256
        )
        val aacFormat = androidx.media3.common.Format.Builder()
            .setSampleMimeType("audio/mp4a-latm")
            .setSampleRate(44100)
            .build()

        assertEquals("AAC • 256kbps • 44.1kHz", Formatters.formatTrackAudioStats(noSuffixTrack, aacFormat))

        org.junit.Assert.assertNull(Formatters.formatTrackAudioStats(null, null))
    }
}
