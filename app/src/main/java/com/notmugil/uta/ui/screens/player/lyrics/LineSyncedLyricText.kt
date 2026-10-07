package com.notmugil.uta.ui.screens.player.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.data.preferences.LyricsStyle
import com.notmugil.uta.data.repository.SyncedWord
import kotlin.math.PI
import kotlin.math.sin

data class ParsedLineText(
    val mainText: String,
    val parentheticalText: String?
)

fun parseLineText(rawText: String): ParsedLineText {
    val parenRegex = Regex("\\(([^)]+)\\)")
    val matches = parenRegex.findAll(rawText).toList()
    if (matches.isEmpty()) {
        return ParsedLineText(mainText = rawText, parentheticalText = null)
    }
    val parenText = matches.joinToString(" ") { it.value }
    val mainText = rawText.replace(parenRegex, "").replace(Regex("\\s+"), " ").trim()
    if (mainText.isEmpty()) {
        return ParsedLineText(mainText = parenText, parentheticalText = null)
    }
    return ParsedLineText(mainText = mainText, parentheticalText = parenText)
}

data class IndexedWord(
    val index: Int,
    val word: SyncedWord,
    val wordEnd: Long
)

data class WordSyncedLineParts(
    val mainWords: List<IndexedWord>,
    val parentheticalWords: List<IndexedWord>
)

fun parseWordSyncedLine(words: List<SyncedWord>, lineEndMs: Long?): WordSyncedLineParts {
    val indexedWords = words.mapIndexed { idx, word ->
        val nextStartMs = if (idx + 1 < words.size) {
            words[idx + 1].startMs
        } else {
            lineEndMs ?: (word.startMs + 700L)
        }
        val wordEnd = word.endMs ?: nextStartMs
        IndexedWord(idx, word, wordEnd)
    }

    val main = mutableListOf<IndexedWord>()
    val paren = mutableListOf<IndexedWord>()
    var inParen = false

    for (item in indexedWords) {
        val text = item.word.text
        val containsOpen = text.contains('(')
        val containsClose = text.contains(')')
        if (containsOpen) inParen = true
        if (inParen) {
            paren.add(item)
        } else {
            main.add(item)
        }
        if (containsClose) inParen = false
    }

    if (main.isEmpty() || paren.isEmpty()) {
        return WordSyncedLineParts(mainWords = indexedWords, parentheticalWords = emptyList())
    }
    return WordSyncedLineParts(mainWords = main, parentheticalWords = paren)
}

@Composable
fun LineSyncedLyricText(
    text: String,
    startMs: Long,
    currentPositionMs: Long,
    isActive: Boolean,
    isPast: Boolean,
    accentColor: Color,
    textColor: Color,
    dimColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    lyricsStyle: LyricsStyle,
    modifier: Modifier = Modifier
) {
    val isBetter = lyricsStyle == LyricsStyle.BETTER
    val isLightMode = (0.2126f * MaterialTheme.colorScheme.background.red +
            0.7152f * MaterialTheme.colorScheme.background.green +
            0.0722f * MaterialTheme.colorScheme.background.blue) > 0.5f
    val activeLineColor = accentColor

    val parsed = remember(text, isBetter) {
        if (isBetter) parseLineText(text) else ParsedLineText(mainText = text, parentheticalText = null)
    }

    if (!isActive) {
        val lineColor = if (isPast) {
            if (isBetter) textColor.copy(alpha = dimColor.alpha) else textColor.copy(alpha = (dimColor.alpha * 0.95f).coerceAtLeast(0.72f))
        } else {
            dimColor
        }
        val lineFontWeight = if (isPast) {
            if (isBetter) FontWeight.Normal else FontWeight.SemiBold
        } else {
            FontWeight.Normal
        }

        if (parsed.parentheticalText != null) {
            Column(modifier = modifier) {
                Text(
                    text = parsed.mainText,
                    fontSize = fontSize,
                    fontWeight = lineFontWeight,
                    color = lineColor,
                    lineHeight = lineHeight,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = parsed.parentheticalText,
                    fontSize = (fontSize.value * 0.75f).sp,
                    fontWeight = lineFontWeight,
                    color = lineColor.copy(alpha = (lineColor.alpha * 0.88f)),
                    lineHeight = (lineHeight.value * 0.75f).sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        } else {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = lineFontWeight,
                color = lineColor,
                lineHeight = lineHeight,
                textAlign = TextAlign.Start,
                modifier = modifier
            )
        }
        return
    }

    val activationElapsed = (currentPositionMs - startMs).coerceAtLeast(0L).toFloat()
    val sweepDuration = 600f
    val sweepProgress = (activationElapsed / sweepDuration).coerceIn(0f, 1f)
    val easeProgress = sweepProgress * sweepProgress * (3f - 2f * sweepProgress)

    val infiniteTransition = rememberInfiniteTransition(label = "line_wave_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "line_wave_phase"
    )

    if (isBetter) {
        val isSweeping = sweepProgress < 1f
        val waveFloatY = if (isSweeping) sin(wavePhase) * 2.2f * (1f - easeProgress) else 0f
        val glowAlpha = if (isLightMode) (0.25f + 0.25f * (1f - easeProgress * 0.3f)) else (0.45f + 0.25f * (1f - easeProgress * 0.3f))
        val shadowBlur = if (isLightMode) 14f else 12f

        val gradientStops = if (isSweeping) {
            arrayOf(
                0.0f to activeLineColor,
                (easeProgress * 0.85f).coerceIn(0f, 1f) to activeLineColor,
                easeProgress to (if (isLightMode) activeLineColor else Color.White.copy(alpha = 0.95f)),
                (easeProgress + 0.08f).coerceIn(0f, 1f) to activeLineColor.copy(alpha = 0.85f),
                (easeProgress + 0.16f).coerceIn(0f, 1f) to textColor.copy(alpha = 0.35f),
                1.0f to textColor.copy(alpha = 0.35f)
            )
        } else {
            null
        }

        val textModifier = if (isSweeping && gradientStops != null) {
            Modifier
                .graphicsLayer {
                    this.translationY = waveFloatY
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
        } else {
            Modifier
        }

        val lineShadow = Shadow(
            color = accentColor.copy(alpha = glowAlpha),
            blurRadius = shadowBlur,
            offset = Offset.Zero
        )

        if (parsed.parentheticalText != null) {
            Column(modifier = modifier.then(textModifier)) {
                Text(
                    text = parsed.mainText,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = activeLineColor,
                    lineHeight = lineHeight,
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        shadow = lineShadow
                    )
                )
                Text(
                    text = parsed.parentheticalText,
                    fontSize = (fontSize.value * 0.75f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = activeLineColor.copy(alpha = 0.90f),
                    lineHeight = (lineHeight.value * 0.75f).sp,
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        shadow = lineShadow
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        } else {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = activeLineColor,
                lineHeight = lineHeight,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyLarge.copy(
                    shadow = lineShadow
                ),
                modifier = modifier.then(textModifier)
            )
        }
    } else {
        val glowAlpha = (if (isLightMode) 0.20f else 0.38f) * (1f - easeProgress * 0.35f)
        val shadowBlur = 10f

        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = activeLineColor,
            lineHeight = lineHeight,
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(
                    color = accentColor.copy(alpha = glowAlpha),
                    blurRadius = shadowBlur,
                    offset = Offset.Zero
                )
            ),
            modifier = modifier
        )
    }
}
