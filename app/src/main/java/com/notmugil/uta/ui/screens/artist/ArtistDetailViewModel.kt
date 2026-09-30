package com.notmugil.uta.ui.screens.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val playbackController: PlaybackController,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val localMediaDao: LocalMediaDao,
    private val subsonicRepository: SubsonicRepository,
    private val networkMonitor: com.notmugil.uta.data.NetworkMonitor
) : ViewModel() {

    private val artistId: String = checkNotNull(savedStateHandle["artistId"])

    private val _uiState = MutableStateFlow(ArtistDetailState())
    val uiState: StateFlow<ArtistDetailState> = _uiState.asStateFlow()

    init {
        observeDownloadStates()
        observeOfflineState()
        loadArtist()
    }

    private fun observeOfflineState() {
        viewModelScope.launch {
            networkMonitor.isOfflineModeActive.collect { isOffline ->
                _uiState.update { it.copy(isOfflineModeActive = isOffline) }
            }
        }
    }

    private fun observeDownloadStates() {
        viewModelScope.launch {
            val serverId = subsonicRepository.currentServerId
            localMediaDao.getDownloadedTrackIdsFlow(serverId).collect { downloadedIdsList ->
                _uiState.update { it.copy(downloadedTrackIds = downloadedIdsList.toSet()) }
            }
        }
        viewModelScope.launch {
            val serverId = subsonicRepository.currentServerId
            localMediaDao.getDownloadedAlbumIdsFlow(serverId).collect { downloadedAlbumIdsList ->
                _uiState.update { it.copy(downloadedAlbumIds = downloadedAlbumIdsList.toSet()) }
            }
        }
    }

    fun loadArtist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.artist == null) }
            try {
                val result = libraryRepository.fetchArtistDetails(artistId)
                _uiState.update {
                    it.copy(
                        artist = result.artist,
                        albums = result.albums,
                        topTracks = result.topTracks,
                        biography = result.biography,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "[Artist] Failed to load artist $artistId")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (it.artist == null) e.localizedMessage ?: "Failed to load artist" else null
                    )
                }
            }
        }
    }

    fun playTrack(track: TrackItem, queue: List<TrackItem> = listOf(track)) {
        val state = _uiState.value
        if (state.isOfflineModeActive && !state.downloadedTrackIds.contains(track.id)) {
            return
        }
        val playableQueue = if (state.isOfflineModeActive) {
            queue.filter { state.downloadedTrackIds.contains(it.id) }
        } else {
            queue
        }
        playbackController.playTrack(track, playableQueue)
    }

    fun playTopTracks(shuffle: Boolean = false) {
        val state = _uiState.value
        val tracks = if (state.isOfflineModeActive) {
            state.topTracks.filter { state.downloadedTrackIds.contains(it.id) }
        } else {
            state.topTracks
        }
        if (tracks.isEmpty()) return
        val queue = if (shuffle) tracks.shuffled() else tracks
        playbackController.playQueue(queue, 0)
    }

    fun toggleTrackDownload(track: TrackItem) {
        val isDownloaded = _uiState.value.downloadedTrackIds.contains(track.id)
        if (isDownloaded) {
            viewModelScope.launch {
                offlineDownloadManager.deleteDownloadedTrack(track.id, scopeId = artistId)
            }
        } else {
            offlineDownloadManager.enqueueTrack(track, scopeId = artistId, scopeType = "ARTIST")
        }
    }

    fun toggleArtistFavorite() {
        val artist = _uiState.value.artist ?: return
        val newStarred = !artist.isStarred
        _uiState.update { current ->
            current.copy(artist = current.artist?.copy(isStarred = newStarred))
        }
        viewModelScope.launch {
            libraryRepository.toggleArtistStarred(artist.id)
        }
    }

    fun toggleTrackFavorite(track: TrackItem) {
        val newStarred = !track.isStarred
        _uiState.update { current ->
            current.copy(
                topTracks = current.topTracks.map {
                    if (it.id == track.id) it.copy(isStarred = newStarred) else it
                }
            )
        }
        viewModelScope.launch {
            libraryRepository.toggleTrackStarred(track.id)
        }
    }
}
