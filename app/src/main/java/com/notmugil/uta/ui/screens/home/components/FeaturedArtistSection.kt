package com.notmugil.uta.ui.screens.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.MediaCard
import com.notmugil.uta.ui.shared.SongListItem
import com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturedArtistSection(
    artist: ArtistItem,
    albums: List<AlbumItem>,
    songs: List<TrackItem>,
    currentTrack: TrackItem?,
    isPlaying: Boolean,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onPlaySong: (TrackItem, List<TrackItem>) -> Unit,
    modifier: Modifier = Modifier,
    downloadedAlbumIds: Set<String> = emptySet(),
    downloadedTrackIds: Set<String> = emptySet(),
    isOffline: Boolean = false
) {
    if (albums.isEmpty() && songs.isEmpty()) return
    val mediaActionHandler = LocalMediaActionHandler.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { onNavigateToArtist(artist.id) },
                        onLongClick = { mediaActionHandler.show(MediaTarget.ArtistTarget(artist)) }
                    )
            ) {
                CoverArtImage(
                    coverArtId = artist.coverArtId,
                    contentDescription = artist.name,
                    size = 48.dp,
                    shape = CircleShape,
                    fallbackIcon = Tabler.Outline.User,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.featured_artist_more_from),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = { onNavigateToArtist(artist.id) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Tabler.Outline.ChevronRight,
                    contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_see_all),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (albums.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(albums, key = { it.id }) { album ->
                    val isDownloaded = downloadedAlbumIds.contains(album.id)
                    val alpha = if (!isOffline || isDownloaded) 1f else 0.38f
                    MediaCard(
                        title = album.title,
                        subtitle = album.artist,
                        coverArtId = album.coverArtId,
                        onClick = { onNavigateToAlbum(album.id) },
                        onLongClick = { mediaActionHandler.show(MediaTarget.AlbumTarget(album)) },
                        alpha = alpha
                    )
                }
            }
        } else if (songs.isNotEmpty()) {
            songs.forEach { song ->
                val isCurrent = currentTrack?.id == song.id
                val isDownloaded = downloadedTrackIds.contains(song.id)
                SongListItem(
                    song = song,
                    isCurrentSong = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    isDownloaded = isDownloaded,
                    isOffline = isOffline,
                    onClick = { onPlaySong(song, songs) },
                    onLongClick = { mediaActionHandler.show(MediaTarget.TrackTarget(song)) }
                )
            }
        }
    }
}
