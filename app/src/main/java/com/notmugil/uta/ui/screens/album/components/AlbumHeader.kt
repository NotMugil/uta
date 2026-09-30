package com.notmugil.uta.ui.screens.album.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.notmugil.uta.ui.shared.ActionConfirmDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.R
import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.shared.AnimatedAlbumArtView
import com.notmugil.uta.util.Formatters

@Composable
fun AlbumHeader(
    album: AlbumItem,
    playableTracks: List<TrackItem>,
    allDownloaded: Boolean,
    isDownloading: Boolean,
    isOffline: Boolean,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onToggleDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDownloadConfirmDialog by remember { mutableStateOf(false) }
    var showRemoveDownloadConfirmDialog by remember { mutableStateOf(false) }
    var showOfflineModeAlertDialog by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(240.dp)
                .aspectRatio(1f)
                .shadow(16.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
        ) {
            AnimatedAlbumArtView(
                album = album,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Tabler.Outline.Heart,
                    contentDescription = stringResource(if (album.isStarred) R.string.starred_cd else R.string.not_starred_cd),
                    tint = if (album.isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )

                if (album.artist.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (album.artistId != null) {
                            Modifier.clickable { onNavigateToArtist(album.artistId) }
                        } else {
                            Modifier
                        }
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                val totalDuration = playableTracks.sumOf { it.durationSeconds }
                val durationStr = if (totalDuration > 0) Formatters.formatHumanDuration(totalDuration) else ""
                val tracksStr = if (playableTracks.isNotEmpty()) {
                    stringResource(R.string.album_duration_format, playableTracks.size, durationStr)
                } else if (album.songCount > 0) {
                    stringResource(R.string.album_tracks_count, album.songCount)
                } else null

                val detailsText = listOfNotNull(
                    album.year?.toString(),
                    album.genre?.takeIf { it.isNotBlank() },
                    tracksStr
                ).joinToString(" • ")

                if (detailsText.isNotBlank()) {
                    Text(
                        text = detailsText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = {
                    if (isDownloading) {
                        onToggleDownload()
                    } else if (allDownloaded) {
                        showRemoveDownloadConfirmDialog = true
                    } else if (isOffline) {
                        showOfflineModeAlertDialog = true
                    } else {
                        showDownloadConfirmDialog = true
                    }
                },
                modifier = Modifier.size(44.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (allDownloaded) {
                    Icon(
                        imageVector = Tabler.Outline.CircleCheck,
                        contentDescription = stringResource(R.string.download_status_completed),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = Tabler.Outline.Download,
                        contentDescription = stringResource(R.string.action_sheet_download_album_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (showOfflineModeAlertDialog) {
            ActionConfirmDialog(
                title = stringResource(R.string.offline_mode_active_title),
                message = stringResource(R.string.offline_mode_active_msg),
                confirmText = stringResource(R.string.dialog_ok),
                dismissText = null,
                onConfirm = { showOfflineModeAlertDialog = false },
                onDismiss = { showOfflineModeAlertDialog = false }
            )
        }

        if (showDownloadConfirmDialog && !isOffline) {
            val estimatedSizeBytes = remember(playableTracks) {
                playableTracks.sumOf { s ->
                    s.durationSeconds * 40000L
                }.coerceAtLeast(10_000_000L)
            }
            val estimatedSizeStr = Formatters.formatBytes(estimatedSizeBytes)
            ActionConfirmDialog(
                title = stringResource(R.string.action_sheet_download_album_title),
                message = stringResource(R.string.action_sheet_download_album_msg, album.title, playableTracks.size, estimatedSizeStr),
                confirmText = stringResource(R.string.action_download),
                dismissText = stringResource(R.string.action_cancel),
                onConfirm = {
                    showDownloadConfirmDialog = false
                    onToggleDownload()
                },
                onDismiss = { showDownloadConfirmDialog = false }
            )
        }

        if (showRemoveDownloadConfirmDialog) {
            val estimatedSizeBytes = remember(playableTracks) {
                playableTracks.sumOf { s ->
                    s.durationSeconds * 40000L
                }.coerceAtLeast(10_000_000L)
            }
            val estimatedSizeStr = Formatters.formatBytes(estimatedSizeBytes)
            ActionConfirmDialog(
                title = stringResource(R.string.action_sheet_remove_download_confirm_title),
                message = stringResource(R.string.action_sheet_remove_download_album_msg, album.title, estimatedSizeStr),
                confirmText = stringResource(R.string.action_remove),
                dismissText = stringResource(R.string.action_cancel),
                isDestructive = true,
                onConfirm = {
                    showRemoveDownloadConfirmDialog = false
                    onToggleDownload()
                },
                onDismiss = { showRemoveDownloadConfirmDialog = false }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onPlayAll,
                enabled = playableTracks.isNotEmpty(),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Tabler.Filled.PlayerPlay,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_play),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            FilledTonalButton(
                onClick = onShuffleAll,
                enabled = playableTracks.isNotEmpty(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Tabler.Outline.ArrowsShuffle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_shuffle),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}
