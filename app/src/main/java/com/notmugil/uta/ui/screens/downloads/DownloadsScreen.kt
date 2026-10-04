package com.notmugil.uta.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.data.download.DownloadStatus
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.screens.downloads.components.DownloadedTrackRow
import com.notmugil.uta.ui.screens.downloads.components.DownloadingTrackRow
import com.notmugil.uta.ui.screens.downloads.components.DownloadsEmptyState
import com.notmugil.uta.ui.screens.downloads.components.DownloadsHeader
import com.notmugil.uta.ui.shared.ActionConfirmDialog
import com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget

@Composable
fun DownloadsScreen(
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
    currentTrack: TrackItem? = null,
    isPlaying: Boolean = false
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mediaActionHandler = LocalMediaActionHandler.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    var trackToDelete by remember { mutableStateOf<TrackItem?>(null) }

    val hasDownloadedItems = state.downloadedTracks.isNotEmpty() || state.activeQueue.isNotEmpty()

    val activeTrackTasks = remember(state.activeQueue, state.searchQuery) {
        val nonCompleted = state.activeQueue.filter { it.status !is DownloadStatus.Completed }
        if (state.searchQuery.isBlank()) {
            nonCompleted
        } else {
            val q = state.searchQuery.trim().lowercase()
            nonCompleted.filter { task ->
                task.track.title.lowercase().contains(q) ||
                    task.track.artist.lowercase().contains(q) ||
                    task.track.album?.lowercase()?.contains(q) == true
            }
        }
    }

    val downloadingIds = remember(state.activeQueue) {
        state.activeQueue
            .filter { it.status !is DownloadStatus.Completed }
            .map { it.track.id }
            .toSet()
    }

    val completedTracks = remember(state.downloadedTracks, downloadingIds, state.searchQuery) {
        val filtered = state.downloadedTracks.filterNot { it.track.id in downloadingIds }
        if (state.searchQuery.isBlank()) {
            filtered
        } else {
            val q = state.searchQuery.trim().lowercase()
            filtered.filter { info ->
                info.track.title.lowercase().contains(q) ||
                    info.track.artist.lowercase().contains(q) ||
                    info.track.album?.lowercase()?.contains(q) == true
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                DownloadsHeader(
                    hasDownloadedItems = hasDownloadedItems,
                    storageStats = state.storageStats,
                    onBack = onNavigateBack,
                    onClearAll = { viewModel.clearAllDownloads() }
                )

                TextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_search_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Tabler.Outline.Search,
                            contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_search)
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Tabler.Outline.X,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_clear)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (completedTracks.isEmpty() && activeTrackTasks.isEmpty()) {
                if (state.searchQuery.isNotBlank()) {
                    DownloadsEmptyState(
                        title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_no_matching_title),
                        subtitle = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_no_matching_subtitle)
                    )
                } else {
                    DownloadsEmptyState(
                        title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_empty_title),
                        subtitle = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_empty_subtitle)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 168.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "tracks_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val totalCount = completedTracks.size + activeTrackTasks.size
                            val downloadingSuffix = if (activeTrackTasks.isNotEmpty()) {
                                androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_downloading_count, activeTrackTasks.size)
                            } else ""
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_tracks_count, totalCount) + downloadingSuffix,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (completedTracks.isNotEmpty()) {
                                FilledTonalButton(
                                    onClick = {
                                        val playable = completedTracks.map { it.track }
                                        viewModel.playAll(playable)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Tabler.Filled.PlayerPlay,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_play_all), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    items(activeTrackTasks, key = { "task_${it.track.id}" }) { task ->
                        DownloadingTrackRow(
                            task = task,
                            onCancel = { viewModel.cancelTask(task.track.id) }
                        )
                    }

                    items(completedTracks, key = { "track_${it.track.id}" }) { info ->
                        val isCurrentTrack = currentTrack?.id == info.track.id
                        DownloadedTrackRow(
                            info = info,
                            isCurrentTrack = isCurrentTrack,
                            isPlaying = isPlaying,
                            onClick = {
                                val allTracks = completedTracks.map { it.track }
                                viewModel.playTrack(info.track, allTracks)
                            },
                            onDelete = { trackToDelete = info.track },
                            onMoreClick = { mediaActionHandler.show(MediaTarget.TrackTarget(info.track)) }
                        )
                    }
                }
            }
        }
    }

    if (trackToDelete != null) {
        val targetTrack = trackToDelete!!
        val downloadedItem = state.downloadedTracks.firstOrNull { it.track.id == targetTrack.id }
        val sizeBytes = if (downloadedItem != null && downloadedItem.fileSizeBytes > 0L) {
            downloadedItem.fileSizeBytes
        } else if (targetTrack.bitRate != null && targetTrack.bitRate > 0) {
            (targetTrack.bitRate * 1000L / 8L) * targetTrack.durationSeconds
        } else {
            0L
        }
        val sizeStr = if (sizeBytes > 0L) com.notmugil.uta.util.Formatters.formatBytes(sizeBytes) else null
        val msg = if (sizeStr != null) {
            androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_sheet_remove_download_track_msg, targetTrack.title, sizeStr)
        } else {
            androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_delete_confirm_msg, targetTrack.title)
        }
        ActionConfirmDialog(
            title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_sheet_remove_download_confirm_title),
            message = msg,
            confirmText = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_delete),
            dismissText = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                viewModel.deleteTrack(targetTrack.id)
                trackToDelete = null
            },
            onDismiss = { trackToDelete = null }
        )
    }
}
