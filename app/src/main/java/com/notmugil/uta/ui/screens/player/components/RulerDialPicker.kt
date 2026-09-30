package com.notmugil.uta.ui.screens.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.util.HapticFeedbackHelper
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class PresetOption<T>(
    val value: T,
    val label: String
)

@Composable
fun <T> PresetPillRow(
    options: List<PresetOption<T>>,
    selectedValue: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = selectedValue == option.value
            val containerColor = if (isSelected) {
                accentColor
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            val context = LocalContext.current
            Surface(
                onClick = {
                    HapticFeedbackHelper.perform(context, HapticFeedbackHelper.HapticType.LIGHT)
                    onSelect(option.value)
                },
                shape = CircleShape,
                color = containerColor,
                contentColor = contentColor,
                modifier = Modifier
                    .height(36.dp)
                    .padding(horizontal = 2.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun RulerDialPicker(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 5f,
    majorStep: Float = 15f,
    modifier: Modifier = Modifier,
    height: Dp = 76.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    valueFormatter: (Float) -> String = { "${it.toInt()}m" }
) {
    val density = LocalDensity.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val coroutineScope = rememberCoroutineScope()

    val totalSteps = ((range.endInclusive - range.start) / step).roundToInt()

    val pxPerStep = with(density) {
        if (totalSteps <= 16) 42.dp.toPx() else 14.dp.toPx()
    }

    var dragSteps by remember(value) {
        mutableFloatStateOf(((value - range.start) / step).coerceIn(0f, totalSteps.toFloat()))
    }

    val animatedSteps = remember { Animatable(dragSteps) }

    LaunchedEffect(value) {
        val target = ((value - range.start) / step).coerceIn(0f, totalSteps.toFloat())
        if (abs(animatedSteps.value - target) > 0.01f) {
            animatedSteps.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val context = LocalContext.current
    var lastEmittedSnappedValue by remember { mutableFloatStateOf(value) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(38.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
            .pointerInput(range, step, totalSteps) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val stepDelta = -dragAmount / pxPerStep
                        coroutineScope.launch {
                            val newSteps = (animatedSteps.value + stepDelta).coerceIn(0f, totalSteps.toFloat())
                            animatedSteps.snapTo(newSteps)
                            val snappedValue = (range.start + newSteps.roundToInt() * step).coerceIn(range.start, range.endInclusive)
                            if (snappedValue != lastEmittedSnappedValue) {
                                lastEmittedSnappedValue = snappedValue
                                HapticFeedbackHelper.perform(context, HapticFeedbackHelper.HapticType.LIGHT)
                            }
                            currentOnValueChange(snappedValue)
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            val targetStep = animatedSteps.value.roundToInt().toFloat()
                            animatedSteps.animateTo(
                                targetValue = targetStep,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            val finalValue = (range.start + targetStep * step).coerceIn(range.start, range.endInclusive)
                            currentOnValueChange(finalValue)
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f

            val currentStepProgress = animatedSteps.value
            val minorTickHeight = 7.dp.toPx()
            val majorTickHeight = 13.dp.toPx()
            val needleTickHeight = 14.dp.toPx()
            val tickThickness = 1.4.dp.toPx()
            val majorTickThickness = 2.0.dp.toPx()

            val majorStepInSteps = (majorStep / step).roundToInt().coerceAtLeast(1)

            for (stepIndex in 0..totalSteps) {
                val tickX = centerX + (stepIndex - currentStepProgress) * pxPerStep
                if (tickX < -20f || tickX > canvasWidth + 20f) continue

                val stepValue = range.start + stepIndex * step
                val isMajor = stepIndex % majorStepInSteps == 0

                val distFromCenter = abs(tickX - centerX)
                val maxDist = canvasWidth * 0.46f
                val alpha = (1f - (distFromCenter / maxDist)).coerceIn(0f, 1f)

                if (alpha > 0.05f) {
                    val tickH = if (isMajor) majorTickHeight else minorTickHeight
                    val tickW = if (isMajor) majorTickThickness else tickThickness
                    val tickColor = if (isMajor) {
                        onSurfaceColor.copy(alpha = 0.55f * alpha)
                    } else {
                        onSurfaceVariantColor.copy(alpha = 0.28f * alpha)
                    }

                    drawLine(
                        color = tickColor,
                        start = Offset(tickX, 6.dp.toPx()),
                        end = Offset(tickX, 6.dp.toPx() + tickH),
                        strokeWidth = tickW,
                        cap = StrokeCap.Round
                    )

                    drawLine(
                        color = tickColor,
                        start = Offset(tickX, canvasHeight - 6.dp.toPx()),
                        end = Offset(tickX, canvasHeight - 6.dp.toPx() - tickH),
                        strokeWidth = tickW,
                        cap = StrokeCap.Round
                    )

                    if (isMajor && distFromCenter > 36.dp.toPx() && distFromCenter < maxDist * 0.9f) {
                        val textAlpha = (alpha * 0.65f).coerceIn(0f, 0.7f)
                        val textPaint = android.graphics.Paint().apply {
                            this.color = onSurfaceVariantColor.toArgb()
                            this.alpha = (textAlpha * 255).toInt()
                            this.textSize = 13.sp.toPx()
                            this.isAntiAlias = true
                            this.textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                valueFormatter(stepValue),
                                tickX,
                                canvasHeight / 2f + 5.dp.toPx(),
                                textPaint
                            )
                        }
                    }
                }
            }

            val needleColor = onSurfaceColor
            drawLine(
                color = needleColor,
                start = Offset(centerX, 5.dp.toPx()),
                end = Offset(centerX, 5.dp.toPx() + needleTickHeight),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = needleColor,
                start = Offset(centerX, canvasHeight - 5.dp.toPx()),
                end = Offset(centerX, canvasHeight - 5.dp.toPx() - needleTickHeight),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Text(
            text = valueFormatter(value),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            color = onSurfaceColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
