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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.notmugil.uta.data.PlaylistCoverManager
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
    customModel: Any? = null,
    highRes: Boolean = false
) {
    val context = LocalContext.current
    val appPreferences = LocalAppPreferences.current
    val coverArtQuality = appPreferences?.coverArtQuality?.value ?: CoverArtQuality.HIGH

    val standardSizePx = remember(size, coverArtQuality, highRes) {
        val base = when {
            highRes -> 1400
            size == Dp.Unspecified -> 600
            size <= 64.dp -> 180
            size <= 160.dp -> 450
            size <= 320.dp -> 900
            else -> 1400
        }
        when (coverArtQuality) {
            CoverArtQuality.HIGH -> base
            CoverArtQuality.MEDIUM -> (base * 0.6f).toInt().coerceAtLeast(120)
            CoverArtQuality.LOW -> (base * 0.35f).toInt().coerceAtLeast(80)
        }
    }

    val customPlaylistCover = remember(playlistId) {
        if (!playlistId.isNullOrBlank()) {
            PlaylistCoverManager.getCustomCoverFile(context, playlistId)
        } else {
            null
        }
    }

    val effectiveCoverId = remember(coverArtId, playlistId) {
        when {
            !coverArtId.isNullOrBlank() -> coverArtId
            !playlistId.isNullOrBlank() -> if (playlistId.startsWith("pl-")) playlistId else "pl-$playlistId"
            else -> null
        }
    }

    val localCoverFile = remember(effectiveCoverId) {
        OfflineDownloadManager.getLocalCoverArtFile(context, effectiveCoverId)
    }

    val isOffline = SubsonicSession.isOfflineModeActive

    val imageData: Any? = remember(customModel, customPlaylistCover, effectiveCoverId, localCoverFile, isOffline, standardSizePx) {
        when {
            customModel != null -> customModel
            customPlaylistCover != null -> customPlaylistCover
            localCoverFile != null -> localCoverFile
            isOffline -> null
            !effectiveCoverId.isNullOrBlank() -> SubsonicSession.client?.getCoverArtUrl(effectiveCoverId, size = standardSizePx.toString())
            else -> null
        }
    }

    val stableCacheKey = remember(customModel, customPlaylistCover, effectiveCoverId, standardSizePx, localCoverFile) {
        when {
            customModel != null -> "custom_${customModel.hashCode()}"
            customPlaylistCover != null -> customPlaylistCover.absolutePath
            localCoverFile != null -> localCoverFile.absolutePath
            !effectiveCoverId.isNullOrBlank() -> "cover_${effectiveCoverId}_$standardSizePx"
            else -> null
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

    var isError by remember(imageData) { mutableStateOf(false) }

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        if (imageData != null && stableCacheKey != null && !isError) {
            val request = remember(imageData, stableCacheKey, standardSizePx) {
                ImageRequest.Builder(context)
                    .data(imageData)
                    .size(standardSizePx)
                    .memoryCacheKey(stableCacheKey)
                    .diskCacheKey(stableCacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(150)
                    .build()
            }

            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                onError = { isError = true },
                onSuccess = { isError = false },
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
