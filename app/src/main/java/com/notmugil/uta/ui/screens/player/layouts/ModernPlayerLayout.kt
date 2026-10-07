package com.notmugil.uta.ui.screens.player.layouts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.SleepTimerMode
import com.notmugil.uta.ui.screens.player.components.LeftAlignedMetadataRow
import com.notmugil.uta.ui.screens.player.components.PlaybackControlsSection
import com.notmugil.uta.ui.screens.player.components.PlayerBottomActionsBar
import com.notmugil.uta.ui.screens.player.components.PlayerSeekBarSection
import com.notmugil.uta.ui.screens.player.components.PlayerTopBar
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
    onToggleDownload: () -> Unit = {},
    trackStats: String? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ModernPlayerAmbientBackground(isPlaying = isPlaying)

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
                songKey = track?.id,
                trackStats = trackStats
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
}

@Composable
private fun ModernPlayerAmbientBackground(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val dynamicThemeManager = LocalDynamicThemeManager.current
    val dynamicDarkBgColor by (dynamicThemeManager?.dynamicDarkBgColor?.collectAsState() ?: remember { mutableStateOf(null) })
    val dynamicSeedColor by (dynamicThemeManager?.dynamicSeedColor?.collectAsState() ?: remember { mutableStateOf(null) })

    val isLightMode = (0.2126f * MaterialTheme.colorScheme.background.red +
            0.7152f * MaterialTheme.colorScheme.background.green +
            0.0722f * MaterialTheme.colorScheme.background.blue) > 0.5f

    val ambientBgColor = if (isLightMode) MaterialTheme.colorScheme.background else (dynamicDarkBgColor ?: Color(0xFF101014))
    val targetBlobColor = dynamicSeedColor ?: MaterialTheme.colorScheme.primary

    val blobColor by animateColorAsState(targetBlobColor, animationSpec = tween(700), label = "modern_player_blob")

    val ambientProgress = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val current = ambientProgress.value
                ambientProgress.animateTo(
                    targetValue = current + 1f,
                    animationSpec = tween(durationMillis = 44000, easing = LinearEasing)
                )
            }
        } else {
            ambientProgress.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ambientBgColor)
    ) {
        val t = ambientProgress.value
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(50.dp)
        ) {
            val w = size.width
            val h = size.height
            val twoPi = 6.2831855f

            val p1Alpha1 = if (isLightMode) 0.34f else 0.58f
            val p1Alpha2 = if (isLightMode) 0.11f else 0.22f
            val p2Alpha1 = if (isLightMode) 0.32f else 0.54f
            val p2Alpha2 = if (isLightMode) 0.10f else 0.20f

            val c1X = w * (0.50f + 0.24f * sin(t * twoPi * 0.45f) + 0.10f * cos(t * twoPi * 0.72f))
            val c1Y = h * (0.12f + 0.06f * cos(t * twoPi * 0.38f) + 0.03f * sin(t * twoPi * 0.65f))
            val r1X = w * 0.58f * (1f + 0.10f * sin(t * 2.8f))
            val r1Y = h * 0.46f * (1f - 0.10f * cos(t * 2.4f))
            val tiltAngle1 = 20f * sin(t * 3.2f)

            val path1 = createOrganicBlobPath(
                centerX = c1X,
                centerY = c1Y,
                baseRadiusX = r1X,
                baseRadiusY = r1Y,
                pointCount = 8,
                time = t,
                speed = 3.4f,
                distortion = 0.36f,
                phase = 0.0f
            )

            withTransform({
                rotate(tiltAngle1, Offset(c1X, c1Y))
            }) {
                drawPath(
                    path = path1,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            blobColor.copy(alpha = p1Alpha1),
                            blobColor.copy(alpha = p1Alpha2),
                            Color.Transparent
                        ),
                        center = Offset(c1X, c1Y),
                        radius = (r1X.coerceAtLeast(r1Y) * 1.15f)
                    )
                )
            }

            val c2X = w * (0.50f - 0.25f * cos(t * twoPi * 0.40f) - 0.10f * sin(t * twoPi * 0.82f))
            val c2Y = h * (0.75f - 0.06f * sin(t * twoPi * 0.35f) + 0.03f * cos(t * twoPi * 0.58f))
            val r2X = w * 0.62f * (1f - 0.10f * cos(t * 2.5f))
            val r2Y = h * 0.48f * (1f + 0.10f * sin(t * 2.7f))
            val tiltAngle2 = -22f * cos(t * 2.8f)

            val path2 = createOrganicBlobPath(
                centerX = c2X,
                centerY = c2Y,
                baseRadiusX = r2X,
                baseRadiusY = r2Y,
                pointCount = 8,
                time = t,
                speed = 3.0f,
                distortion = 0.38f,
                phase = 2.1f
            )

            withTransform({
                rotate(tiltAngle2, Offset(c2X, c2Y))
            }) {
                drawPath(
                    path = path2,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            blobColor.copy(alpha = p2Alpha1),
                            blobColor.copy(alpha = p2Alpha2),
                            Color.Transparent
                        ),
                        center = Offset(c2X, c2Y),
                        radius = (r2X.coerceAtLeast(r2Y) * 1.15f)
                    )
                )
            }
        }
    }
}

private fun createOrganicBlobPath(
    centerX: Float,
    centerY: Float,
    baseRadiusX: Float,
    baseRadiusY: Float,
    pointCount: Int = 8,
    time: Float,
    speed: Float,
    distortion: Float,
    phase: Float
): Path {
    val path = Path()
    val points = ArrayList<Offset>(pointCount)
    val angleStep = (2.0 * Math.PI / pointCount).toFloat()

    for (i in 0 until pointCount) {
        val angle = i * angleStep
        val wave1 = sin(angle * 2f + time * speed + phase)
        val wave2 = cos(angle * 3f - time * speed * 1.3f + phase * 1.5f)
        val wave3 = sin(angle * 1f + time * speed * 0.7f)
        val factor = 1f + distortion * (0.55f * wave1 + 0.30f * wave2 + 0.15f * wave3)

        val rx = baseRadiusX * factor
        val ry = baseRadiusY * factor
        val px = centerX + rx * cos(angle)
        val py = centerY + ry * sin(angle)
        points.add(Offset(px, py))
    }

    val n = points.size
    val firstMid = Offset(
        (points[0].x + points[n - 1].x) / 2f,
        (points[0].y + points[n - 1].y) / 2f
    )
    path.moveTo(firstMid.x, firstMid.y)

    for (i in 0 until n) {
        val current = points[i]
        val next = points[(i + 1) % n]
        val midX = (current.x + next.x) / 2f
        val midY = (current.y + next.y) / 2f
        path.quadraticTo(current.x, current.y, midX, midY)
    }
    path.close()
    return path
}
