package com.notmugil.uta.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notmugil.uta.data.NetworkMonitor
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.db.TrackDao
import com.notmugil.uta.data.db.toDomain
import com.notmugil.uta.data.download.DownloadTask
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val offlineDownloadManager: OfflineDownloadManager,
    private val localMediaDao: LocalMediaDao,
    private val trackDao: TrackDao,
    private val subsonicRepository: SubsonicRepository,
    private val networkMonitor: NetworkMonitor,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    private val downloadedTracksFlow = localMediaDao.getLocalMediaFlow(subsonicRepository.currentServerId).map { mediaList ->
        val trackIds = mediaList.map { it.trackId }
        val trackEntities = if (trackIds.isNotEmpty()) {
            trackDao.getTracksByIds(trackIds, subsonicRepository.currentServerId).associateBy { it.id }
        } else {
            emptyMap()
        }
        mediaList.mapNotNull { media ->
            val trackEntity = trackEntities[media.trackId]
            if (trackEntity != null) {
                DownloadedTrackInfo(
                    track = trackEntity.toDomain(),
                    quality = media.quality,
                    format = media.format,
                    bitRate = media.bitRate,
                    fileSizeBytes = media.fileSizeBytes,
                    downloadedAt = media.downloadedAt
                )
            } else {
                null
            }
        }
    }.flowOn(Dispatchers.IO)

    val state: StateFlow<DownloadsState> = combine(
        searchQuery,
        downloadedTracksFlow,
        offlineDownloadManager.activeQueue,
        offlineDownloadManager.storageStats,
        networkMonitor.isOfflineModeActive
    ) { query, tracks, queue, stats, isOffline ->
        DownloadsState(
            searchQuery = query,
            downloadedTracks = tracks,
            activeQueue = queue,
            storageStats = stats,
            isOfflineModeActive = isOffline
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadsState()
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun playTrack(track: TrackItem, queue: List<TrackItem>) {
        playbackController.playTrack(track, queue)
    }

    fun playAll(tracksToPlay: List<TrackItem>? = null) {
        val tracks = tracksToPlay ?: state.value.downloadedTracks.map { it.track }
        if (tracks.isNotEmpty()) {
            playbackController.playQueue(tracks, 0)
        }
    }

    fun deleteTrack(trackId: String) {
        viewModelScope.launch {
            offlineDownloadManager.deleteDownloadedTrack(trackId)
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            offlineDownloadManager.clearAllSessionDownloads()
        }
    }

    fun cancelTask(trackId: String) {
        offlineDownloadManager.cancelTrack(trackId)
    }
}
