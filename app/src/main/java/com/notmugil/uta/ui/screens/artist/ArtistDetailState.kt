package com.notmugil.uta.ui.screens.artist

import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.TrackItem

data class ArtistDetailState(
    val artist: ArtistItem? = null,
    val albums: List<AlbumItem> = emptyList(),
    val topTracks: List<TrackItem> = emptyList(),
    val biography: String? = null,
    val downloadedTrackIds: Set<String> = emptySet(),
    val downloadedAlbumIds: Set<String> = emptySet(),
    val isOfflineModeActive: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
