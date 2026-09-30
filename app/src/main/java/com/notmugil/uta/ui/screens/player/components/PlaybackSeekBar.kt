package com.notmugil.uta.ui.screens.player.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.data.preferences.SeekBarStyle
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun PlaybackSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isPlaying: Boolean = false,
    songKey: String? = null,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = activeColor,
    style: SeekBarStyle? = null,
    trackThickness: Dp = 4.dp,
    waveAmplitude: Dp = 4.5.dp,
    waveLength: Dp = 26.dp,
    thumbRadius: Dp = 6.dp
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val appPreferences = LocalAppPreferences.current
    val currentSettingStyle by (appPreferences?.seekBarStyle?.collectAsState() ?: remember { mutableStateOf(SeekBarStyle.WAVY) })
    val baseStyle = style ?: currentSettingStyle
    val resolvedStyle = if (isLandscape && baseStyle == SeekBarStyle.NEEDLE) SeekBarStyle.WAVY else baseStyle

    val context = LocalContext.current
    val density = LocalDensity.current
    val effectiveSongKey = songKey ?: "default_waveform"
    var waveformAmplitudes by remember(effectiveSongKey) {
        mutableStateOf(SongWaveformGenerator.getCachedOrGenerate(context, effectiveSongKey, 256))
    }

    LaunchedEffect(effectiveSongKey) {
        val loaded = SongWaveformGenerator.loadWaveform(context, effectiveSongKey, 256)
        waveformAmplitudes = loaded
    }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    // Volume observation to reactively adjust wave frequency
    var volumeFraction by remember {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 8
        mutableFloatStateOf((current.toFloat() / max.coerceAtLeast(1).toFloat()).coerceIn(0.05f, 1f))
    }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 8
                volumeFraction = (current.toFloat() / max.coerceAtLeast(1).toFloat()).coerceIn(0.05f, 1f)
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        context.registerReceiver(receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
    val rawCurrentProgress = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    val smoothProgress by animateFloatAsState(
        targetValue = rawCurrentProgress,
        animationSpec = if (isPlaying && !isDragging) {
            tween(durationMillis = 65, easing = LinearEasing)
        } else {
            snap()
        },
        label = "smooth_playback_progress"
    )

    val displayProgress = if (isDragging) dragProgress else smoothProgress

    val targetWaveLength = if (resolvedStyle == SeekBarStyle.WAVY) {
        (42f - (volumeFraction * 28f)).dp
    } else {
        waveLength
    }

    val animatedWaveLength by animateDpAsState(
        targetValue = targetWaveLength,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "wave_length_volume"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wave_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_angle"
    )

    val targetAmplitude = if (resolvedStyle == SeekBarStyle.WAVY && isPlaying && !isDragging) {
        waveAmplitude
    } else {
        0.dp
    }

    val animatedAmplitude by animateDpAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "wave_amplitude"
    )

    val animatedThumbRadius by animateDpAsState(
        targetValue = if (isDragging) thumbRadius + 2.dp else thumbRadius,
        animationSpec = tween(durationMillis = 180),
        label = "thumb_radius"
    )

    val trackThicknessPx = with(density) { trackThickness.toPx() }
    val waveLengthPx = with(density) { animatedWaveLength.toPx() }
    val amplitudePx = with(density) { animatedAmplitude.toPx() }
    val thumbRadiusPx = with(density) { animatedThumbRadius.toPx() }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(valueRange, rangeSpan, resolvedStyle) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    if (w > 0f) {
                        isDragging = true
                        if (resolvedStyle == SeekBarStyle.NEEDLE) {
                            val tapOffsetFromCenter = down.position.x - (w * 0.5f)
                            val initialProg = (displayProgress + tapOffsetFromCenter / w).coerceIn(0f, 1f)
                            dragProgress = initialProg
                            currentOnValueChange(valueRange.start + initialProg * rangeSpan)

                            try {
                                drag(down.id) { change ->
                                    change.consume()
                                    val deltaX = change.position.x - change.previousPosition.x
                                    val progDelta = -deltaX / w
                                    val newProg = (dragProgress + progDelta).coerceIn(0f, 1f)
                                    dragProgress = newProg
                                    currentOnValueChange(valueRange.start + newProg * rangeSpan)
                                }
                            } finally {
                                isDragging = false
                                currentOnValueChangeFinished?.invoke()
                            }
                        } else {
                            val initialProg = (down.position.x / w).coerceIn(0f, 1f)
                            dragProgress = initialProg
                            currentOnValueChange(valueRange.start + initialProg * rangeSpan)

                            try {
                                drag(down.id) { change ->
                                    change.consume()
                                    val currentProg = (change.position.x / w).coerceIn(0f, 1f)
                                    dragProgress = currentProg
                                    currentOnValueChange(valueRange.start + currentProg * rangeSpan)
                                }
                            } finally {
                                isDragging = false
                                currentOnValueChangeFinished?.invoke()
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val cy = height / 2f
            val activeWidth = (displayProgress * width).coerceIn(0f, width)

            if (resolvedStyle == SeekBarStyle.NEEDLE) {
                val needleTrackThickness = 4.5.dp.toPx()
                val needleHeight = if (isDragging) 20.dp.toPx() else 16.dp.toPx()
                val needleWidth = 3.dp.toPx()
                val dynamicAccent = activeColor

                val needleX = width * 0.5f
                val playedStartX = needleX - displayProgress * width
                val unplayedEndX = needleX + (1f - displayProgress) * width

                if (playedStartX < needleX) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.22f),
                        start = Offset(playedStartX, cy),
                        end = Offset(needleX, cy),
                        strokeWidth = needleTrackThickness,
                        cap = StrokeCap.Round
                    )
                }

                if (unplayedEndX > needleX) {
                    drawLine(
                        color = dynamicAccent,
                        start = Offset(needleX, cy),
                        end = Offset(unplayedEndX, cy),
                        strokeWidth = needleTrackThickness,
                        cap = StrokeCap.Round
                    )
                }

                drawLine(
                    color = Color.Black.copy(alpha = 0.35f),
                    start = Offset(needleX + 0.8f, cy - needleHeight / 2f),
                    end = Offset(needleX + 0.8f, cy + needleHeight / 2f),
                    strokeWidth = needleWidth + 0.8f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = dynamicAccent,
                    start = Offset(needleX, cy - needleHeight / 2f),
                    end = Offset(needleX, cy + needleHeight / 2f),
                    strokeWidth = needleWidth,
                    cap = StrokeCap.Round
                )
            } else if (resolvedStyle == SeekBarStyle.WAVEFORM) {
                val barWidthPx = 3.dp.toPx()
                val barSpacingPx = 2.4.dp.toPx()
                val stepPx = barWidthPx + barSpacingPx
                val barCount = ((width - barWidthPx) / stepPx).toInt().coerceAtLeast(10)
                val startX = barWidthPx / 2f
                val maxBarHeightPx = (height * 0.92f).coerceAtMost(32.dp.toPx())
                val minBarHeightPx = 3.dp.toPx()

                val volumeScale = if (isPlaying) 0.82f + 0.18f * volumeFraction else 0.85f
                val ampCount = waveformAmplitudes.size

                for (i in 0 until barCount) {
                    val barCenterX = startX + i * stepPx
                    val sampleIdx = ((i.toFloat() / (barCount - 1).coerceAtLeast(1).toFloat()) * (ampCount - 1)).toInt().coerceIn(0, ampCount - 1)
                    val normalizedAmp = waveformAmplitudes[sampleIdx]
                    val barHeight = (minBarHeightPx + (maxBarHeightPx - minBarHeightPx) * normalizedAmp * volumeScale)

                    drawLine(
                        color = inactiveColor.copy(alpha = 0.35f),
                        start = Offset(barCenterX, cy - barHeight / 2f),
                        end = Offset(barCenterX, cy + barHeight / 2f),
                        strokeWidth = barWidthPx,
                        cap = StrokeCap.Round
                    )
                }

                if (activeWidth > 0f) {
                    clipRect(left = 0f, top = 0f, right = activeWidth, bottom = size.height) {
                        for (i in 0 until barCount) {
                            val barCenterX = startX + i * stepPx
                            val sampleIdx = ((i.toFloat() / (barCount - 1).coerceAtLeast(1).toFloat()) * (ampCount - 1)).toInt().coerceIn(0, ampCount - 1)
                            val normalizedAmp = waveformAmplitudes[sampleIdx]
                            val barHeight = (minBarHeightPx + (maxBarHeightPx - minBarHeightPx) * normalizedAmp * volumeScale)

                            drawLine(
                                color = activeColor,
                                start = Offset(barCenterX, cy - barHeight / 2f),
                                end = Offset(barCenterX, cy + barHeight / 2f),
                                strokeWidth = barWidthPx,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                if (isDragging) {
                    drawLine(
                        color = thumbColor,
                        start = Offset(activeWidth, cy - maxBarHeightPx / 2f - 2.dp.toPx()),
                        end = Offset(activeWidth, cy + maxBarHeightPx / 2f + 2.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            } else {
                if (activeWidth < width) {
                    val startX = (activeWidth + thumbRadiusPx * 0.5f).coerceAtMost(width)
                    if (startX < width) {
                        drawLine(
                            color = inactiveColor,
                            start = Offset(startX, cy),
                            end = Offset(width, cy),
                            strokeWidth = trackThicknessPx,
                            cap = StrokeCap.Round
                        )
                    }
                }

                if (activeWidth > 0f) {
                    if (amplitudePx > 0.3f && activeWidth > 8f) {
                        val wavePath = Path()
                        wavePath.moveTo(0f, cy)

                        val stepPx = 3f
                        var x = 0f
                        while (x <= activeWidth) {
                            val taperStart = (x / (waveLengthPx * 0.8f)).coerceIn(0f, 1f)
                            val taperEnd = ((activeWidth - x) / (waveLengthPx * 0.8f)).coerceIn(0f, 1f)
                            val taper = taperStart * taperEnd

                            val currentAmp = amplitudePx * taper
                            val y = cy + sin(((x / waveLengthPx) * 2 * PI.toFloat()) - phase) * currentAmp
                            wavePath.lineTo(x, y)
                            x += stepPx
                        }
                        wavePath.lineTo(activeWidth, cy)

                        drawPath(
                            path = wavePath,
                            color = activeColor,
                            style = Stroke(
                                width = trackThicknessPx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } else {
                        drawLine(
                            color = activeColor,
                            start = Offset(0f, cy),
                            end = Offset(activeWidth, cy),
                            strokeWidth = trackThicknessPx,
                            cap = StrokeCap.Round
                        )
                    }
                }

                if (thumbRadiusPx > 0f) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.18f),
                        radius = thumbRadiusPx + 1.2f,
                        center = Offset(activeWidth, cy + 0.8f)
                    )
                    drawCircle(
                        color = thumbColor,
                        radius = thumbRadiusPx,
                        center = Offset(activeWidth, cy)
                    )
                }
            }
        }
    }
}
