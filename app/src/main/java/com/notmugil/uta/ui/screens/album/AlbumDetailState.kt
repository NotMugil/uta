package com.notmugil.uta.ui.screens.album

import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.SongSortOption
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.domain.model.ViewDisplayMode

data class AlbumDetailState(
    val album: AlbumItem? = null,
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
    val viewOption: ViewDisplayMode = ViewDisplayMode.TEXT_ONLY,
    val currentPlayingTrackId: String? = null,
    val isPlaying: Boolean = false
)
