package com.notmugil.uta.data.download

import com.notmugil.uta.data.preferences.DownloadQualityPreference
import com.notmugil.uta.domain.model.TrackItem

object DownloadEstimator {

    fun calculateEstimatedSizeBytes(
        tracks: List<TrackItem>,
        quality: DownloadQualityPreference,
        downloadedTrackIds: Set<String> = emptySet(),
        fallbackSongCount: Int = 0
    ): Long {
        val nonDownloaded = if (tracks.isNotEmpty()) {
            tracks.filter { it.id !in downloadedTrackIds }
        } else {
            emptyList()
        }

        if (nonDownloaded.isNotEmpty()) {
            return nonDownloaded.sumOf { track ->
                val durationSeconds = if (track.durationSeconds > 0) track.durationSeconds else 210L
                val effectiveKbps = when {
                    quality.kbps > 0 -> {
                        if (track.bitRate != null && track.bitRate > 0) {
                            minOf(track.bitRate, quality.kbps)
                        } else {
                            quality.kbps
                        }
                    }
                    else -> { // ORIGINAL (kbps == 0)
                        if (track.bitRate != null && track.bitRate > 0) {
                            track.bitRate
                        } else {
                            320
                        }
                    }
                }
                durationSeconds * (effectiveKbps * 1000L / 8L)
            }
        } else if (fallbackSongCount > 0) {
            val effectiveKbps = if (quality.kbps > 0) quality.kbps else 320
            return fallbackSongCount * 210L * (effectiveKbps * 1000L / 8L)
        }
        return 0L
    }
}
