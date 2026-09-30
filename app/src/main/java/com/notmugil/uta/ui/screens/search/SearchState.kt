package com.notmugil.uta.ui.screens.search

import com.notmugil.uta.data.repository.SearchResults
import com.notmugil.uta.domain.model.GenreItem

data class SearchState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    val genres: List<GenreItem> = emptyList(),
    val genreCovers: Map<String, List<String>> = emptyMap(),
    val isSearching: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
    val downloadedAlbumIds: Set<String> = emptySet(),
    val isOfflineModeActive: Boolean = false
)
