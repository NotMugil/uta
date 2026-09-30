package com.notmugil.uta.ui.screens.genre

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notmugil.uta.data.NetworkMonitor
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.data.repository.LibraryRepository
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GenreDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val libraryRepository: LibraryRepository,
    private val subsonicRepository: SubsonicRepository,
    private val localMediaDao: LocalMediaDao,
    private val networkMonitor: NetworkMonitor,
    val offlineDownloadManager: OfflineDownloadManager,
    val playbackController: PlaybackController
) : ViewModel() {

    private val genreName: String = savedStateHandle["genreName"] ?: ""

    private val _state = MutableStateFlow(GenreDetailState(genreName = genreName))
    val state: StateFlow<GenreDetailState> = _state.asStateFlow()

    init {
        // Observe offline mode status
        viewModelScope.launch {
            networkMonitor.isOfflineModeActive.collect { isOffline ->
                _state.update { it.copy(isOfflineModeActive = isOffline) }
            }
        }

        // Observe downloaded track IDs
        viewModelScope.launch {
            val serverId = subsonicRepository.currentServerId
            localMediaDao.getDownloadedTrackIdsFlow(serverId).collect { downloadedIds ->
                _state.update { it.copy(downloadedTrackIds = downloadedIds.toSet()) }
            }
        }

        loadGenreData()
    }

    fun refresh() {
        loadGenreData()
    }

    private fun loadGenreData() {
        if (genreName.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Genre name is missing") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = libraryRepository.fetchGenreDetails(genreName)
                _state.update {
                    it.copy(
                        albums = result.albums,
                        songs = result.songs,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "[Genre] Failed to load genre '$genreName'")
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load genre"
                    )
                }
            }
        }
    }

    fun playTrack(track: TrackItem) {
        val songs = _state.value.songs
        val isOffline = _state.value.isOfflineModeActive
        if (isOffline && !_state.value.downloadedTrackIds.contains(track.id)) {
            Timber.d("[Genre] Track ${track.id} not downloaded, ignoring play in offline mode")
            return
        }

        val playableSongs = if (isOffline) {
            songs.filter { _state.value.downloadedTrackIds.contains(it.id) }
        } else {
            songs
        }

        val queue = if (playableSongs.isNotEmpty()) playableSongs else listOf(track)
        playbackController.playTrack(track, queue)
    }

    fun playAll() {
        val songs = _state.value.songs
        val isOffline = _state.value.isOfflineModeActive
        val playableSongs = if (isOffline) {
            songs.filter { _state.value.downloadedTrackIds.contains(it.id) }
        } else {
            songs
        }

        playableSongs.firstOrNull()?.let { firstTrack ->
            playbackController.playTrack(firstTrack, playableSongs)
        }
    }

    fun shuffleAll() {
        val songs = _state.value.songs
        val isOffline = _state.value.isOfflineModeActive
        val playableSongs = if (isOffline) {
            songs.filter { _state.value.downloadedTrackIds.contains(it.id) }
        } else {
            songs
        }

        if (playableSongs.isNotEmpty()) {
            val shuffled = playableSongs.shuffled()
            playbackController.playTrack(shuffled.first(), shuffled)
        }
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
}
