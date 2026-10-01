package com.notmugil.uta.ui.screens.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.util.HapticFeedbackHelper
import kotlinx.coroutines.delay
import kotlin.math.abs

val ALPHABET_ITEMS: List<String> = ('A'..'Z').map { it.toString() } + listOf("#")

@Composable
fun AlphabetFastScroller(
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isScrollInProgress: Boolean = false,
    currentScrollLetter: String? = null,
    availableLetters: Set<String> = emptySet(),
    alphabet: List<String> = ALPHABET_ITEMS
) {
    val context = LocalContext.current
    var isDragging by remember { mutableStateOf(false) }
    var activeLetter by remember { mutableStateOf<String?>(null) }
    var componentHeightPx by remember { mutableStateOf(0) }
    var currentYPx by remember { mutableStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isScrollInProgress, isDragging) {
        if (isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(400)
            isVisible = false
        }
    }

    fun updateSelection(y: Float) {
        if (componentHeightPx <= 0 || alphabet.isEmpty()) return
        val clampedY = y.coerceIn(0f, componentHeightPx.toFloat() - 1f)
        val rawIndex = ((clampedY / componentHeightPx) * alphabet.size).toInt().coerceIn(0, alphabet.lastIndex)
        currentYPx = clampedY

        // Skip / snap to closest available letter
        val targetLetter = if (availableLetters.isNotEmpty()) {
            if (alphabet[rawIndex] in availableLetters) {
                alphabet[rawIndex]
            } else {
                var bestLetter: String? = null
                var bestDist = Int.MAX_VALUE
                alphabet.forEachIndexed { idx, l ->
                    if (l in availableLetters) {
                        val dist = abs(idx - rawIndex)
                        if (dist < bestDist) {
                            bestDist = dist
                            bestLetter = l
                        }
                    }
                }
                bestLetter ?: alphabet[rawIndex]
            }
        } else {
            alphabet[rawIndex]
        }

        if (targetLetter != activeLetter) {
            activeLetter = targetLetter
            HapticFeedbackHelper.perform(context, HapticFeedbackHelper.HapticType.LIGHT)
            onLetterSelected(targetLetter)
        }
    }

    val density = LocalDensity.current
    val themeBgColor = MaterialTheme.colorScheme.background

    // Smoothly animate the vertical bubble motion
    val animatedBubbleY by animateFloatAsState(
        targetValue = currentYPx,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 600f
        ),
        label = "bubble_y_animation"
    )

    val highlightedLetter = if (isDragging) activeLetter else currentScrollLetter

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 8.dp, bottom = 168.dp)
                .onSizeChanged { componentHeightPx = it.height }
        ) {
            // Horizontal fading overlay background on the right edge
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(56.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                themeBgColor.copy(alpha = if (isDragging) 0.55f else 0.35f),
                                themeBgColor.copy(alpha = if (isDragging) 0.95f else 0.75f)
                            )
                        )
                    )
            )

            // Lightly animated floating bubble positioned to the left
            if (isDragging && activeLetter != null) {
                val bubbleOffsetY = with(density) {
                    (animatedBubbleY - 28.dp.toPx()).coerceIn(0f, (componentHeightPx - 56.dp.toPx()).coerceAtLeast(0f)).toInt()
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(x = -with(density) { 58.dp.roundToPx() }, y = bubbleOffsetY) }
                        .size(56.dp)
                        .shadow(elevation = 10.dp, shape = CircleShape)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeLetter ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Alphabet touch rail (drag only activates when touching directly on the rail)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .width(28.dp)
                    .fillMaxHeight()
                    .pointerInput(alphabet, availableLetters) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            updateSelection(down.position.y)
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                updateSelection(change.position.y)
                            }
                            isDragging = false
                            activeLetter = null
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                alphabet.forEach { letter ->
                    val isAvailable = availableLetters.isEmpty() || letter in availableLetters
                    val isHighlighted = letter == highlightedLetter

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            fontSize = 10.5.sp,
                            lineHeight = 12.sp,
                            fontWeight = when {
                                isHighlighted -> FontWeight.ExtraBold
                                isAvailable -> FontWeight.Bold
                                else -> FontWeight.Normal
                            },
                            color = when {
                                isHighlighted -> MaterialTheme.colorScheme.primary
                                isAvailable -> MaterialTheme.colorScheme.onBackground.copy(alpha = if (isDragging) 0.95f else 0.80f)
                                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

fun <T> getAvailableAlphabetLetters(items: List<T>, keySelector: (T) -> String): Set<String> {
    val result = mutableSetOf<String>()
    for (item in items) {
        val key = keySelector(item).trimStart()
        if (key.isNotEmpty()) {
            val first = key.first()
            if (first.isLetter()) {
                result.add(first.uppercaseChar().toString())
            } else {
                result.add("#")
            }
        }
    }
    return result
}

fun <T> findAlphabetTargetIndex(
    items: List<T>,
    letter: String,
    isAscending: Boolean,
    keySelector: (T) -> String
): Int {
    if (items.isEmpty()) return 0
    if (letter == "#") {
        if (isAscending) {
            val symbolIndex = items.indexOfFirst {
                val key = keySelector(it).trimStart()
                key.isNotEmpty() && !key.first().isLetter()
            }
            return if (symbolIndex >= 0) symbolIndex else items.lastIndex
        } else {
            val symbolIndex = items.indexOfFirst {
                val key = keySelector(it).trimStart()
                key.isNotEmpty() && !key.first().isLetter()
            }
            return if (symbolIndex >= 0) symbolIndex else 0
        }
    }

    val targetChar = letter.first().uppercaseChar()

    return if (isAscending) {
        val exactIndex = items.indexOfFirst {
            val key = keySelector(it).trimStart().uppercase()
            key.startsWith(targetChar)
        }
        if (exactIndex >= 0) return exactIndex

        val nextIndex = items.indexOfFirst {
            val key = keySelector(it).trimStart().uppercase()
            key.isNotEmpty() && key.first().isLetter() && key.first() > targetChar
        }
        if (nextIndex >= 0) nextIndex else items.lastIndex
    } else {
        val exactIndex = items.indexOfFirst {
            val key = keySelector(it).trimStart().uppercase()
            key.startsWith(targetChar)
        }
        if (exactIndex >= 0) return exactIndex

        val nextIndex = items.indexOfFirst {
            val key = keySelector(it).trimStart().uppercase()
            key.isNotEmpty() && key.first().isLetter() && key.first() < targetChar
        }
        if (nextIndex >= 0) nextIndex else 0
    }
}
