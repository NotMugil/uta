package com.notmugil.uta.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.ExperimentalMaterial3Api
import com.notmugil.uta.ui.screens.home.components.FeaturedArtistSection
import com.notmugil.uta.ui.screens.home.components.HomeAlbumSectionRow
import com.notmugil.uta.ui.screens.home.components.HomeHeader
import com.notmugil.uta.ui.screens.home.components.HomePlaylistSectionRow
import com.notmugil.uta.ui.screens.home.components.QuickPicks
import com.notmugil.uta.ui.screens.library.LibraryTab
import com.notmugil.uta.ui.shared.FullScreenLoader
import com.notmugil.uta.ui.shared.PullToRefreshRevealLayout
import com.notmugil.uta.ui.shared.SleepTimerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {},
    onNavigateToLibraryTab: (LibraryTab) -> Unit = {},
    onNavigateToPlayer: (String) -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    var showSleepTimerSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            HomeHeader(
                syncMessage = state.syncMessage,
                isOffline = state.isOfflineModeActive,
                onNavigateToHistory = onNavigateToHistory
            )
        },
        modifier = modifier
    ) { innerPadding ->
        val isEntirelyEmpty = state.recentlyAdded.data.isEmpty() &&
            state.randomAlbums.data.isEmpty() &&
            state.playlists.data.isEmpty() &&
            state.quickMixSongs.isEmpty()

        PullToRefreshRevealLayout(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isEntirelyEmpty && state.syncMessage == null && !state.recentlyAdded.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.home_empty_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.home_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text(androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_sync))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 168.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                if (state.quickMixSongs.isNotEmpty()) {
                    item {
                        QuickPicks(
                            title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.home_quick_picks),
                            randomSongs = state.quickMixSongs,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            downloadedTrackIds = state.downloadedTrackIds,
                            isOffline = state.isOfflineModeActive,
                            onPlaySong = { track, queue -> viewModel.playTrack(track, queue) }
                        )
                    }
                }

                item {
                    HomePlaylistSectionRow(
                        title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.tab_playlists),
                        sectionState = state.playlists,
                        onSeeMoreClick = { onNavigateToLibraryTab(LibraryTab.PLAYLISTS) },
                        downloadedPlaylistIds = state.downloadedPlaylistIds,
                        isOffline = state.isOfflineModeActive,
                        onPlaylistClick = onNavigateToPlaylist
                    )
                }

                item {
                    HomeAlbumSectionRow(
                        title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.home_recently_added),
                        sectionState = state.recentlyAdded,
                        onSeeMoreClick = { onNavigateToLibraryTab(LibraryTab.ALBUMS) },
                        downloadedAlbumIds = state.downloadedAlbumIds,
                        isOffline = state.isOfflineModeActive,
                        onAlbumClick = onNavigateToAlbum
                    )
                }

                if (!state.isOfflineModeActive) {
                    state.featuredArtist?.let { artist ->
                        item {
                            FeaturedArtistSection(
                                artist = artist,
                                albums = state.featuredArtistAlbums,
                                songs = emptyList(),
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                downloadedAlbumIds = state.downloadedAlbumIds,
                                downloadedTrackIds = state.downloadedTrackIds,
                                isOffline = state.isOfflineModeActive,
                                onNavigateToArtist = onNavigateToArtist,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onPlaySong = { track, queue -> viewModel.playTrack(track, queue) }
                            )
                        }
                    }
                }

                item {
                    HomeAlbumSectionRow(
                        title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.home_random_albums),
                        sectionState = state.randomAlbums,
                        onSeeMoreClick = { onNavigateToLibraryTab(LibraryTab.ALBUMS) },
                        downloadedAlbumIds = state.downloadedAlbumIds,
                        isOffline = state.isOfflineModeActive,
                        onAlbumClick = onNavigateToAlbum
                    )
                }
            }
        }
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            sleepTimerManager = viewModel.sleepTimerManager,
            onDismiss = { showSleepTimerSheet = false }
        )
    }
}
}
