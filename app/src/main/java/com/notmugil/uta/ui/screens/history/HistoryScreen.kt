package com.notmugil.uta.ui.screens.history

import com.notmugil.uta.ui.shared.AppToastManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.notmugil.uta.ui.shared.ActionConfirmDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.ui.screens.history.components.HistorySwipeItem
import com.notmugil.uta.ui.shared.AppTopBar
import com.notmugil.uta.ui.shared.SongListItem
import com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mediaActionState = LocalMediaActionHandler.current
    var showClearConfirm by remember { mutableStateOf(false) }

    val historyClearedToast = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.history_cleared)
    val addedToQueueToast = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.toast_added_to_queue)
    val removedFromHistoryToast = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.history_removed_toast)

    if (showClearConfirm) {
        ClearHistoryDialog(
            onConfirm = {
                viewModel.clearHistory()
                AppToastManager.showSuccess(historyClearedToast)
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.history_title),
                containerColor = Color.Transparent,
                onNavigateBack = onNavigateBack,
                actions = {
                    if (!state.isEmpty) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                imageVector = Tabler.Outline.Trash,
                                contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.history_clear_all),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (state.isEmpty) {
            EmptyHistoryContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 168.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                state.sections.forEach { section ->
                    item(key = "header_${section.title}") {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 6.dp
                            )
                        )
                    }
                    items(
                        items = section.entries,
                        key = { it.id }
                    ) { entry ->
                        HistorySwipeItem(
                            onAddToQueue = {
                                viewModel.addToQueue(entry)
                                AppToastManager.showQueue(
                                    title = addedToQueueToast,
                                    subtitle = entry.track.title,
                                    coverArtId = entry.track.coverArtId
                                )
                            },
                            onDelete = {
                                viewModel.removeEntry(entry.id)
                                AppToastManager.showSuccess(
                                    message = removedFromHistoryToast,
                                    subtitle = entry.track.title,
                                    coverArtId = entry.track.coverArtId
                                )
                            }
                        ) {
                            val isDownloaded = state.downloadedTrackIds.contains(entry.track.id)
                            SongListItem(
                                song = entry.track,
                                isOffline = state.isOfflineModeActive,
                                isDownloaded = isDownloaded,
                                isCurrentSong = state.currentPlayingTrackId == entry.track.id,
                                isPlaying = state.isPlaying,
                                showPlayingIndicator = false,
                                showCoverArt = !state.isOfflineModeActive,
                                showAlbumName = false,
                                onClick = { viewModel.playEntry(entry) },
                                onLongClick = {
                                    mediaActionState.show(
                                        MediaTarget.TrackTarget(entry.track)
                                    )
                                },
                                onMoreClick = {
                                    mediaActionState.show(
                                        MediaTarget.TrackTarget(entry.track)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Tabler.Outline.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.history_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.history_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ClearHistoryDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ActionConfirmDialog(
        title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.history_clear_confirm_title),
        message = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.history_clear_confirm_message),
        confirmText = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_confirm),
        dismissText = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_cancel),
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
