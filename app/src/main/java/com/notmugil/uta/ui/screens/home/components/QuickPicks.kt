package com.notmugil.uta.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.shared.SongListItem

@Composable
fun QuickPicks(
    randomSongs: List<TrackItem>,
    currentTrack: TrackItem?,
    isPlaying: Boolean,
    onPlaySong: (TrackItem, List<TrackItem>) -> Unit,
    modifier: Modifier = Modifier,
    downloadedTrackIds: Set<String> = emptySet(),
    isOffline: Boolean = false,
    title: String = "Quick picks"
) {
    if (randomSongs.isEmpty()) return

    val quickMixPages = remember(randomSongs) { randomSongs.chunked(4) }
    val quickMixPagerState = remember(quickMixPages.size) { androidx.compose.foundation.pager.PagerState(currentPage = 0) { quickMixPages.size } }

    val mediaActionHandler = com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler.current

    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        HorizontalPager(
            state = quickMixPagerState,
            contentPadding = PaddingValues(end = 48.dp),
            pageSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) { pageIndex ->
            val pageSongs = quickMixPages[pageIndex]
            val isLastPage = pageIndex == quickMixPages.lastIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isLastPage) {
                            Modifier.layout { measurable, constraints ->
                                val extraWidth = 48.dp.roundToPx()
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = constraints.maxWidth + extraWidth,
                                        maxWidth = constraints.maxWidth + extraWidth
                                    )
                                )
                                layout(constraints.maxWidth, placeable.height) {
                                    placeable.place(0, 0)
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                pageSongs.forEach { song ->
                    val isCurrent = currentTrack?.id == song.id
                    val isDownloaded = downloadedTrackIds.contains(song.id)
                    SongListItem(
                        song = song.copy(album = null),
                        isCurrentSong = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        isDownloaded = isDownloaded,
                        isOffline = isOffline,
                        contentPadding = PaddingValues(start = 16.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                        onClick = { onPlaySong(song, randomSongs) },
                        onLongClick = { mediaActionHandler.show(com.notmugil.uta.ui.shared.actionsheet.MediaTarget.TrackTarget(song)) },
                        onMoreClick = { mediaActionHandler.show(com.notmugil.uta.ui.shared.actionsheet.MediaTarget.TrackTarget(song)) }
                    )
                }
            }
        }
    }
}
