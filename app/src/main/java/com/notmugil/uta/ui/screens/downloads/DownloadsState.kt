package com.notmugil.uta.ui.screens.downloads

import com.notmugil.uta.data.download.DownloadTask
import com.notmugil.uta.data.download.StorageStats
import com.notmugil.uta.domain.model.TrackItem

data class DownloadedTrackInfo(
    val track: TrackItem,
    val quality: String = "ORIGINAL",
    val format: String = "mp3",
    val bitRate: Int? = null,
    val fileSizeBytes: Long = 0L,
    val downloadedAt: Long = 0L
)

data class DownloadsState(
    val searchQuery: String = "",
    val downloadedTracks: List<DownloadedTrackInfo> = emptyList(),
    val activeQueue: List<DownloadTask> = emptyList(),
    val storageStats: StorageStats = StorageStats(),
    val isOfflineModeActive: Boolean = false
)
