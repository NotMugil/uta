package com.notmugil.uta.ui.screens.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.data.preferences.SeekBarStyle
import com.notmugil.uta.util.Formatters

@Composable
fun PlayerSeekBarSection(
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isDraggingSlider: Boolean,
    dragPositionMs: Float,
    onDraggingSliderChange: (Boolean) -> Unit,
    onDragPositionChange: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    songKey: String? = null,
    trackStats: String? = null,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val appPreferences = LocalAppPreferences.current
    val rawSeekBarStyle by (appPreferences?.seekBarStyle?.collectAsState() ?: remember { mutableStateOf(SeekBarStyle.WAVY) })
    val seekBarStyle = if (isLandscape && rawSeekBarStyle == SeekBarStyle.NEEDLE) SeekBarStyle.WAVY else rawSeekBarStyle
    val isNeedle = seekBarStyle == SeekBarStyle.NEEDLE

    Column(modifier = modifier.fillMaxWidth()) {
        val sliderValue = if (isDraggingSlider) dragPositionMs else currentPositionMs.toFloat()
        val maxDuration = durationMs.toFloat().coerceAtLeast(1f)

        var targetSeekPos by remember { mutableFloatStateOf(dragPositionMs) }

        PlaybackSeekBar(
            value = sliderValue.coerceIn(0f, maxDuration),
            onValueChange = {
                targetSeekPos = it
                onDraggingSliderChange(true)
                onDragPositionChange(it)
            },
            onValueChangeFinished = {
                onDraggingSliderChange(false)
                onSeekTo(targetSeekPos.toLong())
            },
            valueRange = 0f..maxDuration,
            isPlaying = isPlaying,
            style = seekBarStyle,
            songKey = songKey,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isNeedle) {
                        Modifier.layout { measurable, constraints ->
                            val extraWidth = (48.dp).roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = constraints.minWidth + extraWidth,
                                    maxWidth = constraints.maxWidth + extraWidth
                                )
                            )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.placeRelative(-extraWidth / 2, 0)
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isNeedle) Modifier.padding(top = 2.dp) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = Formatters.formatDurationMs(if (isDraggingSlider) dragPositionMs.toLong() else currentPositionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            if (!trackStats.isNullOrBlank()) {
                Text(
                    text = trackStats,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp)
                )
            }
            Text(
                text = Formatters.formatDurationMs(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
