package com.notmugil.uta.ui.screens.player

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.notmugil.uta.R
import com.notmugil.uta.data.SubsonicSession
import com.notmugil.uta.data.download.DownloadStatus
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.data.preferences.LyricsSourceMode
import com.notmugil.uta.data.preferences.PlayerStyle
import com.notmugil.uta.data.preferences.StreamingBitrate
import com.notmugil.uta.domain.model.QueueItem
import com.notmugil.uta.domain.model.TrackItem
import androidx.media3.common.Format
import com.notmugil.uta.player.SleepTimerManager
import com.notmugil.uta.player.SleepTimerMode
import com.notmugil.uta.util.Formatters
import com.notmugil.uta.ui.shared.ActionConfirmDialog
import com.notmugil.uta.ui.screens.player.components.LyricsAmbientBackground
import com.notmugil.uta.ui.screens.player.layouts.CinematicPlayerLayout
import com.notmugil.uta.ui.screens.player.layouts.CoverPlayerLayout
import com.notmugil.uta.ui.screens.player.layouts.DefaultPlayerLayout
import com.notmugil.uta.ui.screens.player.layouts.LandscapePlayerLayout
import com.notmugil.uta.ui.screens.player.layouts.LyricsPlayerLayout
import com.notmugil.uta.ui.screens.player.layouts.ModernPlayerLayout
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.SleepTimerSheet
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import kotlin.math.abs
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class PlayerDragDirection { HORIZONTAL, VERTICAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    track: TrackItem?,
    queue: List<QueueItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    repeatMode: Int,
    isShuffleEnabled: Boolean,
    sleepTimerMode: SleepTimerMode,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onPlayQueueIndex: (Int) -> Unit = {},
    onRemoveQueueIndex: (Int) -> Unit = {},
    onMoveQueueItem: (from: Int, to: Int) -> Unit = { _, _ -> },
    onClearQueue: () -> Unit = {},
    onUndoQueueAction: (() -> Unit)? = null,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onMoreOptions: () -> Unit = {},
    offlineDownloadManager: OfflineDownloadManager? = null,
    sleepTimerManager: SleepTimerManager? = null,
    audioFormat: Format? = null,
    currentEntryId: String? = null,
    modifier: Modifier = Modifier,
    isBuffering: Boolean = false
) {
    if (track == null) return

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val appPreferences = LocalAppPreferences.current

    val playerStyle by (appPreferences?.playerStyle?.collectAsState() ?: remember { mutableStateOf(PlayerStyle.DEFAULT) })
    val lyricsSourceMode by (appPreferences?.lyricsSourceMode?.collectAsState() ?: remember { mutableStateOf(LyricsSourceMode.BOTH) })
    val effectivePlayerStyle = playerStyle

    val wifiBitrate by (appPreferences?.wifiStreamingBitrate?.collectAsState() ?: remember { mutableStateOf(StreamingBitrate.UNLIMITED) })
    val cellBitrate by (appPreferences?.cellularStreamingBitrate?.collectAsState() ?: remember { mutableStateOf(StreamingBitrate.AUTO) })
    val requestedBitrate = if (wifiBitrate.kbps > 0) wifiBitrate.kbps else cellBitrate.kbps.takeIf { it > 0 }

    val trackStats = remember(track, audioFormat, requestedBitrate) {
        Formatters.formatTrackAudioStats(track, audioFormat, requestedBitrate)
    }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Offline download state
    val downloadStatusFlow = remember(track.id, offlineDownloadManager) {
        offlineDownloadManager?.getDownloadStatusFlow(track.id)
    }
    val downloadStatus by (downloadStatusFlow?.collectAsState(initial = DownloadStatus.Idle) ?: remember { mutableStateOf(DownloadStatus.Idle) })

    val isDownloadedFlow = remember(track.id, offlineDownloadManager) {
        offlineDownloadManager?.isTrackDownloadedFlow(track.id)
    }
    val isDownloaded by (isDownloadedFlow?.collectAsState(initial = false) ?: remember { mutableStateOf(false) })

    val isDownloading = downloadStatus is DownloadStatus.Downloading || downloadStatus is DownloadStatus.Queued
    val downloadProgress = (downloadStatus as? DownloadStatus.Downloading)?.progress

    val coroutineScope = rememberCoroutineScope()

    val onToggleDownload: () -> Unit = {
        if (offlineDownloadManager != null) {
            if (isDownloading) {
                offlineDownloadManager.cancelTrack(track.id)
            } else if (isDownloaded) {
                showDeleteConfirmDialog = true
            } else {
                offlineDownloadManager.enqueueTrack(track)
            }
        }
    }

    val horizontalOffset = remember { Animatable(0f) }
    val verticalOffset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val dismissThresholdPx = with(density) { 95.dp.toPx() }

    val scrollState = rememberScrollState()
    var dragDirection by remember { mutableStateOf<PlayerDragDirection?>(null) }

    val ambientRotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val current = ambientRotation.value
                ambientRotation.animateTo(
                    targetValue = current + 360f,
                    animationSpec = tween(
                        durationMillis = 45000,
                        easing = LinearEasing
                    )
                )
            }
        } else {
            ambientRotation.stop()
        }
    }

    val dynamicThemeManager = LocalDynamicThemeManager.current
    val dynamicDarkBgColor by (dynamicThemeManager?.dynamicDarkBgColor?.collectAsState() ?: remember { mutableStateOf(null) })
    val cinematicBgColor = dynamicDarkBgColor ?: Color(0xFF101014)

    val effectiveDurationMs = if (durationMs > 0) durationMs else (track.durationSeconds * 1000L).coerceAtLeast(1L)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!isLandscape && effectivePlayerStyle == PlayerStyle.CINEMATIC) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(cinematicBgColor)
                    )
                } else if (!isLandscape && effectivePlayerStyle == PlayerStyle.LYRICS) {
                    LyricsAmbientBackground(track = track, isPlaying = isPlaying)
                } else if (isLandscape || effectivePlayerStyle != PlayerStyle.COVER) {
                    val ambientCoverArtId = track.coverArtId
                    if (ambientCoverArtId != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = ambientRotation.value
                                    scaleX = 3.2f
                                    scaleY = 3.2f
                                }
                        ) {
                            CoverArtImage(
                                coverArtId = ambientCoverArtId,
                                contentDescription = null,
                                size = 1000.dp,
                                shape = RoundedCornerShape(0.dp),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(65.dp)
                                    .alpha(0.60f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.20f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.70f)
                                    )
                                )
                            )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = verticalOffset.value
                        val pullDownProgress = (verticalOffset.value / 1200f).coerceIn(0f, 0.25f)
                        scaleX = 1f - pullDownProgress * 0.4f
                        scaleY = 1f - pullDownProgress * 0.4f
                        alpha = (1f - pullDownProgress * 1.2f).coerceIn(0.4f, 1f)
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragDirection = null },
                            onDrag = { change, dragAmount ->
                                if (dragDirection == null) {
                                    val absX = abs(dragAmount.x)
                                    val absY = abs(dragAmount.y)
                                    if (absX > absY && absX > 3f) {
                                        dragDirection = PlayerDragDirection.HORIZONTAL
                                    } else if (dragAmount.y > 0 && absY > absX && scrollState.value == 0) {
                                        dragDirection = PlayerDragDirection.VERTICAL
                                    }
                                }

                                when (dragDirection) {
                                    PlayerDragDirection.HORIZONTAL -> {
                                        change.consume()
                                        coroutineScope.launch {
                                            horizontalOffset.snapTo(horizontalOffset.value + dragAmount.x * 0.85f)
                                        }
                                    }
                                    PlayerDragDirection.VERTICAL -> {
                                        if (scrollState.value == 0) {
                                            change.consume()
                                            val newY = (verticalOffset.value + dragAmount.y).coerceAtLeast(0f)
                                            coroutineScope.launch {
                                                verticalOffset.snapTo(newY)
                                            }
                                        }
                                    }
                                    null -> {}
                                }
                            },
                            onDragEnd = {
                                when (dragDirection) {
                                    PlayerDragDirection.HORIZONTAL -> {
                                        val currentX = horizontalOffset.value
                                        if (currentX < -swipeThresholdPx) {
                                            coroutineScope.launch {
                                                horizontalOffset.animateTo(-swipeThresholdPx * 2.5f, tween(160))
                                                onSkipNext()
                                                horizontalOffset.snapTo(swipeThresholdPx * 2.5f)
                                                horizontalOffset.animateTo(
                                                    0f,
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                            }
                                        } else if (currentX > swipeThresholdPx) {
                                            coroutineScope.launch {
                                                horizontalOffset.animateTo(swipeThresholdPx * 2.5f, tween(160))
                                                onSkipPrevious()
                                                horizontalOffset.snapTo(-swipeThresholdPx * 2.5f)
                                                horizontalOffset.animateTo(
                                                    0f,
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                horizontalOffset.animateTo(
                                                    0f,
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    PlayerDragDirection.VERTICAL -> {
                                        val currentY = verticalOffset.value
                                        if (currentY > dismissThresholdPx) {
                                            coroutineScope.launch {
                                                verticalOffset.animateTo(dismissThresholdPx * 4f, tween(180))
                                                onDismiss()
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                verticalOffset.animateTo(
                                                    0f,
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    null -> {}
                                }
                                dragDirection = null
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    horizontalOffset.animateTo(0f)
                                    verticalOffset.animateTo(0f)
                                }
                                dragDirection = null
                            }
                        )
                    }
            ) {
                if (isLandscape) {
                    LandscapePlayerLayout(
                        track = track,
                        queue = queue,
                        horizontalArtworkOffset = horizontalOffset.value,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        isStarred = track.isStarred,
                        currentPositionMs = currentPositionMs,
                        durationMs = effectiveDurationMs,
                        isDraggingSlider = isDraggingSlider,
                        dragPositionMs = dragPositionMs,
                        onDraggingSliderChange = { isDraggingSlider = it },
                        onDragPositionChange = { dragPositionMs = it },
                        onSeekTo = onSeekTo,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        sleepTimerMode = sleepTimerMode,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        onToggleFavorite = onToggleFavorite,
                        onPlayQueueIndex = onPlayQueueIndex,
                        onRemoveQueueIndex = onRemoveQueueIndex,
                        onMoveQueueItem = onMoveQueueItem,
                        onNavigateToArtist = onNavigateToArtist,
                        onBack = onDismiss,
                        onOpenSleepTimer = { showSleepTimerSheet = true },
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        onToggleDownload = onToggleDownload,
                        trackStats = trackStats,
                        currentEntryId = currentEntryId
                    )
                } else {
                    when (effectivePlayerStyle) {
                        PlayerStyle.DEFAULT -> {
                            DefaultPlayerLayout(
                                track = track,
                                queue = queue,
                                horizontalArtworkOffset = horizontalOffset.value,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                isStarred = track.isStarred,
                                currentPositionMs = currentPositionMs,
                                durationMs = effectiveDurationMs,
                                isDraggingSlider = isDraggingSlider,
                                dragPositionMs = dragPositionMs,
                                onDraggingSliderChange = { isDraggingSlider = it },
                                onDragPositionChange = { dragPositionMs = it },
                                onSeekTo = onSeekTo,
                                isShuffleEnabled = isShuffleEnabled,
                                repeatMode = repeatMode,
                                sleepTimerMode = sleepTimerMode,
                                onTogglePlayPause = onTogglePlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                onToggleShuffle = onToggleShuffle,
                                onToggleRepeat = onToggleRepeat,
                                onToggleFavorite = onToggleFavorite,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onNavigateToArtist = onNavigateToArtist,
                                onBack = onDismiss,
                                onMoreOptions = onMoreOptions,
                                scrollState = scrollState,
                                onOpenSleepTimer = { showSleepTimerSheet = true },
                                onOpenLyrics = { showLyricsSheet = true },
                                onOpenQueue = { showQueueSheet = true },
                                showLyrics = true,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                downloadProgress = downloadProgress,
                                onToggleDownload = onToggleDownload,
                                trackStats = trackStats
                            )
                        }
                        PlayerStyle.MODERN -> {
                            ModernPlayerLayout(
                                track = track,
                                horizontalArtworkOffset = horizontalOffset.value,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                isStarred = track.isStarred,
                                currentPositionMs = currentPositionMs,
                                durationMs = effectiveDurationMs,
                                isDraggingSlider = isDraggingSlider,
                                dragPositionMs = dragPositionMs,
                                onDraggingSliderChange = { isDraggingSlider = it },
                                onDragPositionChange = { dragPositionMs = it },
                                onSeekTo = onSeekTo,
                                isShuffleEnabled = isShuffleEnabled,
                                repeatMode = repeatMode,
                                sleepTimerMode = sleepTimerMode,
                                onTogglePlayPause = onTogglePlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                onToggleShuffle = onToggleShuffle,
                                onToggleRepeat = onToggleRepeat,
                                onToggleFavorite = onToggleFavorite,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onNavigateToArtist = onNavigateToArtist,
                                onBack = onDismiss,
                                onMoreOptions = onMoreOptions,
                                scrollState = scrollState,
                                onOpenSleepTimer = { showSleepTimerSheet = true },
                                onOpenLyrics = { showLyricsSheet = true },
                                onOpenQueue = { showQueueSheet = true },
                                showLyrics = true,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                downloadProgress = downloadProgress,
                                onToggleDownload = onToggleDownload,
                                trackStats = trackStats
                            )
                        }
                        PlayerStyle.CINEMATIC -> {
                            CinematicPlayerLayout(
                                track = track,
                                horizontalArtworkOffset = horizontalOffset.value,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                isStarred = track.isStarred,
                                currentPositionMs = currentPositionMs,
                                durationMs = effectiveDurationMs,
                                isDraggingSlider = isDraggingSlider,
                                dragPositionMs = dragPositionMs,
                                onDraggingSliderChange = { isDraggingSlider = it },
                                onDragPositionChange = { dragPositionMs = it },
                                onSeekTo = onSeekTo,
                                isShuffleEnabled = isShuffleEnabled,
                                repeatMode = repeatMode,
                                sleepTimerMode = sleepTimerMode,
                                onTogglePlayPause = onTogglePlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                onToggleShuffle = onToggleShuffle,
                                onToggleRepeat = onToggleRepeat,
                                onToggleFavorite = onToggleFavorite,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onNavigateToArtist = onNavigateToArtist,
                                onBack = onDismiss,
                                onMoreOptions = onMoreOptions,
                                scrollState = scrollState,
                                onOpenSleepTimer = { showSleepTimerSheet = true },
                                onOpenLyrics = { showLyricsSheet = true },
                                onOpenQueue = { showQueueSheet = true },
                                showLyrics = true,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                downloadProgress = downloadProgress,
                                onToggleDownload = onToggleDownload,
                                trackStats = trackStats
                            )
                        }
                        PlayerStyle.LYRICS -> {
                            LyricsPlayerLayout(
                                track = track,
                                horizontalArtworkOffset = horizontalOffset.value,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                isStarred = track.isStarred,
                                currentPositionMs = currentPositionMs,
                                durationMs = effectiveDurationMs,
                                isDraggingSlider = isDraggingSlider,
                                dragPositionMs = dragPositionMs,
                                onDraggingSliderChange = { isDraggingSlider = it },
                                onDragPositionChange = { dragPositionMs = it },
                                onSeekTo = onSeekTo,
                                isShuffleEnabled = isShuffleEnabled,
                                repeatMode = repeatMode,
                                sleepTimerMode = sleepTimerMode,
                                onTogglePlayPause = onTogglePlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                onToggleShuffle = onToggleShuffle,
                                onToggleRepeat = onToggleRepeat,
                                onToggleFavorite = onToggleFavorite,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onNavigateToArtist = onNavigateToArtist,
                                onBack = onDismiss,
                                onMoreOptions = onMoreOptions,
                                onOpenSleepTimer = { showSleepTimerSheet = true },
                                onOpenQueue = { showQueueSheet = true },
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                downloadProgress = downloadProgress,
                                onToggleDownload = onToggleDownload,
                                trackStats = trackStats
                            )
                        }
                        PlayerStyle.COVER -> {
                            CoverPlayerLayout(
                                track = track,
                                horizontalArtworkOffset = horizontalOffset.value,
                                isPlaying = isPlaying,
                                isStarred = track.isStarred,
                                currentPositionMs = currentPositionMs,
                                durationMs = effectiveDurationMs,
                                isDraggingSlider = isDraggingSlider,
                                dragPositionMs = dragPositionMs,
                                onDraggingSliderChange = { isDraggingSlider = it },
                                onDragPositionChange = { dragPositionMs = it },
                                onSeekTo = onSeekTo,
                                isShuffleEnabled = isShuffleEnabled,
                                repeatMode = repeatMode,
                                sleepTimerMode = sleepTimerMode,
                                onTogglePlayPause = onTogglePlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                onToggleShuffle = onToggleShuffle,
                                onToggleRepeat = onToggleRepeat,
                                onToggleFavorite = onToggleFavorite,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onNavigateToArtist = onNavigateToArtist,
                                onBack = onDismiss,
                                onMoreOptions = onMoreOptions,
                                scrollState = scrollState,
                                onOpenSleepTimer = { showSleepTimerSheet = true },
                                onOpenLyrics = { showLyricsSheet = true },
                                onOpenQueue = { showQueueSheet = true },
                                showLyrics = true,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                downloadProgress = downloadProgress,
                                onToggleDownload = onToggleDownload,
                                trackStats = trackStats
                            )
                        }
                    }
                }
            }
        }

        if (showQueueSheet) {
            QueueSheet(
                queue = queue,
                currentIndex = currentIndex,
                isPlaying = isPlaying,
                onDismiss = { showQueueSheet = false },
                onItemClick = { onPlayQueueIndex(it) },
                onRemoveItem = { onRemoveQueueIndex(it) },
                onMoveItem = { from, to -> onMoveQueueItem(from, to) },
                onClearQueue = onClearQueue,
                onUndo = onUndoQueueAction,
                currentTrack = track,
                currentEntryId = currentEntryId
            )
        }

        if (showLyricsSheet) {
            LyricsSheet(
                track = track,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = effectiveDurationMs,
                onSeekTo = onSeekTo,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onDismiss = { showLyricsSheet = false }
            )
        }

        if (showSleepTimerSheet && sleepTimerManager != null) {
            SleepTimerSheet(
                sleepTimerManager = sleepTimerManager,
                onDismiss = { showSleepTimerSheet = false }
            )
        }

        if (showDeleteConfirmDialog) {
            ActionConfirmDialog(
                title = stringResource(R.string.action_sheet_remove_download_confirm_title),
                message = stringResource(R.string.downloads_delete_confirm_msg, track.title),
                confirmText = stringResource(R.string.action_delete),
                dismissText = stringResource(R.string.action_cancel),
                isDestructive = true,
                onConfirm = {
                    coroutineScope.launch {
                        offlineDownloadManager?.deleteDownloadedTrack(track.id)
                    }
                    showDeleteConfirmDialog = false
                },
                onDismiss = { showDeleteConfirmDialog = false }
            )
        }
    }
}
