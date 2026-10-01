package com.notmugil.uta.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notmugil.uta.data.NetworkMonitor
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.ArtistDao
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.db.toDomain
import com.notmugil.uta.data.repository.LibraryRepository
import com.notmugil.uta.data.sync.LibrarySyncEngine
import com.notmugil.uta.data.sync.SyncState
import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.PlaybackController
import com.notmugil.uta.player.SleepTimerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val subsonicRepository: SubsonicRepository,
    private val localMediaDao: LocalMediaDao,
    private val artistDao: ArtistDao,
    private val syncEngine: LibrarySyncEngine,
    private val networkMonitor: NetworkMonitor,
    private val playbackController: PlaybackController,
    val sleepTimerManager: SleepTimerManager
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _randomAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    private val _randomIsLoading = MutableStateFlow(false)
    private val _quickMixSongs = MutableStateFlow<List<TrackItem>>(emptyList())
    private val _featuredArtist = MutableStateFlow<ArtistItem?>(null)
    private val _featuredArtistAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())

    val currentTrack: StateFlow<TrackItem?> = playbackController.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying

    private val catalogSectionsFlow = combine(
        libraryRepository.getRecentlyAddedAlbumsFlow(15),
        _randomAlbums,
        libraryRepository.getPlaylistsFlow(),
        _quickMixSongs
    ) { recent, random, playlists, quickMix ->
        Tuple4(recent, random, playlists, quickMix)
    }

    private val featuredArtistFlow = combine(
        _featuredArtist,
        _featuredArtistAlbums
    ) { artist, albums ->
        Pair(artist, albums)
    }

    private val catalogFlow = combine(
        catalogSectionsFlow,
        featuredArtistFlow
    ) { (recent, random, playlists, quickMix), (featuredArtist, featuredAlbums) ->
        HomeCatalogData(
            recent = recent,
            random = random,
            playlists = playlists,
            quickMix = quickMix,
            featuredArtist = featuredArtist,
            featuredArtistAlbums = featuredAlbums
        )
    }

    private val downloadedMediaFlow = combine(
        localMediaDao.getDownloadedTrackIdsFlow(subsonicRepository.currentServerId),
        localMediaDao.getDownloadedAlbumIdsFlow(subsonicRepository.currentServerId),
        localMediaDao.getDownloadedPlaylistIdsFlow(subsonicRepository.currentServerId),
        libraryRepository.getDownloadedTracksFlow()
    ) { trackIds, albumIds, playlistIds, downloadedTracks ->
        DownloadedMediaInfo(
            trackIds = trackIds.toSet(),
            albumIds = albumIds.toSet(),
            playlistIds = playlistIds.toSet(),
            downloadedTracks = downloadedTracks
        )
    }

    val state: StateFlow<HomeState> = combine(
        catalogFlow,
        downloadedMediaFlow,
        syncEngine.syncState,
        _isRefreshing,
        networkMonitor.isOfflineModeActive
    ) { catalog, downloadedMedia, syncState, refreshing, isOffline ->
        val effectiveQuickMix = if (isOffline) {
            val count = downloadedMedia.downloadedTracks.size
            val pages = (count / 4).coerceAtMost(4)
            if (pages == 0) emptyList() else downloadedMedia.downloadedTracks.take(pages * 4)
        } else {
            catalog.quickMix
        }

        val effectiveFeaturedArtist = if (isOffline) null else catalog.featuredArtist
        val effectiveFeaturedAlbums = if (isOffline) emptyList() else catalog.featuredArtistAlbums

        HomeState(
            quickMixSongs = effectiveQuickMix,
            featuredArtist = effectiveFeaturedArtist,
            featuredArtistAlbums = effectiveFeaturedAlbums,
            recentlyAdded = SectionState(
                isLoading = catalog.recent.isEmpty() && syncState is SyncState.Syncing,
                data = catalog.recent
            ),
            randomAlbums = SectionState(
                isLoading = (catalog.random.isEmpty() && syncState is SyncState.Syncing) || _randomIsLoading.value,
                data = catalog.random
            ),
            playlists = SectionState(
                isLoading = catalog.playlists.isEmpty() && syncState is SyncState.Syncing,
                data = catalog.playlists
            ),
            downloadedTrackIds = downloadedMedia.trackIds,
            downloadedAlbumIds = downloadedMedia.albumIds,
            downloadedPlaylistIds = downloadedMedia.playlistIds,
            isRefreshing = refreshing,
            syncMessage = when (syncState) {
                is SyncState.Syncing -> syncState.stage
                is SyncState.Error -> syncState.message
                is SyncState.Idle -> null
            },
            isOfflineModeActive = isOffline
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeState()
    )

    init {
        // Initial sync in background to populate Room
        syncEngine.triggerSync(force = false)
        loadDynamicSections()

        viewModelScope.launch {
            syncEngine.syncState.collect { syncState ->
                if (syncState is SyncState.Idle) {
                    loadDynamicSections()
                }
            }
        }

        viewModelScope.launch {
            libraryRepository.getAlbumsFlow().collect { albums ->
                if (albums.isNotEmpty() && _randomAlbums.value.isEmpty()) {
                    loadDynamicSections()
                }
            }
        }

        viewModelScope.launch {
            libraryRepository.getArtistsFlow().collect { artists ->
                if (artists.isNotEmpty() && _featuredArtist.value == null) {
                    loadFeaturedArtist()
                }
            }
        }
    }

    private fun loadDynamicSections() {
        viewModelScope.launch {
            _randomIsLoading.value = true
            _randomAlbums.value = libraryRepository.getRandomAlbums(15)
            _quickMixSongs.value = libraryRepository.getRandomTracks(16)
            loadFeaturedArtist()
            _randomIsLoading.value = false
        }
    }

    private suspend fun loadFeaturedArtist() {
        val artists = artistDao.getArtists(subsonicRepository.currentServerId)
        if (artists.isNotEmpty()) {
            val randomArtist = artists.random().toDomain()
            _featuredArtist.value = randomArtist
            val details = libraryRepository.fetchArtistDetails(randomArtist.id)
            _featuredArtistAlbums.value = details.albums.take(8)
        }
    }

    fun playTrack(track: TrackItem, queue: List<TrackItem>) {
        if (state.value.isOfflineModeActive && !state.value.downloadedTrackIds.contains(track.id)) {
            return
        }
        val validQueue = if (state.value.isOfflineModeActive) {
            queue.filter { state.value.downloadedTrackIds.contains(it.id) }
        } else {
            queue
        }
        playbackController.playTrack(track, if (validQueue.isNotEmpty()) validQueue else listOf(track))
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncEngine.syncLibrary(force = true)
                loadDynamicSections()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private data class HomeCatalogData(
    val recent: List<AlbumItem>,
    val random: List<AlbumItem>,
    val playlists: List<PlaylistItem>,
    val quickMix: List<TrackItem>,
    val featuredArtist: ArtistItem?,
    val featuredArtistAlbums: List<AlbumItem>
)

private data class DownloadedMediaInfo(
    val trackIds: Set<String>,
    val albumIds: Set<String>,
    val playlistIds: Set<String>,
    val downloadedTracks: List<TrackItem>
)
