package com.notmugil.uta.ui.screens.genre

import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.TrackItem

data class GenreDetailState(
    val genreName: String = "",
    val albums: List<AlbumItem> = emptyList(),
    val songs: List<TrackItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val downloadedTrackIds: Set<String> = emptySet(),
    val isOfflineModeActive: Boolean = false
)
