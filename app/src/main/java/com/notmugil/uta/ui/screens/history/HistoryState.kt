package com.notmugil.uta.ui.screens.history

import com.notmugil.uta.domain.model.HistoryItem

data class HistoryState(
    val sections: List<HistorySection> = emptyList(),
    val isEmpty: Boolean = true,
    val isOfflineModeActive: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
    val currentPlayingTrackId: String? = null,
    val isPlaying: Boolean = false
)

data class HistorySection(
    val title: String,
    val entries: List<HistoryItem>
)
