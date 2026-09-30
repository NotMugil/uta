package com.notmugil.uta.ui.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun PullToRefreshRevealLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    refreshingHeight: Dp = 52.dp,
    pullThreshold: Dp = 64.dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val thresholdPx = with(density) { pullThreshold.toPx() }
    val refreshingHeightPx = with(density) { refreshingHeight.toPx() }

    val pullOffset = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var hasCrossedThreshold by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (!isDragging) {
            if (isRefreshing) {
                pullOffset.animateTo(
                    targetValue = refreshingHeightPx,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            } else {
                pullOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            }
        }
    }

    val nestedScrollConnection = remember(thresholdPx, refreshingHeightPx, isRefreshing) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f && pullOffset.value > 0f) {
                    isDragging = true
                    val current = pullOffset.value
                    val consumedY = available.y.coerceAtLeast(-current)
                    val next = (current + consumedY).coerceAtLeast(0f)
                    coroutineScope.launch { pullOffset.snapTo(next) }

                    if (next < thresholdPx) {
                        hasCrossedThreshold = false
                    }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    isDragging = true
                    val current = pullOffset.value
                    val friction = (1f - (current / (thresholdPx * 2.5f)).coerceIn(0f, 0.75f)) * 0.5f
                    val next = (current + available.y * friction).coerceAtLeast(0f)
                    coroutineScope.launch { pullOffset.snapTo(next) }

                    if (next >= thresholdPx && !hasCrossedThreshold) {
                        hasCrossedThreshold = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else if (next < thresholdPx && hasCrossedThreshold) {
                        hasCrossedThreshold = false
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val current = pullOffset.value
                val wasDragging = isDragging
                isDragging = false

                if (current > 0f) {
                    if (current >= thresholdPx && !isRefreshing) {
                        onRefresh()
                        pullOffset.animateTo(
                            targetValue = refreshingHeightPx,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            )
                        )
                    } else if (isRefreshing) {
                        pullOffset.animateTo(
                            targetValue = refreshingHeightPx,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                    } else {
                        pullOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                    hasCrossedThreshold = false
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        val currentHeightDp = with(density) { pullOffset.value.toDp() }
        val progress = (pullOffset.value / thresholdPx).coerceIn(0f, 1f)

        if (currentHeightDp > 0.5.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentHeightDp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                val scale = if (isRefreshing) 1f else (0.4f + progress * 0.6f)
                val alpha = if (isRefreshing) 1f else progress.coerceIn(0f, 1f)
                val rotation = if (isRefreshing) 0f else progress * 360f

                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        rotationZ = rotation
                    },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            content()
        }
    }
}
