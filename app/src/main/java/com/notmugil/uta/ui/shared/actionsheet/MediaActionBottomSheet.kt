package com.notmugil.uta.ui.shared.actionsheet

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.R
import com.notmugil.uta.data.db.LocalMediaEntity
import com.notmugil.uta.data.download.DownloadEstimator
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.data.preferences.DownloadQualityPreference
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.data.repository.LibraryRepository
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.PlaybackController
import com.notmugil.uta.ui.shared.ActionConfirmDialog
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.LocalToastHostState
import com.notmugil.uta.ui.shared.ToastType
import com.notmugil.uta.util.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaActionBottomSheet(
    state: MediaActionState,
    playbackController: PlaybackController,
    libraryRepository: LibraryRepository,
    offlineDownloadManager: OfflineDownloadManager,
    isDownloaded: Boolean,
    isOffline: Boolean = false,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToInfo: (type: String, id: String) -> Unit = { _, _ -> },
    onShowPlaylistPicker: () -> Unit = {},
    onNavigateToEditPlaylist: ((String) -> Unit)? = null,
    onPlaylistDeleted: ((String) -> Unit)? = null
) {
    val target = state.currentTarget ?: return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val toastHostState = LocalToastHostState.current

    var showRatingDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDownloadConfirmDialog by remember { mutableStateOf(false) }
    var showRemoveDownloadConfirmDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var localMediaRecord by remember { mutableStateOf<LocalMediaEntity?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    var isDownloadedState by remember(target, isDownloaded) { mutableStateOf(isDownloaded) }

    var isStarredState by remember(target) {
        mutableStateOf(
            when (target) {
                is MediaTarget.TrackTarget -> target.track.isStarred
                is MediaTarget.AlbumTarget -> target.album.isStarred
                is MediaTarget.ArtistTarget -> target.artist.isStarred
                is MediaTarget.PlaylistTarget -> false
            }
        )
    }

    var ratingState by remember(target) {
        mutableIntStateOf(
            when (target) {
                is MediaTarget.TrackTarget -> target.track.userRating ?: 0
                is MediaTarget.AlbumTarget -> target.album.userRating ?: 0
                else -> 0
            }
        )
    }

    LaunchedEffect(target, isDownloadedState) {
        if (target is MediaTarget.TrackTarget && isDownloadedState) {
            localMediaRecord = offlineDownloadManager.getLocalMedia(target.track.id)
        } else {
            localMediaRecord = null
        }
    }

    val allPlaylists by libraryRepository.getPlaylistsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val editablePlaylists = remember(allPlaylists) {
        allPlaylists.filter { it.canEditTracks }
    }

    val appPreferences = LocalAppPreferences.current
    val downloadQuality by (appPreferences?.downloadQuality ?: remember { MutableStateFlow(DownloadQualityPreference.BITRATE_192) }).collectAsStateWithLifecycle()
    val downloadedTrackIdsList by offlineDownloadManager.getDownloadedTrackIdsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadedTrackIds = remember(downloadedTrackIdsList) { downloadedTrackIdsList.toSet() }

    val albumTracks by if (target is MediaTarget.AlbumTarget) {
        libraryRepository.getTracksForAlbumFlow(target.album.id).collectAsStateWithLifecycle(initialValue = emptyList())
    } else {
        remember { MutableStateFlow(emptyList<TrackItem>()) }.collectAsStateWithLifecycle()
    }

    val playlistTracks by if (target is MediaTarget.PlaylistTarget) {
        libraryRepository.getTracksForPlaylistFlow(target.playlist.id).collectAsStateWithLifecycle(initialValue = emptyList())
    } else {
        remember { MutableStateFlow(emptyList<TrackItem>()) }.collectAsStateWithLifecycle()
    }

    val cancelString = stringResource(R.string.action_cancel)
    val deleteString = stringResource(R.string.action_delete)
    val downloadString = stringResource(R.string.action_download)
    val removeString = stringResource(R.string.action_remove)

    if (showCreatePlaylistDialog) {
        ActionConfirmDialog(
            title = stringResource(R.string.library_new_playlist),
            confirmText = stringResource(R.string.library_create_playlist),
            dismissText = cancelString,
            confirmEnabled = newPlaylistName.trim().isNotBlank(),
            onConfirm = {
                val cleanName = newPlaylistName.trim()
                if (cleanName.isNotEmpty()) {
                    val trackId = (target as? MediaTarget.TrackTarget)?.track?.id
                    coroutineScope.launch {
                        val result = libraryRepository.createPlaylist(
                            name = cleanName,
                            songIds = if (trackId != null) listOf(trackId) else emptyList()
                        )
                        if (result.isSuccess) {
                            toastHostState.showToast(context.getString(R.string.toast_created_playlist, cleanName), ToastType.SUCCESS)
                        } else {
                            toastHostState.showToast(context.getString(R.string.toast_failed_create_playlist), ToastType.ERROR)
                        }
                        showCreatePlaylistDialog = false
                        state.dismiss()
                    }
                }
            },
            onDismiss = { showCreatePlaylistDialog = false },
            content = {
                TextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.library_playlist_name),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    if (showDeleteConfirmDialog && target is MediaTarget.PlaylistTarget) {
        ActionConfirmDialog(
            title = stringResource(R.string.playlist_delete_confirm_title),
            message = stringResource(R.string.playlist_delete_confirm_message, target.playlist.name),
            confirmText = deleteString,
            dismissText = cancelString,
            isDestructive = true,
            onConfirm = {
                val playlistId = target.playlist.id
                coroutineScope.launch {
                    try {
                        libraryRepository.deletePlaylist(playlistId)
                        toastHostState.showToast(context.getString(R.string.toast_playlist_deleted), ToastType.SUCCESS)
                        onPlaylistDeleted?.invoke(playlistId)
                    } catch (e: Exception) {
                        toastHostState.showToast(context.getString(R.string.failed_to_delete_format, e.message.orEmpty()), ToastType.ERROR)
                    } finally {
                        showDeleteConfirmDialog = false
                        state.dismiss()
                    }
                }
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    if (showRatingDialog && target !is MediaTarget.PlaylistTarget) {
        var selectedRating by remember(ratingState) { mutableIntStateOf(if (ratingState > 0) ratingState else 5) }
        ActionConfirmDialog(
            title = stringResource(R.string.action_sheet_rate_title),
            confirmText = stringResource(R.string.dialog_ok),
            dismissText = cancelString,
            onConfirm = {
                val newRating = selectedRating
                ratingState = newRating
                coroutineScope.launch {
                    try {
                        val (id, type) = when (target) {
                            is MediaTarget.TrackTarget -> Pair(target.track.id, "TRACK")
                            is MediaTarget.AlbumTarget -> Pair(target.album.id, "ALBUM")
                            is MediaTarget.ArtistTarget -> Pair(target.artist.id, "ARTIST")
                            is MediaTarget.PlaylistTarget -> return@launch
                        }
                        libraryRepository.setRating(id, type, newRating)
                        toastHostState.showToast(context.getString(R.string.toast_rating_set, newRating), ToastType.SUCCESS)
                    } catch (e: Exception) {
                        toastHostState.showToast(context.getString(R.string.toast_failed_rate), ToastType.ERROR)
                    } finally {
                        showRatingDialog = false
                    }
                }
            },
            onDismiss = { showRatingDialog = false },
            content = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val title = when (target) {
                        is MediaTarget.TrackTarget -> target.track.title
                        is MediaTarget.AlbumTarget -> target.album.title
                        is MediaTarget.ArtistTarget -> target.artist.name
                        is MediaTarget.PlaylistTarget -> ""
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            IconButton(onClick = { selectedRating = i }) {
                                Icon(
                                    imageVector = Tabler.Filled.Star,
                                    contentDescription = stringResource(R.string.rating_stars_cd, i),
                                    tint = if (i <= selectedRating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    val performDownload: () -> Unit = {
        coroutineScope.launch {
            try {
                when (target) {
                    is MediaTarget.TrackTarget -> {
                        offlineDownloadManager.enqueueTrack(target.track)
                        isDownloadedState = true
                        toastHostState.showToast(context.getString(R.string.toast_downloading_track, target.track.title), ToastType.INFO, Tabler.Outline.Download)
                    }
                    is MediaTarget.AlbumTarget -> {
                        var tracks = libraryRepository.getTracksForAlbumFlow(target.album.id).firstOrNull()
                        if (tracks.isNullOrEmpty()) {
                            val res = libraryRepository.fetchAlbumTracks(target.album.id)
                            tracks = res.getOrNull()
                        }
                        if (!tracks.isNullOrEmpty()) {
                            offlineDownloadManager.enqueueTracks(tracks, scopeId = target.album.id, scopeType = "ALBUM")
                            isDownloadedState = true
                            val nonDownloaded = tracks.filter { it.id !in downloadedTrackIds }
                            val count = nonDownloaded.size.takeIf { it > 0 } ?: tracks.size
                            toastHostState.showToast(context.getString(R.string.toast_downloading_album, count), ToastType.INFO, Tabler.Outline.Download)
                        } else {
                            toastHostState.showToast(context.getString(R.string.toast_no_tracks_download), ToastType.ERROR)
                        }
                    }
                    is MediaTarget.PlaylistTarget -> {
                        var tracks = libraryRepository.getTracksForPlaylistFlow(target.playlist.id).firstOrNull()
                        if (tracks.isNullOrEmpty()) {
                            val res = libraryRepository.fetchPlaylistTracks(target.playlist.id)
                            tracks = res.getOrNull()
                        }
                        if (!tracks.isNullOrEmpty()) {
                            offlineDownloadManager.enqueueTracks(tracks, scopeId = target.playlist.id, scopeType = "PLAYLIST")
                            isDownloadedState = true
                            val nonDownloaded = tracks.filter { it.id !in downloadedTrackIds }
                            val count = nonDownloaded.size.takeIf { it > 0 } ?: tracks.size
                            toastHostState.showToast(context.getString(R.string.toast_downloading_playlist, count), ToastType.INFO, Tabler.Outline.Download)
                        } else {
                            toastHostState.showToast(context.getString(R.string.toast_no_tracks_download), ToastType.ERROR)
                        }
                    }
                    is MediaTarget.ArtistTarget -> {}
                }
            } catch (e: Exception) {
                toastHostState.showToast(context.getString(R.string.toast_download_failed, e.message.orEmpty()), ToastType.ERROR)
            }
        }
    }

    if (showDownloadConfirmDialog && (target is MediaTarget.AlbumTarget || target is MediaTarget.PlaylistTarget)) {
        val (title, message) = when (target) {
            is MediaTarget.AlbumTarget -> {
                val nonDownloaded = if (albumTracks.isNotEmpty()) {
                    albumTracks.filter { it.id !in downloadedTrackIds }
                } else {
                    emptyList()
                }
                val nonDownloadedCount = if (nonDownloaded.isNotEmpty()) {
                    nonDownloaded.size
                } else if (albumTracks.isEmpty()) {
                    target.album.songCount
                } else {
                    0
                }
                val estimatedBytes = DownloadEstimator.calculateEstimatedSizeBytes(
                    tracks = nonDownloaded,
                    quality = downloadQuality,
                    fallbackSongCount = nonDownloadedCount
                )
                val sizeStr = Formatters.formatBytes(estimatedBytes)
                val tracksStr = if (nonDownloadedCount == 1) {
                    stringResource(R.string.action_track_single_format)
                } else {
                    stringResource(R.string.action_tracks_count_format, nonDownloadedCount)
                }
                Pair(
                    stringResource(R.string.action_sheet_download_album_title),
                    stringResource(
                        R.string.action_sheet_download_album_msg,
                        target.album.title,
                        tracksStr,
                        sizeStr,
                        downloadQuality.displayName
                    )
                )
            }
            is MediaTarget.PlaylistTarget -> {
                val nonDownloaded = if (playlistTracks.isNotEmpty()) {
                    playlistTracks.filter { it.id !in downloadedTrackIds }
                } else {
                    emptyList()
                }
                val nonDownloadedCount = if (nonDownloaded.isNotEmpty()) {
                    nonDownloaded.size
                } else if (playlistTracks.isEmpty()) {
                    target.playlist.songCount
                } else {
                    0
                }
                val estimatedBytes = DownloadEstimator.calculateEstimatedSizeBytes(
                    tracks = nonDownloaded,
                    quality = downloadQuality,
                    fallbackSongCount = nonDownloadedCount
                )
                val sizeStr = Formatters.formatBytes(estimatedBytes)
                val tracksStr = if (nonDownloadedCount == 1) {
                    stringResource(R.string.action_track_single_format)
                } else {
                    stringResource(R.string.action_tracks_count_format, nonDownloadedCount)
                }
                Pair(
                    stringResource(R.string.action_sheet_download_playlist_title),
                    stringResource(
                        R.string.action_sheet_download_playlist_msg,
                        target.playlist.name,
                        tracksStr,
                        sizeStr,
                        downloadQuality.displayName
                    )
                )
            }
            is MediaTarget.TrackTarget, is MediaTarget.ArtistTarget -> Pair("", "")
        }
        ActionConfirmDialog(
            title = title,
            message = message,
            confirmText = downloadString,
            dismissText = cancelString,
            icon = Tabler.Outline.Download,
            onConfirm = {
                showDownloadConfirmDialog = false
                performDownload()
            },
            onDismiss = { showDownloadConfirmDialog = false }
        )
    }

    if (showRemoveDownloadConfirmDialog) {
        val (title, message) = when (target) {
            is MediaTarget.TrackTarget -> {
                val estimatedBytes = if (target.track.bitRate != null && target.track.bitRate > 0) {
                    (target.track.bitRate * 1000L / 8L) * target.track.durationSeconds
                } else {
                    DownloadEstimator.calculateEstimatedSizeBytes(
                        tracks = listOf(target.track),
                        quality = downloadQuality
                    )
                }
                val sizeStr = Formatters.formatBytes(estimatedBytes)
                Pair(
                    stringResource(R.string.action_sheet_remove_download_confirm_title),
                    stringResource(R.string.action_sheet_remove_download_track_msg, target.track.title, sizeStr)
                )
            }
            is MediaTarget.AlbumTarget -> {
                val downloadedTracks = if (albumTracks.isNotEmpty()) {
                    albumTracks.filter { it.id in downloadedTrackIds }
                } else {
                    emptyList()
                }
                val downloadedCount = if (downloadedTracks.isNotEmpty()) {
                    downloadedTracks.size
                } else if (albumTracks.isEmpty()) {
                    target.album.songCount
                } else {
                    0
                }
                val estimatedBytes = DownloadEstimator.calculateEstimatedSizeBytes(
                    tracks = downloadedTracks,
                    quality = downloadQuality,
                    fallbackSongCount = downloadedCount
                )
                val sizeStr = Formatters.formatBytes(estimatedBytes)
                val tracksStr = if (downloadedCount == 1) {
                    stringResource(R.string.action_track_single_format)
                } else {
                    stringResource(R.string.action_tracks_count_format, downloadedCount)
                }
                Pair(
                    stringResource(R.string.action_sheet_remove_download_confirm_title),
                    stringResource(
                        R.string.action_sheet_remove_download_album_msg,
                        target.album.title,
                        tracksStr,
                        sizeStr
                    )
                )
            }
            is MediaTarget.PlaylistTarget -> {
                val downloadedTracks = if (playlistTracks.isNotEmpty()) {
                    playlistTracks.filter { it.id in downloadedTrackIds }
                } else {
                    emptyList()
                }
                val downloadedCount = if (downloadedTracks.isNotEmpty()) {
                    downloadedTracks.size
                } else if (playlistTracks.isEmpty()) {
                    target.playlist.songCount
                } else {
                    0
                }
                val estimatedBytes = DownloadEstimator.calculateEstimatedSizeBytes(
                    tracks = downloadedTracks,
                    quality = downloadQuality,
                    fallbackSongCount = downloadedCount
                )
                val sizeStr = Formatters.formatBytes(estimatedBytes)
                val tracksStr = if (downloadedCount == 1) {
                    stringResource(R.string.action_track_single_format)
                } else {
                    stringResource(R.string.action_tracks_count_format, downloadedCount)
                }
                Pair(
                    stringResource(R.string.action_sheet_remove_download_confirm_title),
                    stringResource(
                        R.string.action_sheet_remove_download_playlist_msg,
                        target.playlist.name,
                        tracksStr,
                        sizeStr
                    )
                )
            }
            is MediaTarget.ArtistTarget -> Pair("", "")
        }
        ActionConfirmDialog(
            title = title,
            message = message,
            confirmText = removeString,
            dismissText = cancelString,
            isDestructive = true,
            icon = Tabler.Outline.DownloadOff,
            onConfirm = {
                showRemoveDownloadConfirmDialog = false
                coroutineScope.launch {
                    try {
                        when (target) {
                            is MediaTarget.TrackTarget -> {
                                offlineDownloadManager.deleteDownloadedTrack(target.track.id)
                            }
                            is MediaTarget.AlbumTarget -> {
                                offlineDownloadManager.deleteDownloadedScope(target.album.id)
                            }
                            is MediaTarget.PlaylistTarget -> {
                                offlineDownloadManager.deleteDownloadedScope(target.playlist.id)
                            }
                            is MediaTarget.ArtistTarget -> {}
                        }
                        isDownloadedState = false
                        toastHostState.showToast(context.getString(R.string.toast_removed_from_downloads), ToastType.SUCCESS, Tabler.Outline.DownloadOff)
                    } catch (e: Exception) {
                        toastHostState.showToast(context.getString(R.string.toast_download_failed, e.message.orEmpty()), ToastType.ERROR)
                    }
                }
            },
            onDismiss = { showRemoveDownloadConfirmDialog = false }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val handleToggleFavorite: () -> Unit = {
        val newStarred = !isStarredState
        isStarredState = newStarred
        coroutineScope.launch {
            try {
                when (target) {
                    is MediaTarget.TrackTarget -> libraryRepository.setTrackStarred(target.track.id, newStarred)
                    is MediaTarget.AlbumTarget -> libraryRepository.setAlbumStarred(target.album.id, newStarred)
                    is MediaTarget.ArtistTarget -> libraryRepository.setArtistStarred(target.artist.id, newStarred)
                    is MediaTarget.PlaylistTarget -> {}
                }
                toastHostState.showToast(
                    if (newStarred) context.getString(R.string.toast_added_to_favorites) else context.getString(R.string.toast_removed_from_favorites),
                    ToastType.SUCCESS,
                    if (newStarred) Tabler.Filled.Heart else Tabler.Outline.HeartBroken
                )
            } catch (e: Exception) {
                toastHostState.showToast(context.getString(R.string.toast_failed_rate), ToastType.ERROR)
            }
        }
    }

    val handleDownload: () -> Unit = {
        if (isOffline) {
            toastHostState.showToast(context.getString(R.string.toast_cannot_download_offline), ToastType.ERROR)
        } else if (target is MediaTarget.AlbumTarget || target is MediaTarget.PlaylistTarget) {
            showDownloadConfirmDialog = true
        } else {
            performDownload()
        }
    }

    val chooserTitle = stringResource(R.string.share_chooser_title)
    val handleShare: () -> Unit = {
        coroutineScope.launch {
            val shareText = when (target) {
                is MediaTarget.TrackTarget -> libraryRepository.getShareContent("TRACK", target.track.id, target.track.title, target.track.artist)
                is MediaTarget.AlbumTarget -> libraryRepository.getShareContent("ALBUM", target.album.id, target.album.title, target.album.artist)
                is MediaTarget.ArtistTarget -> libraryRepository.getShareContent("ARTIST", target.artist.id, target.artist.name, null)
                is MediaTarget.PlaylistTarget -> libraryRepository.getShareContent("PLAYLIST", target.playlist.id, target.playlist.name, null)
            }
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
            state.dismiss()
        }
    }

    val handleOpenInfo: () -> Unit = {
        val (mediaType, mediaId) = when (target) {
            is MediaTarget.TrackTarget -> Pair("TRACK", target.track.id)
            is MediaTarget.AlbumTarget -> Pair("ALBUM", target.album.id)
            is MediaTarget.ArtistTarget -> Pair("ARTIST", target.artist.id)
            is MediaTarget.PlaylistTarget -> Pair("PLAYLIST", target.playlist.id)
        }
        onNavigateToInfo(mediaType, mediaId)
        state.dismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { state.dismiss() },
        sheetState = sheetState,
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
                if (showPlaylistPicker) {
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
                            IconButton(onClick = { showPlaylistPicker = false }) {
                                Icon(
                                    imageVector = Tabler.Outline.ArrowLeft,
                                    contentDescription = stringResource(R.string.content_desc_back)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_add_to_playlist),
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
                                    contentDescription = stringResource(R.string.library_new_playlist),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.library_new_playlist),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.playlist_create_new_desc),
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
                                    text = stringResource(R.string.library_empty_playlists),
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
                                            val trackId = (target as? MediaTarget.TrackTarget)?.track?.id
                                            coroutineScope.launch {
                                                if (trackId != null) {
                                                    val res = libraryRepository.addTracksToPlaylist(playlist.id, listOf(trackId))
                                                    if (res.isSuccess) {
                                                        toastHostState.showToast(context.getString(R.string.toast_added_to_named_playlist, playlist.name), ToastType.SUCCESS)
                                                    } else {
                                                        toastHostState.showToast(context.getString(R.string.failed_to_add_to_playlist), ToastType.ERROR)
                                                    }
                                                }
                                                state.dismiss()
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
                                            modifier = Modifier.basicMarquee()
                                        )
                                        Text(
                                            text = stringResource(R.string.info_songs_count_format, playlist.songCount),
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
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        MediaActionHeader(
                            target = target,
                            rating = ratingState,
                            onRateClick = if (target is MediaTarget.TrackTarget || target is MediaTarget.AlbumTarget) {
                                { showRatingDialog = true }
                            } else null,
                            onShare = handleShare,
                            onInfoClick = handleOpenInfo
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        Spacer(modifier = Modifier.height(4.dp))

                        MediaActionList(
                            target = target,
                            coroutineScope = coroutineScope,
                            playbackController = playbackController,
                            libraryRepository = libraryRepository,
                            offlineDownloadManager = offlineDownloadManager,
                            isDownloaded = isDownloadedState,
                            isOffline = isOffline,
                            isStarred = isStarredState,
                            onToggleFavorite = handleToggleFavorite,
                            onDownload = handleDownload,
                            onShowRemoveDownloadConfirmDialog = { showRemoveDownloadConfirmDialog = true },
                            onNavigateToArtist = onNavigateToArtist,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onShowPlaylistSheet = {
                                if (onShowPlaylistPicker != {}) {
                                    onShowPlaylistPicker()
                                }
                                showPlaylistPicker = true
                            },
                            onShowEditPlaylistDialog = {
                                if (target is MediaTarget.PlaylistTarget) {
                                    onNavigateToEditPlaylist?.invoke(target.playlist.id)
                                }
                                state.dismiss()
                            },
                            onShowDeleteConfirmDialog = { showDeleteConfirmDialog = true },
                            onDismiss = { state.dismiss() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { state.dismiss() },
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
}
