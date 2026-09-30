package com.notmugil.uta.ui.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.notmugil.uta.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.data.preferences.MiniPlayerPlacement
import com.notmugil.uta.data.preferences.MiniPlayerStyle
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    currentTrack: TrackItem?,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBuffering: Boolean = false,
    onSkipNext: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
    isStarred: Boolean = false,
    onToggleFavorite: () -> Unit = {}
) {
    val prefs = LocalAppPreferences.current
    val dynamicThemeManager = LocalDynamicThemeManager.current

    val miniPlayerStyle by prefs?.miniPlayerStyle?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(MiniPlayerStyle.DEFAULT) }
    val miniPlayerPlacement by prefs?.miniPlayerPlacement?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(MiniPlayerPlacement.ISOLATED) }
    val tintMiniPlayerAccent by prefs?.tintMiniPlayerAccent?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(true) }

    val dynamicDarkBg by dynamicThemeManager?.dynamicDarkBgColor?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(null) }

    val accentColor = MaterialTheme.colorScheme.primary
    val defaultBlackColor = Color.Black
    val containerBgColor = if (miniPlayerStyle == MiniPlayerStyle.AMBIENT) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else if (tintMiniPlayerAccent) {
        dynamicDarkBg?.let {
            Color(
                red = (it.red * 0.75f + defaultBlackColor.red * 0.25f),
                green = (it.green * 0.75f + defaultBlackColor.green * 0.25f),
                blue = (it.blue * 0.75f + defaultBlackColor.blue * 0.25f),
                alpha = 0.98f
            )
        } ?: Color(
            red = (accentColor.red * 0.35f + defaultBlackColor.red * 0.65f),
            green = (accentColor.green * 0.35f + defaultBlackColor.green * 0.65f),
            blue = (accentColor.blue * 0.35f + defaultBlackColor.blue * 0.65f),
            alpha = 0.98f
        )
    } else {
        defaultBlackColor
    }

    val visible = currentTrack != null

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val track = currentTrack ?: return@AnimatedVisibility
        val animatedProgress by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "miniplayer_progress")

        val coroutineScope = rememberCoroutineScope()
        val offsetY = remember { Animatable(0f) }
        val density = LocalDensity.current
        var cardHeightPx by remember { mutableFloatStateOf(0f) }
        val fallbackHeightPx = with(density) { 68.dp.toPx() }
        val dismissThresholdPx = (if (cardHeightPx > 0f) cardHeightPx else fallbackHeightPx) * 0.8f

        val isCombined = miniPlayerPlacement == MiniPlayerPlacement.COMBINED
        val cornerRadius = if (miniPlayerStyle == MiniPlayerStyle.CAPSULE_NEEDLE || miniPlayerStyle == MiniPlayerStyle.ROTATING_VINYL) 36.dp else 16.dp
        val cardShape = if (isCombined) {
            RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius, bottomStart = 0.dp, bottomEnd = 0.dp)
        } else {
            RoundedCornerShape(cornerRadius)
        }

        val cardPadding = if (isCombined) {
            Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
        } else {
            Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
        }

        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = containerBgColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isCombined) 4.dp else 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(cardPadding)
                .onGloballyPositioned { coordinates ->
                    cardHeightPx = coordinates.size.height.toFloat()
                }
                .offset { IntOffset(0, offsetY.value.roundToInt().coerceAtLeast(0)) }
                .graphicsLayer {
                    alpha = (1f - (offsetY.value / (dismissThresholdPx * 2f))).coerceIn(0.2f, 1f)
                }
                .pointerInput(dismissThresholdPx) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newY = (offsetY.value + dragAmount).coerceAtLeast(0f)
                                offsetY.snapTo(newY)
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetY.value >= dismissThresholdPx) {
                                    offsetY.animateTo(
                                        targetValue = dismissThresholdPx * 2.5f,
                                        animationSpec = tween(150)
                                    )
                                    onDismiss()
                                    offsetY.snapTo(0f)
                                } else {
                                    offsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetY.animateTo(0f)
                            }
                        }
                    )
                }
                .clickable(onClick = onClick)
        ) {
            when (miniPlayerStyle) {
                MiniPlayerStyle.FLUSH_COVER -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverArtImage(
                            coverArtId = track.coverArtId,
                            contentDescription = track.title,
                            shape = RoundedCornerShape(
                                topStart = cornerRadius,
                                bottomStart = if (isCombined) 0.dp else cornerRadius,
                                topEnd = 0.dp,
                                bottomEnd = 0.dp
                            ),
                            size = 60.dp,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(60.dp)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(
                                    RoundedCornerShape(
                                        topEnd = cornerRadius,
                                        bottomEnd = if (isCombined) 0.dp else cornerRadius,
                                        topStart = 0.dp,
                                        bottomStart = 0.dp
                                    )
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                                    .background(accentColor.copy(alpha = 0.25f))
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.basicMarquee()
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = onToggleFavorite) {
                                    Icon(
                                        imageVector = if (isStarred || track.isStarred) Tabler.Filled.Heart else Tabler.Outline.Heart,
                                        contentDescription = if (isStarred || track.isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
                                        tint = if (isStarred || track.isStarred) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(onClick = onTogglePlayPause) {
                                    if (isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.5.dp,
                                            color = accentColor
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlaying) Tabler.Filled.PlayerPause else Tabler.Filled.PlayerPlay,
                                            contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                                            tint = accentColor,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                MiniPlayerStyle.AMBIENT -> {
                    Box(modifier = Modifier.fillMaxWidth().clip(cardShape)) {
                        CoverArtImage(
                            coverArtId = track.coverArtId,
                            contentDescription = null,
                            shape = cardShape,
                            size = 120.dp,
                            modifier = Modifier
                                .matchParentSize()
                                .blur(36.dp)
                                .alpha(0.60f)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.70f)
                                        )
                                    )
                                )
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CoverArtImage(
                                    coverArtId = track.coverArtId,
                                    contentDescription = track.title,
                                    shape = RoundedCornerShape(10.dp),
                                    size = 44.dp,
                                    modifier = Modifier.size(44.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.basicMarquee()
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = onToggleFavorite) {
                                    Icon(
                                        imageVector = if (isStarred || track.isStarred) Tabler.Filled.Heart else Tabler.Outline.Heart,
                                        contentDescription = if (isStarred || track.isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
                                        tint = if (isStarred || track.isStarred) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(onClick = onTogglePlayPause) {
                                    if (isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.5.dp,
                                            color = accentColor
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlaying) Tabler.Filled.PlayerPause else Tabler.Filled.PlayerPlay,
                                            contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                                            tint = accentColor,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = accentColor,
                                trackColor = accentColor.copy(alpha = 0.2f)
                            )
                        }
                    }
                }

                MiniPlayerStyle.ROTATING_VINYL -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotation")
                    val rotationAngle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 8000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "vinyl_angle"
                    )
                    val animatedRotation = if (isPlaying) rotationAngle else 0f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = accentColor,
                                trackColor = accentColor.copy(alpha = 0.2f),
                                strokeWidth = 2.8.dp,
                                strokeCap = StrokeCap.Round
                            )

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .graphicsLayer { rotationZ = animatedRotation }
                                    .clip(CircleShape)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                CoverArtImage(
                                    coverArtId = track.coverArtId,
                                    contentDescription = track.title,
                                    shape = CircleShape,
                                    size = 42.dp,
                                    modifier = Modifier.size(42.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(3.5.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.basicMarquee()
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isStarred || track.isStarred) Tabler.Filled.Heart else Tabler.Outline.Heart,
                                contentDescription = if (isStarred || track.isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
                                tint = if (isStarred || track.isStarred) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(onClick = onTogglePlayPause) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = accentColor
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Tabler.Filled.PlayerPause else Tabler.Filled.PlayerPlay,
                                    contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                                    tint = accentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                MiniPlayerStyle.CAPSULE_NEEDLE -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                                .clickable { onTogglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Tabler.Filled.PlayerPause else Tabler.Filled.PlayerPlay,
                                    contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.basicMarquee()
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val needleTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        Canvas(
                            modifier = Modifier
                                .width(64.dp)
                                .height(26.dp)
                        ) {
                            val w = size.width
                            val h = size.height
                            val cy = h / 2f
                            val activeW = (animatedProgress * w).coerceIn(0f, w)

                            drawLine(
                                color = needleTrackColor,
                                start = Offset(0f, cy),
                                end = Offset(w, cy),
                                strokeWidth = 3.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            if (activeW > 0f) {
                                drawLine(
                                    color = accentColor,
                                    start = Offset(0f, cy),
                                    end = Offset(activeW, cy),
                                    strokeWidth = 3.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            drawLine(
                                color = accentColor,
                                start = Offset(activeW, cy - 7.dp.toPx()),
                                end = Offset(activeW, cy + 7.dp.toPx()),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isStarred || track.isStarred) Tabler.Filled.Heart else Tabler.Outline.Heart,
                                contentDescription = if (isStarred || track.isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
                                tint = if (isStarred || track.isStarred) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                MiniPlayerStyle.DEFAULT -> {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArtImage(
                                coverArtId = track.coverArtId,
                                contentDescription = track.title,
                                shape = RoundedCornerShape(10.dp),
                                size = 46.dp,
                                modifier = Modifier.size(46.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.basicMarquee()
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    imageVector = if (isStarred || track.isStarred) Tabler.Filled.Heart else Tabler.Outline.Heart,
                                    contentDescription = if (isStarred || track.isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
                                    tint = if (isStarred || track.isStarred) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(onClick = onTogglePlayPause) {
                                if (isBuffering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp,
                                        color = accentColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Tabler.Filled.PlayerPause else Tabler.Filled.PlayerPlay,
                                        contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                                        tint = accentColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = accentColor,
                            trackColor = accentColor.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}
