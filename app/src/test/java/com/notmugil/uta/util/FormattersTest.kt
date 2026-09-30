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
}
