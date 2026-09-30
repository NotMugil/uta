package com.notmugil.uta.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notmugil.uta.data.NetworkMonitor
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.repository.HistoryRepository
import com.notmugil.uta.domain.model.HistoryItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val playbackController: PlaybackController,
    private val networkMonitor: NetworkMonitor,
    private val subsonicRepository: SubsonicRepository,
    private val localMediaDao: LocalMediaDao
) : ViewModel() {

    private val downloadedTrackIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>> = subsonicRepository.authState.flatMapLatest {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) {
            flowOf(emptySet<String>())
        } else {
            localMediaDao.getDownloadedTrackIdsFlow(serverId).map { it.toSet() }
        }
    }

    val state: StateFlow<HistoryState> = combine(
        historyRepository.getHistory(),
        networkMonitor.isOfflineModeActive,
        downloadedTrackIdsFlow,
        playbackController.currentTrack,
        playbackController.isPlaying
    ) { items: List<HistoryItem>, isOffline: Boolean, downloadedIds: Set<String>, currentTrack: TrackItem?, isPlaying: Boolean ->
        HistoryState(
            sections = groupByDate(items),
            isEmpty = items.isEmpty(),
            isOfflineModeActive = isOffline,
            downloadedTrackIds = downloadedIds,
            currentPlayingTrackId = currentTrack?.id,
            isPlaying = isPlaying
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryState()
    )

    fun playEntry(entry: HistoryItem) {
        playbackController.playTrack(entry.track)
    }

    fun addToQueue(entry: HistoryItem) {
        playbackController.addToQueue(entry.track)
    }

    fun removeEntry(id: Long) {
        viewModelScope.launch {
            historyRepository.removeEntry(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }

    companion object {
        internal fun groupByDate(entries: List<HistoryItem>): List<HistorySection> {
            if (entries.isEmpty()) return emptyList()

            val now = Calendar.getInstance()
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val yesterdayStart = todayStart - TimeUnit.DAYS.toMillis(1)
            val thisWeekStart = todayStart - TimeUnit.DAYS.toMillis(6)
            val currentYear = now.get(Calendar.YEAR)
            val currentMonth = now.get(Calendar.MONTH)

            val groups = LinkedHashMap<String, MutableList<HistoryItem>>()

            for (entry in entries) {
                val entryCal = Calendar.getInstance().apply { timeInMillis = entry.playedAt }
                val entryYear = entryCal.get(Calendar.YEAR)
                val entryMonth = entryCal.get(Calendar.MONTH)

                val header = when {
                    entry.playedAt >= todayStart -> "Today"
                    entry.playedAt >= yesterdayStart -> "Yesterday"
                    entry.playedAt >= thisWeekStart -> "This Week"
                    entryYear == currentYear && entryMonth == currentMonth -> "This Month"
                    entryYear == currentYear -> SimpleDateFormat("MMMM", Locale.getDefault()).format(entry.playedAt)
                    else -> "$entryYear"
                }

                groups.getOrPut(header) { mutableListOf() }.add(entry)
            }

            return groups.map { (title, items) -> HistorySection(title, items) }
        }
    }
}
