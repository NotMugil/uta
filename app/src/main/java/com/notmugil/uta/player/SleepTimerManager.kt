package com.notmugil.uta.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SleepTimerMode {
    data object Disabled : SleepTimerMode
    data class Duration(val endTimestampMs: Long, val initialDurationMs: Long) : SleepTimerMode
}

@Singleton
class SleepTimerManager @Inject constructor(
    private val playbackController: PlaybackController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    private val _activeMode = MutableStateFlow<SleepTimerMode>(SleepTimerMode.Disabled)
    val activeMode: StateFlow<SleepTimerMode> = _activeMode.asStateFlow()

    private val _remainingSeconds = MutableStateFlow<Long?>(null)
    val remainingSeconds: StateFlow<Long?> = _remainingSeconds.asStateFlow()

    val isRunning: Boolean
        get() = _activeMode.value !is SleepTimerMode.Disabled

    fun startDurationTimer(minutes: Int) {
        startDurationMs(minutes * 60 * 1000L)
    }

    fun startDurationMs(durationMs: Long) {
        cancelTimer()
        val now = System.currentTimeMillis()
        val endTimestamp = now + durationMs
        _activeMode.value = SleepTimerMode.Duration(endTimestamp, durationMs)

        timerJob = scope.launch {
            while (isActive) {
                val current = System.currentTimeMillis()
                val diffMs = endTimestamp - current
                if (diffMs <= 0) {
                    _remainingSeconds.value = 0L
                    triggerSleepAction()
                    break
                }
                _remainingSeconds.value = (diffMs + 999) / 1000
                delay(1000)
            }
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _activeMode.value = SleepTimerMode.Disabled
        _remainingSeconds.value = null
    }

    private fun triggerSleepAction() {
        playbackController.pause()
        cancelTimer()
    }
}
