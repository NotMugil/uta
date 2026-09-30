package com.notmugil.uta.ui.screens.player.lyrics

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier
) {
    val duration = (wordEnd - word.startMs).toFloat()
    val isWordActive = currentPositionMs >= word.startMs && currentPositionMs < wordEnd
    val isWordSung = currentPositionMs >= wordEnd

    val wordText = if (!word.text.endsWith(" ")) "${word.text} " else word.text

    if (!isCurrentLine) {
        Text(
            text = wordText,
            fontSize = fontSize,
            fontWeight = FontWeight.Normal,
            color = dimColor,
            lineHeight = (fontSize.value * 1.35f).sp,
            modifier = modifier
        )
        return
    }

    val isLightMode = (0.2126f * MaterialTheme.colorScheme.background.red + 0.7152f * MaterialTheme.colorScheme.background.green + 0.0722f * MaterialTheme.colorScheme.background.blue) > 0.5f
    val activeWordColor = if (isLightMode) androidx.compose.ui.graphics.lerp(accentColor, Color.White, 0.35f) else accentColor

    if (isWordActive && duration > 0f) {
        val raw = ((currentPositionMs - word.startMs).toFloat() / duration).coerceIn(0f, 1f)
        val fillProgress = raw * raw * (3.0f - 2.0f * raw)
        val glowIntensity = fillProgress * fillProgress
        val scalePop = 1.0f + (0.04f * sin(fillProgress * PI.toFloat()))
        val glowAlpha = if (isLightMode) (0.20f + 0.25f * glowIntensity) else (0.35f + 0.45f * glowIntensity)
        val shadowBlur = if (isLightMode) (12f + 16f * glowIntensity) else (10f + 14f * glowIntensity)

        Text(
            text = wordText,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
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
                            colorStops = arrayOf(
                                0.0f to activeWordColor,
                                (fillProgress * 0.92f).coerceIn(0f, 1f) to activeWordColor,
                                fillProgress to activeWordColor.copy(alpha = 0.90f),
                                (fillProgress + 0.08f).coerceIn(0f, 1f) to textColor.copy(alpha = 0.35f),
                                1.0f to textColor.copy(alpha = 0.35f)
                            ),
                            startX = 0f,
                            endX = size.width
                        ),
                        blendMode = BlendMode.SrcIn
                    )
                }
        )
    } else if (isWordSung) {
        Text(
            text = wordText,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            lineHeight = (fontSize.value * 1.35f).sp,
            style = MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(
                    color = accentColor.copy(alpha = if (isLightMode) 0.15f else 0.30f),
                    blurRadius = if (isLightMode) 10f else 8f,
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
