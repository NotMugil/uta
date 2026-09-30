package com.notmugil.uta.data.download

import com.notmugil.uta.domain.model.TrackItem

sealed interface DownloadQuality {
    data object Original : DownloadQuality
    data class Transcoded(val bitrateKbps: Int? = null, val format: String? = null) : DownloadQuality {
        val label: String
            get() = when {
                bitrateKbps != null && format != null -> "$bitrateKbps kbps ($format)"
                bitrateKbps != null -> "$bitrateKbps kbps"
                format != null -> format.uppercase()
                else -> "Original"
            }
    }

    companion object {
        fun fromQualityString(quality: String, bitRate: Int?, format: String?): DownloadQuality {
            return if (quality == "TRANSCODED" && (bitRate != null || (format != null && format != "raw"))) {
                Transcoded(bitrateKbps = bitRate, format = format?.takeIf { it != "raw" })
            } else {
                Original
            }
        }
    }
}

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data object Queued : DownloadStatus
    data class Downloading(
        val progress: Float = 0f,
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long? = null
    ) : DownloadStatus
    data class Completed(val relativePath: String) : DownloadStatus
    data class Failed(val error: String) : DownloadStatus
}

data class DownloadTask(
    val track: TrackItem,
    val serverId: String,
    val scopeId: String = track.id,
    val scopeType: String = "TRACK",
    val quality: DownloadQuality = DownloadQuality.Original,
    val status: DownloadStatus = DownloadStatus.Queued,
    val queuedAt: Long = System.currentTimeMillis()
)

data class StorageStats(
    val downloadedAudioBytes: Long = 0L,
    val downloadedTrackCount: Int = 0,
    val availableDiskSpaceBytes: Long = 0L,
    val totalDiskSpaceBytes: Long = 0L
)
