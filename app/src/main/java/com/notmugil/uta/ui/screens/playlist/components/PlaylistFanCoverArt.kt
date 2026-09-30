package com.notmugil.uta.ui.screens.playlist.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.shared.CoverArtImage

@Composable
fun PlaylistFanCoverArt(
    playlist: PlaylistItem,
    tracks: List<TrackItem>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val coverTracks = remember(tracks) {
        val unique = mutableListOf<TrackItem>()
        val seen = mutableSetOf<String>()
        for (track in tracks) {
            val id = track.coverArtId ?: track.albumId ?: track.id
            if (seen.add(id)) {
                unique.add(track)
                if (unique.size == 3) break
            }
        }
        if (unique.isEmpty() && tracks.isNotEmpty()) {
            tracks.take(3)
        } else {
            unique
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val hasCustomCover = remember(playlist.id) {
        com.notmugil.uta.data.PlaylistCoverManager.hasCustomCover(context, playlist.id)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (hasCustomCover || !playlist.coverArtId.isNullOrBlank()) {
            CoverArtImage(
                coverArtId = playlist.coverArtId,
                playlistId = playlist.id,
                contentDescription = playlist.name,
                size = 240.dp,
                shape = RoundedCornerShape(26.dp),
                fallbackIcon = Tabler.Outline.Playlist,
                modifier = Modifier
                    .size(240.dp)
                    .aspectRatio(1f)
                    .shadow(16.dp, RoundedCornerShape(26.dp))
                    .clickable(onClick = onClick)
            )
        } else if (coverTracks.size >= 3) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                CoverArtImage(
                    coverArtId = coverTracks[1].coverArtId,
                    contentDescription = null,
                    size = 190.dp,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .size(190.dp)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            rotationZ = -9f
                            translationX = -85f
                            translationY = 10f
                            alpha = 0.75f
                        }
                )

                CoverArtImage(
                    coverArtId = coverTracks[2].coverArtId,
                    contentDescription = null,
                    size = 190.dp,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .size(190.dp)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            rotationZ = 9f
                            translationX = 85f
                            translationY = 10f
                            alpha = 0.75f
                        }
                )

                CoverArtImage(
                    coverArtId = coverTracks[0].coverArtId,
                    contentDescription = playlist.name,
                    size = 200.dp,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .size(200.dp)
                        .aspectRatio(1f)
                        .shadow(16.dp, RoundedCornerShape(24.dp))
                )
            }
        } else if (coverTracks.size == 2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                CoverArtImage(
                    coverArtId = coverTracks[1].coverArtId,
                    contentDescription = null,
                    size = 180.dp,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .size(180.dp)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            rotationZ = -8f
                            translationX = -65f
                            scaleX = 0.88f
                            scaleY = 0.88f
                            alpha = 0.75f
                        }
                )

                CoverArtImage(
                    coverArtId = coverTracks[0].coverArtId,
                    contentDescription = playlist.name,
                    size = 200.dp,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .size(200.dp)
                        .aspectRatio(1f)
                        .shadow(16.dp, RoundedCornerShape(24.dp))
                )
            }
        } else if (coverTracks.size == 1) {
            CoverArtImage(
                coverArtId = coverTracks[0].coverArtId,
                contentDescription = playlist.name,
                size = 240.dp,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .size(240.dp)
                    .aspectRatio(1f)
                    .shadow(16.dp, RoundedCornerShape(26.dp))
                    .clickable(onClick = onClick)
            )
        } else {
            CoverArtImage(
                coverArtId = playlist.coverArtId,
                contentDescription = playlist.name,
                size = 240.dp,
                shape = RoundedCornerShape(26.dp),
                fallbackIcon = Tabler.Outline.Playlist,
                modifier = Modifier
                    .size(240.dp)
                    .aspectRatio(1f)
                    .shadow(16.dp, RoundedCornerShape(26.dp))
                    .clickable(onClick = onClick)
            )
        }
    }
}
