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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.SleepTimerMode
import com.notmugil.uta.ui.screens.player.components.LeftAlignedMetadataRow
import com.notmugil.uta.ui.screens.player.components.PlaybackControlsSection
import com.notmugil.uta.ui.screens.player.components.PlayerBottomActionsBar
import com.notmugil.uta.ui.screens.player.components.PlayerSeekBarSection
import com.notmugil.uta.ui.theme.DarkColorScheme
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import com.notmugil.uta.ui.theme.UtaTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.isActive

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
        val dynamicDarkBgColor by (dynamicThemeManager?.dynamicDarkBgColor?.collectAsState() ?: remember { mutableStateOf(null) })
        val dynamicSeedColor by (dynamicThemeManager?.dynamicSeedColor?.collectAsState() ?: remember { mutableStateOf(null) })
        val dynamicSecondaryColor by (dynamicThemeManager?.dynamicSecondaryColor?.collectAsState() ?: remember { mutableStateOf(null) })
        val cinematicBgColor = dynamicDarkBgColor ?: Color(0xFF101014)

        val targetPrimary = dynamicSeedColor ?: MaterialTheme.colorScheme.primary
        val targetSecondary = dynamicSecondaryColor ?: deriveLighterTone(targetPrimary)

        val primaryBlobColor by animateColorAsState(targetPrimary, animationSpec = tween(700), label = "cinematic_primary_blob")
        val secondaryBlobColor by animateColorAsState(targetSecondary, animationSpec = tween(700), label = "cinematic_secondary_blob")

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
            modifier = Modifier
                .fillMaxSize()
                .background(cinematicBgColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.60f)
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
                    highRes = true,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.30f),
                                    0.15f to Color.Transparent,
                                    0.40f to Color.Transparent,
                                    0.60f to cinematicBgColor.copy(alpha = 0.35f),
                                    0.76f to cinematicBgColor.copy(alpha = 0.72f),
                                    0.88f to cinematicBgColor.copy(alpha = 0.92f),
                                    0.96f to cinematicBgColor,
                                    1.0f to cinematicBgColor
                                )
                            )
                        )
                )
            }

            val t = ambientProgress.value
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(70.dp)
            ) {
                val w = size.width
                val h = size.height
                val twoPi = 6.2831855f

                val c1X = w * (0.50f + 0.38f * sin(t * twoPi * 0.45f) + 0.18f * cos(t * twoPi * 0.72f))
                val c1Y = h * (0.78f + 0.07f * cos(t * twoPi * 0.38f) + 0.04f * sin(t * twoPi * 0.65f))
                val r1X = w * 0.72f * (1f + 0.16f * sin(t * 2.8f))
                val r1Y = w * 0.64f * (1f - 0.16f * cos(t * 2.4f))
                val path1 = createOrganicBlobPath(
                    centerX = c1X,
                    centerY = c1Y,
                    baseRadiusX = r1X,
                    baseRadiusY = r1Y,
                    pointCount = 8,
                    time = t,
                    speed = 3.6f,
                    distortion = 0.38f,
                    phase = 0.0f
                )
                drawPath(
                    path = path1,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryBlobColor.copy(alpha = 0.54f),
                            primaryBlobColor.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(c1X, c1Y),
                        radius = (r1X.coerceAtLeast(r1Y) * 1.15f)
                    )
                )

                val c2X = w * (0.50f - 0.36f * cos(t * twoPi * 0.40f) - 0.20f * sin(t * twoPi * 0.82f))
                val c2Y = h * (0.82f - 0.06f * sin(t * twoPi * 0.35f) + 0.04f * cos(t * twoPi * 0.58f))
                val r2X = w * 0.68f * (1f - 0.18f * cos(t * 2.5f))
                val r2Y = w * 0.72f * (1f + 0.18f * sin(t * 2.7f))
                val path2 = createOrganicBlobPath(
                    centerX = c2X,
                    centerY = c2Y,
                    baseRadiusX = r2X,
                    baseRadiusY = r2Y,
                    pointCount = 8,
                    time = t,
                    speed = 3.2f,
                    distortion = 0.40f,
                    phase = 2.1f
                )
                drawPath(
                    path = path2,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryBlobColor.copy(alpha = 0.50f),
                            primaryBlobColor.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(c2X, c2Y),
                        radius = (r2X.coerceAtLeast(r2Y) * 1.15f)
                    )
                )

                val c3X = w * (0.50f + 0.65f * sin(t * twoPi * 0.80f + 1.2f) - 0.20f * cos(t * twoPi * 1.35f))
                val c3Y = h * (0.70f + 0.08f * sin(t * twoPi * 0.70f) - 0.04f * cos(t * twoPi * 1.10f))
                val r3X = w * 0.95f * (1f + 0.20f * sin(t * 4.8f))
                val r3Y = h * 0.18f * (1f - 0.20f * sin(t * 4.8f))
                val path3 = createOrganicBlobPath(
                    centerX = c3X,
                    centerY = c3Y,
                    baseRadiusX = r3X,
                    baseRadiusY = r3Y,
                    pointCount = 10,
                    time = t,
                    speed = 5.8f,
                    distortion = 0.42f,
                    phase = 4.3f
                )
                drawPath(
                    path = path3,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryBlobColor.copy(alpha = 0.48f),
                            secondaryBlobColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(c3X, c3Y),
                        radius = (r3X * 1.1f)
                    )
                )

                val c4X = w * (0.50f - 0.60f * cos(t * twoPi * 0.75f + 0.7f) + 0.22f * sin(t * twoPi * 1.45f))
                val c4Y = h * (0.58f + 0.05f * cos(t * twoPi * 0.65f) + 0.03f * sin(t * twoPi * 1.05f))
                val r4X = w * 0.80f * (1f + 0.18f * cos(t * 4.2f))
                val r4Y = h * 0.16f * (1f - 0.18f * sin(t * 4.2f))
                val tiltAngle = 30f * sin(t * 4.5f)
                val path4 = createOrganicBlobPath(
                    centerX = c4X,
                    centerY = c4Y,
                    baseRadiusX = r4X,
                    baseRadiusY = r4Y,
                    pointCount = 8,
                    time = t,
                    speed = 5.2f,
                    distortion = 0.40f,
                    phase = 1.2f
                )
                withTransform({
                    rotate(tiltAngle, Offset(c4X, c4Y))
                }) {
                    drawPath(
                        path = path4,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                secondaryBlobColor.copy(alpha = 0.44f),
                                secondaryBlobColor.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(c4X, c4Y),
                            radius = (r4X * 1.1f)
                        )
                    )
                }

                val c5X = w * (0.15f + 0.55f * sin(t * twoPi * 0.90f + 2.5f) - 0.20f * cos(t * twoPi * 1.60f))
                val c5Y = h * (0.68f - 0.08f * sin(t * twoPi * 0.80f) + 0.05f * cos(t * twoPi * 1.25f))
                val r5X = w * 0.65f * (1f + 0.22f * sin(t * 5.5f))
                val r5Y = w * 0.58f * (1f - 0.22f * cos(t * 5.5f))
                val path5 = createOrganicBlobPath(
                    centerX = c5X,
                    centerY = c5Y,
                    baseRadiusX = r5X,
                    baseRadiusY = r5Y,
                    pointCount = 7,
                    time = t,
                    speed = 6.8f,
                    distortion = 0.45f,
                    phase = 3.5f
                )
                drawPath(
                    path = path5,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryBlobColor.copy(alpha = 0.50f),
                            secondaryBlobColor.copy(alpha = 0.17f),
                            Color.Transparent
                        ),
                        center = Offset(c5X, c5Y),
                        radius = (r5X * 1.15f)
                    )
                )

                val c6X = w * (0.85f - 0.55f * cos(t * twoPi * 0.88f + 3.1f) + 0.22f * sin(t * twoPi * 1.65f))
                val c6Y = h * (0.75f + 0.08f * cos(t * twoPi * 0.72f) - 0.05f * sin(t * twoPi * 1.20f))
                val r6X = w * 0.60f * (1f - 0.20f * cos(t * 5.2f))
                val r6Y = w * 0.65f * (1f + 0.20f * sin(t * 5.2f))
                val path6 = createOrganicBlobPath(
                    centerX = c6X,
                    centerY = c6Y,
                    baseRadiusX = r6X,
                    baseRadiusY = r6Y,
                    pointCount = 7,
                    time = t,
                    speed = 6.4f,
                    distortion = 0.44f,
                    phase = 5.1f
                )
                drawPath(
                    path = path6,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryBlobColor.copy(alpha = 0.46f),
                            secondaryBlobColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(c6X, c6Y),
                        radius = (r6Y * 1.15f)
                    )
                )

                val c7X = w * (0.50f + 0.58f * sin(t * twoPi * 0.68f + 4.0f) - 0.22f * cos(t * twoPi * 1.30f))
                val c7Y = h * (0.92f - 0.06f * sin(t * twoPi * 0.55f) + 0.04f * cos(t * twoPi * 0.95f))
                val r7X = w * 0.88f * (1f + 0.18f * sin(t * 4.6f))
                val r7Y = h * 0.18f * (1f - 0.18f * cos(t * 4.6f))
                val path7 = createOrganicBlobPath(
                    centerX = c7X,
                    centerY = c7Y,
                    baseRadiusX = r7X,
                    baseRadiusY = r7Y,
                    pointCount = 8,
                    time = t,
                    speed = 5.4f,
                    distortion = 0.38f,
                    phase = 2.8f
                )
                drawPath(
                    path = path7,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryBlobColor.copy(alpha = 0.42f),
                            secondaryBlobColor.copy(alpha = 0.13f),
                            Color.Transparent
                        ),
                        center = Offset(c7X, c7Y),
                        radius = (r7X * 1.1f)
                    )
                )
            }

            val cinematicColorScheme = DarkColorScheme.copy(
                primary = Color.White,
                onPrimary = Color.Black,
                secondary = Color.White,
                onSecondary = Color.Black,
                tertiary = Color.White,
                onTertiary = Color.Black,
                background = cinematicBgColor,
                onBackground = Color.White,
                surface = cinematicBgColor,
                onSurface = Color.White,
                surfaceVariant = Color.White.copy(alpha = 0.20f),
                onSurfaceVariant = Color.White.copy(alpha = 0.65f)
            )

            MaterialTheme(colorScheme = cinematicColorScheme) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Spacer(modifier = Modifier.height(260.dp))

                    LeftAlignedMetadataRow(
                        track = track,
                        isStarred = isStarred,
                        onToggleFavorite = onToggleFavorite,
                        onNavigateToArtist = onNavigateToArtist,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onMoreOptions = onMoreOptions,
                        enableMarquee = false
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

private fun deriveLighterTone(color: Color): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(
        android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        ),
        hsl
    )
    hsl[2] = (hsl[2] * 1.25f + 0.12f).coerceIn(0.48f, 0.68f)
    hsl[1] = (hsl[1] * 0.95f).coerceIn(0.35f, 0.85f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}
