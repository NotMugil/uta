package com.notmugil.uta.ui.screens.player.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.data.preferences.LyricsStyle
import com.notmugil.uta.data.repository.SyncedWord
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun KaraokeWord(
    word: SyncedWord,
    wordEnd: Long,
    currentPositionMs: Long,
    accentColor: Color,
    textColor: Color,
    dimColor: Color,
    fontSize: TextUnit,
    isCurrentLine: Boolean,
    lyricsStyle: LyricsStyle = LyricsStyle.DEFAULT,
    modifier: Modifier = Modifier
) {
    val duration = (wordEnd - word.startMs).toFloat()
    val isWordActive = currentPositionMs >= word.startMs && currentPositionMs < wordEnd
    val isWordSung = currentPositionMs >= wordEnd

    val wordText = if (!word.text.endsWith(" ")) "${word.text} " else word.text

    val timeSinceSung = (currentPositionMs - wordEnd).coerceAtLeast(0L).toFloat()
    val glowDecayDuration = 500f
    val glowEaseOut = if (isWordSung && timeSinceSung < glowDecayDuration) {
        val decayFraction = (timeSinceSung / glowDecayDuration).coerceIn(0f, 1f)
        (1f - decayFraction) * (1f - decayFraction)
    } else {
        0f
    }

    if (!isCurrentLine) {
        val isPastWord = currentPositionMs >= wordEnd
        val isBetter = lyricsStyle == LyricsStyle.BETTER
        val pastAlpha = if (isBetter) (dimColor.alpha * 0.5f).coerceAtMost(0.30f) else dimColor.alpha.coerceAtLeast(0.72f)
        val pastWeight = if (isBetter) FontWeight.Normal else FontWeight.SemiBold
        val pastShadow = if (glowEaseOut > 0f) {
            Shadow(
                color = accentColor.copy(alpha = 0.35f * glowEaseOut),
                blurRadius = 10f * glowEaseOut,
                offset = Offset.Zero
            )
        } else {
            null
        }

        Text(
            text = wordText,
            fontSize = fontSize,
            fontWeight = if (isPastWord) pastWeight else FontWeight.Normal,
            color = if (isPastWord) textColor.copy(alpha = pastAlpha) else dimColor,
            lineHeight = (fontSize.value * 1.35f).sp,
            style = MaterialTheme.typography.bodyLarge.copy(
                shadow = pastShadow
            ),
            modifier = modifier
        )
        return
    }

    val isLightMode = (0.2126f * MaterialTheme.colorScheme.background.red + 0.7152f * MaterialTheme.colorScheme.background.green + 0.0722f * MaterialTheme.colorScheme.background.blue) > 0.5f
    val activeWordColor = if (isLightMode) androidx.compose.ui.graphics.lerp(accentColor, Color.White, 0.35f) else accentColor

    val isBetter = lyricsStyle == LyricsStyle.BETTER
    val infiniteTransition = rememberInfiniteTransition(label = "word_wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "word_wave_phase"
    )

    if (isBetter) {
        val totalChars = wordText.length.coerceAtLeast(1)
        val raw = if (duration > 0f) ((currentPositionMs - word.startMs).toFloat() / duration).coerceIn(0f, 1f) else 0f
        val fillProgress = raw * raw * (3.0f - 2.0f * raw)
        val glowIntensity = fillProgress * fillProgress
        val glowAlpha = if (isLightMode) (0.25f + 0.30f * glowIntensity) else (0.40f + 0.45f * glowIntensity)
        val shadowBlur = if (isLightMode) (12f + 16f * glowIntensity) else (10f + 14f * glowIntensity)

        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = modifier
        ) {
            wordText.forEachIndexed { i, ch ->
                if (ch == ' ') {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width((fontSize.value * 0.28f).dp))
                } else {
                    val charPhase = wavePhase + (i * 0.50f)
                    val charStart = i.toFloat() / totalChars.toFloat()
                    val charEnd = (i + 1).toFloat() / totalChars.toFloat()
                    val charCenter = (i + 0.5f) / totalChars.toFloat()

                    val (charFloatY, charScale, charColor, charWeight, charShadow, isTransitioning, localProgress) = when {
                        isWordActive -> {
                            val floatY = sin(charPhase) * 3.4f
                            val dist = kotlin.math.abs(fillProgress - charCenter)
                            val scale = 1.0f + (0.09f * (1.0f - (dist / 0.22f)).coerceIn(0f, 1f))
                            when {
                                fillProgress >= charEnd -> {
                                    val shadow = Shadow(
                                        color = accentColor.copy(alpha = glowAlpha * 0.8f),
                                        blurRadius = shadowBlur * 0.8f,
                                        offset = Offset.Zero
                                    )
                                    WaveCharState(floatY, scale, activeWordColor, FontWeight.Bold, shadow, false, 1f)
                                }
                                fillProgress <= charStart -> {
                                    WaveCharState(floatY, 1.0f, textColor.copy(alpha = 0.35f), FontWeight.Bold, null, false, 0f)
                                }
                                else -> {
                                    val localProg = ((fillProgress - charStart) / (charEnd - charStart)).coerceIn(0f, 1f)
                                    val shadow = Shadow(
                                        color = accentColor.copy(alpha = glowAlpha),
                                        blurRadius = shadowBlur,
                                        offset = Offset.Zero
                                    )
                                    WaveCharState(floatY, scale, activeWordColor, FontWeight.Bold, shadow, true, localProg)
                                }
                            }
                        }
                        isWordSung -> {
                            val sungGlowAlpha = if (isLightMode) (0.12f + 0.35f * glowEaseOut) else (0.24f + 0.45f * glowEaseOut)
                            val sungGlowBlur = (if (isLightMode) 8f else 6f) + 14f * glowEaseOut
                            val shadow = Shadow(
                                color = accentColor.copy(alpha = sungGlowAlpha),
                                blurRadius = sungGlowBlur,
                                offset = Offset.Zero
                            )
                            WaveCharState(0f, 1.0f, activeWordColor, FontWeight.Bold, shadow, false, 1f)
                        }
                        else -> {
                            WaveCharState(0f, 1.0f, textColor.copy(alpha = 0.35f), FontWeight.SemiBold, null, false, 0f)
                        }
                    }

                    val charModifier = Modifier
                        .graphicsLayer {
                            translationY = charFloatY
                            scaleX = charScale
                            scaleY = charScale
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            if (isTransitioning) {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                        }
                        .then(
                            if (isTransitioning) {
                                Modifier.drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colorStops = arrayOf(
                                                0.0f to activeWordColor,
                                                localProgress to activeWordColor,
                                                (localProgress + 0.10f).coerceIn(0f, 1f) to textColor.copy(alpha = 0.35f),
                                                1.0f to textColor.copy(alpha = 0.35f)
                                            ),
                                            startX = 0f,
                                            endX = size.width
                                        ),
                                        blendMode = BlendMode.SrcIn
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )

                    Text(
                        text = ch.toString(),
                        fontSize = fontSize,
                        fontWeight = charWeight,
                        color = charColor,
                        lineHeight = (fontSize.value * 1.35f).sp,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            shadow = charShadow
                        ),
                        modifier = charModifier
                    )
                }
            }
        }
        return
    }

    if (isWordActive && duration > 0f) {
        val raw = ((currentPositionMs - word.startMs).toFloat() / duration).coerceIn(0f, 1f)
        val fillProgress = raw * raw * (3.0f - 2.0f * raw)
        val glowIntensity = fillProgress * fillProgress
        val scalePop = 1.0f + (0.006f * sin(fillProgress * PI.toFloat()))
        val glowAlpha = if (isLightMode) (0.20f + 0.25f * glowIntensity) else (0.35f + 0.45f * glowIntensity)
        val shadowBlur = if (isLightMode) (12f + 16f * glowIntensity) else (10f + 14f * glowIntensity)

        val gradientStops = arrayOf(
            0.0f to activeWordColor,
            (fillProgress * 0.92f).coerceIn(0f, 1f) to activeWordColor,
            fillProgress to activeWordColor.copy(alpha = 0.90f),
            (fillProgress + 0.08f).coerceIn(0f, 1f) to textColor.copy(alpha = 0.35f),
            1.0f to textColor.copy(alpha = 0.35f)
        )

        Text(
            text = wordText,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = (fontSize.value * 1.35f).sp,
            style = MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(
                    color = accentColor.copy(alpha = glowAlpha),
                    blurRadius = shadowBlur,
                    offset = Offset.Zero
                )
            ),
            modifier = modifier
                .graphicsLayer {
                    this.scaleX = scalePop
                    this.scaleY = scalePop
                    this.transformOrigin = TransformOrigin(0f, 0.5f)
                    this.compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colorStops = gradientStops,
                            startX = 0f,
                            endX = size.width
                        ),
                        blendMode = BlendMode.SrcIn
                    )
                }
        )
    } else if (isWordSung) {
        val defaultSungAlpha = if (isLightMode) (0.10f + 0.30f * glowEaseOut) else (0.20f + 0.40f * glowEaseOut)
        val defaultSungBlur = (if (isLightMode) 7f else 5f) + 12f * glowEaseOut

        Text(
            text = wordText,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            lineHeight = (fontSize.value * 1.35f).sp,
            style = MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(
                    color = accentColor.copy(alpha = defaultSungAlpha),
                    blurRadius = defaultSungBlur,
                    offset = Offset.Zero
                )
            ),
            modifier = modifier
        )
    } else {
        Text(
            text = wordText,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = textColor.copy(alpha = 0.35f),
            lineHeight = (fontSize.value * 1.35f).sp,
            modifier = modifier
        )
    }
}

private data class WaveCharState(
    val floatY: Float,
    val scale: Float,
    val color: Color,
    val fontWeight: FontWeight,
    val shadow: Shadow?,
    val isTransitioning: Boolean,
    val localProgress: Float
)
