package com.notmugil.uta.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.ui.screens.home.SectionState
import com.notmugil.uta.ui.shared.MediaCard
import com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget

@Composable
fun HomeAlbumSectionRow(
    title: String,
    sectionState: SectionState<List<AlbumItem>>,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSeeMoreClick: (() -> Unit)? = null,
    downloadedAlbumIds: Set<String> = emptySet(),
    isOffline: Boolean = false
) {
    if (sectionState.data.isEmpty() && !sectionState.isLoading) return
    val mediaActionHandler = LocalMediaActionHandler.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (onSeeMoreClick != null) {
                IconButton(
                    onClick = onSeeMoreClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Tabler.Outline.ChevronRight,
                        contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_see_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(sectionState.data, key = { it.id }) { album ->
                val isDownloaded = downloadedAlbumIds.contains(album.id)
                val alpha = if (!isOffline || isDownloaded) 1f else 0.38f
                MediaCard(
                    title = album.title,
                    subtitle = album.artist,
                    coverArtId = album.coverArtId,
                    alpha = alpha,
                    onClick = { onAlbumClick(album.id) },
                    onLongClick = { mediaActionHandler.show(MediaTarget.AlbumTarget(album)) }
                )
            }
        }
    }
}

@Composable
fun HomePlaylistSectionRow(
    title: String,
    sectionState: SectionState<List<PlaylistItem>>,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSeeMoreClick: (() -> Unit)? = null,
    downloadedPlaylistIds: Set<String> = emptySet(),
    isOffline: Boolean = false
) {
    if (sectionState.data.isEmpty() && !sectionState.isLoading) return
    val mediaActionHandler = LocalMediaActionHandler.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (onSeeMoreClick != null) {
                IconButton(
                    onClick = onSeeMoreClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Tabler.Outline.ChevronRight,
                        contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_see_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(sectionState.data, key = { it.id }) { playlist ->
                val isDownloaded = downloadedPlaylistIds.contains(playlist.id)
                val alpha = if (!isOffline || isDownloaded) 1f else 0.38f
                MediaCard(
                    title = playlist.name,
                    subtitle = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.playlist_songs_count, playlist.songCount),
                    coverArtId = playlist.coverArtId,
                    playlistId = playlist.id,
                    fallbackIcon = Tabler.Outline.Playlist,
                    alpha = alpha,
                    onClick = { onPlaylistClick(playlist.id) },
                    onLongClick = { mediaActionHandler.show(MediaTarget.PlaylistTarget(playlist)) }
                )
            }
        }
    }
}
