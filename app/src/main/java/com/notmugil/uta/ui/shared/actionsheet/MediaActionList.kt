package com.notmugil.uta.ui.shared.actionsheet

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.R
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.data.repository.LibraryRepository
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.PlaybackController
import com.notmugil.uta.ui.shared.LocalToastHostState
import com.notmugil.uta.ui.shared.ToastType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private suspend fun resolveTracksForTarget(
    target: MediaTarget,
    libraryRepository: LibraryRepository
): List<TrackItem> {
    return when (target) {
        is MediaTarget.TrackTarget -> listOf(target.track)
        is MediaTarget.AlbumTarget -> {
            var tracks = libraryRepository.getTracksForAlbumFlow(target.album.id).firstOrNull()
            if (tracks.isNullOrEmpty()) {
                val res = libraryRepository.fetchAlbumTracks(target.album.id)
                tracks = res.getOrNull()
            }
            tracks ?: emptyList()
        }
        is MediaTarget.PlaylistTarget -> {
            var tracks = libraryRepository.getTracksForPlaylistFlow(target.playlist.id).firstOrNull()
            if (tracks.isNullOrEmpty()) {
                val res = libraryRepository.fetchPlaylistTracks(target.playlist.id)
                tracks = res.getOrNull()
            }
            tracks ?: emptyList()
        }
        is MediaTarget.ArtistTarget -> emptyList()
    }
}

