package com.notmugil.uta.ui.screens.player.layouts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
    Box(modifier = Modifier.fillMaxSize()) {
        DefaultPlayerAmbientBackground(isPlaying = isPlaying)

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
                AnimatedContent(
                    targetState = prevItem,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(320, easing = EaseOutCubic))
                            .togetherWith(fadeOut(animationSpec = tween(220, easing = EaseInCubic)))
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = -sideOffsetDistance),
                    label = "default_player_prev_art"
                ) { item ->
                    if (item != null) {
                        Box(
                            modifier = Modifier
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
                                coverArtId = item.track.coverArtId,
                                contentDescription = item.track.title,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = nextItem,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(320, easing = EaseOutCubic))
                            .togetherWith(fadeOut(animationSpec = tween(220, easing = EaseInCubic)))
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = sideOffsetDistance),
                    label = "default_player_next_art"
                ) { item ->
                    if (item != null) {
                        Box(
                            modifier = Modifier
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
                                coverArtId = item.track.coverArtId,
                                contentDescription = item.track.title,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(360, easing = EaseOutCubic)) +
                                scaleIn(initialScale = 0.93f, animationSpec = tween(360, easing = EaseOutCubic)))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(240, easing = EaseInCubic)) +
                                    scaleOut(targetScale = 0.93f, animationSpec = tween(240, easing = EaseInCubic))
                                )
                        },
                        label = "default_player_center_art"
                    ) { currentTrack ->
                        com.notmugil.uta.ui.shared.AnimatedAlbumArtView(
                            track = currentTrack,
                            isPlaying = isPlaying,
                            shape = RoundedCornerShape(26.dp),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(320, easing = EaseOutCubic)) +
                                slideInVertically(animationSpec = tween(320, easing = EaseOutCubic)) { it / 6 })
                                .togetherWith(
                                    fadeOut(animationSpec = tween(200, easing = EaseInCubic)) +
                                    slideOutVertically(animationSpec = tween(200, easing = EaseInCubic)) { -it / 6 }
                                )
                        },
                        label = "default_player_metadata"
                    ) { targetTrack ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = targetTrack?.title ?: stringResource(R.string.album_unknown_album),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            if (targetTrack != null) {
                                Text(
                                    text = targetTrack.artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .basicMarquee()
                                        .clickable(enabled = targetTrack.artistId != null) {
                                            targetTrack.artistId?.let { onNavigateToArtist(it) }
                                        }
                                )
                            }
                        }
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
}

