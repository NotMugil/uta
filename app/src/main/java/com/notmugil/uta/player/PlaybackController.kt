package com.notmugil.uta.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.notmugil.uta.data.AuthState
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.TrackDao
import com.notmugil.uta.data.db.toDomain
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.domain.model.QueueItem
import com.notmugil.uta.domain.model.TrackItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

data class QueueUndoSnapshot(
    val mediaItems: List<MediaItem>,
    val currentIndex: Int,
    val positionMs: Long,
    val wasPlaying: Boolean
)

data class RemoteQueuePrompt(
    val changedBy: String?,
    val trackCount: Int,
    val onResume: () -> Unit,
    val onDismiss: () -> Unit
)

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subsonicRepository: SubsonicRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val queuePrefetchManager: QueuePrefetchManager,
    private val playbackQueueStore: PlaybackQueueStore,
    private val trackDao: TrackDao,
    private val scrobbleManager: com.notmugil.uta.data.sync.ScrobbleManager,
    private val mutationManager: com.notmugil.uta.data.sync.MutationManager,
    private val networkMonitorProvider: Provider<com.notmugil.uta.data.NetworkMonitor>,
    private val historyRepository: com.notmugil.uta.data.repository.HistoryRepository,
    private val appPreferences: com.notmugil.uta.data.preferences.AppPreferences
) {
    private val isOffline: Boolean
        get() = try {
            networkMonitorProvider.get().isOfflineModeActive.value
        } catch (_: Exception) {
            false
        }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun isConnectedToWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false

        // If network is metered (e.g. mobile hotspot), treat as cellular to preserve user data
        if (!caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            return false
        }

        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun resolveStreamUrl(track: TrackItem): String? {
        val localUri = offlineDownloadManager.getLocalUriForTrack(track.id)
        if (localUri != null) return localUri
        val prefetchedUri = queuePrefetchManager.getPrefetchedAudioUri(track.id)
        if (prefetchedUri != null) return prefetchedUri.toString()
        if (isOffline) return null

        val isWifi = isConnectedToWifi()
        val bitratePref = if (isWifi) appPreferences.wifiStreamingBitrate.value else appPreferences.cellularStreamingBitrate.value
        val formatPref = appPreferences.transcodingFormat.value

        val params = StreamParamsResolver.resolve(
            bitrateSetting = bitratePref,
            formatSetting = formatPref,
            sourceSuffix = track.suffix,
            sourceBitRate = track.bitRate
        )

        return subsonicRepository.getStreamUrl(
            id = track.id,
            maxBitRate = params.maxBitRate,
            format = params.format
        )
    }

    private suspend fun resolveStreamUrl(trackId: String): String? {
        val serverId = subsonicRepository.currentServerId
        val track = if (serverId.isNotBlank()) trackDao.getTrack(trackId, serverId)?.toDomain() else null
        return if (track != null) {
            resolveStreamUrl(track)
        } else {
            val localUri = offlineDownloadManager.getLocalUriForTrack(trackId)
            if (localUri != null) return localUri
            val prefetchedUri = queuePrefetchManager.getPrefetchedAudioUri(trackId)
            if (prefetchedUri != null) return prefetchedUri.toString()
            if (isOffline) return null

            val isWifi = isConnectedToWifi()
            val bitratePref = if (isWifi) appPreferences.wifiStreamingBitrate.value else appPreferences.cellularStreamingBitrate.value
            val formatPref = appPreferences.transcodingFormat.value
            val params = StreamParamsResolver.resolve(bitratePref, formatPref)
            subsonicRepository.getStreamUrl(trackId, params.maxBitRate, params.format)
        }
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _currentTrack = MutableStateFlow<TrackItem?>(null)
    val currentTrack: StateFlow<TrackItem?> = _currentTrack.asStateFlow()

    private val _currentEntryId = MutableStateFlow<String?>(null)
    val currentEntryId: StateFlow<String?> = _currentEntryId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _remoteQueuePrompt = MutableStateFlow<RemoteQueuePrompt?>(null)
    val remoteQueuePrompt: StateFlow<RemoteQueuePrompt?> = _remoteQueuePrompt.asStateFlow()

    private var lastUndoSnapshot: QueueUndoSnapshot? = null

    private var positionJob: Job? = null
    private var queuePersistJob: Job? = null

    private var scrobbledEntryId: String? = null
    private var historyRecordedEntryId: String? = null
    private var nowPlayingEntryId: String? = null
    private var playStartTimeMs: Long = System.currentTimeMillis()
    private var accumulatedPlayTimeMs: Long = 0L
    private var lastPlayTickMs: Long = android.os.SystemClock.elapsedRealtime()

    init {
        initController()
        observeAuthState()
        observeOfflineState()
        observeStreamingPreferences()
    }

    private fun observeStreamingPreferences() {
        scope.launch {
            kotlinx.coroutines.flow.combine(
                appPreferences.transcodingFormat,
                appPreferences.wifiStreamingBitrate,
                appPreferences.cellularStreamingBitrate
            ) { format, wifiBitrate, cellBitrate ->
                Triple(format, wifiBitrate, cellBitrate)
            }.drop(1).collect { (format, wifiBitrate, cellBitrate) ->
                Timber.d("[PlaybackController] Streaming preferences changed: format=${format.displayName}, wifi=${wifiBitrate.displayName}, cell=${cellBitrate.displayName}")
                queuePrefetchManager.clearCache()
                MediaCacheManager.clearCache()
                refreshQueueStreamUrls()
            }
        }
    }

    private fun refreshQueueStreamUrls() {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            val count = ctrl.mediaItemCount
            if (count == 0) return@launch

            val currentIndex = ctrl.currentMediaItemIndex

            // Read items safely on Main thread
            val currentItems = (0 until count).map { i -> ctrl.getMediaItemAt(i) }

            // Resolve updated stream URLs for upcoming items on IO thread
            val updatedItems = withContext(Dispatchers.IO) {
                currentItems.mapIndexed { i, item ->
                    if (i <= currentIndex) {
                        item // keep active and preceding tracks untouched so current playback is not interrupted
                    } else {
                        val track = MediaItemMapper.toTrackItem(item) ?: return@mapIndexed item
                        val entryId = MediaItemMapper.getEntryId(item)
                        val streamUrl = resolveStreamUrl(track) ?: return@mapIndexed item
                        val coverUrl = if (isOffline) null else subsonicRepository.getCoverArtUrl(track.coverArtId)
                        MediaItemMapper.toMediaItem(track, streamUrl, coverUrl, entryId = entryId)
                    }
                }
            }

            if (updatedItems.size == count && count > currentIndex + 1) {
                for (i in (currentIndex + 1) until count) {
                    ctrl.replaceMediaItem(i, updatedItems[i])
                }
                syncState(ctrl)
                debouncePersistQueue()
            }
        }
    }

    private fun observeAuthState() {
        scope.launch {
            subsonicRepository.authState.collect { state ->
                if (state is AuthState.Unauthenticated) {
                    stopAndClearQueue()
                    playbackQueueStore.clearQueue(null)
                    val serverId = subsonicRepository.currentServerId
                    if (serverId.isNotBlank()) {
                        scrobbleManager.clearOutbox(serverId)
                    }
                }
            }
        }
    }

    private fun observeOfflineState() {
        scope.launch {
            networkMonitorProvider.get().isOfflineModeActive.collect { isOfflineActive ->
                if (isOfflineActive) {
                    reconcileQueueForOfflineMode()
                }
            }
        }
    }

    private fun reconcileQueueForOfflineMode() {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            val count = ctrl.mediaItemCount
            if (count == 0) return@launch

            val currentCtrlIndex = ctrl.currentMediaItemIndex
            val currentPosition = ctrl.currentPosition.coerceAtLeast(0L)
            val isCurrentlyPlaying = ctrl.isPlaying

            val newMediaItems = mutableListOf<MediaItem>()
            var newCurrentIndex = -1

            for (i in 0 until count) {
                val item = ctrl.getMediaItemAt(i)
                val track = MediaItemMapper.toTrackItem(item) ?: continue
                val entryId = MediaItemMapper.getEntryId(item)
                val localUri = offlineDownloadManager.getLocalUriForTrack(track.id)
                    ?: queuePrefetchManager.getPrefetchedAudioUri(track.id)?.toString()

                val isCurrentItem = (i == currentCtrlIndex)

                if (localUri != null) {
                    val mediaItem = MediaItemMapper.toMediaItem(
                        track = track,
                        streamUrl = localUri,
                        coverArtUrl = null,
                        entryId = entryId
                    )
                    if (isCurrentItem) {
                        newCurrentIndex = newMediaItems.size
                    }
                    newMediaItems.add(mediaItem)
                } else if (isCurrentItem) {
                    // Retain the currently playing track so playback does not stop or close abruptly mid-play
                    newCurrentIndex = newMediaItems.size
                    newMediaItems.add(item)
                }
            }

            if (newMediaItems.isEmpty()) {
                Timber.i("[PlaybackController] Offline mode activated: No downloaded or prefetched tracks in queue.")
                ctrl.stop()
                ctrl.clearMediaItems()
                _currentTrack.value = null
                _currentEntryId.value = null
                _isPlaying.value = false
                _queue.value = emptyList()
                _currentQueueIndex.value = -1
                _currentPositionMs.value = 0L
                _durationMs.value = 0L
                val serverId = subsonicRepository.currentServerId
                playbackQueueStore.clearQueue(serverId)
            } else {
                Timber.i("[PlaybackController] Offline mode activated: Retained ${newMediaItems.size}/$count tracks (downloaded, prefetched & active track).")
                if (newCurrentIndex >= 0) {
                    ctrl.setMediaItems(newMediaItems, newCurrentIndex, currentPosition)
                    ctrl.prepare()
                    if (isCurrentlyPlaying) {
                        ctrl.play()
                    }
                } else {
                    ctrl.pause()
                    ctrl.setMediaItems(newMediaItems, 0, 0L)
                    ctrl.prepare()
                }
                syncState(ctrl)
                debouncePersistQueue()
            }
        }
    }

    private fun initController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    val mediaController = controllerFuture?.get()
                    controller = mediaController
                    mediaController?.let { bindController(it) }
                } catch (e: Exception) {
                    Timber.e(e, "[PlaybackController] Binding MediaController failed")
                }
            },
            androidx.core.content.ContextCompat.getMainExecutor(context)
        )
    }

    private var isListenerAttached = false

    private suspend fun getConnectedController(): MediaController? {
        controller?.let {
            if (it.isConnected) return it
            controller = null
            isListenerAttached = false
        }
        return withContext(Dispatchers.Main) {
            try {
                var future = controllerFuture
                val shouldRebuild = future == null || future.isCancelled || (future.isDone && runCatching { future.get() }.isFailure)
                if (shouldRebuild) {
                    val sessionToken = SessionToken(
                        context,
                        ComponentName(context, PlaybackService::class.java)
                    )
                    future = MediaController.Builder(context, sessionToken).buildAsync()
                    controllerFuture = future
                }

                if (future.isDone) {
                    val ctrl = future.get()
                    controller = ctrl
                    bindController(ctrl)
                    ctrl
                } else {
                    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                        future.addListener(
                            {
                                try {
                                    val ctrl = future.get()
                                    controller = ctrl
                                    bindController(ctrl)
                                    if (continuation.isActive) {
                                        continuation.resumeWith(Result.success(ctrl))
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "[PlaybackController] Error connecting MediaController")
                                    controllerFuture = null
                                    controller = null
                                    if (continuation.isActive) {
                                        continuation.resumeWith(Result.success(null))
                                    }
                                }
                            },
                            androidx.core.content.ContextCompat.getMainExecutor(context)
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[PlaybackController] Error getting MediaController")
                controllerFuture = null
                controller = null
                null
            }
        }
    }

    private fun bindController(mediaController: MediaController) {
        syncState(mediaController)
        if (isListenerAttached) return
        isListenerAttached = true

        mediaController.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                try {
                    val track = MediaItemMapper.toTrackItem(mediaItem)
                    val entryId = MediaItemMapper.getEntryId(mediaItem)
                    _currentTrack.value = track
                    _currentEntryId.value = entryId
                    val rawIndex = mediaController.currentMediaItemIndex
                    if (mediaController.shuffleModeEnabled) {
                        val displayIndex = _queue.value.indexOfFirst { it.entryId == entryId }
                        _currentQueueIndex.value = if (displayIndex >= 0) displayIndex else rawIndex
                    } else {
                        _currentQueueIndex.value = rawIndex
                    }
                    val dur = mediaController.duration
                    _durationMs.value = if (dur > 0L) dur else ((track?.durationSeconds ?: 0L) * 1000L)
                    updatePosition()

                    // Reset scrobble state on item change or repeat
                    val isRepeat = (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                    if (entryId != nowPlayingEntryId || isRepeat) {
                        scrobbledEntryId = null
                        historyRecordedEntryId = null
                        nowPlayingEntryId = entryId
                        playStartTimeMs = System.currentTimeMillis()
                        accumulatedPlayTimeMs = 0L
                        lastPlayTickMs = android.os.SystemClock.elapsedRealtime()
                        track?.let { t ->
                            scope.launch {
                                try {
                                    scrobbleManager.recordNowPlaying(t.id)
                                    scrobbleManager.flushOutbox()
                                } catch (e: Exception) {
                                    Timber.w(e, "[PlaybackController] Scrobble recording failed")
                                }
                            }
                        }
                    }
                    debouncePersistQueue()
                } catch (e: Exception) {
                    Timber.e(e, "[PlaybackController] Error handling onMediaItemTransition")
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                lastPlayTickMs = android.os.SystemClock.elapsedRealtime()
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                    updatePosition()
                    persistCurrentPosition()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                val dur = mediaController.duration
                if (dur > 0L) {
                    _durationMs.value = dur
                } else {
                    val fallback = (_currentTrack.value?.durationSeconds ?: 0L) * 1000L
                    if (fallback > 0L && _durationMs.value <= 0L) {
                        _durationMs.value = fallback
                    }
                }
                updatePosition()
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    persistCurrentPosition()
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncQueue(mediaController)
                _currentQueueIndex.value = mediaController.currentMediaItemIndex
                debouncePersistQueue()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                _currentPositionMs.value = newPosition.positionMs.coerceAtLeast(0L)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
                debouncePersistQueue()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffleEnabled.value = shuffleModeEnabled
                debouncePersistQueue()
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }
        })

        // Cold-start restoration: if timeline is empty, restore persisted queue paused
        if (mediaController.mediaItemCount == 0) {
            restorePersistedQueue(mediaController)
        }
    }

    private fun restorePersistedQueue(mediaController: MediaController) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return

        scope.launch(Dispatchers.IO) {
            val savedState = playbackQueueStore.loadQueue(serverId) ?: return@launch
            if (savedState.entries.isEmpty()) return@launch

            val trackIds = savedState.entries.map { it.trackId }
            val trackEntities = trackDao.getTracksByIds(trackIds, serverId).associateBy { it.id }

            val mediaItems = savedState.entries.mapNotNull { entry ->
                val trackEntity = trackEntities[entry.trackId] ?: return@mapNotNull null
                val track = trackEntity.toDomain()
                val streamUrl = resolveStreamUrl(track.id) ?: return@mapNotNull null
                val coverUrl = if (isOffline) null else subsonicRepository.getCoverArtUrl(track.coverArtId)
                MediaItemMapper.toMediaItem(track, streamUrl, coverUrl, entryId = entry.entryId)
            }

            if (mediaItems.isEmpty()) {
                checkForRemoteQueue()
                return@launch
            }

            withContext(Dispatchers.Main) {
                val targetIndex = savedState.currentIndex.coerceIn(0, mediaItems.size - 1)
                if (mediaItems.size <= 100) {
                    mediaController.setMediaItems(mediaItems, targetIndex, savedState.positionMs)
                } else {
                    val firstChunk = mediaItems.take(100)
                    val initialIndex = if (targetIndex < 100) targetIndex else 0
                    val initialPos = if (targetIndex < 100) savedState.positionMs else 0L
                    mediaController.setMediaItems(firstChunk, initialIndex, initialPos)
                    for (chunk in mediaItems.drop(100).chunked(100)) {
                        mediaController.addMediaItems(chunk)
                    }
                    if (targetIndex >= 100) {
                        if (savedState.positionMs > 0L) {
                            performSeek(mediaController, savedState.positionMs, targetIndex)
                        } else if (mediaController.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) {
                            mediaController.seekToDefaultPosition(targetIndex)
                        } else {
                            performSeek(mediaController, 0L, targetIndex)
                        }
                    }
                }
                mediaController.repeatMode = savedState.repeatMode
                mediaController.shuffleModeEnabled = savedState.isShuffleEnabled
                mediaController.prepare()
                // Do NOT call mediaController.play() on cold restore - keep paused
                syncState(mediaController)
                checkForRemoteQueue()
            }
        }
    }

    private fun checkForRemoteQueue() {
        if (isOffline) return
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val remoteQueue = subsonicRepository.getServerPlayQueue() ?: return@launch
                if (remoteQueue.trackIds.isEmpty()) return@launch

                val localTracks = _queue.value.map { it.track.id }
                if (localTracks == remoteQueue.trackIds) return@launch

                withContext(Dispatchers.Main) {
                    _remoteQueuePrompt.value = RemoteQueuePrompt(
                        changedBy = remoteQueue.changedBy ?: "another device",
                        trackCount = remoteQueue.trackIds.size,
                        onResume = {
                            resumeRemoteQueue(remoteQueue)
                            _remoteQueuePrompt.value = null
                        },
                        onDismiss = {
                            _remoteQueuePrompt.value = null
                        }
                    )
                }
            } catch (e: Exception) {
                Timber.d(e, "[PlaybackController] Remote queue check skipped")
            }
        }
    }

    fun resumeRemoteQueue(remoteQueue: com.notmugil.uta.data.ServerPlayQueue) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return

        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            val trackEntities = withContext(Dispatchers.IO) {
                trackDao.getTracksByIds(remoteQueue.trackIds, serverId).associateBy { it.id }
            }
            val mediaItems = withContext(Dispatchers.IO) {
                remoteQueue.trackIds.mapNotNull { trackId ->
                    val trackEntity = trackEntities[trackId] ?: return@mapNotNull null
                    val track = trackEntity.toDomain()
                    val streamUrl = resolveStreamUrl(track.id) ?: return@mapNotNull null
                    val coverUrl = subsonicRepository.getCoverArtUrl(track.coverArtId)
                    MediaItemMapper.toMediaItem(track, streamUrl, coverUrl, entryId = UUID.randomUUID().toString())
                }
            }

            if (mediaItems.isEmpty()) return@launch

            val targetIndex = (remoteQueue.currentIndex ?: 0).coerceIn(0, mediaItems.size - 1)
            if (mediaItems.size <= 100) {
                ctrl.setMediaItems(mediaItems, targetIndex, remoteQueue.positionMs)
            } else {
                val firstChunk = mediaItems.take(100)
                val initialIndex = if (targetIndex < 100) targetIndex else 0
                val initialPos = if (targetIndex < 100) remoteQueue.positionMs else 0L
                ctrl.setMediaItems(firstChunk, initialIndex, initialPos)
                for (chunk in mediaItems.drop(100).chunked(100)) {
                    ctrl.addMediaItems(chunk)
                }
                if (targetIndex >= 100) {
                    if (remoteQueue.positionMs > 0L) {
                        performSeek(ctrl, remoteQueue.positionMs, targetIndex)
                    } else if (ctrl.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) {
                        ctrl.seekToDefaultPosition(targetIndex)
                    } else {
                        performSeek(ctrl, 0L, targetIndex)
                    }
                }
            }
            ctrl.prepare()
            syncState(ctrl)
            debouncePersistQueue()
        }
    }

    private fun syncState(mediaController: MediaController) {
        val currentItem = mediaController.currentMediaItem
        val mappedTrack = MediaItemMapper.toTrackItem(currentItem)
        _currentTrack.value = mappedTrack
        mappedTrack?.let { track ->
            scope.launch(Dispatchers.IO) {
                val serverId = subsonicRepository.currentServerId
                val dbTrack = if (serverId.isNotBlank()) trackDao.getTrack(track.id, serverId) else trackDao.getTrackGlobal(track.id)
                val isDbStarred = dbTrack != null && dbTrack.starredAt != null
                if (dbTrack != null && isDbStarred != track.isStarred) {
                    if (_currentTrack.value?.id == track.id) {
                        _currentTrack.value = track.copy(isStarred = isDbStarred)
                    }
                }
            }
        }
        _currentEntryId.value = currentItem?.let { MediaItemMapper.getEntryId(it) }
        _isPlaying.value = mediaController.isPlaying
        _repeatMode.value = mediaController.repeatMode
        _isShuffleEnabled.value = mediaController.shuffleModeEnabled
        _playbackSpeed.value = mediaController.playbackParameters.speed
        val dur = mediaController.duration
        _durationMs.value = if (dur > 0L) dur else ((mappedTrack?.durationSeconds ?: 0L) * 1000L)
        syncQueue(mediaController)
        if (mediaController.isPlaying) {
            startPositionUpdates()
        }
    }

    private fun syncQueue(mediaController: MediaController) {
        val count = mediaController.mediaItemCount
        val timeline = mediaController.currentTimeline

        // When shuffle is enabled, display the queue in the actual order it will play next
        if (mediaController.shuffleModeEnabled && !timeline.isEmpty) {
            val items = ArrayList<QueueItem>(count)
            val visited = HashSet<Int>()
            var windowIndex = timeline.getFirstWindowIndex(true)
            while (windowIndex != androidx.media3.common.C.INDEX_UNSET && visited.add(windowIndex) && windowIndex in 0 until count) {
                val item = mediaController.getMediaItemAt(windowIndex)
                MediaItemMapper.toQueueItem(item)?.let { items.add(it) }
                windowIndex = timeline.getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)
            }
            if (items.size < count) {
                for (i in 0 until count) {
                    if (!visited.contains(i)) {
                        val item = mediaController.getMediaItemAt(i)
                        MediaItemMapper.toQueueItem(item)?.let { items.add(it) }
                    }
                }
            }
            _queue.value = items
            val currentEntryId = _currentEntryId.value
            val displayIndex = items.indexOfFirst { it.entryId == currentEntryId }
            val resolvedIndex = if (displayIndex >= 0) displayIndex else mediaController.currentMediaItemIndex
            _currentQueueIndex.value = resolvedIndex
            queuePrefetchManager.prefetchUpcomingTracks(items.map { it.track }, resolvedIndex)
        } else {
            val items = ArrayList<QueueItem>(count)
            for (i in 0 until count) {
                val item = mediaController.getMediaItemAt(i)
                MediaItemMapper.toQueueItem(item)?.let { items.add(it) }
            }
            _queue.value = items
            val rawIndex = mediaController.currentMediaItemIndex
            _currentQueueIndex.value = rawIndex
            queuePrefetchManager.prefetchUpcomingTracks(items.map { it.track }, rawIndex)
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            var tickCount = 0
            while (isActive) {
                updatePosition()
                checkScrobbleThreshold()
                tickCount++
                if (tickCount % 30 == 0) { // ~3 seconds
                    persistCurrentPosition()
                }
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private var lastSeekRequestedTimeMs: Long = 0L

    private fun updatePosition() {
        controller?.let {
            val now = System.currentTimeMillis()
            if (now - lastSeekRequestedTimeMs < 800L && _isBuffering.value) {
                return
            }
            _currentPositionMs.value = it.currentPosition.coerceAtLeast(0L)
            val dur = it.duration
            if (dur > 0L) {
                _durationMs.value = dur
            } else {
                val fallback = (_currentTrack.value?.durationSeconds ?: 0L) * 1000L
                if (fallback > 0L && _durationMs.value <= 0L) {
                    _durationMs.value = fallback
                }
            }
        }
    }

    private fun checkScrobbleThreshold() {
        val track = _currentTrack.value ?: return
        val entryId = _currentEntryId.value ?: return

        val now = android.os.SystemClock.elapsedRealtime()
        if (_isPlaying.value) {
            val elapsed = (now - lastPlayTickMs).coerceAtLeast(0L)
            accumulatedPlayTimeMs += elapsed
        }
        lastPlayTickMs = now

        val durationMs = if (_durationMs.value > 0) _durationMs.value else track.durationSeconds * 1000L
        if (durationMs < 5_000L) return

        val percent = appPreferences.scrobbleThresholdPercent.value.coerceIn(1, 100)
        val targetPercentMs = (durationMs * percent) / 100L
        val minThresholdMs = minOf(30_000L, durationMs)
        val thresholdMs = minOf(targetPercentMs, 240_000L).coerceAtLeast(minThresholdMs)

        if (accumulatedPlayTimeMs >= thresholdMs) {
            if (historyRecordedEntryId != entryId) {
                historyRecordedEntryId = entryId
                scope.launch {
                    try {
                        historyRepository.recordPlay(track)
                    } catch (e: Exception) {
                        Timber.w(e, "[PlaybackController] History recording failed")
                    }
                }
            }

            if (scrobbledEntryId != entryId) {
                scrobbledEntryId = entryId
                val startTime = playStartTimeMs
                scope.launch {
                    try {
                        scrobbleManager.submitScrobble(track.id, startTime)
                    } catch (e: Exception) {
                        Timber.w(e, "[PlaybackController] Scrobble submission failed")
                    }
                }
            }
        }
    }

    private fun debouncePersistQueue() {
        queuePersistJob?.cancel()
        queuePersistJob = scope.launch {
            delay(500)
            val serverId = subsonicRepository.currentServerId
            if (serverId.isBlank()) return@launch

            val ctrl = controller
            val count = ctrl?.mediaItemCount ?: 0

            // Save local persisted queue (read on application/main thread)
            val localEntries = ArrayList<PersistedQueueEntry>(count)
            val canonicalTrackIds = ArrayList<String>(count)
            for (i in 0 until count) {
                val item = ctrl?.getMediaItemAt(i)
                val entryId = item?.let { MediaItemMapper.getEntryId(it) } ?: ""
                val trackId = item?.mediaMetadata?.extras?.getString(MediaItemMapper.EXTRA_TRACK_ID)
                    ?: item?.mediaId ?: ""
                if (trackId.isNotBlank()) {
                    localEntries.add(PersistedQueueEntry(entryId = entryId, trackId = trackId))
                    canonicalTrackIds.add(trackId)
                }
            }

            val currentIndex = ctrl?.currentMediaItemIndex ?: _currentQueueIndex.value
            val positionMs = _currentPositionMs.value
            val repeat = _repeatMode.value
            val shuffle = _isShuffleEnabled.value
            val currentTrackId = _currentTrack.value?.id

            withContext(Dispatchers.IO) {
                playbackQueueStore.saveQueue(serverId, localEntries, currentIndex, positionMs, repeat, shuffle)

                if (!isOffline && canonicalTrackIds.isNotEmpty()) {
                    subsonicRepository.saveServerPlayQueue(
                        trackIds = canonicalTrackIds,
                        currentId = currentTrackId,
                        currentIndex = currentIndex,
                        positionMs = positionMs
                    )
                }
            }
        }
    }

    private fun persistCurrentPosition() {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return
        val pos = _currentPositionMs.value
        val idx = _currentQueueIndex.value
        playbackQueueStore.savePosition(serverId, pos, idx)
    }

    private fun captureUndoSnapshot() {
        val ctrl = controller ?: return
        val count = ctrl.mediaItemCount
        if (count == 0) return
        val items = ArrayList<MediaItem>(count)
        for (i in 0 until count) {
            items.add(ctrl.getMediaItemAt(i))
        }
        lastUndoSnapshot = QueueUndoSnapshot(
            mediaItems = items,
            currentIndex = ctrl.currentMediaItemIndex,
            positionMs = ctrl.currentPosition.coerceAtLeast(0L),
            wasPlaying = ctrl.isPlaying
        )
    }

    fun undoLastQueueAction(): Boolean {
        val snapshot = lastUndoSnapshot ?: return false
        val ctrl = controller ?: return false
        lastUndoSnapshot = null

        val targetIndex = snapshot.currentIndex.coerceIn(0, snapshot.mediaItems.size - 1)
        val items = snapshot.mediaItems

        if (items.size <= 100) {
            ctrl.setMediaItems(items, targetIndex, snapshot.positionMs)
        } else {
            val firstChunk = items.take(100)
            val initialIndex = if (targetIndex < 100) targetIndex else 0
            val initialPos = if (targetIndex < 100) snapshot.positionMs else 0L
            ctrl.setMediaItems(firstChunk, initialIndex, initialPos)
            for (chunk in items.drop(100).chunked(100)) {
                ctrl.addMediaItems(chunk)
            }
            if (targetIndex >= 100) {
                if (snapshot.positionMs > 0L) {
                    performSeek(ctrl, snapshot.positionMs, targetIndex)
                } else if (ctrl.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) {
                    ctrl.seekToDefaultPosition(targetIndex)
                } else {
                    performSeek(ctrl, 0L, targetIndex)
                }
            }
        }

        ctrl.prepare()
        if (snapshot.wasPlaying) {
            ctrl.play()
        }
        syncState(ctrl)
        debouncePersistQueue()
        return true
    }

    fun playTrack(track: TrackItem, queue: List<TrackItem> = listOf(track)) {
        val targetIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playQueue(queue, targetIndex)
    }

    fun playQueue(tracks: List<TrackItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return

        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            val mediaItems = withContext(Dispatchers.IO) {
                tracks.mapNotNull { track ->
                    val streamUrl = resolveStreamUrl(track.id) ?: return@mapNotNull null
                    val coverUrl = if (isOffline) null else subsonicRepository.getCoverArtUrl(track.coverArtId)
                    MediaItemMapper.toMediaItem(track, streamUrl, coverUrl, entryId = UUID.randomUUID().toString())
                }
            }

            if (mediaItems.isEmpty()) {
                Timber.w("[PlaybackController] playQueue: No playable media items constructed for ${tracks.size} tracks (isOffline=$isOffline)")
                return@launch
            }

            val targetIndex = startIndex.coerceIn(0, mediaItems.size - 1)
            if (mediaItems.size <= 100) {
                ctrl.setMediaItems(mediaItems, targetIndex, startPositionMs)
            } else {
                val firstChunk = mediaItems.take(100)
                val initialIndex = if (targetIndex < 100) targetIndex else 0
                val initialPos = if (targetIndex < 100) startPositionMs else 0L
                ctrl.setMediaItems(firstChunk, initialIndex, initialPos)
                for (chunk in mediaItems.drop(100).chunked(100)) {
                    ctrl.addMediaItems(chunk)
                }
                if (targetIndex >= 100) {
                    if (startPositionMs > 0L) {
                        performSeek(ctrl, startPositionMs, targetIndex)
                    } else if (ctrl.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) {
                        ctrl.seekToDefaultPosition(targetIndex)
                    } else {
                        performSeek(ctrl, 0L, targetIndex)
                    }
                }
            }
            ctrl.prepare()
            ctrl.play()
            syncState(ctrl)
        }
    }

    fun playNext(track: TrackItem) = playNext(listOf(track))

    fun playNext(tracks: List<TrackItem>) {
        if (tracks.isEmpty()) return

        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            val mediaItems = withContext(Dispatchers.IO) {
                tracks.mapNotNull { track ->
                    val streamUrl = resolveStreamUrl(track.id) ?: return@mapNotNull null
                    val coverUrl = if (isOffline) null else subsonicRepository.getCoverArtUrl(track.coverArtId)
                    MediaItemMapper.toMediaItem(track, streamUrl, coverUrl, entryId = UUID.randomUUID().toString())
                }
            }
            if (mediaItems.isEmpty()) return@launch

            val insertIndex = (ctrl.currentMediaItemIndex + 1).coerceAtLeast(0).coerceAtMost(ctrl.mediaItemCount)
            if (mediaItems.size <= 100) {
                ctrl.addMediaItems(insertIndex, mediaItems)
            } else {
                var curIdx = insertIndex
                for (chunk in mediaItems.chunked(100)) {
                    ctrl.addMediaItems(curIdx, chunk)
                    curIdx += chunk.size
                }
            }
            if (ctrl.mediaItemCount == mediaItems.size) {
                ctrl.prepare()
                ctrl.play()
            }
            syncState(ctrl)
        }
    }

    fun addToQueue(track: TrackItem) = addToQueue(listOf(track))

    fun addToQueue(tracks: List<TrackItem>) {
        if (tracks.isEmpty()) return

        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            val mediaItems = withContext(Dispatchers.IO) {
                tracks.mapNotNull { track ->
                    val streamUrl = resolveStreamUrl(track.id) ?: return@mapNotNull null
                    val coverUrl = if (isOffline) null else subsonicRepository.getCoverArtUrl(track.coverArtId)
                    MediaItemMapper.toMediaItem(track, streamUrl, coverUrl, entryId = UUID.randomUUID().toString())
                }
            }
            if (mediaItems.isEmpty()) return@launch

            val wasEmpty = ctrl.mediaItemCount == 0
            if (mediaItems.size <= 100) {
                ctrl.addMediaItems(mediaItems)
            } else {
                for (chunk in mediaItems.chunked(100)) {
                    ctrl.addMediaItems(chunk)
                }
            }
            if (wasEmpty) {
                ctrl.prepare()
                ctrl.play()
            }
            syncState(ctrl)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            val currentQueueList = _queue.value
            if (fromIndex !in currentQueueList.indices || toIndex !in currentQueueList.indices) return@launch

            val fromEntryId = currentQueueList[fromIndex].entryId
            val toEntryId = currentQueueList[toIndex].entryId

            val fromCtrlIndex = (0 until ctrl.mediaItemCount).firstOrNull { i ->
                MediaItemMapper.getEntryId(ctrl.getMediaItemAt(i)) == fromEntryId
            } ?: fromIndex
            val toCtrlIndex = (0 until ctrl.mediaItemCount).firstOrNull { i ->
                MediaItemMapper.getEntryId(ctrl.getMediaItemAt(i)) == toEntryId
            } ?: toIndex

            if (fromCtrlIndex in 0 until ctrl.mediaItemCount && toCtrlIndex in 0 until ctrl.mediaItemCount) {
                ctrl.moveMediaItem(fromCtrlIndex, toCtrlIndex)
            }
        }
    }

    fun removeQueueItem(index: Int) {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            val currentQueueList = _queue.value
            if (index !in currentQueueList.indices) return@launch

            val targetEntryId = currentQueueList[index].entryId
            val targetControllerIndex = (0 until ctrl.mediaItemCount).firstOrNull { i ->
                MediaItemMapper.getEntryId(ctrl.getMediaItemAt(i)) == targetEntryId
            } ?: index

            if (targetControllerIndex in 0 until ctrl.mediaItemCount) {
                captureUndoSnapshot()
                ctrl.removeMediaItem(targetControllerIndex)
            }
        }
    }

    fun removeQueueEntry(entryId: String) {
        val q = _queue.value
        val index = q.indexOfFirst { it.entryId == entryId }
        if (index >= 0) {
            removeQueueItem(index)
        }
    }

    fun seekToQueueIndex(index: Int, positionMs: Long = 0L) {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            val currentQueueList = _queue.value
            if (index !in currentQueueList.indices) return@launch

            val targetEntryId = currentQueueList[index].entryId
            val targetControllerIndex = (0 until ctrl.mediaItemCount).firstOrNull { i ->
                MediaItemMapper.getEntryId(ctrl.getMediaItemAt(i)) == targetEntryId
            } ?: index

            if (targetControllerIndex in 0 until ctrl.mediaItemCount) {
                if (positionMs > 0L) {
                    performSeek(ctrl, positionMs, targetControllerIndex)
                } else if (ctrl.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) {
                    ctrl.seekToDefaultPosition(targetControllerIndex)
                } else {
                    performSeek(ctrl, 0L, targetControllerIndex)
                }
                if (!ctrl.isPlaying) {
                    ctrl.play()
                }
            }
        }
    }

    fun togglePlayPause() {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            if (ctrl.isPlaying) {
                ctrl.pause()
            } else {
                ctrl.play()
            }
        }
    }

    fun pause() {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            ctrl.pause()
        }
    }

    fun resume() {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            ctrl.play()
        }
    }

    private fun performSeek(ctrl: MediaController, positionMs: Long, targetIndex: Int = ctrl.currentMediaItemIndex) {
        val curIndex = if (targetIndex >= 0) targetIndex else ctrl.currentMediaItemIndex
        lastSeekRequestedTimeMs = System.currentTimeMillis()
        _currentPositionMs.value = positionMs

        val isCurrentItem = (curIndex == ctrl.currentMediaItemIndex)
        if (isCurrentItem && ctrl.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            ctrl.seekTo(positionMs)
        } else if (ctrl.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) && curIndex in 0 until ctrl.mediaItemCount) {
            ctrl.seekTo(curIndex, positionMs)
        } else {
            val args = android.os.Bundle().apply {
                putLong("position_ms", positionMs)
                putInt("media_item_index", curIndex)
            }
            ctrl.sendCustomCommand(
                androidx.media3.session.SessionCommand(PlaybackService.ACTION_SEEK_TO, android.os.Bundle.EMPTY),
                args
            )
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            performSeek(ctrl, positionMs)
            _currentPositionMs.value = positionMs
        }
    }

    fun skipToNext() {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            if (ctrl.hasNextMediaItem()) {
                ctrl.seekToNextMediaItem()
            }
        }
    }

    fun skipToPrevious() {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            if (ctrl.currentPosition > 3000L) {
                ctrl.seekTo(0L)
            } else if (ctrl.hasPreviousMediaItem()) {
                ctrl.seekToPreviousMediaItem()
            } else {
                ctrl.seekTo(0L)
            }
        }
    }

    fun toggleRepeatMode() {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            val nextMode = when (ctrl.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            ctrl.repeatMode = nextMode
            _repeatMode.value = nextMode
            debouncePersistQueue()
        }
    }

    fun toggleShuffle() {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            val newMode = !ctrl.shuffleModeEnabled
            ctrl.shuffleModeEnabled = newMode
            _isShuffleEnabled.value = newMode
            debouncePersistQueue()
        }
    }

    fun toggleFavorite() {
        val track = _currentTrack.value ?: return
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return

        val newStarred = !track.isStarred
        _currentTrack.value = track.copy(isStarred = newStarred)

        scope.launch(Dispatchers.IO) {
            try {
                mutationManager.setTrackStarred(track.id, newStarred)
            } catch (e: Exception) {
                Timber.e(e, "[PlaybackController] Failed to toggle favorite for ${track.id}")
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        scope.launch {
            val ctrl = getConnectedController() ?: return@launch
            ctrl.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
            _playbackSpeed.value = speed
        }
    }

    fun stopAndClearQueue() {
        captureUndoSnapshot()
        stopPositionUpdates()
        scope.launch {
            val ctrl = getConnectedController()
            ctrl?.let {
                it.stop()
                it.clearMediaItems()
            }
        }
        _currentTrack.value = null
        _currentEntryId.value = null
        _isPlaying.value = false
        _queue.value = emptyList()
        _currentQueueIndex.value = -1
        _currentPositionMs.value = 0L
        _durationMs.value = 0L

        val serverId = subsonicRepository.currentServerId
        playbackQueueStore.clearQueue(serverId)
    }
}
