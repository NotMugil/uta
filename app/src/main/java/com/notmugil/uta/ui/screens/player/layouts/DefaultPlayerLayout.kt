package com.notmugil.uta.ui.screens.player.layouts

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.*
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.notmugil.uta.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notmugil.uta.domain.model.QueueItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.SleepTimerMode
import com.notmugil.uta.ui.screens.player.components.PlaybackControlsSection
import com.notmugil.uta.ui.screens.player.components.PlayerBottomActionsBar
import com.notmugil.uta.ui.screens.player.components.PlayerSeekBarSection
import com.notmugil.uta.ui.screens.player.components.PlayerTopBar
import com.notmugil.uta.ui.shared.CoverArtImage
import kotlin.math.abs

@Composable
fun DefaultPlayerLayout(
    track: TrackItem?,
    queue: List<QueueItem>,
    horizontalArtworkOffset: Float,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isStarred: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isDraggingSlider: Boolean,
    dragPositionMs: Float,
    onDraggingSliderChange: (Boolean) -> Unit,
    onDragPositionChange: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    sleepTimerMode: SleepTimerMode,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onBack: () -> Unit,
    onMoreOptions: () -> Unit,
    scrollState: ScrollState,
    onOpenSleepTimer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    showLyrics: Boolean = true,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float? = null,
    onToggleDownload: () -> Unit = {},
    trackStats: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        PlayerTopBar(
            track = track,
            onBack = onBack,
            onNavigateToAlbum = onNavigateToAlbum,
            onMoreOptions = onMoreOptions,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        val currentIndex = if (track != null) queue.indexOfFirst { it.track.id == track.id } else -1
        val prevItem = if (currentIndex > 0) queue[currentIndex - 1] else null
        val nextItem = if (currentIndex >= 0 && currentIndex < queue.size - 1) queue[currentIndex + 1] else null

        val playAnimSpec = tween<Float>(durationMillis = 450, easing = EaseOutCubic)
        val playDpAnimSpec = tween<androidx.compose.ui.unit.Dp>(durationMillis = 450, easing = EaseOutCubic)

        val centerScale by animateFloatAsState(
            targetValue = if (isPlaying) 1.05f else 1.0f,
            animationSpec = playAnimSpec,
            label = "center_artwork_scale"
        )

        val centerTilt by animateFloatAsState(
            targetValue = if (isPlaying) -1.8f else 0f,
            animationSpec = playAnimSpec,
            label = "center_artwork_tilt"
        )

        val sideOffsetDistance by animateDpAsState(
            targetValue = if (isPlaying) 270.dp else 295.dp,
            animationSpec = playDpAnimSpec,
            label = "side_artwork_offset"
        )

        val sideTilt by animateFloatAsState(
            targetValue = if (isPlaying) 1.2f else 0f,
            animationSpec = playAnimSpec,
            label = "side_artwork_tilt"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(315.dp)
                .graphicsLayer {
                    translationX = horizontalArtworkOffset
                },
            contentAlignment = Alignment.Center
        ) {
            if (prevItem != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = -sideOffsetDistance)
                        .size(190.dp)
                        .graphicsLayer {
                            alpha = 0.28f
                            scaleX = 0.85f
                            scaleY = 0.85f
                            rotationZ = -sideTilt
                        }
                        .clickable { onSkipPrevious() }
                ) {
                    CoverArtImage(
                        coverArtId = prevItem.track.coverArtId,
                        contentDescription = prevItem.track.title,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (nextItem != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = sideOffsetDistance)
                        .size(190.dp)
                        .graphicsLayer {
                            alpha = 0.28f
                            scaleX = 0.85f
                            scaleY = 0.85f
                            rotationZ = sideTilt
                        }
                        .clickable { onSkipNext() }
                ) {
                    CoverArtImage(
                        coverArtId = nextItem.track.coverArtId,
                        contentDescription = nextItem.track.title,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(290.dp)
                    .graphicsLayer {
                        val progress = (abs(horizontalArtworkOffset) / 900f).coerceIn(0f, 0.45f)
                        alpha = 1f - progress
                        val dragScale = 1f - progress * 0.15f
                        scaleX = centerScale * dragScale
                        scaleY = centerScale * dragScale
                        rotationZ = centerTilt * (1f - progress * 2f)
                    },
                contentAlignment = Alignment.Center
            ) {
                com.notmugil.uta.ui.shared.AnimatedAlbumArtView(
                    track = track,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            IconButton(
                onClick = onToggleDownload,
                modifier = Modifier.size(44.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress ?: 0.1f },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.4.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = if (isDownloaded) Tabler.Outline.CircleCheck else Tabler.Outline.Download,
                        contentDescription = if (isDownloaded) stringResource(R.string.download_status_completed) else stringResource(R.string.action_download),
                        tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = track?.title ?: stringResource(R.string.album_unknown_album),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (track != null) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .basicMarquee()
                            .clickable(enabled = track.artistId != null) {
                                track.artistId?.let { onNavigateToArtist(it) }
                            }
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isStarred) Tabler.Filled.Heart else Tabler.Outline.Heart,
                    contentDescription = if (isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
                    tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        PlayerSeekBarSection(
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            isDraggingSlider = isDraggingSlider,
            dragPositionMs = dragPositionMs,
            onDraggingSliderChange = onDraggingSliderChange,
            onDragPositionChange = onDragPositionChange,
            onSeekTo = onSeekTo,
            songKey = track?.id,
            trackStats = trackStats,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        PlaybackControlsSection(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            onTogglePlayPause = onTogglePlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onToggleShuffle = onToggleShuffle,
            onToggleRepeat = onToggleRepeat,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        PlayerBottomActionsBar(
            showFavorite = false,
            showDownload = false,
            isStarred = isStarred,
            onOpenSleepTimer = onOpenSleepTimer,
            onOpenLyrics = onOpenLyrics,
            onOpenQueue = onOpenQueue,
            onToggleFavorite = onToggleFavorite,
            showLyrics = showLyrics,
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            onToggleDownload = onToggleDownload,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))
    }
}