@Composable
private fun DefaultPlayerAmbientBackground(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val dynamicThemeManager = LocalDynamicThemeManager.current
    val dynamicDarkBgColor by (dynamicThemeManager?.dynamicDarkBgColor?.collectAsState() ?: remember { mutableStateOf(null) })
    val dynamicSeedColor by (dynamicThemeManager?.dynamicSeedColor?.collectAsState() ?: remember { mutableStateOf(null) })

    val isLightMode = (0.2126f * MaterialTheme.colorScheme.background.red +
            0.7152f * MaterialTheme.colorScheme.background.green +
            0.0722f * MaterialTheme.colorScheme.background.blue) > 0.5f

    val targetBgColor = if (isLightMode) MaterialTheme.colorScheme.background else (dynamicDarkBgColor ?: Color(0xFF101014))
    val targetBlobColor = dynamicSeedColor ?: MaterialTheme.colorScheme.primary

    val ambientBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 750, easing = EaseInOutCubic),
        label = "default_player_ambient_bg"
    )
    val blobColor by animateColorAsState(
        targetValue = targetBlobColor,
        animationSpec = tween(durationMillis = 750, easing = EaseInOutCubic),
        label = "default_player_blob"
    )

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

            val p1Alpha1 = if (isLightMode) 0.28f else 0.52f
            val p1Alpha2 = if (isLightMode) 0.09f else 0.18f
            val p2Alpha1 = if (isLightMode) 0.25f else 0.48f
            val p2Alpha2 = if (isLightMode) 0.08f else 0.16f
            val s3Alpha1 = if (isLightMode) 0.24f else 0.46f
            val s3Alpha2 = if (isLightMode) 0.07f else 0.14f
            val s4Alpha1 = if (isLightMode) 0.22f else 0.44f
            val s4Alpha2 = if (isLightMode) 0.07f else 0.14f
            val s5Alpha1 = if (isLightMode) 0.25f else 0.48f
            val s5Alpha2 = if (isLightMode) 0.08f else 0.16f

            val c1X = w * (0.50f + 0.36f * sin(t * twoPi * 0.45f) + 0.16f * cos(t * twoPi * 0.72f))
            val c1Y = h * (0.35f + 0.12f * cos(t * twoPi * 0.38f) + 0.05f * sin(t * twoPi * 0.65f))
            val r1X = w * 0.85f * (1f + 0.15f * sin(t * 2.8f))
            val r1Y = w * 0.75f * (1f - 0.15f * cos(t * 2.4f))
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
                        blobColor.copy(alpha = p1Alpha1),
                        blobColor.copy(alpha = p1Alpha2),
                        Color.Transparent
                    ),
                    center = Offset(c1X, c1Y),
                    radius = (r1X.coerceAtLeast(r1Y) * 1.15f)
                )
            )

            val c2X = w * (0.50f - 0.34f * cos(t * twoPi * 0.40f) - 0.18f * sin(t * twoPi * 0.82f))
            val c2Y = h * (0.55f - 0.10f * sin(t * twoPi * 0.35f) + 0.05f * cos(t * twoPi * 0.58f))
            val r2X = w * 0.80f * (1f - 0.16f * cos(t * 2.5f))
            val r2Y = w * 0.85f * (1f + 0.16f * sin(t * 2.7f))
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
                        blobColor.copy(alpha = p2Alpha1),
                        blobColor.copy(alpha = p2Alpha2),
                        Color.Transparent
                    ),
                    center = Offset(c2X, c2Y),
                    radius = (r2X.coerceAtLeast(r2Y) * 1.15f)
                )
            )

            val c3X = w * (0.50f + 0.60f * sin(t * twoPi * 0.80f + 1.2f) - 0.18f * cos(t * twoPi * 1.35f))
            val c3Y = h * (0.28f + 0.10f * sin(t * twoPi * 0.70f) - 0.05f * cos(t * twoPi * 1.10f))
            val r3X = w * 0.90f * (1f + 0.18f * sin(t * 4.8f))
            val r3Y = h * 0.22f * (1f - 0.18f * sin(t * 4.8f))
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
                        blobColor.copy(alpha = s3Alpha1),
                        blobColor.copy(alpha = s3Alpha2),
                        Color.Transparent
                    ),
                    center = Offset(c3X, c3Y),
                    radius = (r3X * 1.1f)
                )
            )

            val c4X = w * (0.50f - 0.55f * cos(t * twoPi * 0.75f + 0.7f) + 0.20f * sin(t * twoPi * 1.45f))
            val c4Y = h * (0.72f + 0.08f * cos(t * twoPi * 0.65f) + 0.04f * sin(t * twoPi * 1.05f))
            val r4X = w * 0.85f * (1f + 0.16f * cos(t * 4.2f))
            val r4Y = h * 0.20f * (1f - 0.16f * sin(t * 4.2f))
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
                            blobColor.copy(alpha = s4Alpha1),
                            blobColor.copy(alpha = s4Alpha2),
                            Color.Transparent
                        ),
                        center = Offset(c4X, c4Y),
                        radius = (r4X * 1.1f)
                    )
                )
            }

            val c5X = w * (0.25f + 0.50f * sin(t * twoPi * 0.90f + 2.5f) - 0.18f * cos(t * twoPi * 1.60f))
            val c5Y = h * (0.85f - 0.08f * sin(t * twoPi * 0.80f) + 0.04f * cos(t * twoPi * 1.25f))
            val r5X = w * 0.80f * (1f + 0.20f * sin(t * 5.5f))
            val r5Y = w * 0.70f * (1f - 0.20f * cos(t * 5.5f))
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
                        blobColor.copy(alpha = s5Alpha1),
                        blobColor.copy(alpha = s5Alpha2),
                        Color.Transparent
                    ),
                    center = Offset(c5X, c5Y),
                    radius = (r5X * 1.15f)
                )
            )
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
