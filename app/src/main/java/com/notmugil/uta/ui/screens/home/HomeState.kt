package com.notmugil.uta.ui.screens.home

import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.domain.model.TrackItem

data class SectionState<T>(
    val isLoading: Boolean = false,
    val data: T,
    val errorMessage: String? = null
)

data class HomeState(
    val quickMixSongs: List<TrackItem> = emptyList(),
    val featuredArtist: ArtistItem? = null,
    val featuredArtistAlbums: List<AlbumItem> = emptyList(),
    val recentlyAdded: SectionState<List<AlbumItem>> = SectionState(isLoading = true, data = emptyList()),
    val randomAlbums: SectionState<List<AlbumItem>> = SectionState(isLoading = true, data = emptyList()),
    val playlists: SectionState<List<PlaylistItem>> = SectionState(isLoading = true, data = emptyList()),
    val downloadedTrackIds: Set<String> = emptySet(),
    val downloadedAlbumIds: Set<String> = emptySet(),
    val downloadedPlaylistIds: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    val syncMessage: String? = null,
    val isOfflineModeActive: Boolean = false
)
