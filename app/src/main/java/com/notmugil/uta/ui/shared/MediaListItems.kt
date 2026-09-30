package com.notmugil.uta.ui.shared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notmugil.uta.R
import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.util.Formatters

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: TrackItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isCurrentSong: Boolean = false,
    isPlaying: Boolean = false,
    showPlayingIndicator: Boolean = true,
    isDownloaded: Boolean = false,
    isOffline: Boolean = false,
    showCoverArt: Boolean = true,
    showAlbumName: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    onDownloadClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val isPlayable = !isOffline || isDownloaded
    val alpha = if (isPlayable) 1f else 0.38f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .combinedClickable(
                enabled = isPlayable,
                onClick = onClick,
                onLongClick = onLongClick ?: onMoreClick
            )
            .padding(contentPadding)
    ) {
        if (showCoverArt) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CoverArtImage(
                    coverArtId = if (isOffline && !isDownloaded) null else song.coverArtId,
                    contentDescription = song.title,
                    size = 48.dp,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxSize()
                )
                if (isCurrentSong && showPlayingIndicator) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.52f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingAnimatedEqualizer(
                            color = Color.White,
                            isPlaying = isPlaying,
                            modifier = Modifier.size(width = 18.dp, height = 18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
        } else if (isCurrentSong && showPlayingIndicator) {
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 24.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                PlayingAnimatedEqualizer(
                    color = MaterialTheme.colorScheme.primary,
                    isPlaying = isPlaying,
                    modifier = Modifier.size(width = 16.dp, height = 16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            val sub = if (showAlbumName) {
                listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.album.takeIf { !it.isNullOrBlank() })
                    .joinToString(" • ")
            } else {
                song.artist.takeIf { it.isNotBlank() }.orEmpty()
            }
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
        }

        if (song.durationSeconds > 0) {
            val min = song.durationSeconds / 60
            val sec = song.durationSeconds % 60
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "%d:%02d".format(min, sec),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (onDownloadClick != null && (!isOffline || isDownloaded)) {
            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier.size(40.dp)
            ) {
                if (isDownloaded) {
                    Icon(
                        imageVector = Tabler.Outline.CircleCheck,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Tabler.Outline.Download,
                        contentDescription = "Download Song",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (onMoreClick != null) {
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Tabler.Outline.DotsVertical,
                    contentDescription = "More actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumListItem(
    album: AlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    isOffline: Boolean = false
) {
    val isPlayable = !isOffline || isDownloaded
    val alpha = if (isPlayable) 1f else 0.38f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .combinedClickable(
                enabled = isPlayable,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        CoverArtImage(
            coverArtId = album.coverArtId,
            contentDescription = album.title,
            size = 52.dp,
            fallbackIcon = Tabler.Outline.Disc,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(52.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            val subText = buildString {
                append(album.artist)
                val year = album.year
                if (year != null && year > 0) {
                    append(" • $year")
                }
                if (album.songCount > 0) {
                    append(" • ${album.songCount} songs")
                }
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistListItem(
    artist: ArtistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isOffline: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        CoverArtImage(
            coverArtId = artist.coverArtId,
            contentDescription = artist.name,
            size = 52.dp,
            fallbackIcon = Tabler.Outline.User,
            shape = CircleShape,
            modifier = Modifier.size(52.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Text(
                text = if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistListItem(
    playlist: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    isOffline: Boolean = false
) {
    val isPlayable = !isOffline || isDownloaded
    val alpha = if (isPlayable) 1f else 0.38f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .combinedClickable(
                enabled = isPlayable,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        CoverArtImage(
            coverArtId = playlist.coverArtId,
            playlistId = playlist.id,
            contentDescription = playlist.name,
            size = 52.dp,
            fallbackIcon = Tabler.Outline.Playlist,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(52.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            val subText = buildString {
                append("${playlist.songCount} tracks")
                if (playlist.durationSeconds > 0) {
                    append(" • ${Formatters.formatHumanDuration(playlist.durationSeconds)}")
                }
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
