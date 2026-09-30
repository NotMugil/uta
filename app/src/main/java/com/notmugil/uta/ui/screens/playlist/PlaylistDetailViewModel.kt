package com.notmugil.uta.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.download.DownloadStatus
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.data.repository.LibraryRepository
import com.notmugil.uta.domain.model.SongSortOption
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.domain.model.ViewDisplayMode
import com.notmugil.uta.player.PlaybackController
import com.notmugil.uta.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val libraryRepository: LibraryRepository,
    val playbackController: PlaybackController,
    val offlineDownloadManager: OfflineDownloadManager,
    private val localMediaDao: LocalMediaDao,
    private val subsonicRepository: SubsonicRepository,
    private val networkMonitor: com.notmugil.uta.data.NetworkMonitor,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    private val _uiState = MutableStateFlow(PlaylistDetailState(viewOption = appPreferences.playlistViewMode.value))
    val uiState: StateFlow<PlaylistDetailState> = _uiState.asStateFlow()

    init {
        observePlaylistData()
        observeDownloadStates()
        observeOfflineState()
        observePlaybackState()
        refreshPlaylist()
    }

    private fun observeOfflineState() {
        viewModelScope.launch {
            networkMonitor.isOfflineModeActive.collect { isOffline ->
                _uiState.update { it.copy(isOfflineModeActive = isOffline) }
            }
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            combine(
                playbackController.currentTrack,
                playbackController.isPlaying
            ) { currentTrack, isPlaying ->
                Pair(currentTrack?.id, isPlaying)
            }.collect { (currentTrackId, isPlaying) ->
                _uiState.update {
                    it.copy(
                        currentPlayingTrackId = currentTrackId,
                        isPlaying = isPlaying
                    )
                }
            }
        }
    }

    private fun observeDownloadStates() {
        viewModelScope.launch {
            val serverId = subsonicRepository.currentServerId
            combine(
                localMediaDao.getDownloadedTrackIdsFlow(serverId),
                offlineDownloadManager.downloadStates
            ) { downloadedIdsList, downloadMap ->
                val validDownloadedIds = downloadedIdsList.toSet()

                val trackIds = _uiState.value.tracks.map { it.id }.toSet()
                val isAnyDownloading = trackIds.any { id ->
                    val status = downloadMap[id]
                    status is DownloadStatus.Downloading || status is DownloadStatus.Queued
                }

                Pair(validDownloadedIds, isAnyDownloading)
            }.collect { (downloadedIds, isDownloading) ->
                _uiState.update { current ->
                    current.copy(
                        downloadedTrackIds = downloadedIds,
                        isDownloading = isDownloading
                    )
                }
            }
        }
    }

    private fun observePlaylistData() {
        viewModelScope.launch {
            combine(
                libraryRepository.getPlaylistFlow(playlistId),
                libraryRepository.getTracksForPlaylistFlow(playlistId)
            ) { playlist, tracks ->
                Pair(playlist, tracks)
            }.collect { (playlist, tracks) ->
                _uiState.update { current ->
                    current.copy(
                        playlist = playlist,
                        tracks = tracks,
                        isLoading = tracks.isEmpty() && current.isLoading
                    )
                }
            }
        }
    }

    fun refreshPlaylist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.tracks.isEmpty()) }
            launch {
                val navidromeInfo = subsonicRepository.getNavidromePlaylistInfo(playlistId)
                if (navidromeInfo != null) {
                    _uiState.update { cur ->
                        cur.copy(
                            playlist = cur.playlist?.copy(
                                isSmart = navidromeInfo.isSmart,
                                isSync = navidromeInfo.isSync,
                                isOwner = navidromeInfo.isOwner
                            )
                        )
                    }
                }
            }
            val result = libraryRepository.fetchPlaylistTracks(playlistId)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (it.tracks.isEmpty()) error.localizedMessage ?: "Failed to load playlist tracks" else null
                        )
                    }
                }
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (!active) "" else it.searchQuery
            )
        }
    }

    fun setSortOption(option: SongSortOption) {
        _uiState.update { it.copy(sortOption = option) }
    }

    fun toggleAscending() {
        _uiState.update { it.copy(isAscending = !it.isAscending) }
    }

    fun setViewOption(mode: ViewDisplayMode) {
        _uiState.update { it.copy(viewOption = mode) }
        appPreferences.setPlaylistViewMode(mode)
    }

    fun playTrack(track: TrackItem, queueTracks: List<TrackItem>) {
        val state = _uiState.value
        if (state.isOfflineModeActive && !state.downloadedTrackIds.contains(track.id)) {
            return
        }
        val playableTracks = if (state.isOfflineModeActive) {
            queueTracks.filter { state.downloadedTrackIds.contains(it.id) }
        } else {
            queueTracks
        }
        playbackController.playTrack(track, playableTracks)
    }

    fun playAll(shuffle: Boolean = false) {
        val state = _uiState.value
        val tracks = if (state.isOfflineModeActive) {
            state.tracks.filter { state.downloadedTrackIds.contains(it.id) }
        } else {
            state.tracks
        }
        if (tracks.isEmpty()) return
        val queue = if (shuffle) tracks.shuffled() else tracks
        playbackController.playQueue(queue, 0)
    }

    fun togglePlaylistDownload() {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) return

        val state = _uiState.value
        val allDownloaded = tracks.all { state.downloadedTrackIds.contains(it.id) }

        if (allDownloaded) {
            viewModelScope.launch {
                offlineDownloadManager.deleteDownloadedScope(playlistId)
            }
        } else {
            if (state.isOfflineModeActive) return
            offlineDownloadManager.enqueueTracks(tracks, scopeId = playlistId, scopeType = "PLAYLIST")
        }
    }

    fun toggleTrackDownload(track: TrackItem) {
        val state = _uiState.value
        val isDownloaded = state.downloadedTrackIds.contains(track.id)
        if (isDownloaded) {
            viewModelScope.launch {
                offlineDownloadManager.deleteDownloadedTrack(track.id, scopeId = playlistId)
            }
        } else {
            if (state.isOfflineModeActive) return
            offlineDownloadManager.enqueueTrack(track, scopeId = playlistId, scopeType = "PLAYLIST")
        }
    }

    fun removeTrack(index: Int) {
        viewModelScope.launch {
            libraryRepository.removeTrackFromPlaylist(playlistId, index)
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            libraryRepository.deletePlaylist(playlistId)
            onDeleted()
        }
    }

    fun updatePlaylistMeta(name: String, comment: String? = null, isPublic: Boolean? = null) {
        viewModelScope.launch {
            libraryRepository.updatePlaylistMeta(playlistId, name, comment, isPublic)
        }
    }
}
