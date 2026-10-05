package com.notmugil.uta.ui.screens.player.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MiniWaveformEqualizer(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    idleFraction: Float = 0.2f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")

    val h1Anim by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val h2Anim by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 540, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val h3Anim by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )

    val playProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "waveformPlayProgress"
    )

    val h1 = (idleFraction + (h1Anim - idleFraction) * playProgress).coerceIn(0.08f, 1f)
    val h2 = (idleFraction + (h2Anim - idleFraction) * playProgress).coerceIn(0.08f, 1f)
    val h3 = (idleFraction + (h3Anim - idleFraction) * playProgress).coerceIn(0.08f, 1f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = 2.4.dp.toPx()
        val spacing = 2.dp.toPx()
        val totalW = 3 * barW + 2 * spacing
        val startX = (w - totalW) / 2f
        val cy = h / 2f

        val heights = listOf(h1, h2, h3)

        heights.forEachIndexed { i, factor ->
            val barH = (h * factor).coerceIn(2.dp.toPx(), h)
            val bx = startX + i * (barW + spacing)
            drawRoundRect(
                color = color,
                topLeft = Offset(bx, cy - barH / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}
