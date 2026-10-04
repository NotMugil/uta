package com.notmugil.uta.ui.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.notmugil.uta.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PullToRefreshRevealLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onFullSync: (() -> Unit)? = null,
    syncProgressMessage: String? = null,
    refreshingHeight: Dp = 52.dp,
    fullSyncRefreshingHeight: Dp = 68.dp,
    pullThreshold: Dp = 64.dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val thresholdPx = with(density) { pullThreshold.toPx() }
    val refreshingHeightPx = with(density) { refreshingHeight.toPx() }
    val fullSyncRefreshingHeightPx = with(density) { fullSyncRefreshingHeight.toPx() }

    val pullOffset = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var hasCrossedThreshold by remember { mutableStateOf(false) }
    var holdDurationMs by remember { mutableLongStateOf(0L) }
    var isFullSyncArmed by remember { mutableStateOf(false) }
    var isFullSyncMode by remember { mutableStateOf(false) }
    var showCompletionSuccess by remember { mutableStateOf(false) }

    // Hold detection past threshold
    LaunchedEffect(hasCrossedThreshold, isDragging, onFullSync) {
        if (onFullSync == null || !hasCrossedThreshold || !isDragging) {
            holdDurationMs = 0L
            isFullSyncArmed = false
            return@LaunchedEffect
        }

        val start = System.currentTimeMillis()
        while (isActive && hasCrossedThreshold && isDragging) {
            val elapsed = System.currentTimeMillis() - start
            holdDurationMs = elapsed
            if (elapsed >= 1500L && !isFullSyncArmed) {
                isFullSyncArmed = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            delay(20L)
        }
    }

    // React to refresh state changes
    LaunchedEffect(isRefreshing) {
        if (!isDragging) {
            if (isRefreshing) {
                val targetPx = if (isFullSyncMode) fullSyncRefreshingHeightPx else refreshingHeightPx
                pullOffset.animateTo(
                    targetValue = targetPx,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            } else {
                if (isFullSyncMode) {
                    showCompletionSuccess = true
                    delay(1200L)
                    showCompletionSuccess = false
                    isFullSyncMode = false
                    isFullSyncArmed = false
                    holdDurationMs = 0L
                    pullOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                } else {
                    isFullSyncArmed = false
                    holdDurationMs = 0L
                    pullOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                }
            }
        }
    }

    val nestedScrollConnection = remember(thresholdPx, refreshingHeightPx, fullSyncRefreshingHeightPx, isRefreshing, onFullSync) {
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
                val wasFullSyncArmed = isFullSyncArmed
                isDragging = false

                if (current > 0f) {
                    if (current >= thresholdPx && !isRefreshing) {
                        if (wasFullSyncArmed && onFullSync != null) {
                            isFullSyncMode = true
                            showCompletionSuccess = false
                            onFullSync()
                            pullOffset.animateTo(
                                targetValue = fullSyncRefreshingHeightPx,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                )
                            )
                        } else {
                            isFullSyncMode = false
                            showCompletionSuccess = false
                            onRefresh()
                            pullOffset.animateTo(
                                targetValue = refreshingHeightPx,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                )
                            )
                        }
                    } else if (isRefreshing) {
                        val targetPx = if (isFullSyncMode) fullSyncRefreshingHeightPx else refreshingHeightPx
                        pullOffset.animateTo(
                            targetValue = targetPx,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                    } else {
                        pullOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                    hasCrossedThreshold = false
                    isFullSyncArmed = false
                    holdDurationMs = 0L
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
                val scale = if (isRefreshing || showCompletionSuccess) 1f else (0.4f + progress * 0.6f)
                val alpha = if (isRefreshing || showCompletionSuccess) 1f else progress.coerceIn(0f, 1f)
                val rotation = if (isRefreshing || showCompletionSuccess) 0f else progress * 360f

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    if (showCompletionSuccess) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.full_sync_completed),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
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
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        }

                        // Hint when dragging / holding past threshold
                        if (isDragging && hasCrossedThreshold && onFullSync != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isFullSyncArmed) {
                                    Icon(
                                        imageVector = Tabler.Outline.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (isFullSyncArmed) {
                                        stringResource(R.string.pull_refresh_release_full_sync)
                                    } else {
                                        stringResource(R.string.pull_refresh_hold_hint)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isFullSyncArmed) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isFullSyncArmed) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        } else if (isFullSyncMode && isRefreshing && !syncProgressMessage.isNullOrBlank()) {
                            // Stage/count progress text while full sync is running
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = syncProgressMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
