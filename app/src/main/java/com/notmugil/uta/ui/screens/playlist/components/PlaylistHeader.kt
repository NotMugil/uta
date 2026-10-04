package com.notmugil.uta.ui.screens.playlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.R
import com.notmugil.uta.data.download.DownloadEstimator
import com.notmugil.uta.data.preferences.DownloadQualityPreference
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.util.Formatters

@Composable
fun PlaylistHeader(
    playlist: PlaylistItem,
    tracks: List<TrackItem>,
    playableTracks: List<TrackItem>,
    allDownloaded: Boolean,
    isDownloading: Boolean,
    isOffline: Boolean,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onToggleDownload: () -> Unit,
    onEditPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    downloadedTrackIds: Set<String> = emptySet()
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

        PlaylistFanCoverArt(
            playlist = playlist,
            tracks = tracks,
            modifier = Modifier
                .fillMaxWidth()
                .height(245.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onEditPlaylist,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Tabler.Outline.Pencil,
                    contentDescription = stringResource(R.string.playlist_edit_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (playlist.isSmart) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.edit_playlist_auto_gen),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                } else if (playlist.isSync) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.edit_playlist_auto_imported),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                if (!playlist.comment.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playlist.comment,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                val totalDuration = tracks.sumOf { it.durationSeconds }
                val durationStr = if (totalDuration > 0) Formatters.formatHumanDuration(totalDuration) else ""
                val countText = if (tracks.isNotEmpty()) {
                    stringResource(R.string.album_duration_format, tracks.size, durationStr)
                } else {
                    stringResource(R.string.playlist_songs_count, playlist.songCount)
                }

                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                modifier = Modifier.size(40.dp)
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
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Tabler.Outline.Download,
                        contentDescription = stringResource(R.string.action_sheet_download_playlist_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
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

        val appPreferences = LocalAppPreferences.current
        val downloadQuality = appPreferences?.downloadQuality?.collectAsStateWithLifecycle()?.value
            ?: DownloadQualityPreference.BITRATE_192

        if (showDownloadConfirmDialog && !isOffline) {
            val nonDownloadedTracks = remember(tracks, downloadedTrackIds) {
                if (tracks.isNotEmpty()) {
                    tracks.filter { it.id !in downloadedTrackIds }
                } else {
                    emptyList()
                }
            }
            val nonDownloadedCount = if (nonDownloadedTracks.isNotEmpty()) {
                nonDownloadedTracks.size
            } else if (tracks.isEmpty()) {
                playlist.songCount
            } else {
                0
            }
            val estimatedSizeBytes = remember(nonDownloadedTracks, downloadQuality, nonDownloadedCount) {
                DownloadEstimator.calculateEstimatedSizeBytes(
                    tracks = nonDownloadedTracks,
                    quality = downloadQuality,
                    fallbackSongCount = nonDownloadedCount
                )
            }
            val estimatedSizeStr = Formatters.formatBytes(estimatedSizeBytes)
            val tracksStr = if (nonDownloadedCount == 1) {
                stringResource(R.string.action_track_single_format)
            } else {
                stringResource(R.string.action_tracks_count_format, nonDownloadedCount)
            }
            ActionConfirmDialog(
                title = stringResource(R.string.action_sheet_download_playlist_title),
                message = stringResource(
                    R.string.action_sheet_download_playlist_msg,
                    playlist.name,
                    tracksStr,
                    estimatedSizeStr,
                    downloadQuality.displayName
                ),
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
            val downloadedTracks = remember(tracks, downloadedTrackIds) {
                if (tracks.isNotEmpty()) {
                    tracks.filter { it.id in downloadedTrackIds }
                } else {
                    playableTracks
                }
            }
            val downloadedCount = if (downloadedTracks.isNotEmpty()) {
                downloadedTracks.size
            } else if (tracks.isEmpty()) {
                playlist.songCount
            } else {
                0
            }
            val estimatedSizeBytes = remember(downloadedTracks, downloadQuality, downloadedCount) {
                DownloadEstimator.calculateEstimatedSizeBytes(
                    tracks = downloadedTracks,
                    quality = downloadQuality,
                    fallbackSongCount = downloadedCount
                )
            }
            val estimatedSizeStr = Formatters.formatBytes(estimatedSizeBytes)
            val tracksStr = if (downloadedCount == 1) {
                stringResource(R.string.action_track_single_format)
            } else {
                stringResource(R.string.action_tracks_count_format, downloadedCount)
            }
            ActionConfirmDialog(
                title = stringResource(R.string.action_sheet_remove_download_confirm_title),
                message = stringResource(
                    R.string.action_sheet_remove_download_playlist_msg,
                    playlist.name,
                    tracksStr,
                    estimatedSizeStr
                ),
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
