package com.notmugil.uta.ui.screens.playlist

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.R
import com.notmugil.uta.data.PlaylistCoverManager
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.shared.ActionConfirmDialog
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.FullScreenLoader
import com.notmugil.uta.ui.shared.ImageCropDialog
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun EditPlaylistScreen(
    onNavigateBack: () -> Unit,
    onPlaylistDeleted: () -> Unit = onNavigateBack,
    modifier: Modifier = Modifier,
    viewModel: EditPlaylistViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCoverMenu by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            showCropDialog = true
        }
    }

    if (showCropDialog && selectedImageUri != null) {
        ImageCropDialog(
            imageUri = selectedImageUri!!,
            onDismiss = { showCropDialog = false },
            onCropDone = { croppedBitmap ->
                viewModel.setPendingCroppedBitmap(croppedBitmap)
                showCropDialog = false
            }
        )
    }

    val customCoverFile = remember(viewModel.playlistId) {
        PlaylistCoverManager.getCustomCoverFile(context, viewModel.playlistId)
    }

    val ambientCoverArtId = remember(state.playlist, viewModel.tracks) {
        state.playlist?.coverArtId ?: viewModel.tracks.firstOrNull { !it.track.coverArtId.isNullOrBlank() }?.track?.coverArtId
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isLoading) {
            FullScreenLoader(onNavigateBack = onNavigateBack)
            return@Box
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (state.pendingCroppedBitmap != null) {
                Image(
                    bitmap = state.pendingCroppedBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.35f)
                        .blur(50.dp)
                        .alpha(0.5f)
                )
            } else if (state.isCoverRemoved) {
            } else if (customCoverFile != null) {
                CoverArtImage(
                    playlistId = viewModel.playlistId,
                    contentDescription = null,
                    size = 1000.dp,
                    shape = RoundedCornerShape(0.dp),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.35f)
                        .blur(50.dp)
                        .alpha(0.5f)
                )
            } else if (!ambientCoverArtId.isNullOrBlank()) {
                CoverArtImage(
                    coverArtId = ambientCoverArtId,
                    contentDescription = null,
                    size = 1000.dp,
                    shape = RoundedCornerShape(0.dp),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.35f)
                        .blur(50.dp)
                        .alpha(0.5f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        val canEditTracks = state.playlist?.canEditTracks ?: true

        val listState = rememberLazyListState()
        val isScrolled by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20
            }
        }

        var displayedCount by remember(viewModel.tracks.size) { mutableIntStateOf(50) }
        val shouldLoadMore by remember(viewModel.tracks.size, displayedCount) {
            derivedStateOf {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = listState.layoutInfo.totalItemsCount
                lastVisible >= total - 15 && displayedCount < viewModel.tracks.size
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) {
                displayedCount = (displayedCount + 50).coerceAtMost(viewModel.tracks.size)
            }
        }

        val reorderableLazyListState = rememberReorderableLazyListState(
            lazyListState = listState,
            scrollThreshold = 56.dp
        ) { from, to ->
            if (!canEditTracks || viewModel.tracks.isEmpty()) return@rememberReorderableLazyListState
            val fromIdx = (from.index - 1).coerceIn(0, viewModel.tracks.lastIndex)
            val toIdx = (to.index - 1).coerceIn(0, viewModel.tracks.lastIndex)
            if (fromIdx != toIdx) {
                viewModel.moveTrack(fromIdx, toIdx)
            }
        }

        val displayedTracks = remember(viewModel.tracks.toList(), displayedCount, canEditTracks) {
            if (canEditTracks) viewModel.tracks.take(displayedCount) else viewModel.tracks
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = if (isScrolled) MaterialTheme.colorScheme.background.copy(alpha = 0.85f) else Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !state.isSaving && !state.isDeleting
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.ArrowLeft,
                            contentDescription = stringResource(R.string.content_desc_back)
                        )
                    }

                    Text(
                        text = stringResource(R.string.playlist_edit_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            viewModel.savePlaylist(onSuccess = onNavigateBack)
                        },
                        enabled = state.name.trim().isNotEmpty() && !state.isSaving && !state.isDeleting,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.action_save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "edit_playlist_header") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { showCoverMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.pendingCroppedBitmap != null) {
                                Image(
                                    bitmap = state.pendingCroppedBitmap!!.asImageBitmap(),
                                    contentDescription = stringResource(R.string.edit_playlist_cover_cd),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (state.isCoverRemoved) {
                                Icon(
                                    imageVector = Tabler.Outline.Playlist,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                                )
                            } else {
                                CoverArtImage(
                                    coverArtId = state.playlist?.coverArtId,
                                    playlistId = viewModel.playlistId,
                                    contentDescription = state.playlist?.name,
                                    size = 160.dp,
                                    shape = RoundedCornerShape(22.dp),
                                    fallbackIcon = Tabler.Outline.Playlist,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Tabler.Outline.Pencil,
                                            contentDescription = stringResource(R.string.edit_playlist_edit_cover_cd),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showCoverMenu,
                                onDismissRequest = { showCoverMenu = false },
                                modifier = Modifier
                                    .widthIn(min = 160.dp, max = 220.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.edit_playlist_choose_image),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        showCoverMenu = false
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                )
                                val hasExistingCover = state.pendingCroppedBitmap != null ||
                                    (!state.isCoverRemoved && (customCoverFile != null || !state.playlist?.coverArtId.isNullOrBlank()))
                                if (hasExistingCover) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        thickness = 0.5.dp
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.edit_playlist_remove_image),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showCoverMenu = false
                                            viewModel.removeCover()
                                        },
                                        modifier = Modifier.height(38.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedTextField(
                            value = state.name,
                            onValueChange = { viewModel.setName(it) },
                            label = { Text(stringResource(R.string.library_playlist_name)) },
                            singleLine = true,
                            isError = state.name.trim().isEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = state.comment,
                            onValueChange = { viewModel.setComment(it) },
                            label = { Text(stringResource(R.string.edit_playlist_description_label)) },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Tabler.Outline.Lock,
                                        contentDescription = stringResource(R.string.edit_playlist_private),
                                        tint = if (!state.isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = state.isPublic,
                                        onCheckedChange = { viewModel.setIsPublic(it) },
                                        modifier = Modifier.scale(0.82f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Tabler.Outline.Eye,
                                        contentDescription = stringResource(R.string.edit_playlist_public),
                                        tint = if (state.isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(20.dp),
                                enabled = !state.isSaving && !state.isDeleting,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.Trash,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_delete),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (state.errorMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = state.errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (state.playlist?.isSmart == true) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.edit_playlist_auto_gen),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center
                            )
                        } else if (state.playlist?.isSync == true) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.edit_playlist_auto_gen_desc),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center
                            )
                        }

                        if (state.playlist?.isSmart != true && viewModel.tracks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.edit_playlist_songs_count, viewModel.tracks.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val subtitleText = when {
                                    !canEditTracks && state.playlist?.isSync == true -> stringResource(R.string.edit_playlist_auto_imported)
                                    !canEditTracks -> stringResource(R.string.edit_playlist_read_only)
                                    else -> stringResource(R.string.edit_playlist_swipe_drag_reorder)
                                }
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!canEditTracks) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (state.playlist?.isSmart != true) {
                    if (canEditTracks) {
                        itemsIndexed(
                            items = displayedTracks,
                            key = { _, entry -> entry.editId }
                        ) { index, entry ->
                            ReorderableItem(reorderableLazyListState, key = entry.editId) { isDragging ->
                                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "edit_drag_elevation")

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                ) {
                                    EditPlaylistTrackSwipeItem(
                                        entry = entry,
                                        index = index,
                                        isDragging = isDragging,
                                        canEdit = true,
                                        onRemove = {
                                            val curIdx = viewModel.tracks.indexOf(entry)
                                            if (curIdx != -1) {
                                                viewModel.removeTrack(curIdx)
                                            }
                                        },
                                        dragHandleModifier = Modifier.draggableHandle()
                                    )

                                    if (index < displayedTracks.size - 1) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                            thickness = 0.5.dp,
                                            modifier = Modifier.padding(horizontal = 20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = displayedTracks,
                            key = { _, entry -> entry.editId }
                        ) { index, entry ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                EditPlaylistTrackSwipeItem(
                                    entry = entry,
                                    index = index,
                                    isDragging = false,
                                    canEdit = false,
                                    onRemove = {},
                                    dragHandleModifier = null
                                )

                                if (index < displayedTracks.size - 1) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            ActionConfirmDialog(
                title = stringResource(R.string.playlist_delete_confirm_title),
                message = stringResource(R.string.playlist_delete_confirm_message, state.playlist?.name.orEmpty()),
                confirmText = stringResource(R.string.action_delete),
                dismissText = stringResource(R.string.action_cancel),
                isDestructive = true,
                onConfirm = {
                    viewModel.deletePlaylist(onDeleted = {
                        showDeleteConfirm = false
                        onPlaylistDeleted()
                    })
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPlaylistTrackSwipeItem(
    entry: EditPlaylistTrackEntry,
    index: Int,
    isDragging: Boolean,
    canEdit: Boolean,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier? = Modifier,
    modifier: Modifier = Modifier
) {
    val track = entry.track
    val accentColor = MaterialTheme.colorScheme.primary
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart && canEdit) {
            onRemove()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canEdit,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        backgroundContent = {
            if (!canEdit) return@SwipeToDismissBox
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "edit_swipe_bg_color"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.action_remove),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Tabler.Outline.Trash,
                            contentDescription = stringResource(R.string.action_remove),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isDragging) {
                        accentColor.copy(alpha = 0.35f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            if (canEdit && dragHandleModifier != null) {
                IconButton(
                    onClick = {},
                    modifier = dragHandleModifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Tabler.Outline.GripVertical,
                        contentDescription = stringResource(R.string.playlist_reorder),
                        tint = if (isDragging) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            } else {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(28.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                CoverArtImage(
                    coverArtId = track.coverArtId,
                    contentDescription = track.title,
                    size = 44.dp,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = if (track.album.isNullOrBlank()) track.artist else "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }

            if (canEdit) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Tabler.Outline.X,
                        contentDescription = stringResource(R.string.action_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
