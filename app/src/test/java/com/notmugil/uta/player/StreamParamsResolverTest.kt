package com.notmugil.uta.player

import com.notmugil.uta.data.preferences.StreamingBitrate
import com.notmugil.uta.data.preferences.TranscodingFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamParamsResolverTest {

    @Test
    fun `UNLIMITED bitrate always returns raw format and no maxBitRate`() {
        val params = StreamParamsResolver.resolve(
            bitrateSetting = StreamingBitrate.UNLIMITED,
            formatSetting = TranscodingFormat.MP3,
            sourceSuffix = "flac",
            sourceBitRate = 1000
        )
        assertEquals("raw", params.format)
        assertNull(params.maxBitRate)
    }

    @Test
    fun `ServerDefault format with AUTO bitrate returns no format and no maxBitRate`() {
        val params = StreamParamsResolver.resolve(
            bitrateSetting = StreamingBitrate.AUTO,
            formatSetting = TranscodingFormat.RAW,
            sourceSuffix = "flac",
            sourceBitRate = 1000
        )
        assertNull(params.format)
        assertNull(params.maxBitRate)
    }

    @Test
    fun `ServerDefault format with explicit bitrate cap returns cap with server default format`() {
        val params = StreamParamsResolver.resolve(
            bitrateSetting = StreamingBitrate.BITRATE_192,
            formatSetting = TranscodingFormat.RAW,
            sourceSuffix = "flac",
            sourceBitRate = 1000
        )
        assertNull(params.format)
        assertEquals(192, params.maxBitRate)
    }

    @Test
    fun `FLAC format is lossless so maxBitRate is always null`() {
        val params = StreamParamsResolver.resolve(
            bitrateSetting = StreamingBitrate.BITRATE_320,
            formatSetting = TranscodingFormat.FLAC,
            sourceSuffix = "opus",
            sourceBitRate = 160
        )
        assertEquals("flac", params.format)
        assertNull(params.maxBitRate)
    }

    @Test
    fun `MP3 format with AUTO bitrate uses 320 kbps default for transcoding`() {
        val params = StreamParamsResolver.resolve(
            bitrateSetting = StreamingBitrate.AUTO,
            formatSetting = TranscodingFormat.MP3,
            sourceSuffix = "flac",
            sourceBitRate = 1000
        )
        assertEquals("mp3", params.format)
        assertEquals(320, params.maxBitRate)
    }

    @Test
    fun `AAC format with AUTO bitrate uses 256 kbps default for transcoding`() {
        val params = StreamParamsResolver.resolve(
            bitrateSetting = StreamingBitrate.AUTO,
            formatSetting = TranscodingFormat.AAC,
            sourceSuffix = "flac",
            sourceBitRate = 1000
        )
        assertEquals("aac", params.format)
        assertEquals(256, params.maxBitRate)
    }

    @Test
    fun `Opus format with AUTO bitrate uses 192 kbps default for transcoding`() {
        val params = StreamParamsResolver.resolve(
            bitrateSetting = StreamingBitrate.AUTO,
            formatSetting = TranscodingFormat.OPUS,
            sourceSuffix = "flac",
            sourceBitRate = 1000
        )
        assertEquals("opus", params.format)
        assertEquals(192, params.maxBitRate)
    }

    @Test
    fun `Requested bitrate is capped at source bitrate when source is below cap`() {
        val params = StreamParamsResolver.resolve(
            bitrateSetting = StreamingBitrate.BITRATE_320,
            formatSetting = TranscodingFormat.MP3,
            sourceSuffix = "flac",
            sourceBitRate = 96
        )
        assertEquals("mp3", params.format)
        assertEquals(96, params.maxBitRate)
    }

    @Test
    fun `Matching format and bitrate within cap passes through direct stream`() {
        val params = StreamParamsResolver.resolve(
            bitrateSetting = StreamingBitrate.BITRATE_320,
            formatSetting = TranscodingFormat.MP3,
            sourceSuffix = "mp3",
            sourceBitRate = 192
        )
        assertNull(params.format)
        assertNull(params.maxBitRate)
    }

    @Test
    fun `Matching format but source exceeds cap transcodes to cap`() {
        val params = StreamParamsResolver.resolve(
            bitrateSetting = StreamingBitrate.BITRATE_128,
            formatSetting = TranscodingFormat.MP3,
            sourceSuffix = "mp3",
            sourceBitRate = 320
        )
        assertEquals("mp3", params.format)
        assertEquals(128, params.maxBitRate)
    }
}
