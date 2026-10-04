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
import kotlinx.coroutines.sync.withLock
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
    private val appPreferences: com.notmugil.uta.data.preferences.AppPreferences,
    val sleepTimerManager: SleepTimerManager
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _randomAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    private val _randomIsLoading = MutableStateFlow(false)
    private val _quickMixSongs = MutableStateFlow<List<TrackItem>>(emptyList())
    private val _featuredArtistData = MutableStateFlow<FeaturedArtistData?>(null)
    private val featuredArtistMutex = kotlinx.coroutines.sync.Mutex()

    val currentTrack: StateFlow<TrackItem?> = playbackController.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying

    private val catalogSectionsFlow = combine(
        libraryRepository.getRecentlyAddedAlbumsFlow(15),
        _randomAlbums,
        libraryRepository.getPlaylistsFlow(),
        _quickMixSongs,
        libraryRepository.getMostPlayedTracksFlow(16)
    ) { recent, random, playlists, quickMix, mostPlayedTracks ->
        Tuple5(recent, random, playlists, quickMix, mostPlayedTracks)
    }

    private val playedAlbumsFlow = combine(
        libraryRepository.getMostPlayedAlbumsFlow(15),
        libraryRepository.getRecentlyPlayedAlbumsFlow(15)
    ) { mostPlayed, recentlyPlayed ->
        Pair(mostPlayed, recentlyPlayed)
    }

    private val catalogFlow = combine(
        catalogSectionsFlow,
        playedAlbumsFlow,
        _featuredArtistData
    ) { (recent, random, playlists, quickMix, mostPlayedTracks), (mostPlayedAlbums, recentlyPlayedAlbums), featuredData ->
        HomeCatalogData(
            recent = recent,
            random = random,
            playlists = playlists,
            quickMix = quickMix,
            mostPlayedSongs = mostPlayedTracks,
            mostPlayedAlbums = mostPlayedAlbums,
            recentlyPlayedAlbums = recentlyPlayedAlbums,
            featuredArtist = featuredData?.artist,
            featuredArtistAlbums = featuredData?.albums ?: emptyList()
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

    private val homeFlow = combine(
        catalogFlow,
        downloadedMediaFlow,
        appPreferences.homeSectionConfigs
    ) { catalog, downloadedMedia, sectionConfigs ->
        Triple(catalog, downloadedMedia, sectionConfigs)
    }

    val state: StateFlow<HomeState> = combine(
        homeFlow,
        syncEngine.syncState,
        _isRefreshing,
        networkMonitor.isOfflineModeActive
    ) { (catalog, downloadedMedia, sectionConfigs), syncState, refreshing, isOffline ->
        val effectiveQuickMix = if (isOffline) {
            val count = downloadedMedia.downloadedTracks.size
            val pages = (count / 4).coerceAtMost(4)
            if (pages == 0) emptyList() else downloadedMedia.downloadedTracks.take(pages * 4)
        } else {
            catalog.quickMix
        }

        val effectiveMostPlayedSongs = if (isOffline) {
            catalog.mostPlayedSongs.filter { downloadedMedia.trackIds.contains(it.id) }
        } else {
            catalog.mostPlayedSongs
        }

        val effectiveFeaturedArtist = if (isOffline) null else catalog.featuredArtist
        val effectiveFeaturedAlbums = if (isOffline) emptyList() else catalog.featuredArtistAlbums

        HomeState(
            quickMixSongs = effectiveQuickMix,
            mostPlayedSongs = effectiveMostPlayedSongs,
            featuredArtist = effectiveFeaturedArtist,
            featuredArtistAlbums = effectiveFeaturedAlbums,
            recentlyAdded = SectionState(
                isLoading = catalog.recent.isEmpty() && syncState is SyncState.Syncing,
                data = catalog.recent
            ),
            mostPlayedAlbums = SectionState(
                isLoading = catalog.mostPlayedAlbums.isEmpty() && syncState is SyncState.Syncing,
                data = catalog.mostPlayedAlbums
            ),
            recentlyPlayedAlbums = SectionState(
                isLoading = catalog.recentlyPlayedAlbums.isEmpty() && syncState is SyncState.Syncing,
                data = catalog.recentlyPlayedAlbums
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
            isOfflineModeActive = isOffline,
            visibleSections = sectionConfigs.filter { it.enabled }.map { it.section }
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
                if (artists.isNotEmpty() && _featuredArtistData.value == null) {
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
        featuredArtistMutex.withLock {
            val artists = artistDao.getArtists(subsonicRepository.currentServerId)
            if (artists.isEmpty()) return

            val candidates = artists.shuffled()
            for (candidateEntity in candidates.take(5)) {
                val candidateArtist = candidateEntity.toDomain()
                val details = libraryRepository.fetchArtistDetails(candidateArtist.id)
                val validAlbums = details.albums
                    .map { album ->
                        album.copy(
                            artist = if (album.artist.isBlank() || album.artist == "Unknown Artist") candidateArtist.name else album.artist,
                            artistId = if (album.artistId.isNullOrBlank()) candidateArtist.id else album.artistId
                        )
                    }
                    .filter { it.artistId == candidateArtist.id || it.artist.equals(candidateArtist.name, ignoreCase = true) }

                if (validAlbums.isNotEmpty()) {
                    _featuredArtistData.value = FeaturedArtistData(
                        artist = candidateArtist,
                        albums = validAlbums.take(8)
                    )
                    return
                }
            }
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

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncEngine.syncLibrary(force = force)
                loadDynamicSections()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

private data class FeaturedArtistData(
    val artist: ArtistItem,
    val albums: List<AlbumItem>
)

private data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

private data class HomeCatalogData(
    val recent: List<AlbumItem>,
    val random: List<AlbumItem>,
    val playlists: List<PlaylistItem>,
    val quickMix: List<TrackItem>,
    val mostPlayedSongs: List<TrackItem>,
    val mostPlayedAlbums: List<AlbumItem>,
    val recentlyPlayedAlbums: List<AlbumItem>,
    val featuredArtist: ArtistItem?,
    val featuredArtistAlbums: List<AlbumItem>
)

private data class DownloadedMediaInfo(
    val trackIds: Set<String>,
    val albumIds: Set<String>,
    val playlistIds: Set<String>,
    val downloadedTracks: List<TrackItem>
)
