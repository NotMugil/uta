package com.notmugil.uta.ui.screens.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.R
import com.notmugil.uta.data.db.LocalMediaEntity
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.data.repository.LibraryRepository
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.FullScreenLoader
import com.notmugil.uta.ui.shared.LocalToastHostState
import com.notmugil.uta.ui.shared.ToastType
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget
import com.notmugil.uta.util.Formatters
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MediaInfoScreen(
    type: String,
    id: String,
    libraryRepository: LibraryRepository,
    offlineDownloadManager: OfflineDownloadManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val toastHostState = LocalToastHostState.current

    var target by remember { mutableStateOf<MediaTarget?>(null) }
    var localMediaRecord by remember { mutableStateOf<LocalMediaEntity?>(null) }
    var isDownloaded by remember { mutableStateOf(false) }

    LaunchedEffect(type, id) {
        when (type.uppercase()) {
            "TRACK" -> {
                val track = libraryRepository.getTrack(id)
                if (track != null) {
                    target = MediaTarget.TrackTarget(track)
                    val local = offlineDownloadManager.getLocalMedia(track.id)
                    localMediaRecord = local
                    isDownloaded = local != null
                }
            }
            "ALBUM" -> {
                val album = libraryRepository.getAlbum(id)
                if (album != null) {
                    target = MediaTarget.AlbumTarget(album)
                    val tracksResult = libraryRepository.fetchAlbumTracks(album.id)
                    val tracks = tracksResult.getOrNull() ?: emptyList()
                    val downloadedCount = tracks.count { offlineDownloadManager.getLocalMedia(it.id) != null }
                    isDownloaded = tracks.isNotEmpty() && downloadedCount == tracks.size
                }
            }
            "PLAYLIST" -> {
                val playlist = libraryRepository.getPlaylist(id)
                if (playlist != null) {
                    target = MediaTarget.PlaylistTarget(playlist)
                    val tracksResult = libraryRepository.fetchPlaylistTracks(playlist.id)
                    val tracks = tracksResult.getOrNull() ?: emptyList()
                    val downloadedCount = tracks.count { offlineDownloadManager.getLocalMedia(it.id) != null }
                    isDownloaded = tracks.isNotEmpty() && downloadedCount == tracks.size
                }
            }
            "ARTIST" -> {
                val artist = libraryRepository.getArtist(id)
                if (artist != null) {
                    target = MediaTarget.ArtistTarget(artist)
                }
            }
        }
    }

    val downloadedFile = remember(localMediaRecord, offlineDownloadManager.musicRootDir) {
        localMediaRecord?.relativePath?.let { File(offlineDownloadManager.musicRootDir, it) }
    }

    val screenTitle = when (type.uppercase()) {
        "TRACK" -> stringResource(R.string.info_track_title)
        "ALBUM" -> stringResource(R.string.info_album_title)
        "PLAYLIST" -> stringResource(R.string.info_playlist_title)
        "ARTIST" -> stringResource(R.string.info_artist_title)
        else -> stringResource(R.string.info_title)
    }

    val scrollState = rememberScrollState()
    val fadeColor = MaterialTheme.colorScheme.background

    val currentTarget = target
    if (currentTarget == null) {
        FullScreenLoader(
            title = screenTitle,
            onNavigateBack = onNavigateBack,
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fadeColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(68.dp))

            val yesString = stringResource(R.string.info_yes)
            val noString = stringResource(R.string.info_no)
            val copiedToast = stringResource(R.string.info_copied)
            val copiedTitleToast = stringResource(R.string.info_copied_title)
            val copiedArtistToast = stringResource(R.string.info_copied_artist)
            val copiedAlbumToast = stringResource(R.string.info_copied_album)
            val copiedAlbumTitleToast = stringResource(R.string.info_copied_album_title)
            val copiedFileNameToast = stringResource(R.string.info_copied_filename)
            val copiedStoragePathToast = stringResource(R.string.info_copied_path)
            val copiedSongIdToast = stringResource(R.string.info_copied_song_id)
            val copiedAlbumIdToast = stringResource(R.string.info_copied_album_id)
            val copiedArtistIdToast = stringResource(R.string.info_copied_artist_id)
            val copiedPlaylistIdToast = stringResource(R.string.info_copied_playlist_id)

            MediaInfoHeader(target = currentTarget)

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 0.5.dp
                )

                when (currentTarget) {
                    is MediaTarget.TrackTarget -> {
                        val track = currentTarget.track

                        InfoRow(label = stringResource(R.string.info_title_label), value = track.title) {
                            clipboardManager.setText(AnnotatedString(track.title))
                            toastHostState.showToast(copiedTitleToast, ToastType.SUCCESS)
                        }
                        InfoRow(label = stringResource(R.string.info_artist_label), value = track.artist) {
                            clipboardManager.setText(AnnotatedString(track.artist))
                            toastHostState.showToast(copiedArtistToast, ToastType.SUCCESS)
                        }
                        track.album?.takeIf { it.isNotBlank() }?.let { albumTitle ->
                            InfoRow(label = stringResource(R.string.info_album_label), value = albumTitle) {
                                clipboardManager.setText(AnnotatedString(albumTitle))
                                toastHostState.showToast(copiedAlbumToast, ToastType.SUCCESS)
                            }
                        }
                        track.trackNumber?.let { InfoRow(label = stringResource(R.string.info_track_number_label), value = it.toString()) }
                        track.discNumber?.let { InfoRow(label = stringResource(R.string.info_disc_number_label), value = it.toString()) }
                        track.year?.takeIf { it > 0 }?.let { InfoRow(label = stringResource(R.string.info_year_label), value = it.toString()) }
                        track.genre?.takeIf { it.isNotBlank() }?.let { InfoRow(label = stringResource(R.string.info_genre_label), value = it) }
                        if (track.durationSeconds > 0) {
                            InfoRow(label = stringResource(R.string.info_duration_label), value = Formatters.formatDurationSeconds(track.durationSeconds))
                        }
                        track.bitRate?.takeIf { it > 0 }?.let { InfoRow(label = stringResource(R.string.info_bitrate_label), value = stringResource(R.string.info_kbps_format, it)) }
                        track.suffix?.takeIf { it.isNotBlank() }?.let { InfoRow(label = stringResource(R.string.info_format_label), value = it.uppercase()) }
                        track.createdAt?.takeIf { it > 0 }?.let { InfoRow(label = stringResource(R.string.info_date_added_label), value = formatFullDate(it)) }
                        track.playedAt?.takeIf { it > 0 }?.let { InfoRow(label = stringResource(R.string.info_last_played_label), value = formatFullDate(it)) }
                        track.playCount?.takeIf { it > 0 }?.let { InfoRow(label = stringResource(R.string.info_play_count_label), value = it.toString()) }
                        track.userRating?.takeIf { it > 0 }?.let { InfoRow(label = stringResource(R.string.info_user_rating_label), value = stringResource(R.string.info_rating_format, it)) }
                        InfoRow(label = stringResource(R.string.info_favourited_label), value = if (track.isStarred) yesString else noString)
                        InfoRow(label = stringResource(R.string.info_downloaded_label), value = if (isDownloaded) yesString else noString)

                        if (isDownloaded) {
                            val fileName = downloadedFile?.name ?: localMediaRecord?.relativePath ?: "${track.title}.mp3"
                            InfoRow(label = stringResource(R.string.info_file_name_label), value = fileName) {
                                clipboardManager.setText(AnnotatedString(fileName))
                                toastHostState.showToast(copiedFileNameToast, ToastType.SUCCESS)
                            }

                            val storagePath = downloadedFile?.absolutePath ?: localMediaRecord?.relativePath
                            if (!storagePath.isNullOrBlank()) {
                                InfoRow(label = stringResource(R.string.info_storage_path_label), value = storagePath, isMonospace = true) {
                                    clipboardManager.setText(AnnotatedString(storagePath))
                                    toastHostState.showToast(copiedStoragePathToast, ToastType.SUCCESS)
                                }
                            }

                            val downloadedSize = localMediaRecord?.fileSizeBytes?.takeIf { it > 0L }
                                ?: (downloadedFile?.length()?.takeIf { it > 0L } ?: 0L)
                            if (downloadedSize > 0L) {
                                InfoRow(label = stringResource(R.string.info_size_label), value = Formatters.formatBytes(downloadedSize))
                            }

                            val local = localMediaRecord
                            val quality = when {
                                local?.quality == "ORIGINAL" -> stringResource(R.string.info_quality_original_lossless)
                                local?.bitRate != null && local.bitRate > 0 -> stringResource(R.string.info_kbps_format, local.bitRate)
                                else -> stringResource(R.string.info_quality_original)
                            }
                            InfoRow(label = stringResource(R.string.info_download_quality_label), value = quality)
                        }

                        InfoRow(label = stringResource(R.string.info_song_id_label), value = track.id, isMonospace = true) {
                            clipboardManager.setText(AnnotatedString(track.id))
                            toastHostState.showToast(copiedSongIdToast, ToastType.SUCCESS)
                        }
                        track.albumId?.takeIf { it.isNotBlank() }?.let {
                            InfoRow(label = stringResource(R.string.info_album_id_label), value = it, isMonospace = true) {
                                clipboardManager.setText(AnnotatedString(it))
                                toastHostState.showToast(copiedAlbumIdToast, ToastType.SUCCESS)
                            }
                        }
                        track.artistId?.takeIf { it.isNotBlank() }?.let {
                            InfoRow(label = stringResource(R.string.info_artist_id_label), value = it, isMonospace = true) {
                                clipboardManager.setText(AnnotatedString(it))
                                toastHostState.showToast(copiedArtistIdToast, ToastType.SUCCESS)
                            }
                        }
                    }

                    is MediaTarget.AlbumTarget -> {
                        val album = currentTarget.album

                        InfoRow(label = stringResource(R.string.info_title_label), value = album.title) {
                            clipboardManager.setText(AnnotatedString(album.title))
                            toastHostState.showToast(copiedAlbumTitleToast, ToastType.SUCCESS)
                        }
                        if (album.artist.isNotBlank()) {
                            InfoRow(label = stringResource(R.string.info_album_artist_label), value = album.artist) {
                                clipboardManager.setText(AnnotatedString(album.artist))
                                toastHostState.showToast(copiedArtistToast, ToastType.SUCCESS)
                            }
                        }
                        album.year?.takeIf { it > 0 }?.let { InfoRow(label = stringResource(R.string.info_year_label), value = it.toString()) }
                        album.genre?.takeIf { it.isNotBlank() }?.let { InfoRow(label = stringResource(R.string.info_genre_label), value = it) }
                        if (album.songCount > 0) InfoRow(label = stringResource(R.string.info_tracks_count_label), value = album.songCount.toString())
                        if (album.durationSeconds > 0) InfoRow(label = stringResource(R.string.info_total_duration_label), value = Formatters.formatHumanDuration(album.durationSeconds))
                        InfoRow(label = stringResource(R.string.info_favourited_label), value = if (album.isStarred) yesString else noString)
                        InfoRow(label = stringResource(R.string.info_downloaded_label), value = if (isDownloaded) yesString else noString)
                        InfoRow(label = stringResource(R.string.info_album_id_label), value = album.id, isMonospace = true) {
                            clipboardManager.setText(AnnotatedString(album.id))
                            toastHostState.showToast(copiedAlbumIdToast, ToastType.SUCCESS)
                        }
                    }

                    is MediaTarget.PlaylistTarget -> {
                        val playlist = currentTarget.playlist

                        InfoRow(label = stringResource(R.string.info_title_label), value = playlist.name) {
                            clipboardManager.setText(AnnotatedString(playlist.name))
                            toastHostState.showToast(copiedToast, ToastType.SUCCESS)
                        }
                        playlist.comment?.takeIf { it.isNotBlank() }?.let { InfoRow(label = stringResource(R.string.info_comment_label), value = it) }
                        if (playlist.songCount > 0) InfoRow(label = stringResource(R.string.info_tracks_count_label), value = playlist.songCount.toString())
                        if (playlist.durationSeconds > 0) InfoRow(label = stringResource(R.string.info_duration_label), value = Formatters.formatHumanDuration(playlist.durationSeconds))
                        InfoRow(label = stringResource(R.string.info_public_label), value = if (playlist.isPublic) yesString else noString)
                        InfoRow(label = stringResource(R.string.info_smart_playlist_label), value = if (playlist.isSmart) yesString else noString)
                        val ownerYou = stringResource(R.string.info_owner_you)
                        val ownerOther = stringResource(R.string.info_owner_other)
                        InfoRow(label = stringResource(R.string.info_owner_label), value = if (playlist.isOwner) ownerYou else ownerOther)
                        InfoRow(label = stringResource(R.string.info_downloaded_label), value = if (isDownloaded) yesString else noString)
                        InfoRow(label = stringResource(R.string.info_playlist_id_label), value = playlist.id, isMonospace = true) {
                            clipboardManager.setText(AnnotatedString(playlist.id))
                            toastHostState.showToast(copiedPlaylistIdToast, ToastType.SUCCESS)
                        }
                    }

                    is MediaTarget.ArtistTarget -> {
                        val artist = currentTarget.artist

                        InfoRow(label = stringResource(R.string.info_artist_name_label), value = artist.name) {
                            clipboardManager.setText(AnnotatedString(artist.name))
                            toastHostState.showToast(copiedArtistToast, ToastType.SUCCESS)
                        }
                        if (artist.albumCount > 0) InfoRow(label = stringResource(R.string.artist_albums), value = artist.albumCount.toString())
                        InfoRow(label = stringResource(R.string.info_favourited_label), value = if (artist.isStarred) yesString else noString)
                        InfoRow(label = stringResource(R.string.info_artist_id_label), value = artist.id, isMonospace = true) {
                            clipboardManager.setText(AnnotatedString(artist.id))
                            toastHostState.showToast(copiedArtistIdToast, ToastType.SUCCESS)
                        }
                    }
                }

            Spacer(modifier = Modifier.height(130.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            fadeColor,
                            fadeColor.copy(alpha = 0.96f),
                            fadeColor.copy(alpha = 0.82f),
                            fadeColor.copy(alpha = 0.50f),
                            Color.Transparent
                        )
                    )
                )
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 12.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (scrollState.value > 15) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
                            }
                        )
                ) {
                    Icon(
                        imageVector = Tabler.Outline.ArrowLeft,
                        contentDescription = stringResource(R.string.content_desc_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = screenTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MediaInfoHeader(
    target: MediaTarget,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        when (target) {
            is MediaTarget.TrackTarget -> {
                CoverArtImage(
                    coverArtId = target.track.coverArtId,
                    contentDescription = target.track.title,
                    size = 72.dp,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.track.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = target.track.artist,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    target.track.album?.takeIf { it.isNotBlank() }?.let { album ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = album,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            is MediaTarget.AlbumTarget -> {
                CoverArtImage(
                    coverArtId = target.album.coverArtId,
                    contentDescription = target.album.title,
                    size = 72.dp,
                    shape = RoundedCornerShape(14.dp),
                    fallbackIcon = Tabler.Outline.Disc,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.album.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = target.album.artist,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            is MediaTarget.PlaylistTarget -> {
                CoverArtImage(
                    coverArtId = target.playlist.coverArtId,
                    playlistId = target.playlist.id,
                    contentDescription = target.playlist.name,
                    size = 72.dp,
                    shape = RoundedCornerShape(14.dp),
                    fallbackIcon = Tabler.Outline.Playlist,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.playlist.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.info_songs_count_format, target.playlist.songCount),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            is MediaTarget.ArtistTarget -> {
                CoverArtImage(
                    coverArtId = target.artist.coverArtId,
                    contentDescription = target.artist.name,
                    size = 72.dp,
                    shape = CircleShape,
                    fallbackIcon = Tabler.Outline.User,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.artist.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (target.artist.albumCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.info_albums_count_format, target.artist.albumCount),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
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

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClick() }
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                    else Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                ),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(132.dp)
            )

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = value,
                    style = if (isMonospace) {
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (onClick != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Tabler.Outline.Copy,
                        contentDescription = stringResource(R.string.info_copy),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(15.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            thickness = 0.5.dp
        )
    }
}

private fun formatFullDate(epochMs: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        sdf.format(Date(epochMs))
    } catch (_: Exception) {
        epochMs.toString()
    }
}
