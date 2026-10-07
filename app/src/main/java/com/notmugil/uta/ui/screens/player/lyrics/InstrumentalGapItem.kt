package com.notmugil.uta.ui.screens.player.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Music
import com.notmugil.uta.data.preferences.LyricsStyle
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun InstrumentalGapItem(
    gap: LyricsItem.InstrumentalGap,
    currentPositionMs: Long,
    isActive: Boolean,
    accentColor: Color,
    textColor: Color,
    dimColor: Color,
    onSeekTo: (Long) -> Unit,
    lyricsStyle: LyricsStyle = LyricsStyle.DEFAULT,
    modifier: Modifier = Modifier
) {
    val duration = (gap.endMs - gap.startMs).coerceAtLeast(1L).toFloat()
    val rawProgress = ((currentPositionMs - gap.startMs).toFloat() / duration).coerceIn(0f, 1f)
    val isPast = currentPositionMs >= gap.endMs

    val effectiveProgress = if (isActive && !isPast) rawProgress else 0f

    val isLightMode = (0.2126f * MaterialTheme.colorScheme.background.red +
            0.7152f * MaterialTheme.colorScheme.background.green +
            0.0722f * MaterialTheme.colorScheme.background.blue) > 0.5f

    val activeFillColor = accentColor
    val unfilledColor = textColor.copy(alpha = if (isActive) 0.35f else dimColor.alpha.coerceAtMost(0.28f))

    val infiniteTransition = rememberInfiniteTransition(label = "gap_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val bobPhase0 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bob_phase_0"
    )

    val bobPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bob_phase_1"
    )

    val bobPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bob_phase_2"
    )

    val isWaveActive = isActive && !isPast
    val iconFloatY = if (isWaveActive) sin(wavePhase) * 3.2f else 0f

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSeekTo(gap.startMs) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (lyricsStyle == LyricsStyle.BETTER) {
                Icon(
                    imageVector = Tabler.Outline.Music,
                    contentDescription = "Instrumental gap",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            translationY = iconFloatY
                            rotationZ = -12f
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()

                            drawRect(color = unfilledColor, blendMode = BlendMode.SrcIn)

                            if (effectiveProgress > 0f) {
                                val w = size.width
                                val h = size.height
                                val waterLevelY = h * (1f - effectiveProgress)

                                val backWavePath = Path().apply {
                                    moveTo(0f, h)
                                    val amp1 = 3.8f
                                    val freq1 = 2f * PI.toFloat() / w
                                    val p1 = wavePhase
                                    for (px in 0..w.toInt()) {
                                        val x = px.toFloat()
                                        val y = (waterLevelY + sin(p1 + x * freq1) * amp1).coerceIn(0f, h)
                                        lineTo(x, y)
                                    }
                                    lineTo(w, h)
                                    close()
                                }
                                drawPath(
                                    path = backWavePath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            lerp(activeFillColor, Color.White, 0.40f),
                                            activeFillColor.copy(alpha = 0.70f)
                                        ),
                                        startY = (waterLevelY - 4f).coerceAtLeast(0f),
                                        endY = h
                                    ),
                                    blendMode = BlendMode.SrcAtop
                                )

                                val midWavePath = Path().apply {
                                    moveTo(0f, h)
                                    val amp2 = 3.0f
                                    val freq2 = 2.5f * PI.toFloat() / w
                                    val p2 = wavePhase * 1.3f + 1.6f
                                    for (px in 0..w.toInt()) {
                                        val x = px.toFloat()
                                        val y = (waterLevelY + sin(p2 + x * freq2) * amp2).coerceIn(0f, h)
                                        lineTo(x, y)
                                    }
                                    lineTo(w, h)
                                    close()
                                }
                                drawPath(
                                    path = midWavePath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            lerp(activeFillColor, Color.White, 0.35f),
                                            activeFillColor
                                        ),
                                        startY = (waterLevelY - 3f).coerceAtLeast(0f),
                                        endY = h
                                    ),
                                    blendMode = BlendMode.SrcAtop
                                )

                                val frontWavePath = Path().apply {
                                    moveTo(0f, h)
                                    val amp3 = 2.4f
                                    val freq3 = 2f * PI.toFloat() / w
                                    val p3 = wavePhase * 0.9f + 0.8f
                                    for (px in 0..w.toInt()) {
                                        val x = px.toFloat()
                                        val y = (waterLevelY + sin(p3 + x * freq3) * amp3).coerceIn(0f, h)
                                        lineTo(x, y)
                                    }
                                    lineTo(w, h)
                                    close()
                                }
                                drawPath(
                                    path = frontWavePath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            lerp(activeFillColor, Color.White, 0.25f),
                                            activeFillColor
                                        ),
                                        startY = (waterLevelY - 2f).coerceAtLeast(0f),
                                        endY = h
                                    ),
                                    blendMode = BlendMode.SrcAtop
                                )

                                val crestShimmerPath = Path().apply {
                                    val amp3 = 2.4f
                                    val freq3 = 2f * PI.toFloat() / w
                                    val p3 = wavePhase * 0.9f + 0.8f
                                    moveTo(0f, (waterLevelY + sin(p3) * amp3).coerceIn(0f, h))
                                    for (px in 0..w.toInt()) {
                                        val x = px.toFloat()
                                        val y = (waterLevelY + sin(p3 + x * freq3) * amp3).coerceIn(0f, h)
                                        lineTo(x, y)
                                    }
                                    for (px in w.toInt() downTo 0) {
                                        val x = px.toFloat()
                                        val y = (waterLevelY + sin(p3 + x * freq3) * amp3 + 1.8f).coerceIn(0f, h)
                                        lineTo(x, y)
                                    }
                                    close()
                                }
                                val shimmerColor = if (isLightMode) lerp(activeFillColor, Color.White, 0.40f).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.85f)
                                drawPath(
                                    path = crestShimmerPath,
                                    color = shimmerColor,
                                    blendMode = BlendMode.SrcAtop
                                )
                            }
                        }
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    val dotCount = 3
                    val stepInterval = 1f / (dotCount + 1)

                    for (i in 0 until dotCount) {
                        val dotTurnStart = i * stepInterval
                        val dotTurnEnd = (i + 1) * stepInterval

                        val dotColor = if (isActive && !isPast) {
                            if (rawProgress >= dotTurnEnd) {
                                accentColor
                            } else if (rawProgress >= dotTurnStart) {
                                val fraction = ((rawProgress - dotTurnStart) / stepInterval).coerceIn(0f, 1f)
                                lerp(unfilledColor, accentColor, fraction)
                            } else {
                                unfilledColor
                            }
                        } else {
                            unfilledColor
                        }

                        val bobOffsetY = if (isActive && !isPast) {
                            when (i) {
                                0 -> sin(bobPhase0) * 5.0f
                                1 -> (sin(bobPhase1) * 4.2f + sin(bobPhase1 * 2f) * 1.5f)
                                else -> sin(bobPhase2) * 6.0f
                            }
                        } else {
                            0f
                        }

                        val isDotActive = isActive && !isPast && rawProgress >= dotTurnStart
                        val dotBaseSize = 6.dp
                        val dotSize = if (isDotActive) dotBaseSize + 1.dp else dotBaseSize

                        Surface(
                            shape = CircleShape,
                            color = dotColor,
                            modifier = Modifier
                                .offset(y = bobOffsetY.dp)
                                .size(dotSize)
                        ) {}
                    }
                }
            }
        }
    }
}
