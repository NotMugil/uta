package com.notmugil.uta.ui.shared.actionsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.R
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.util.Formatters

@Composable
fun MediaActionHeader(
    target: MediaTarget,
    rating: Int = 0,
    onRateClick: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        when (target) {
            is MediaTarget.TrackTarget -> {
                CoverArtImage(
                    coverArtId = target.track.coverArtId,
                    contentDescription = target.track.title,
                    size = 52.dp,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(52.dp)
                )
            }
            is MediaTarget.AlbumTarget -> {
                CoverArtImage(
                    coverArtId = target.album.coverArtId,
                    contentDescription = target.album.title,
                    size = 52.dp,
                    shape = RoundedCornerShape(10.dp),
                    fallbackIcon = Tabler.Outline.Disc,
                    modifier = Modifier.size(52.dp)
                )
            }
            is MediaTarget.PlaylistTarget -> {
                CoverArtImage(
                    coverArtId = target.playlist.coverArtId,
                    contentDescription = target.playlist.name,
                    size = 52.dp,
                    shape = RoundedCornerShape(10.dp),
                    fallbackIcon = Tabler.Outline.Playlist,
                    modifier = Modifier.size(52.dp)
                )
            }
            is MediaTarget.ArtistTarget -> {
                CoverArtImage(
                    coverArtId = target.artist.coverArtId,
                    contentDescription = target.artist.name,
                    size = 52.dp,
                    shape = CircleShape,
                    fallbackIcon = Tabler.Outline.User,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            when (target) {
                is MediaTarget.TrackTarget -> {
                    Text(
                        text = target.track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    val subtitle = listOfNotNull(
                        target.track.artist.takeIf { it.isNotBlank() },
                        target.track.album?.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
                is MediaTarget.AlbumTarget -> {
                    Text(
                        text = target.album.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    val sub = buildString {
                        if (target.album.artist.isNotBlank()) append(target.album.artist)
                        target.album.year?.takeIf { it > 0 }?.let {
                            if (isNotEmpty()) append(" • ")
                            append(it)
                        }
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
                is MediaTarget.PlaylistTarget -> {
                    Text(
                        text = target.playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = stringResource(R.string.action_tracks_count_format, target.playlist.songCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
                is MediaTarget.ArtistTarget -> {
                    Text(
                        text = target.artist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    if (target.artist.albumCount > 0) {
                        Text(
                            text = stringResource(R.string.info_albums_count_format, target.artist.albumCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (onRateClick != null && (target is MediaTarget.TrackTarget || target is MediaTarget.AlbumTarget)) {
                if (rating > 0) {
                    Surface(
                        onClick = onRateClick,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Tabler.Filled.Star,
                                contentDescription = stringResource(R.string.rating_val_cd, rating),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = rating.toString(),
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onRateClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.Star,
                            contentDescription = stringResource(R.string.action_rate),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (onShare != null) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Tabler.Outline.Share2,
                        contentDescription = stringResource(R.string.action_share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (onInfoClick != null) {
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Tabler.Outline.InfoCircle,
                        contentDescription = stringResource(R.string.action_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
