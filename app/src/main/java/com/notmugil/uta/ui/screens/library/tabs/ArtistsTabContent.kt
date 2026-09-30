package com.notmugil.uta.ui.screens.library.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.ui.screens.library.SortCriteria
import com.notmugil.uta.ui.shared.ArtistCard
import com.notmugil.uta.ui.shared.ArtistListItem
import com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget

@Composable
fun ArtistsTabContent(
    artists: List<ArtistItem>,
    selectedCriteria: SortCriteria,
    isAscending: Boolean,
    columnCount: Int,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    listState: LazyListState = rememberLazyListState()
) {
    if (artists.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.library_empty_artists),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val mediaActionHandler = LocalMediaActionHandler.current

    val sortedArtists = remember(artists, selectedCriteria, isAscending) {
        val sorted = when (selectedCriteria) {
            SortCriteria.ALPHABETICAL -> artists.sortedBy { it.name.lowercase() }
            SortCriteria.COUNT -> artists.sortedBy { it.albumCount }
            else -> artists.sortedBy { it.name.lowercase() }
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedArtists) { artist ->
                    ArtistCard(
                        artist = artist,
                        columnCount = columnCount,
                        onClick = { onArtistClick(artist.id) },
                        onLongClick = { mediaActionHandler.show(MediaTarget.ArtistTarget(artist)) }
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = 168.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedArtists) { artist ->
                    ArtistListItem(
                        artist = artist,
                        onClick = { onArtistClick(artist.id) },
                        onLongClick = { mediaActionHandler.show(MediaTarget.ArtistTarget(artist)) }
                    )
                }
            }
        }

        val isScrollable = if (columnCount > 1) {
            gridState.canScrollForward || gridState.canScrollBackward || (gridState.layoutInfo.totalItemsCount > gridState.layoutInfo.visibleItemsInfo.size && gridState.layoutInfo.visibleItemsInfo.isNotEmpty())
        } else {
            listState.canScrollForward || listState.canScrollBackward || (listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size && listState.layoutInfo.visibleItemsInfo.isNotEmpty())
        }

        if (isAlphabetical && sortedArtists.isNotEmpty() && isScrollable) {
            val isScrollInProgress = if (columnCount > 1) gridState.isScrollInProgress else listState.isScrollInProgress
            val availableLetters = androidx.compose.runtime.remember(sortedArtists) {
                com.notmugil.uta.ui.screens.library.components.getAvailableAlphabetLetters(sortedArtists) { it.name }
            }
            val currentScrollLetter by androidx.compose.runtime.remember(sortedArtists, columnCount) {
                androidx.compose.runtime.derivedStateOf {
                    val idx = if (columnCount > 1) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
                    val item = sortedArtists.getOrNull(idx)
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
                    val targetIndex = com.notmugil.uta.ui.screens.library.components.findAlphabetTargetIndex(
                        items = sortedArtists,
                        letter = letter,
                        isAscending = isAscending
                    ) { it.name }

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
