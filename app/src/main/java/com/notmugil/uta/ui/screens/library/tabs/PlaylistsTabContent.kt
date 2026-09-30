package com.notmugil.uta.ui.screens.library.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.ui.screens.library.SortCriteria
import com.notmugil.uta.ui.shared.MediaCard
import com.notmugil.uta.ui.shared.PlaylistListItem
import com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget

@Composable
fun PlaylistsTabContent(
    playlists: List<PlaylistItem>,
    selectedCriteria: SortCriteria,
    isAscending: Boolean,
    columnCount: Int,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    listState: LazyListState = rememberLazyListState(),
    downloadedPlaylistIds: Set<String> = emptySet(),
    isOffline: Boolean = false
) {
    val mediaActionHandler = LocalMediaActionHandler.current

    val sortedPlaylists = remember(playlists, selectedCriteria, isAscending) {
        val sorted = when (selectedCriteria) {
            SortCriteria.ALPHABETICAL -> playlists.sortedBy { it.name.lowercase() }
            SortCriteria.COUNT -> playlists.sortedBy { it.songCount }
            SortCriteria.DATE_MODIFIED -> playlists.sortedBy { it.changedAt ?: it.createdAt ?: 0L }
            SortCriteria.DATE_CREATED -> playlists.sortedBy { it.createdAt ?: it.changedAt ?: 0L }
            SortCriteria.DURATION -> playlists.sortedBy { it.durationSeconds }
            else -> playlists.sortedBy { it.name.lowercase() }
        }
        if (isAscending) sorted else sorted.reversed()
    }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val isAlphabetical = selectedCriteria == SortCriteria.ALPHABETICAL

    Box(modifier = modifier.fillMaxSize()) {
        if (columnCount > 1) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 168.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "create_new_playlist_card") {
                    Card(
                        onClick = onCreatePlaylistClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.Plus,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (columnCount >= 3) 28.dp else 36.dp)
                            )
                            Spacer(modifier = Modifier.height(if (columnCount >= 3) 4.dp else 8.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.library_new_playlist),
                                style = if (columnCount >= 3) MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp) else MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                items(sortedPlaylists) { playlist ->
                    val isDownloaded = downloadedPlaylistIds.contains(playlist.id)
                    val alpha = if (!isOffline || isDownloaded) 1f else 0.38f
                    MediaCard(
                        title = playlist.name,
                        subtitle = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.playlist_songs_count, playlist.songCount),
                        coverArtId = playlist.coverArtId,
                        playlistId = playlist.id,
                        fallbackIcon = Tabler.Outline.Playlist,
                        columnCount = columnCount,
                        alpha = alpha,
                        onClick = { onPlaylistClick(playlist.id) },
                        onLongClick = { mediaActionHandler.show(MediaTarget.PlaylistTarget(playlist)) }
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = 168.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCreatePlaylistClick)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.Plus,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.library_new_playlist),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                items(sortedPlaylists) { playlist ->
                    val isDownloaded = downloadedPlaylistIds.contains(playlist.id)
                    PlaylistListItem(
                        playlist = playlist,
                        isDownloaded = isDownloaded,
                        isOffline = isOffline,
                        onClick = { onPlaylistClick(playlist.id) },
                        onLongClick = { mediaActionHandler.show(MediaTarget.PlaylistTarget(playlist)) }
                    )
                }
            }
        }

        val isScrollable = if (columnCount > 1) {
            gridState.canScrollForward || gridState.canScrollBackward || (gridState.layoutInfo.totalItemsCount > gridState.layoutInfo.visibleItemsInfo.size && gridState.layoutInfo.visibleItemsInfo.isNotEmpty())
        } else {
            listState.canScrollForward || listState.canScrollBackward || (listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size && listState.layoutInfo.visibleItemsInfo.isNotEmpty())
        }

        if (isAlphabetical && sortedPlaylists.isNotEmpty() && isScrollable) {
            val isScrollInProgress = if (columnCount > 1) gridState.isScrollInProgress else listState.isScrollInProgress
            val availableLetters = androidx.compose.runtime.remember(sortedPlaylists) {
                com.notmugil.uta.ui.screens.library.components.getAvailableAlphabetLetters(sortedPlaylists) { it.name }
            }
            val currentScrollLetter by androidx.compose.runtime.remember(sortedPlaylists, columnCount) {
                androidx.compose.runtime.derivedStateOf {
                    val rawIdx = if (columnCount > 1) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
                    val playlistIdx = (rawIdx - 1).coerceAtLeast(0)
                    val item = sortedPlaylists.getOrNull(playlistIdx)
                    if (item != null) {
                        val key = item.name.trimStart()
                        if (key.isNotEmpty() && key.first().isLetter()) {
                            key.first().uppercaseChar().toString()
                        } else {
                            "#"
                        }
                    } else null
                }
            }
            var scrollJob by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }

            com.notmugil.uta.ui.screens.library.components.AlphabetFastScroller(
                isScrollInProgress = isScrollInProgress,
                currentScrollLetter = currentScrollLetter,
                availableLetters = availableLetters,
                onLetterSelected = { letter ->
                    val playlistIndex = com.notmugil.uta.ui.screens.library.components.findAlphabetTargetIndex(
                        items = sortedPlaylists,
                        letter = letter,
                        isAscending = isAscending
                    ) { it.name }

                    val targetIndex = if (letter == "#" && isAscending) 0 else playlistIndex + 1

                    scrollJob?.cancel()
                    scrollJob = coroutineScope.launch {
                        if (columnCount > 1) {
                            gridState.scrollToItem(targetIndex)
                        } else {
                            listState.scrollToItem(targetIndex)
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
