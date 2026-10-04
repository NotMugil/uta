package com.notmugil.uta.ui.screens.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.R
import com.notmugil.uta.domain.model.sortWithOption
import com.notmugil.uta.ui.screens.album.components.AlbumHeader
import com.notmugil.uta.ui.screens.album.components.AlbumToolbar
import com.notmugil.uta.ui.screens.album.components.AlbumTopBar
import com.notmugil.uta.ui.screens.album.components.AlbumTrackRow
import com.notmugil.uta.ui.shared.AppTopBar
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.FullScreenLoader
import com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler
import com.notmugil.uta.ui.shared.actionsheet.MediaActionBottomSheet
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget
import kotlinx.coroutines.launch

@Composable
fun AlbumDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mediaActionState = LocalMediaActionHandler.current
    val listState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    var savedScrollIndex by remember { mutableIntStateOf(0) }
    var savedScrollOffset by remember { mutableIntStateOf(0) }
    val topBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp

    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 380
        }
    }

    LaunchedEffect(state.isSearchActive) {
        if (state.isSearchActive) {
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    fun openSearch() {
        savedScrollIndex = listState.firstVisibleItemIndex
        savedScrollOffset = listState.firstVisibleItemScrollOffset
        viewModel.setSearchActive(true)
        coroutineScope.launch {
            listState.animateScrollToItem(index = 1, scrollOffset = 0)
        }
    }

    fun closeSearch() {
        viewModel.setSearchActive(false)
        coroutineScope.launch {
            listState.animateScrollToItem(savedScrollIndex, savedScrollOffset)
        }
    }

    val processedTracks = remember(state.tracks, state.searchQuery, state.sortOption, state.isAscending) {
        var list = state.tracks
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q)
            }
        }
        list.sortWithOption(state.sortOption, state.isAscending)
    }

    val playableTracks = remember(processedTracks, state.isOfflineModeActive, state.downloadedTrackIds) {
        if (state.isOfflineModeActive) {
            processedTracks.filter { state.downloadedTrackIds.contains(it.id) }
        } else {
            processedTracks
        }
    }

    var displayedCount by remember(processedTracks.size) { mutableIntStateOf(50) }
    val visibleTracks = remember(processedTracks, displayedCount) {
        processedTracks.take(displayedCount)
    }
    val shouldLoadMore by remember(processedTracks.size, displayedCount) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 15 && displayedCount < processedTracks.size
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            displayedCount = (displayedCount + 50).coerceAtMost(processedTracks.size)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        state.album?.coverArtId?.let { coverArtId ->
            Box(modifier = Modifier.fillMaxSize()) {
                CoverArtImage(
                    coverArtId = coverArtId,
                    contentDescription = null,
                    size = 1000.dp,
                    shape = RoundedCornerShape(0.dp),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.35f)
                        .blur(50.dp)
                        .alpha(0.5f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }
        }

        val isPageLoading = state.isLoading && (state.album == null || state.tracks.isEmpty())
        if (isPageLoading) {
            FullScreenLoader(
                onNavigateBack = onNavigateBack
            )
        } else if (state.errorMessage != null && state.album == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AppTopBar(
                    title = "",
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.errorMessage ?: stringResource(R.string.failed_load_album),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.refreshAlbum() }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        } else {
            val allDownloaded = state.tracks.isNotEmpty() && state.tracks.all { state.downloadedTrackIds.contains(it.id) }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = topBarHeight, bottom = 168.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                state.album?.let { album ->
                    item(key = "album_header") {
                        AlbumHeader(
                            album = album,
                            allTracks = state.tracks,
                            downloadedTrackIds = state.downloadedTrackIds,
                            playableTracks = playableTracks,
                            allDownloaded = allDownloaded,
                            isDownloading = state.isDownloading,
                            isOffline = state.isOfflineModeActive,
                            onPlayAll = { viewModel.playAll(shuffle = false) },
                            onShuffleAll = { viewModel.playAll(shuffle = true) },
                            onToggleDownload = { viewModel.toggleAlbumDownload() },
                            onToggleFavorite = { viewModel.toggleAlbumFavorite() },
                            onNavigateToArtist = onNavigateToArtist
                        )
                    }

                    item(key = "album_toolbar") {
                        AlbumToolbar(
                            searchQuery = state.searchQuery,
                            onSearchQueryChange = { query ->
                                viewModel.setSearchQuery(query)
                                if (listState.firstVisibleItemIndex == 0) {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(index = 1, scrollOffset = 0)
                                    }
                                }
                            },
                            isSearchActive = state.isSearchActive,
                            onOpenSearch = { openSearch() },
                            onCloseSearch = { closeSearch() },
                            searchFocusRequester = searchFocusRequester,
                            processedSongsCount = processedTracks.size,
                            sortOption = state.sortOption,
                            onSortOptionChange = { viewModel.setSortOption(it) },
                            isAscending = state.isAscending,
                            onToggleAscending = { viewModel.toggleAscending() },
                            viewOption = state.viewOption,
                            onViewOptionChange = { viewModel.setViewOption(it) }
                        )
                    }
                }

                val hasMultipleDiscs = visibleTracks.mapNotNull { it.discNumber }.distinct().size > 1
                if (hasMultipleDiscs) {
                    val grouped = visibleTracks.groupBy { it.discNumber ?: 1 }
                    grouped.forEach { (disc, discTracks) ->
                        item(key = "disc_header_$disc") {
                            Text(
                                text = stringResource(R.string.album_disc_header, disc),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                        itemsIndexed(discTracks, key = { _, track -> track.id }) { index, track ->
                            val isDownloaded = state.downloadedTrackIds.contains(track.id)
                            val isUnavailableOffline = state.isOfflineModeActive && !isDownloaded
                            val isCurrentSong = state.currentPlayingTrackId == track.id

                            AlbumTrackRow(
                                track = track,
                                index = track.trackNumber ?: (index + 1),
                                isCurrentSong = isCurrentSong,
                                isPlaying = state.isPlaying,
                                isUnavailableOffline = isUnavailableOffline,
                                viewOption = state.viewOption,
                                isLastItem = index == discTracks.lastIndex,
                                onClick = { viewModel.playTrack(track, playableTracks) },
                                onOptionsClick = {
                                    mediaActionState.show(MediaTarget.TrackTarget(track))
                                }
                            )
                        }
                    }
                } else {
                    itemsIndexed(visibleTracks, key = { _, track -> track.id }) { index, track ->
                        val isDownloaded = state.downloadedTrackIds.contains(track.id)
                        val isUnavailableOffline = state.isOfflineModeActive && !isDownloaded
                        val isCurrentSong = state.currentPlayingTrackId == track.id

                        AlbumTrackRow(
                            track = track,
                            index = track.trackNumber ?: (index + 1),
                            isCurrentSong = isCurrentSong,
                            isPlaying = state.isPlaying,
                            isUnavailableOffline = isUnavailableOffline,
                            viewOption = state.viewOption,
                            isLastItem = index == processedTracks.lastIndex,
                            onClick = { viewModel.playTrack(track, playableTracks) },
                            onOptionsClick = {
                                mediaActionState.show(MediaTarget.TrackTarget(track))
                            }
                        )
                    }
                }
            }

            AlbumTopBar(
                title = state.album?.title.orEmpty(),
                showTitle = showTopBarTitle,
                onBack = onNavigateBack,
                onOptionsClick = {
                    state.album?.let { album ->
                        mediaActionState.show(MediaTarget.AlbumTarget(album))
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        val currentTarget = mediaActionState.currentTarget
        val isTargetDownloaded = when (currentTarget) {
            is MediaTarget.TrackTarget -> state.downloadedTrackIds.contains(currentTarget.track.id)
            is MediaTarget.AlbumTarget -> state.tracks.isNotEmpty() && state.tracks.all { state.downloadedTrackIds.contains(it.id) }
            else -> false
        }

        MediaActionBottomSheet(
            state = mediaActionState,
            playbackController = viewModel.playbackController,
            libraryRepository = viewModel.libraryRepository,
            offlineDownloadManager = viewModel.offlineDownloadManager,
            isDownloaded = isTargetDownloaded,
            isOffline = state.isOfflineModeActive,
            onNavigateToArtist = onNavigateToArtist
        )
    }
}
