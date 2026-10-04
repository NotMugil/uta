package com.notmugil.uta.data.download

import com.notmugil.uta.data.preferences.DownloadQualityPreference
import com.notmugil.uta.domain.model.TrackItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadEstimatorTest {

    private fun createTrack(id: String, durationSeconds: Long, bitRate: Int? = null): TrackItem {
        return TrackItem(
            id = id,
            title = "Track $id",
            artist = "Artist",
            album = "Album",
            durationSeconds = durationSeconds,
            bitRate = bitRate
        )
    }

    @Test
    fun `calculateEstimatedSizeBytes with specific bitrate calculates accurately`() {
        // Track: 200s, quality: 320 kbps -> 200 * (320 * 1000 / 8) = 200 * 40000 = 8,000,000 bytes
        val tracks = listOf(createTrack("t1", 200L))
        val size = DownloadEstimator.calculateEstimatedSizeBytes(
            tracks = tracks,
            quality = DownloadQualityPreference.BITRATE_320
        )
        assertEquals(8_000_000L, size)
    }

    @Test
    fun `calculateEstimatedSizeBytes with 128 kbps calculates accurately`() {
        // Track: 180s, quality: 128 kbps -> 180 * (128 * 1000 / 8) = 180 * 16000 = 2,880,000 bytes
        val tracks = listOf(createTrack("t1", 180L))
        val size = DownloadEstimator.calculateEstimatedSizeBytes(
            tracks = tracks,
            quality = DownloadQualityPreference.BITRATE_128
        )
        assertEquals(2_880_000L, size)
    }

    @Test
    fun `calculateEstimatedSizeBytes excludes already downloaded tracks`() {
        val tracks = listOf(
            createTrack("t1", 100L), // Downloaded, excluded
            createTrack("t2", 150L), // Not downloaded: 150 * (256 * 1000 / 8) = 150 * 32000 = 4,800,000
            createTrack("t3", 50L)   // Not downloaded: 50 * (256 * 1000 / 8) = 50 * 32000 = 1,600,000
        )
        val downloaded = setOf("t1")

        val size = DownloadEstimator.calculateEstimatedSizeBytes(
            tracks = tracks,
            quality = DownloadQualityPreference.BITRATE_256,
            downloadedTrackIds = downloaded
        )
        assertEquals(6_400_000L, size)
    }

    @Test
    fun `calculateEstimatedSizeBytes with original quality uses track bitRate when available`() {
        // Track: 100s with 1411 kbps (FLAC) -> 100 * (1411 * 1000 / 8) = 100 * 176375 = 17,637,500
        val tracks = listOf(createTrack("t1", 100L, bitRate = 1411))
        val size = DownloadEstimator.calculateEstimatedSizeBytes(
            tracks = tracks,
            quality = DownloadQualityPreference.ORIGINAL
        )
        assertEquals(100L * (1411 * 1000L / 8L), size)
    }

    @Test
    fun `calculateEstimatedSizeBytes with fallbackSongCount calculates accurately`() {
        // 5 songs fallback at 192 kbps -> 5 * 210s * (192 * 1000 / 8) = 1050 * 24000 = 25,200,000
        val size = DownloadEstimator.calculateEstimatedSizeBytes(
            tracks = emptyList(),
            quality = DownloadQualityPreference.BITRATE_192,
            fallbackSongCount = 5
        )
        assertEquals(25_200_000L, size)
    }

    @Test
    fun `calculateEstimatedSizeBytes returns 0 when all tracks downloaded`() {
        val tracks = listOf(createTrack("t1", 100L))
        val downloaded = setOf("t1")

        val size = DownloadEstimator.calculateEstimatedSizeBytes(
            tracks = tracks,
            quality = DownloadQualityPreference.BITRATE_320,
            downloadedTrackIds = downloaded
        )
        assertEquals(0L, size)
    }
}
