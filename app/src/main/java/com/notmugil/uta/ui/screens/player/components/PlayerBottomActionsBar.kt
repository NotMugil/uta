package com.notmugil.uta.ui.screens.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.*
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.notmugil.uta.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlayerBottomActionsBar(
    showFavorite: Boolean,
    isStarred: Boolean,
    onOpenSleepTimer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    showLyrics: Boolean = true,
    showDownload: Boolean = true,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float? = null,
    onToggleDownload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenSleepTimer) {
            Icon(
                imageVector = Tabler.Outline.Moon,
                contentDescription = stringResource(R.string.sleep_timer),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showDownload) {
            IconButton(onClick = onToggleDownload) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress ?: 0.1f },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = if (isDownloaded) Tabler.Outline.CircleCheck else Tabler.Outline.Download,
                        contentDescription = if (isDownloaded) stringResource(R.string.download_status_completed) else stringResource(R.string.action_download),
                        tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showFavorite) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isStarred) Tabler.Filled.Heart else Tabler.Outline.Heart,
                    contentDescription = if (isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
                    tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showLyrics) {
            IconButton(onClick = onOpenLyrics) {
                Icon(
                    imageVector = Tabler.Outline.Microphone2,
                    contentDescription = stringResource(R.string.lyrics),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onOpenQueue) {
            Icon(
                imageVector = Tabler.Outline.Playlist,
                contentDescription = stringResource(R.string.queue_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

