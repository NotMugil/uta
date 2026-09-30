package com.notmugil.uta.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notmugil.uta.data.NetworkMonitor
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.data.repository.LibraryRepository
import com.notmugil.uta.data.repository.SearchResults
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val subsonicRepository: SubsonicRepository,
    private val localMediaDao: LocalMediaDao,
    private val networkMonitor: NetworkMonitor,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val initialQuery: String = savedStateHandle["search_query"] ?: ""

    private val _state = MutableStateFlow(SearchState(query = initialQuery))
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow(initialQuery)

    init {
        // Observe offline mode status
        viewModelScope.launch {
            networkMonitor.isOfflineModeActive.collect { isOffline ->
                _state.update { it.copy(isOfflineModeActive = isOffline) }
            }
        }

        // Observe downloaded track and album IDs for the current active account
        viewModelScope.launch {
            val serverId = subsonicRepository.currentServerId
            localMediaDao.getDownloadedTrackIdsFlow(serverId).collect { downloadedIds ->
                _state.update { it.copy(downloadedTrackIds = downloadedIds.toSet()) }
            }
        }
        viewModelScope.launch {
            val serverId = subsonicRepository.currentServerId
            localMediaDao.getDownloadedAlbumIdsFlow(serverId).collect { downloadedAlbumIds ->
                _state.update { it.copy(downloadedAlbumIds = downloadedAlbumIds.toSet()) }
            }
        }

        // Observe genres and album/track covers for Browse Genres section
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                libraryRepository.getGenresFlow(),
                libraryRepository.getAlbumsFlow(),
                libraryRepository.getTracksFlow()
            ) { genres, albums, tracks ->
                val genreAllCovers = mutableMapOf<String, MutableList<String>>()

                fun addCover(rawGenre: String?, coverId: String?) {
                    if (rawGenre.isNullOrBlank() || coverId.isNullOrBlank()) return
                    val keys = extractGenreKeys(rawGenre)
                    keys.forEach { key ->
                        val list = genreAllCovers.getOrPut(key) { mutableListOf() }
                        if (!list.contains(coverId)) {
                            list.add(coverId)
                        }
                    }
                }

                albums.forEach { album ->
                    addCover(album.genre, album.coverArtId ?: album.id)
                }

                tracks.forEach { track ->
                    addCover(track.genre, track.coverArtId ?: track.albumId)
                }

                // Pick random album covers for each genre so a random album is the front/center image
                val coversMap = genreAllCovers.mapValues { (_, list) ->
                    list.shuffled().take(3)
                }

                val distinctGenres = genres.distinctBy { it.name.trim().lowercase() }
                Pair(distinctGenres, coversMap)
            }.collect { (distinctGenres, coversMap) ->
                _state.update {
                    it.copy(
                        genres = distinctGenres,
                        genreCovers = coversMap
                    )
                }

                // For genres that still have no covers in local Room, fetch from remote on-demand
                if (!networkMonitor.isOfflineModeActive.value) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val missingGenres = distinctGenres.filter { genre ->
                            val keys = extractGenreKeys(genre.name)
                            keys.none { coversMap[it]?.isNotEmpty() == true }
                        }
                        for (genre in missingGenres.take(25)) {
                            try {
                                val details = libraryRepository.fetchGenreDetails(genre.name)
                                val remoteCovers = (details.albums.mapNotNull { it.coverArtId ?: it.id } +
                                        details.songs.mapNotNull { it.coverArtId ?: it.albumId })
                                    .distinct()
                                    .take(3)
                                if (remoteCovers.isNotEmpty()) {
                                    _state.update { current ->
                                        val updatedMap = current.genreCovers.toMutableMap()
                                        extractGenreKeys(genre.name).forEach { k ->
                                            val existing = updatedMap[k]
                                            if (existing.isNullOrEmpty()) {
                                                updatedMap[k] = remoteCovers
                                            }
                                        }
                                        current.copy(genreCovers = updatedMap)
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.d(e, "[Search] Could not fetch remote covers for genre '${genre.name}'")
                            }
                        }
                    }
                }
            }
        }

        // Live debounce search pipeline with cancellation of stale queries
        viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { rawQuery ->
                    val clean = rawQuery.trim()
                    savedStateHandle["search_query"] = rawQuery
                    if (clean.isBlank()) {
                        _state.update { it.copy(results = SearchResults(), isSearching = false) }
                    } else {
                        performSearch(clean)
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun toggleTrackDownload(track: TrackItem) {
        viewModelScope.launch {
            val isDownloaded = _state.value.downloadedTrackIds.contains(track.id)
            if (isDownloaded) {
                offlineDownloadManager.deleteDownloadedTrack(track.id)
            } else {
                offlineDownloadManager.enqueueTrack(track)
            }
        }
    }

    private suspend fun performSearch(query: String) {
        _state.update { it.copy(isSearching = true) }
        Timber.d("[Search] Executing search for: '$query'")
        try {
            val results = libraryRepository.search(query)
            _state.update { it.copy(results = results, isSearching = false) }
            Timber.d("[Search] Search completed for '$query': ${results.tracks.size} tracks, ${results.albums.size} albums, ${results.artists.size} artists")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "[Search] Search failed for query '$query'")
            _state.update { it.copy(isSearching = false) }
        }
    }

    fun playTrack(track: TrackItem) {
        if (_state.value.isOfflineModeActive && !_state.value.downloadedTrackIds.contains(track.id)) {
            Timber.d("[Search] Track ${track.id} not downloaded, ignoring play in offline mode")
            return
        }
        val tracks = _state.value.results.tracks
        val validQueue = if (_state.value.isOfflineModeActive) {
            tracks.filter { _state.value.downloadedTrackIds.contains(it.id) }
        } else {
            tracks
        }
        val queue = if (validQueue.isNotEmpty()) validQueue else listOf(track)
        playbackController.playTrack(track, queue)
    }
}

internal fun extractGenreKeys(raw: String): List<String> {
    val clean = raw.trim().lowercase()
    if (clean.isBlank()) return emptyList()
    val tokens = clean.split(Regex("[,/;|&+•\\\\]"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val alphaNumericOnly = clean.replace(Regex("[^a-z0-9]"), "")
    val words = clean.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
    return (listOf(clean, alphaNumericOnly) + tokens + words).distinct().filter { it.isNotBlank() }
}

