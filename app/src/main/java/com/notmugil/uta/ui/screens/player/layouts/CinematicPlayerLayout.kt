package com.notmugil.uta.ui.screens.player.layouts

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.SleepTimerMode
import com.notmugil.uta.ui.screens.player.components.LeftAlignedMetadataRow
import com.notmugil.uta.ui.screens.player.components.PlaybackControlsSection
import com.notmugil.uta.ui.screens.player.components.PlayerBottomActionsBar
import com.notmugil.uta.ui.screens.player.components.PlayerSeekBarSection
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import com.notmugil.uta.ui.theme.UtaTheme
import kotlin.math.abs

@Composable
fun CinematicPlayerLayout(
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
    onToggleDownload: () -> Unit = {},
    trackStats: String? = null
) {
    UtaTheme(darkTheme = true) {
        val dynamicThemeManager = LocalDynamicThemeManager.current
        val dynamicDarkBgColor by (dynamicThemeManager?.dynamicDarkBgColor?.collectAsState() ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) })
        val cinematicBgColor = dynamicDarkBgColor ?: Color(0xFF101014)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cinematicBgColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.42f)
                    .graphicsLayer {
                        translationX = horizontalArtworkOffset
                        val progress = (abs(horizontalArtworkOffset) / 900f).coerceIn(0f, 0.45f)
                        alpha = 1f - progress
                    }
            ) {
                com.notmugil.uta.ui.shared.AnimatedAlbumArtView(
                    track = track,
                    isPlaying = isPlaying,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.25f),
                                    0.18f to Color.Transparent,
                                    0.45f to cinematicBgColor.copy(alpha = 0.25f),
                                    0.70f to cinematicBgColor.copy(alpha = 0.70f),
                                    1.0f to cinematicBgColor
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Spacer(modifier = Modifier.height(240.dp))

                LeftAlignedMetadataRow(
                    track = track,
                    isStarred = isStarred,
                    onToggleFavorite = onToggleFavorite,
                    onNavigateToArtist = onNavigateToArtist,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onMoreOptions = onMoreOptions
                )

                Spacer(modifier = Modifier.height(26.dp))

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
                    trackStats = trackStats
                )

                Spacer(modifier = Modifier.height(26.dp))

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

                Spacer(modifier = Modifier.height(30.dp))

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

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}
