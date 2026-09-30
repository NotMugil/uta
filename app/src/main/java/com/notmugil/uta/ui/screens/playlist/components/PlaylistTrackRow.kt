package com.notmugil.uta.ui.screens.playlist.components

import com.notmugil.uta.ui.shared.AppToastManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.R
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.domain.model.ViewDisplayMode
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.PlayingAnimatedEqualizer
import com.notmugil.uta.util.Formatters

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistTrackRow(
    track: TrackItem,
    index: Int,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    isUnavailableOffline: Boolean,
    viewOption: ViewDisplayMode,
    isLastItem: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alpha = if (isUnavailableOffline) 0.38f else 1f
    val offlineWarningMessage = stringResource(R.string.track_not_downloaded_toast)

    when (viewOption) {
        ViewDisplayMode.LIST -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .fillMaxWidth()
                    .alpha(alpha)
                    .combinedClickable(
                        onClick = {
                            if (isUnavailableOffline) {
                                AppToastManager.showWarning(
                                    message = offlineWarningMessage,
                                    subtitle = track.title,
                                    coverArtId = track.coverArtId
                                )
                            } else {
                                onClick()
                            }
                        },
                        onLongClick = onOptionsClick
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CoverArtImage(
                        coverArtId = if (isUnavailableOffline) null else track.coverArtId,
                        contentDescription = track.title,
                        size = 46.dp,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isCurrentSong) {
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
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    val sub = listOfNotNull(
                        track.artist.takeIf { it.isNotBlank() },
                        track.album?.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }

                if (track.durationSeconds > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Formatters.formatDurationSeconds(track.durationSeconds),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Tabler.Outline.DotsVertical,
                        contentDescription = stringResource(R.string.more_actions_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        ViewDisplayMode.TEXT_ONLY -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .alpha(alpha)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (isUnavailableOffline) {
                                    AppToastManager.showWarning(
                                        message = offlineWarningMessage,
                                        subtitle = track.title,
                                        coverArtId = track.coverArtId
                                    )
                                } else {
                                    onClick()
                                }
                            },
                            onLongClick = onOptionsClick
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (isCurrentSong) {
                        PlayingAnimatedEqualizer(
                            color = MaterialTheme.colorScheme.primary,
                            isPlaying = isPlaying,
                            modifier = Modifier.size(width = 16.dp, height = 14.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                            fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        val sub = listOfNotNull(
                            track.artist.takeIf { it.isNotBlank() },
                            track.album?.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")
                        if (sub.isNotBlank()) {
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }

                    if (track.durationSeconds > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Formatters.formatDurationSeconds(track.durationSeconds),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onOptionsClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.DotsVertical,
                            contentDescription = stringResource(R.string.more_actions_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (!isLastItem) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}
