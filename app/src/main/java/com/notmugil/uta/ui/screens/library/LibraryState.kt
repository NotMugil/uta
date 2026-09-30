package com.notmugil.uta.ui.screens.library

import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.GenreItem
import com.notmugil.uta.domain.model.PlaylistItem

import com.notmugil.uta.domain.model.TrackItem

enum class LibraryTab(@androidx.annotation.StringRes val titleResId: Int) {
    ALBUMS(com.notmugil.uta.R.string.tab_albums),
    ARTISTS(com.notmugil.uta.R.string.tab_artists),
    PLAYLISTS(com.notmugil.uta.R.string.tab_playlists)
}

enum class SortCriteria(@androidx.annotation.StringRes val labelResId: Int) {
    ALPHABETICAL(com.notmugil.uta.R.string.sort_alphabetical),
    ARTIST(com.notmugil.uta.R.string.sort_artist),
    YEAR(com.notmugil.uta.R.string.sort_year),
    COUNT(com.notmugil.uta.R.string.sort_item_count),
    DATE_MODIFIED(com.notmugil.uta.R.string.sort_date_modified),
    DATE_CREATED(com.notmugil.uta.R.string.sort_date_created),
    DURATION(com.notmugil.uta.R.string.sort_duration)
}

data class LibraryState(
    val selectedTab: LibraryTab = LibraryTab.ALBUMS,
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val starredAlbums: List<AlbumItem> = emptyList(),
    val starredArtists: List<ArtistItem> = emptyList(),
    val starredTracks: List<TrackItem> = emptyList(),
    val downloadedTrackIds: Set<String> = emptySet(),
    val downloadedAlbumIds: Set<String> = emptySet(),
    val downloadedPlaylistIds: Set<String> = emptySet(),
    val isOfflineModeActive: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isRefreshing: Boolean = false
)
