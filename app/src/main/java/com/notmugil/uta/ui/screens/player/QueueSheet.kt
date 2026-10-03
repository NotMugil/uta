package com.notmugil.uta.ui.screens.player

import com.notmugil.uta.ui.shared.AppToastManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.notmugil.uta.R
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.domain.model.QueueItem
import com.notmugil.uta.ui.UtaAppEntryPoint
import com.notmugil.uta.ui.shared.ActionConfirmDialog
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.PlayingAnimatedEqualizer
import com.notmugil.uta.util.Formatters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<QueueItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onItemClick: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onClearQueue: () -> Unit,
    onUndo: (() -> Unit)? = null,
    onSaveAsPlaylist: () -> Unit = {},
    onSaveQueueAsPlaylist: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showClearQueueConfirmDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        scrollThreshold = 56.dp
    ) { from, to ->
        onMoveItem(from.index, to.index)
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex in queue.indices) {
            try {
                listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
            } catch (_: Exception) {}
        }
    }

    val currentTrack = queue.getOrNull(currentIndex)?.track
    val ambientCoverArtId = currentTrack?.coverArtId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (ambientCoverArtId != null) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    CoverArtImage(
                        coverArtId = ambientCoverArtId,
                        contentDescription = null,
                        size = 300.dp,
                        shape = RoundedCornerShape(0.dp),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(50.dp)
                            .alpha(0.55f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.20f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.70f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.ArrowLeft,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.queue_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (queue.isEmpty()) stringResource(R.string.queue_empty) else stringResource(R.string.playlist_songs_count, queue.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (queue.isNotEmpty()) {
                        IconButton(
                            onClick = { showPlaylistPicker = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.PlaylistAdd,
                                contentDescription = stringResource(R.string.action_add_queue_to_playlist),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { showClearQueueConfirmDialog = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.Trash,
                                contentDescription = stringResource(R.string.action_clear_queue),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }
                }

                if (queue.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.queue_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 4.dp,
                                bottom = 24.dp
                            ),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(queue, key = { _, item -> item.entryId }) { index, item ->
                                ReorderableItem(reorderableLazyListState, key = item.entryId) { isDragging ->
                                    val isCurrent = index == currentIndex
                                    val accentColor = MaterialTheme.colorScheme.primary

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isDragging) {
                                                    accentColor.copy(alpha = 0.35f)
                                                } else if (isCurrent) {
                                                    accentColor.copy(alpha = 0.18f)
                                                } else {
                                                    Color.Transparent
                                                }
                                            )
                                            .clickable {
                                                if (!isDragging) {
                                                    onItemClick(index)
                                                }
                                            }
                                            .padding(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier
                                                .draggableHandle()
                                                .size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Tabler.Outline.GripVertical,
                                                contentDescription = stringResource(R.string.queue_drag_reorder_cd),
                                                tint = if (isDragging) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CoverArtImage(
                                                coverArtId = item.track.coverArtId,
                                                contentDescription = item.track.title,
                                                size = 44.dp,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            if (isCurrent) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color.Black.copy(alpha = 0.52f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    PlayingAnimatedEqualizer(
                                                        color = Color.White,
                                                        isPlaying = isPlaying,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.track.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = if (isCurrent) Modifier.basicMarquee() else Modifier
                                            )
                                            Text(
                                                text = if (item.track.album.isNullOrBlank()) item.track.artist else "${item.track.artist} • ${item.track.album}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isCurrent) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = if (isCurrent) Modifier.basicMarquee() else Modifier
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = Formatters.formatDurationSeconds(item.track.durationSeconds),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))

                                        IconButton(
                                            onClick = {
                                                onRemoveItem(index)
                                                if (onUndo != null) {
                                                    AppToastManager.showQueue(
                                                        title = context.getString(R.string.queue_removed_track),
                                                        subtitle = item.track.title,
                                                        coverArtId = item.track.coverArtId,
                                                        actionLabel = context.getString(R.string.action_undo),
                                                        onAction = onUndo
                                                    )
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Tabler.Outline.X,
                                                contentDescription = stringResource(R.string.action_remove_from_queue),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val topScrollAlpha by animateFloatAsState(
                            targetValue = if (reorderableLazyListState.isAnyItemDragging) 1f else 0f,
                            animationSpec = tween(200),
                            label = "top_scroll_gradient_alpha"
                        )
                        val bottomScrollAlpha by animateFloatAsState(
                            targetValue = if (reorderableLazyListState.isAnyItemDragging) 1f else 0f,
                            animationSpec = tween(200),
                            label = "bottom_scroll_gradient_alpha"
                        )

                        if (topScrollAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .graphicsLayer { alpha = topScrollAlpha }
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                Color.Transparent
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.ChevronUp,
                                    contentDescription = stringResource(R.string.action_scroll_up),
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f),
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(22.dp)
                                )
                            }
                        }

                        if (bottomScrollAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .graphicsLayer { alpha = bottomScrollAlpha }
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.ChevronDown,
                                    contentDescription = stringResource(R.string.action_scroll_down),
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f),
                                    modifier = Modifier
                                        .padding(bottom = 4.dp)
                                        .size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearQueueConfirmDialog) {
        ActionConfirmDialog(
            title = stringResource(R.string.queue_clear_confirm_title),
            message = stringResource(R.string.queue_clear_confirm_message),
            confirmText = stringResource(R.string.action_clear),
            dismissText = stringResource(R.string.action_cancel),
            isDestructive = true,
            icon = Tabler.Outline.Trash,
            onConfirm = {
                showClearQueueConfirmDialog = false
                onClearQueue()
                if (onUndo != null) {
                    AppToastManager.showQueue(
                        title = context.getString(R.string.queue_cleared),
                        actionLabel = context.getString(R.string.action_undo),
                        onAction = onUndo
                    )
                }
            },
            onDismiss = { showClearQueueConfirmDialog = false }
        )
    }

    if (showPlaylistPicker) {
        QueuePlaylistPickerSheet(
            queue = queue,
            onDismiss = { showPlaylistPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueuePlaylistPickerSheet(
    queue: List<QueueItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val libraryRepository = remember(context) {
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, UtaAppEntryPoint::class.java).libraryRepository()
        }.getOrNull()
    }

    val allPlaylists by (libraryRepository?.getPlaylistsFlow()?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) })
    val editablePlaylists = remember(allPlaylists) {
        allPlaylists.filter { it.canEditTracks }
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = pickerSheetState,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.58f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Tabler.Outline.ArrowLeft,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_add_queue_to_playlist),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                newPlaylistName = ""
                                showCreatePlaylistDialog = true
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.Plus,
                                contentDescription = stringResource(R.string.playlist_new),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.playlist_new),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.queue_create_playlist_subtitle, queue.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (editablePlaylists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.playlists_empty_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        editablePlaylists.forEach { playlist ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val trackIds = queue.map { it.track.id }
                                            val res = libraryRepository?.addTracksToPlaylist(playlist.id, trackIds)
                                            if (res?.isSuccess == true) {
                                                AppToastManager.showPlaylist(
                                                    title = context.getString(R.string.toast_added_to_named_playlist, playlist.name),
                                                    subtitle = context.getString(R.string.playlist_songs_count, trackIds.size),
                                                    coverArtId = playlist.coverArtId,
                                                    playlistId = playlist.id
                                                )
                                            } else {
                                                AppToastManager.showError(context.getString(R.string.failed_to_add_to_playlist))
                                            }
                                            onDismiss()
                                        }
                                    }
                                    .padding(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                CoverArtImage(
                                    coverArtId = playlist.coverArtId,
                                    playlistId = playlist.id,
                                    contentDescription = playlist.name,
                                    size = 42.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.playlist_songs_count, playlist.songCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_close),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        ActionConfirmDialog(
            title = stringResource(R.string.playlist_new),
            confirmText = stringResource(R.string.action_create_and_add),
            dismissText = stringResource(R.string.action_cancel),
            confirmEnabled = newPlaylistName.trim().isNotBlank(),
            onConfirm = {
                scope.launch {
                    val trackIds = queue.map { it.track.id }
                    val result = libraryRepository?.createPlaylist(newPlaylistName.trim(), trackIds)
                    if (result?.isSuccess == true) {
                        AppToastManager.showSuccess(
                            message = context.getString(R.string.toast_created_playlist, newPlaylistName.trim()),
                            subtitle = context.getString(R.string.playlist_songs_count, trackIds.size)
                        )
                    } else {
                        AppToastManager.showError(context.getString(R.string.toast_failed_create_playlist))
                    }
                    showCreatePlaylistDialog = false
                    onDismiss()
                }
            },
            onDismiss = { showCreatePlaylistDialog = false },
            content = {
                TextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.playlist_name_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}
