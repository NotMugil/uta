package com.notmugil.uta.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notmugil.uta.data.NetworkMonitor
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.data.repository.LibraryRepository
import com.notmugil.uta.data.sync.LibrarySyncEngine
import com.notmugil.uta.data.sync.SyncState
import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private data class CoreLibraryData(
    val tab: LibraryTab,
    val albums: List<AlbumItem>,
    val artists: List<ArtistItem>,
    val playlists: List<PlaylistItem>
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val subsonicRepository: SubsonicRepository,
    private val localMediaDao: LocalMediaDao,
    private val networkMonitor: NetworkMonitor,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val syncEngine: LibrarySyncEngine,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(LibraryTab.ALBUMS)
    private val _isRefreshing = MutableStateFlow(false)

    private val coreDataFlow = combine(
        _selectedTab,
        libraryRepository.getAlbumsFlow(),
        libraryRepository.getArtistsFlow(),
        libraryRepository.getPlaylistsFlow()
    ) { tab, albums, artists, playlists ->
        CoreLibraryData(tab, albums, artists, playlists)
    }

    private val starredFlow = combine(
        libraryRepository.getStarredAlbumsFlow(),
        libraryRepository.getStarredArtistsFlow(),
        libraryRepository.getStarredTracksFlow()
    ) { starredAlbums, starredArtists, starredTracks ->
        Triple(starredAlbums, starredArtists, starredTracks)
    }

    private val downloadedMediaFlow = combine(
        localMediaDao.getDownloadedTrackIdsFlow(subsonicRepository.currentServerId),
        localMediaDao.getDownloadedAlbumIdsFlow(subsonicRepository.currentServerId),
        localMediaDao.getDownloadedPlaylistIdsFlow(subsonicRepository.currentServerId)
    ) { trackIds, albumIds, playlistIds ->
        Triple(trackIds.toSet(), albumIds.toSet(), playlistIds.toSet())
    }

    private val catalogDataFlow = combine(
        coreDataFlow,
        starredFlow
    ) { core, starred ->
        LibraryState(
            selectedTab = core.tab,
            albums = core.albums,
            artists = core.artists,
            playlists = core.playlists,
            starredAlbums = starred.first,
            starredArtists = starred.second,
            starredTracks = starred.third
        )
    }

    val state: StateFlow<LibraryState> = combine(
        catalogDataFlow,
        downloadedMediaFlow,
        networkMonitor.isOfflineModeActive,
        syncEngine.syncState,
        _isRefreshing
    ) { catalog, (downloadedTracks, downloadedAlbums, downloadedPlaylists), isOffline, syncState, refreshing ->
        val syncing = syncState is SyncState.Syncing
        catalog.copy(
            downloadedTrackIds = downloadedTracks,
            downloadedAlbumIds = downloadedAlbums,
            downloadedPlaylistIds = downloadedPlaylists,
            isOfflineModeActive = isOffline,
            isLoading = catalog.albums.isEmpty() && catalog.artists.isEmpty() && syncing,
            isSyncing = syncing,
            isRefreshing = refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryState()
    )

    fun selectTab(tab: LibraryTab) {
        _selectedTab.update { tab }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncEngine.syncLibrary(force = true)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun toggleTrackDownload(track: TrackItem) {
        viewModelScope.launch {
            val isDownloaded = state.value.downloadedTrackIds.contains(track.id)
            if (isDownloaded) {
                offlineDownloadManager.deleteDownloadedTrack(track.id)
            } else {
                offlineDownloadManager.enqueueTrack(track)
            }
        }
    }

    fun playTrack(track: TrackItem) {
        if (state.value.isOfflineModeActive && !state.value.downloadedTrackIds.contains(track.id)) {
            Timber.d("[Library] Track ${track.id} not downloaded, ignoring play in offline mode")
            return
        }
        val tracks = state.value.starredTracks
        val validQueue = if (state.value.isOfflineModeActive) {
            tracks.filter { state.value.downloadedTrackIds.contains(it.id) }
        } else {
            tracks
        }
        val queue = if (validQueue.isNotEmpty()) validQueue else listOf(track)
        playbackController.playTrack(track, queue)
    }

    fun createPlaylist(name: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = libraryRepository.createPlaylist(name)
            result.getOrNull()?.let { onCreated(it.id) }
        }
    }
}
