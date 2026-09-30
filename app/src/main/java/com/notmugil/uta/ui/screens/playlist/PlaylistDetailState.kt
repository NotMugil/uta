package com.notmugil.uta.ui.screens.playlist

import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.domain.model.SongSortOption
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.domain.model.ViewDisplayMode

data class PlaylistDetailState(
    val playlist: PlaylistItem? = null,
    val tracks: List<TrackItem> = emptyList(),
    val downloadedTrackIds: Set<String> = emptySet(),
    val isDownloading: Boolean = false,
    val isOfflineModeActive: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOption: SongSortOption = SongSortOption.ID,
    val isAscending: Boolean = true,
    val viewOption: ViewDisplayMode = ViewDisplayMode.LIST,
    val currentPlayingTrackId: String? = null,
    val isPlaying: Boolean = false
)
