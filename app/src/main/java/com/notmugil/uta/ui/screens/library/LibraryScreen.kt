package com.notmugil.uta.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.ui.screens.library.components.CreatePlaylistDialog
import com.notmugil.uta.ui.screens.library.components.LibraryHeader
import com.notmugil.uta.ui.screens.library.components.LibraryToolbar
import com.notmugil.uta.ui.screens.library.tabs.AlbumsTabContent
import com.notmugil.uta.ui.screens.library.tabs.ArtistsTabContent
import com.notmugil.uta.ui.screens.library.tabs.PlaylistsTabContent
import com.notmugil.uta.ui.shared.FullScreenLoader
import com.notmugil.uta.ui.shared.PullToRefreshRevealLayout

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    initialTab: LibraryTab? = null,
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appPreferences = LocalAppPreferences.current

    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            viewModel.selectTab(initialTab)
        }
    }

    val tabColumnCounts = remember {
        mutableStateMapOf<LibraryTab, Int>().apply {
            LibraryTab.entries.forEach { tab ->
                val defaultCols = when (tab) {
                    LibraryTab.ALBUMS -> 1
                    LibraryTab.PLAYLISTS -> 2
                    LibraryTab.ARTISTS -> 3
                }
                put(tab, appPreferences?.getLibraryTabColumnCount(tab.name, default = defaultCols) ?: defaultCols)
            }
        }
    }

    var selectedCriteria by rememberSaveable { mutableStateOf(SortCriteria.ALPHABETICAL) }
    var isAscending by rememberSaveable { mutableStateOf(true) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(state.selectedTab) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }

    val availableSortOptions = remember(state.selectedTab) {
        when (state.selectedTab) {
            LibraryTab.ALBUMS -> listOf(SortCriteria.ALPHABETICAL, SortCriteria.ARTIST, SortCriteria.YEAR, SortCriteria.COUNT)
            LibraryTab.ARTISTS -> listOf(SortCriteria.ALPHABETICAL, SortCriteria.COUNT)
            LibraryTab.PLAYLISTS -> listOf(
                SortCriteria.ALPHABETICAL,
                SortCriteria.COUNT,
                SortCriteria.DATE_MODIFIED,
                SortCriteria.DATE_CREATED,
                SortCriteria.DURATION
            )
        }
    }

    LaunchedEffect(availableSortOptions) {
        if (selectedCriteria !in availableSortOptions) {
            selectedCriteria = availableSortOptions.first()
        }
    }

    val columnCount = tabColumnCounts[state.selectedTab] ?: 2

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                LibraryHeader(
                    selectedTab = state.selectedTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    onNavigateToDownloads = onNavigateToDownloads,
                    onNavigateToHistory = onNavigateToHistory
                )

                LibraryToolbar(
                    selectedCriteria = selectedCriteria,
                    availableSortOptions = availableSortOptions,
                    onSortCriteriaSelected = { selectedCriteria = it },
                    isAscending = isAscending,
                    onToggleAscending = { isAscending = !isAscending },
                    columnCount = columnCount,
                    onColumnCountChanged = { cols ->
                        tabColumnCounts[state.selectedTab] = cols
                        appPreferences?.setLibraryTabColumnCount(state.selectedTab.name, cols)
                    }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        PullToRefreshRevealLayout(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isEntirelyEmpty = state.albums.isEmpty() && state.artists.isEmpty() && state.playlists.isEmpty()
            if (isEntirelyEmpty && (state.isLoading || state.isSyncing)) {
                FullScreenLoader(
                    message = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.home_syncing),
                    containerColor = Color.Transparent
                )
            } else {
                when (state.selectedTab) {
                    LibraryTab.ALBUMS -> AlbumsTabContent(
                        albums = state.albums,
                        selectedCriteria = selectedCriteria,
                        isAscending = isAscending,
                        columnCount = columnCount,
                        gridState = gridState,
                        listState = listState,
                        downloadedAlbumIds = state.downloadedAlbumIds,
                        isOffline = state.isOfflineModeActive,
                        onAlbumClick = onNavigateToAlbum
                    )
                    LibraryTab.ARTISTS -> ArtistsTabContent(
                        artists = state.artists,
                        selectedCriteria = selectedCriteria,
                        isAscending = isAscending,
                        columnCount = columnCount,
                        gridState = gridState,
                        listState = listState,
                        onArtistClick = onNavigateToArtist
                    )
                    LibraryTab.PLAYLISTS -> PlaylistsTabContent(
                        playlists = state.playlists,
                        selectedCriteria = selectedCriteria,
                        isAscending = isAscending,
                        columnCount = columnCount,
                        gridState = gridState,
                        listState = listState,
                        downloadedPlaylistIds = state.downloadedPlaylistIds,
                        isOffline = state.isOfflineModeActive,
                        onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                        onPlaylistClick = onNavigateToPlaylist
                    )
                }
            }
        }

        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onPlaylistCreated = { name ->
                    viewModel.createPlaylist(name, onCreated = onNavigateToPlaylist)
                }
            )
        }
    }
}
