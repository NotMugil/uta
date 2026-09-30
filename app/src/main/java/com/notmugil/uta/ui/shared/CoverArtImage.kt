package com.notmugil.uta.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.notmugil.uta.data.SubsonicSession
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.data.preferences.CoverArtQuality
import com.notmugil.uta.data.preferences.LocalAppPreferences

@Composable
fun CoverArtImage(
    coverArtId: String? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    shape: Shape = RoundedCornerShape(8.dp),
    fallbackIcon: ImageVector = Tabler.Outline.Music,
    contentScale: ContentScale = ContentScale.Crop,
    playlistId: String? = null,
    customModel: Any? = null
) {
    val context = LocalContext.current
    val appPreferences = LocalAppPreferences.current
    val coverArtQuality = appPreferences?.coverArtQuality?.collectAsStateWithLifecycle()?.value ?: CoverArtQuality.HIGH

    val standardSizePx = remember(size, coverArtQuality) {
        val base = when {
            size == Dp.Unspecified -> 1000
            size <= 64.dp -> 180
            size <= 160.dp -> 450
            size <= 320.dp -> 900
            else -> 1200
        }
        when (coverArtQuality) {
            CoverArtQuality.HIGH -> base
            CoverArtQuality.MEDIUM -> (base * 0.6f).toInt().coerceAtLeast(120)
            CoverArtQuality.LOW -> (base * 0.35f).toInt().coerceAtLeast(80)
        }
    }

    val customPlaylistCover = remember(playlistId) {
        if (!playlistId.isNullOrBlank()) {
            com.notmugil.uta.data.PlaylistCoverManager.getCustomCoverFile(context, playlistId)
        } else {
            null
        }
    }

    val localCoverFile = remember(coverArtId) {
        OfflineDownloadManager.getLocalCoverArtFile(context, coverArtId)
    }

    val isOffline = SubsonicSession.isOfflineModeActive

    val imageData: Any? = remember(customModel, customPlaylistCover, coverArtId, localCoverFile, isOffline, standardSizePx) {
        when {
            customModel != null -> customModel
            customPlaylistCover != null && customPlaylistCover.exists() -> customPlaylistCover
            localCoverFile != null && localCoverFile.exists() -> localCoverFile
            isOffline -> null
            !coverArtId.isNullOrBlank() -> SubsonicSession.client?.getCoverArtUrl(coverArtId, size = standardSizePx.toString())
            else -> null
        }
    }

    val stableCacheKey = remember(customModel, customPlaylistCover, customPlaylistCover?.lastModified(), coverArtId, standardSizePx, localCoverFile) {
        if (customModel != null) {
            "custom_${customModel.hashCode()}"
        } else if (customPlaylistCover != null) {
            "${customPlaylistCover.absolutePath}_${customPlaylistCover.lastModified()}"
        } else if (localCoverFile != null) {
            localCoverFile.absolutePath
        } else if (coverArtId.isNullOrBlank()) {
            null
        } else {
            "cover_${coverArtId}_$standardSizePx"
        }
    }

    val boxModifier = if (size != Dp.Unspecified) {
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    } else {
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    }

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        if (imageData != null && stableCacheKey != null) {
            val request = remember(imageData, stableCacheKey, isOffline) {
                ImageRequest.Builder(context)
                    .data(imageData)
                    .memoryCacheKey(stableCacheKey)
                    .diskCacheKey(stableCacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(200)
                    .build()
            }

            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val iconSize = if (size != Dp.Unspecified) (size / 2).coerceAtLeast(20.dp) else 48.dp
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