@Composable
fun MediaActionList(
    target: MediaTarget,
    coroutineScope: CoroutineScope,
    playbackController: PlaybackController,
    libraryRepository: LibraryRepository,
    offlineDownloadManager: OfflineDownloadManager,
    isDownloaded: Boolean,
    isOffline: Boolean = false,
    isStarred: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onDownload: () -> Unit = {},
    onShowRemoveDownloadConfirmDialog: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onShowPlaylistSheet: () -> Unit = {},
    onShowEditPlaylistDialog: () -> Unit = {},
    onShowDeleteConfirmDialog: () -> Unit = {},
    onShowRatingDialog: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val toastHostState = LocalToastHostState.current
    val executionScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val appPreferences = LocalAppPreferences.current
    val showExternalLinks = appPreferences?.showExternalLinks?.collectAsStateWithLifecycle()?.value ?: true
    val showLastFmLinks = appPreferences?.showLastFmLinks?.collectAsStateWithLifecycle()?.value ?: true
    val showMusicBrainzLinks = appPreferences?.showMusicBrainzLinks?.collectAsStateWithLifecycle()?.value ?: true

    val noPlayableTracksToast = stringResource(R.string.toast_no_playable_tracks)
    val playingNextToast = stringResource(R.string.toast_playing_next)
    val addedToQueueToast = stringResource(R.string.toast_added_to_queue)

    Column(modifier = modifier.fillMaxWidth()) {
        ActionRowItem(
            icon = Tabler.Filled.PlayerPlay,
            title = stringResource(R.string.action_play),
            onClick = {
                executionScope.launch {
                    val tracks = resolveTracksForTarget(target, libraryRepository)
                    val playable = if (isOffline) {
                        tracks.filter { offlineDownloadManager.getLocalUriForTrack(it.id) != null }
                    } else tracks
                    if (playable.isNotEmpty()) {
                        if (target is MediaTarget.TrackTarget) {
                            playbackController.playTrack(target.track)
                        } else {
                            playbackController.playQueue(playable)
                        }
                    } else {
                        toastHostState.showToast(noPlayableTracksToast, ToastType.ERROR)
                    }
                }
                onDismiss()
            }
        )

        ActionRowItem(
            icon = Tabler.Outline.ArrowsShuffle,
            title = stringResource(R.string.action_shuffle),
            onClick = {
                executionScope.launch {
                    val tracks = resolveTracksForTarget(target, libraryRepository)
                    val playable = if (isOffline) {
                        tracks.filter { offlineDownloadManager.getLocalUriForTrack(it.id) != null }
                    } else tracks
                    if (playable.isNotEmpty()) {
                        if (target is MediaTarget.TrackTarget) {
                            playbackController.playTrack(target.track)
                            if (!playbackController.isShuffleEnabled.value) {
                                playbackController.toggleShuffle()
                            }
                        } else {
                            playbackController.playQueue(playable.shuffled())
                        }
                    } else {
                        toastHostState.showToast(noPlayableTracksToast, ToastType.ERROR)
                    }
                }
                onDismiss()
            }
        )

        if (target !is MediaTarget.ArtistTarget) {
            val tracksCountSubtitle = stringResource(R.string.action_tracks_count_format, target.let {
                when (it) {
                    is MediaTarget.AlbumTarget -> it.album.songCount
                    is MediaTarget.PlaylistTarget -> it.playlist.songCount
                    else -> 0
                }
            })
            ActionRowItem(
                icon = Tabler.Outline.Playlist,
                title = stringResource(R.string.action_play_next),
                onClick = {
                    executionScope.launch {
                        val tracks = resolveTracksForTarget(target, libraryRepository)
                        val playable = if (isOffline) {
                            tracks.filter { offlineDownloadManager.getLocalUriForTrack(it.id) != null }
                        } else tracks
                        if (playable.isNotEmpty()) {
                            if (target is MediaTarget.TrackTarget) {
                                playbackController.playNext(target.track)
                                toastHostState.showInfo(
                                    message = playingNextToast,
                                    subtitle = target.track.title,
                                    coverArtId = target.track.coverArtId,
                                    icon = Tabler.Outline.PlayerTrackNext
                                )
                            } else {
                                playbackController.playNext(playable)
                                val (name, coverId) = when (target) {
                                    is MediaTarget.AlbumTarget -> Pair(target.album.title, target.album.coverArtId)
                                    is MediaTarget.PlaylistTarget -> Pair(target.playlist.name, target.playlist.coverArtId)
                                    is MediaTarget.TrackTarget, is MediaTarget.ArtistTarget -> Pair("", null)
                                }
                                toastHostState.showInfo(
                                    message = "$playingNextToast: $name",
                                    subtitle = tracksCountSubtitle,
                                    coverArtId = coverId,
                                    icon = Tabler.Outline.PlayerTrackNext
                                )
                            }
                        } else {
                            toastHostState.showError(noPlayableTracksToast)
                        }
                    }
                    onDismiss()
                }
            )
        }

        if (target !is MediaTarget.ArtistTarget) {
            val tracksCountSubtitle = stringResource(R.string.action_tracks_count_format, target.let {
                when (it) {
                    is MediaTarget.AlbumTarget -> it.album.songCount
                    is MediaTarget.PlaylistTarget -> it.playlist.songCount
                    else -> 0
                }
            })
            ActionRowItem(
                icon = Tabler.Outline.Playlist,
                title = stringResource(R.string.action_add_to_queue),
                onClick = {
                    executionScope.launch {
                        val tracks = resolveTracksForTarget(target, libraryRepository)
                        val playable = if (isOffline) {
                            tracks.filter { offlineDownloadManager.getLocalUriForTrack(it.id) != null }
                        } else tracks
                        if (playable.isNotEmpty()) {
                            if (target is MediaTarget.TrackTarget) {
                                playbackController.addToQueue(target.track)
                                toastHostState.showQueue(
                                    title = addedToQueueToast,
                                    subtitle = target.track.title,
                                    coverArtId = target.track.coverArtId
                                )
                            } else {
                                playbackController.addToQueue(playable)
                                val (name, coverId) = when (target) {
                                    is MediaTarget.AlbumTarget -> Pair(target.album.title, target.album.coverArtId)
                                    is MediaTarget.PlaylistTarget -> Pair(target.playlist.name, target.playlist.coverArtId)
                                    is MediaTarget.TrackTarget, is MediaTarget.ArtistTarget -> Pair("", null)
                                }
                                toastHostState.showQueue(
                                    title = "$addedToQueueToast: $name",
                                    subtitle = tracksCountSubtitle,
                                    coverArtId = coverId
                                )
                            }
                        } else {
                            toastHostState.showError(noPlayableTracksToast)
                        }
                    }
                    onDismiss()
                }
            )
        }

        if (target is MediaTarget.TrackTarget) {
            if (target.playlistId != null) {
                ActionRowItem(
                    icon = Tabler.Outline.PlaylistX,
                    title = stringResource(R.string.action_remove_from_playlist),
                    iconTint = MaterialTheme.colorScheme.primary,
                    textColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        val pId = target.playlistId
                        val trackIndex = target.playlistTrackIndex
                        coroutineScope.launch {
                            if (trackIndex != null) {
                                libraryRepository.removeTrackFromPlaylist(pId, trackIndex)
                            }
                            target.onRemoveFromPlaylist?.invoke()
                        }
                        onDismiss()
                    }
                )
            } else if (!isOffline) {
                ActionRowItem(
                    icon = Tabler.Outline.PlaylistAdd,
                    title = stringResource(R.string.action_add_to_playlist),
                    onClick = {
                        onShowPlaylistSheet()
                    }
                )
            }
        }

        if (target is MediaTarget.PlaylistTarget && !isOffline) {
            ActionRowItem(
                icon = Tabler.Outline.Pencil,
                title = stringResource(R.string.playlist_edit_title),
                onClick = onShowEditPlaylistDialog
            )
        }

        if (target !is MediaTarget.PlaylistTarget) {
            ActionRowItem(
                icon = if (isStarred) Tabler.Outline.HeartBroken else Tabler.Outline.Heart,
                title = if (isStarred) stringResource(R.string.action_remove_from_favorites) else stringResource(R.string.action_add_to_favorites),
                iconTint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textColor = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = onToggleFavorite
            )
        }

        ActionRowItem(
            icon = if (isDownloaded) Tabler.Outline.DownloadOff else Tabler.Outline.Download,
            title = if (isDownloaded) stringResource(R.string.action_remove_from_downloads) else stringResource(R.string.action_download),
            iconTint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textColor = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            onClick = {
                if (isDownloaded) {
                    onShowRemoveDownloadConfirmDialog()
                } else {
                    onDownload()
                }
            }
        )

        val artistId = when (target) {
            is MediaTarget.TrackTarget -> target.track.artistId
            is MediaTarget.AlbumTarget -> target.album.artistId
            else -> null
        }
        if (!artistId.isNullOrBlank()) {
            ActionRowItem(
                icon = Tabler.Outline.User,
                title = stringResource(R.string.action_go_to_artist),
                onClick = {
                    onNavigateToArtist(artistId)
                    onDismiss()
                }
            )
        }

        val albumId = when (target) {
            is MediaTarget.TrackTarget -> target.track.albumId
            else -> null
        }
        if (!albumId.isNullOrBlank()) {
            ActionRowItem(
                icon = Tabler.Outline.Disc,
                title = stringResource(R.string.action_go_to_album),
                onClick = {
                    onNavigateToAlbum(albumId)
                    onDismiss()
                }
            )
        }

        if (target is MediaTarget.PlaylistTarget && !isOffline) {
            ActionRowItem(
                icon = Tabler.Outline.Trash,
                title = stringResource(R.string.action_delete_playlist),
                iconTint = MaterialTheme.colorScheme.primary,
                textColor = MaterialTheme.colorScheme.primary,
                onClick = onShowDeleteConfirmDialog
            )
        }

        if (showExternalLinks && showLastFmLinks && target !is MediaTarget.PlaylistTarget && target !is MediaTarget.ArtistTarget && !isOffline) {
            ActionRowItem(
                icon = Tabler.Outline.BrandLastfm,
                title = stringResource(R.string.action_open_lastfm),
                onClick = {
                    val url = when (target) {
                        is MediaTarget.TrackTarget -> "https://www.last.fm/music/${Uri.encode(target.track.artist)}/_/${Uri.encode(target.track.title)}"
                        is MediaTarget.AlbumTarget -> "https://www.last.fm/music/${Uri.encode(target.album.artist)}/${Uri.encode(target.album.title)}"
                    }
                    if (url.isNotBlank()) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {}
                    }
                    onDismiss()
                }
            )
        }

        if (showExternalLinks && showMusicBrainzLinks && target !is MediaTarget.PlaylistTarget && target !is MediaTarget.ArtistTarget && !isOffline) {
            ActionRowItem(
                icon = Tabler.Outline.BrandMetabrainz,
                title = stringResource(R.string.action_open_musicbrainz),
                onClick = {
                    val query = when (target) {
                        is MediaTarget.TrackTarget -> "${target.track.title} ${target.track.artist}"
                        is MediaTarget.AlbumTarget -> "${target.album.title} ${target.album.artist}"
                    }
                    if (query.isNotBlank()) {
                        val type = if (target is MediaTarget.TrackTarget) "recording" else "release"
                        val url = "https://musicbrainz.org/search?query=${Uri.encode(query)}&type=$type"
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {}
                    }
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ActionRowItem(
    icon: ImageVector,
    title: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tintColor: Color? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tintColor ?: iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}
