package com.notmugil.uta.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.notmugil.uta.R
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notmugil.uta.player.SleepTimerManager
import com.notmugil.uta.player.SleepTimerMode
import com.notmugil.uta.ui.screens.player.components.PresetOption
import com.notmugil.uta.ui.screens.player.components.PresetPillRow
import com.notmugil.uta.ui.screens.player.components.RulerDialPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    sleepTimerManager: SleepTimerManager,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val activeMode by sleepTimerManager.activeMode.collectAsState()
    val remainingSeconds by sleepTimerManager.remainingSeconds.collectAsState()
    val isRunning = activeMode !is SleepTimerMode.Disabled

    val accentColor = MaterialTheme.colorScheme.primary

    val initialMinutes = when (activeMode) {
        is SleepTimerMode.Duration -> {
            val sec = remainingSeconds ?: 0L
            (sec / 60).toFloat().coerceIn(5f, 120f)
        }
        else -> 30f
    }

    var selectedMinutes by remember { mutableFloatStateOf(if (isRunning) initialMinutes else 30f) }

    val presetOptions = listOf(
        PresetOption(15f, "15m"),
        PresetOption(30f, "30m"),
        PresetOption(45f, "45m"),
        PresetOption(60f, "60m"),
        PresetOption(90f, "90m")
    )

    val offLabel = stringResource(R.string.sleep_timer_off)
    fun formatDuration(mins: Float): String {
        val m = mins.toInt()
        return when {
            m == 0 -> offLabel
            m < 60 -> "${m}m"
            m % 60 == 0 -> "${m / 60}h"
            else -> "${m / 60}h ${m % 60}m"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Tabler.Outline.Moon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.sleep_timer),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Tabler.Outline.X, contentDescription = stringResource(R.string.action_close))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (isRunning) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = accentColor.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.sleep_timer_active_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when (activeMode) {
                                    is SleepTimerMode.Duration -> {
                                        val sec = remainingSeconds ?: 0L
                                        val minutes = sec / 60
                                        val seconds = sec % 60
                                        stringResource(R.string.sleep_timer_time_remaining, minutes, seconds)
                                    }
                                    else -> stringResource(R.string.sleep_timer_active_title)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                sleepTimerManager.cancelTimer()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.sleep_timer_turn_off), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            PresetPillRow(
                options = presetOptions,
                selectedValue = presetOptions.find { kotlin.math.abs(it.value - selectedMinutes) < 0.5f }?.value,
                onSelect = { mins ->
                    selectedMinutes = mins
                },
                accentColor = accentColor,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            RulerDialPicker(
                value = selectedMinutes,
                onValueChange = { mins ->
                    selectedMinutes = mins
                },
                range = 5f..120f,
                step = 5f,
                majorStep = 15f,
                accentColor = accentColor,
                valueFormatter = ::formatDuration,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRunning) {
                    Surface(
                        onClick = {
                            sleepTimerManager.cancelTimer()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.Power,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.sleep_timer_turn_off),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        sleepTimerManager.startDurationTimer(selectedMinutes.toInt())
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_set_format, formatDuration(selectedMinutes)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
