package com.notmugil.uta.ui.screens.player.layouts

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.SleepTimerMode
import com.notmugil.uta.ui.screens.player.components.LeftAlignedMetadataRow
import com.notmugil.uta.ui.screens.player.components.PlaybackControlsSection
import com.notmugil.uta.ui.screens.player.components.PlayerBottomActionsBar
import com.notmugil.uta.ui.screens.player.components.PlayerSeekBarSection
import com.notmugil.uta.ui.screens.player.components.PlayerTopBar
import com.notmugil.uta.ui.shared.CoverArtImage
import kotlin.math.abs

@Composable
fun ModernPlayerLayout(
    track: TrackItem?,
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
    onToggleDownload: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        PlayerTopBar(
            track = track,
            onBack = onBack,
            onNavigateToAlbum = onNavigateToAlbum,
            onMoreOptions = onMoreOptions
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = horizontalArtworkOffset
                    val progress = (abs(horizontalArtworkOffset) / 900f).coerceIn(0f, 0.45f)
                    alpha = 1f - progress
                    scaleX = 1f - progress * 0.15f
                    scaleY = 1f - progress * 0.15f
                },
            contentAlignment = Alignment.Center
        ) {
            com.notmugil.uta.ui.shared.AnimatedAlbumArtView(
                track = track,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        LeftAlignedMetadataRow(
            track = track,
            isStarred = isStarred,
            onToggleFavorite = onToggleFavorite,
            onNavigateToArtist = onNavigateToArtist,
            onNavigateToAlbum = onNavigateToAlbum
        )

        Spacer(modifier = Modifier.height(16.dp))

        PlayerSeekBarSection(
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            isDraggingSlider = isDraggingSlider,
            dragPositionMs = dragPositionMs,
            onDraggingSliderChange = onDraggingSliderChange,
            onDragPositionChange = onDragPositionChange,
            onSeekTo = onSeekTo,
            songKey = track?.id
        )

        Spacer(modifier = Modifier.height(16.dp))

        PlaybackControlsSection(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            onTogglePlayPause = onTogglePlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onToggleShuffle = onToggleShuffle,
            onToggleRepeat = onToggleRepeat
        )

        Spacer(modifier = Modifier.height(20.dp))

        PlayerBottomActionsBar(
            showFavorite = false,
            isStarred = isStarred,
            onOpenSleepTimer = onOpenSleepTimer,
            onOpenLyrics = onOpenLyrics,
            onOpenQueue = onOpenQueue,
            onToggleFavorite = onToggleFavorite,
            showLyrics = showLyrics,
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            onToggleDownload = onToggleDownload
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}
