package com.notmugil.uta.data.download

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.DownloadQueueEntity
import com.notmugil.uta.data.db.DownloadScopeEntity
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.db.LocalMediaEntity
import com.notmugil.uta.data.db.PlaylistDao
import com.notmugil.uta.data.db.TrackDao
import com.notmugil.uta.data.db.toDomain
import com.notmugil.uta.domain.model.TrackItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private data class DownloadPathInfo(
    val safeServerId: String,
    val albumDirName: String,
    val safeTrackId: String,
    val ext: String,
    val targetDir: File,
    val tempFile: File,
    val targetFile: File,
    val relativePath: String
)

@Singleton
class OfflineDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subsonicRepository: SubsonicRepository,
    private val localMediaDao: LocalMediaDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val okHttpClient: OkHttpClient,
    private val notificationManager: DownloadNotificationManager,
    private val networkMonitorProvider: javax.inject.Provider<com.notmugil.uta.data.NetworkMonitor>,
    private val appPreferences: com.notmugil.uta.data.preferences.AppPreferences
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "[Download] Unhandled error in download manager")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val enqueueMutex = Mutex()

    private fun isConnectedToWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun resolveDefaultDownloadQuality(): DownloadQuality {
        val qualityPref = appPreferences.downloadQuality.value
        val formatPref = appPreferences.downloadFormat.value

        val isOriginalQuality = qualityPref == com.notmugil.uta.data.preferences.DownloadQualityPreference.ORIGINAL
        val isOriginalFormat = formatPref == com.notmugil.uta.data.preferences.DownloadFormatPreference.ORIGINAL

        return if (isOriginalQuality && isOriginalFormat) {
            DownloadQuality.Original
        } else {
            val bitrateKbps = if (qualityPref.kbps > 0) qualityPref.kbps else null
            val format = if (formatPref.extension != "raw") formatPref.extension else null
            if (bitrateKbps == null && format == null) {
                DownloadQuality.Original
            } else {
                DownloadQuality.Transcoded(
                    bitrateKbps = bitrateKbps,
                    format = format
                )
            }
        }
    }

    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadStatus>> = _downloadStates.asStateFlow()

    private val _activeQueue = MutableStateFlow<List<DownloadTask>>(emptyList())
    val activeQueue: StateFlow<List<DownloadTask>> = _activeQueue.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val downloadSemaphore = Semaphore(2)

    private val batchTotal = AtomicInteger(0)
    private val batchCompleted = AtomicInteger(0)
    private val batchFailed = AtomicInteger(0)
    private var notificationLoopJob: Job? = null

    val musicRootDir: File
        get() {
            val dir = context.getExternalFilesDir("music") ?: File(context.filesDir, "music")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    init {
        scope.launch {
            reconcile()
            refreshStorageStats()
            if (!networkMonitorProvider.get().isOfflineModeActive.value) {
                resumePersistedQueue()
            }
        }
        scope.launch {
            networkMonitorProvider.get().isOfflineModeActive.collect { isOffline ->
                if (isOffline) {
                    downloadJobs.values.forEach { it.cancel() }
                    downloadJobs.clear()
                    _activeQueue.value = emptyList()
                    notificationLoopJob?.cancel()
                    notificationManager.hideAll()
                } else {
                    resumePersistedQueue()
                }
            }
        }
    }

    private fun sanitize(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)
    }

    private fun sanitizeFilename(input: String): String {
        return input
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
            .trim()
            .take(120)
    }

    private suspend fun reconcile() = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isNotBlank()) {
            localMediaDao.resetDownloadingToQueued(serverId)
        }

        // Clean orphaned temporary files
        try {
            musicRootDir.walkTopDown()
                .filter { it.isFile && (it.name.endsWith(".tmp") || it.name.endsWith(".part")) }
                .forEach { tmpFile ->
                    try {
                        tmpFile.delete()
                    } catch (_: Exception) {}
                }
        } catch (_: Exception) {}
    }

    private suspend fun resumePersistedQueue() = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext

        val queuedEntities = localMediaDao.getQueueForServer(serverId)
        val tasksToResume = mutableListOf<DownloadTask>()

        for (entity in queuedEntities) {
            val trackEntity = trackDao.getTrack(entity.trackId, serverId) ?: continue
            val quality = DownloadQuality.fromQualityString(entity.quality, entity.bitRate, entity.format)
            tasksToResume.add(
                DownloadTask(
                    track = trackEntity.toDomain(),
                    serverId = serverId,
                    scopeId = entity.scopeId,
                    scopeType = entity.scopeType,
                    quality = quality,
                    status = DownloadStatus.Queued,
                    queuedAt = entity.queuedAt
                )
            )
        }

        if (tasksToResume.isNotEmpty()) {
            enqueueTasksInternal(tasksToResume, persistToDb = false)
        }
    }

    fun getDownloadStatusFlow(trackId: String): Flow<DownloadStatus> {
        return downloadStates.map { it[trackId] ?: DownloadStatus.Idle }
            .distinctUntilChanged()
    }

    fun isTrackDownloadedFlow(trackId: String): Flow<Boolean> {
        val serverId = subsonicRepository.currentServerId
        return localMediaDao.getDownloadedTrackIdsFlow(serverId).map { idList ->
            idList.contains(trackId)
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    suspend fun getLocalUriForTrack(trackId: String): String? = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        val record = localMediaDao.getLocalMedia(trackId, serverId) ?: return@withContext null
        val file = File(musicRootDir, record.relativePath)
        if (file.exists() && file.length() > 0) {
            Uri.fromFile(file).toString()
        } else {
            null
        }
    }

    suspend fun getLocalMedia(trackId: String): LocalMediaEntity? = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext null
        localMediaDao.getLocalMedia(trackId, serverId)
    }

    fun enqueueTrack(
        track: TrackItem,
        quality: DownloadQuality = resolveDefaultDownloadQuality(),
        scopeId: String = track.id,
        scopeType: String = "TRACK"
    ) {
        if (networkMonitorProvider.get().isOfflineModeActive.value) {
            Timber.w("[Download] Offline mode is active, skipping enqueueTrack for ${track.id}")
            return
        }
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return

        val task = DownloadTask(
            track = track,
            serverId = serverId,
            scopeId = scopeId,
            scopeType = scopeType,
            quality = quality
        )
        enqueueTasks(listOf(task))
    }

    fun enqueueTracks(
        tracks: List<TrackItem>,
        quality: DownloadQuality = resolveDefaultDownloadQuality(),
        scopeId: String,
        scopeType: String = "ALBUM"
    ) {
        if (networkMonitorProvider.get().isOfflineModeActive.value) {
            Timber.w("[Download] Offline mode is active, skipping enqueueTracks")
            return
        }
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank() || tracks.isEmpty()) return

        val tasks = tracks.map {
            DownloadTask(
                track = it,
                serverId = serverId,
                scopeId = scopeId,
                scopeType = scopeType,
                quality = quality
            )
        }
        enqueueTasks(tasks)
    }

    private fun enqueueTasks(tasks: List<DownloadTask>) {
        scope.launch {
            enqueueTasksInternal(tasks, persistToDb = true)
        }
    }

    private suspend fun enqueueTasksInternal(tasks: List<DownloadTask>, persistToDb: Boolean) {
        if (networkMonitorProvider.get().isOfflineModeActive.value) {
            Timber.w("[Download] Offline mode is active, skipping enqueueTasksInternal")
            return
        }
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return

        val newTasks = mutableListOf<DownloadTask>()

        enqueueMutex.withLock {
            val currentStates = _downloadStates.value
            val existingDownloaded = localMediaDao.getDownloadedTrackIds(serverId).toSet()

            for (task in tasks) {
                val trackId = task.track.id

                // Add scope reference
                localMediaDao.upsertScope(
                    DownloadScopeEntity(
                        scopeId = task.scopeId,
                        scopeType = task.scopeType,
                        trackId = trackId,
                        serverId = serverId
                    )
                )

                // Skip if already downloaded and valid
                if (existingDownloaded.contains(trackId)) {
                    val existingMedia = localMediaDao.getLocalMedia(trackId, serverId)
                    if (existingMedia != null && File(musicRootDir, existingMedia.relativePath).exists()) {
                        continue
                    }
                }

                val currentStatus = currentStates[trackId]
                if (currentStatus is DownloadStatus.Downloading || currentStatus is DownloadStatus.Queued) {
                    continue
                }

                if (persistToDb) {
                    val (qualityStr, bitRate, fmt) = when (val q = task.quality) {
                        is DownloadQuality.Original -> Triple("ORIGINAL", null, null)
                        is DownloadQuality.Transcoded -> Triple("TRANSCODED", q.bitrateKbps, q.format)
                    }
                    localMediaDao.upsertQueueItem(
                        DownloadQueueEntity(
                            trackId = trackId,
                            serverId = serverId,
                            scopeId = task.scopeId,
                            scopeType = task.scopeType,
                            quality = qualityStr,
                            bitRate = bitRate,
                            format = fmt,
                            status = "QUEUED",
                            queuedAt = task.queuedAt
                        )
                    )
                }

                _downloadStates.update { it + (trackId to DownloadStatus.Queued) }
                _activeQueue.update { q -> q + task }
                newTasks.add(task)
            }

            if (newTasks.isNotEmpty()) {
                batchTotal.addAndGet(newTasks.size)
                ensureNotificationLoop()
            }
        }

        for (task in newTasks) {
            startDownloadJob(task)
        }
    }

    private fun ensureNotificationLoop() {
        synchronized(this) {
            if (notificationLoopJob?.isActive == true) return
            notificationLoopJob = scope.launch {
                var wasActive = false
                while (currentCoroutineContext().isActive) {
                    delay(1000L) // Rate-limit safe: 1 update per second max
                    val queue = _activeQueue.value
                    val states = _downloadStates.value
                    val activeTasks = queue.filter { states[it.track.id] is DownloadStatus.Downloading }

                    if (queue.isNotEmpty() || activeTasks.isNotEmpty()) {
                        wasActive = true
                        val total = batchTotal.get().coerceAtLeast(queue.size)
                        val completed = batchCompleted.get()
                        val activeTitles = activeTasks.map {
                            if (it.track.artist.isNotBlank()) "${it.track.title} • ${it.track.artist}" else it.track.title
                        }
                        val avgProgress = if (activeTasks.isNotEmpty()) {
                            activeTasks.map { task ->
                                (states[task.track.id] as? DownloadStatus.Downloading)?.progress ?: 0f
                            }.average().toFloat()
                        } else 0f

                        notificationManager.showAggregateProgress(
                            completedCount = completed,
                            totalCount = total,
                            activeTitles = activeTitles,
                            progress = avgProgress
                        )
                    } else if (wasActive) {
                        wasActive = false
                        val completed = batchCompleted.getAndSet(0)
                        val failed = batchFailed.getAndSet(0)
                        batchTotal.set(0)
                        notificationManager.showSummaryCompleted(completed, failed)
                        break
                    } else {
                        break
                    }
                }
            }
        }
    }

    private fun updateDownloadStatus(trackId: String, status: DownloadStatus) {
        _downloadStates.update { it + (trackId to status) }
        _activeQueue.update { queue ->
            queue.map { task ->
                if (task.track.id == trackId) task.copy(status = status) else task
            }
        }
    }

    private fun startDownloadJob(task: DownloadTask) {
        val trackId = task.track.id
        val job = scope.launch {
            downloadSemaphore.withPermit {
                executeDownload(task)
            }
        }
        downloadJobs[trackId] = job
    }

    private suspend fun executeDownload(task: DownloadTask) {
        val track = task.track
        val trackId = track.id
        val taskServerId = task.serverId
        val currentServerId = subsonicRepository.currentServerId

        if (taskServerId != currentServerId || currentServerId.isBlank() || networkMonitorProvider.get().isOfflineModeActive.value) {
            updateDownloadStatus(trackId, DownloadStatus.Idle)
            _activeQueue.update { q -> q.filterNot { it.track.id == trackId } }
            downloadJobs.remove(trackId)
            return
        }

        if (appPreferences.wifiOnlyDownloads.value && !isConnectedToWifi()) {
            val error = "Wi-Fi required for downloads"
            Timber.d("[Download] Skipping track $trackId download: Wi-Fi only downloads enabled and device not on Wi-Fi")
            updateDownloadStatus(trackId, DownloadStatus.Failed(error))
            _activeQueue.update { q -> q.filterNot { it.track.id == trackId } }
            localMediaDao.updateQueueStatus(trackId, taskServerId, "FAILED", error)
            batchFailed.incrementAndGet()
            downloadJobs.remove(trackId)
            return
        }

        val streamUrl = resolveDownloadUrl(task)
        if (streamUrl == null) {
            val error = "Unable to generate download URL"
            updateDownloadStatus(trackId, DownloadStatus.Failed(error))
            _activeQueue.update { q -> q.filterNot { it.track.id == trackId } }
            localMediaDao.updateQueueStatus(trackId, taskServerId, "FAILED", error)
            batchFailed.incrementAndGet()
            downloadJobs.remove(trackId)
            return
        }

        val request = Request.Builder().url(streamUrl).get().build()
        val call = downloadClient.newCall(request)

        val completionHandle = currentCoroutineContext().job.invokeOnCompletion {
            call.cancel()
        }

        var pathInfo: DownloadPathInfo? = null

        try {
            updateDownloadStatus(trackId, DownloadStatus.Downloading(0f, 0L, null))
            localMediaDao.updateQueueStatus(trackId, taskServerId, "DOWNLOADING")

            val (fileSizeBytes, resolvedPathInfo) = downloadStreamAndSave(call, task)
            pathInfo = resolvedPathInfo

            finalizeDownload(task, resolvedPathInfo, fileSizeBytes)
            batchCompleted.incrementAndGet()
        } catch (e: Exception) {
            pathInfo?.tempFile?.let { if (it.exists()) it.delete() }

            if (e is CancellationException) {
                updateDownloadStatus(trackId, DownloadStatus.Idle)
                throw e
            }

            val errorMsg = e.localizedMessage ?: "Download failed"
            Timber.w("[Download] Track $trackId download failed: $errorMsg")
            updateDownloadStatus(trackId, DownloadStatus.Failed(errorMsg))
            localMediaDao.updateQueueStatus(trackId, taskServerId, "FAILED", errorMsg)
            batchFailed.incrementAndGet()
        } finally {
            completionHandle.dispose()
            withContext(NonCancellable) {
                downloadJobs.remove(trackId)
                _activeQueue.update { q -> q.filterNot { it.track.id == trackId } }
                refreshStorageStats()
            }
        }
    }

    private fun resolveDownloadUrl(task: DownloadTask): String? {
        val maxBitRate = when (val q = task.quality) {
            is DownloadQuality.Original -> null
            is DownloadQuality.Transcoded -> q.bitrateKbps
        }
        val reqFormat = when (val q = task.quality) {
            is DownloadQuality.Original -> null
            is DownloadQuality.Transcoded -> q.format
        }
        return subsonicRepository.getStreamUrl(task.track.id, maxBitRate, reqFormat)
    }

    private fun preparePathInfo(task: DownloadTask, ext: String): DownloadPathInfo {
        val artist = task.track.artist.trim().ifBlank { "Unknown Artist" }
        val title = task.track.title.trim().ifBlank { task.track.id }
        val rawBaseName = "$artist - $title"
        var baseName = sanitizeFilename(rawBaseName)
        if (baseName.isBlank()) {
            baseName = sanitizeFilename(task.track.id)
        }

        val targetDir = musicRootDir
        val safeTrackId = sanitize(task.track.id)
        val tempFile = File(targetDir, "${safeTrackId}.part")
        val targetFile = File(targetDir, "$baseName.$ext")
        val relativePath = "$baseName.$ext"

        if (!targetFile.canonicalPath.startsWith(musicRootDir.canonicalPath)) {
            throw SecurityException("Invalid target file path")
        }

        return DownloadPathInfo(
            safeServerId = sanitize(task.serverId),
            albumDirName = "",
            safeTrackId = safeTrackId,
            ext = ext,
            targetDir = targetDir,
            tempFile = tempFile,
            targetFile = targetFile,
            relativePath = relativePath
        )
    }

    private fun downloadStreamAndSave(call: Call, task: DownloadTask): Pair<Long, DownloadPathInfo> {
        val trackId = task.track.id
        val reqFormat = when (val q = task.quality) {
            is DownloadQuality.Original -> null
            is DownloadQuality.Transcoded -> q.format
        }

        return call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed with HTTP ${response.code}")
            }

            // Reject non-audio payloads disguised as HTTP 200
            val contentType = response.header("Content-Type")?.lowercase() ?: ""
            if (contentType.startsWith("text/") ||
                contentType.contains("json") ||
                contentType.contains("xml") ||
                contentType.contains("html")
            ) {
                throw IllegalStateException("Server returned non-audio response ($contentType)")
            }

            val body = response.body
            val rawContentLength = body.contentLength()
            val contentLength: Long? = if (rawContentLength > 0L) rawContentLength else null

            // Storage space check (expected length + 50MB reserve)
            val requiredBytes = (contentLength ?: (10 * 1024 * 1024L)) + (50 * 1024 * 1024L)
            val availableBytes = try {
                StatFs(musicRootDir.absolutePath).availableBytes
            } catch (_: Throwable) {
                musicRootDir.freeSpace
            }
            if (availableBytes in 1..<requiredBytes) {
                throw IllegalStateException("Storage full: cannot download audio")
            }

            val ext = when {
                contentType.contains("flac") -> "flac"
                contentType.contains("mp4") || contentType.contains("m4a") -> "m4a"
                contentType.contains("ogg") || contentType.contains("opus") -> "opus"
                contentType.contains("wav") -> "wav"
                !reqFormat.isNullOrBlank() -> reqFormat
                !task.track.suffix.isNullOrBlank() -> task.track.suffix
                else -> "mp3"
            }

            val pathInfo = preparePathInfo(task, ext)

            body.byteStream().use { input ->
                FileOutputStream(pathInfo.tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastProgressUpdate = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 500L) {
                            val progress = if (contentLength != null && contentLength > 0L) {
                                totalRead.toFloat() / contentLength.toFloat()
                            } else {
                                0f
                            }
                            updateDownloadStatus(
                                trackId,
                                DownloadStatus.Downloading(progress, totalRead, contentLength)
                            )
                            lastProgressUpdate = now
                        }
                    }
                    output.flush()

                    if (contentLength != null && totalRead < contentLength) {
                        throw IllegalStateException("Download truncated ($totalRead of $contentLength bytes)")
                    }
                    if (totalRead < 1024L) {
                        throw IllegalStateException("Downloaded file is suspiciously small ($totalRead bytes)")
                    }

                    updateDownloadStatus(
                        trackId,
                        DownloadStatus.Downloading(1f, totalRead, contentLength)
                    )

                    Pair(totalRead, pathInfo)
                }
            }
        }
    }

    private suspend fun finalizeDownload(task: DownloadTask, pathInfo: DownloadPathInfo, fileSizeBytes: Long) {
        val track = task.track
        val trackId = track.id
        val taskServerId = task.serverId

        moveSafely(pathInfo.tempFile, pathInfo.targetFile)

        if (subsonicRepository.currentServerId == taskServerId) {
            downloadCoverArtIfMissing(taskServerId, track.coverArtId, pathInfo.targetFile)

            val (qualityStr, bitRate, fmt) = when (val q = task.quality) {
                is DownloadQuality.Original -> Triple("ORIGINAL", null, pathInfo.ext)
                is DownloadQuality.Transcoded -> Triple("TRANSCODED", q.bitrateKbps, q.format ?: pathInfo.ext)
            }

            localMediaDao.upsertLocalMedia(
                LocalMediaEntity(
                    trackId = trackId,
                    serverId = taskServerId,
                    relativePath = pathInfo.relativePath,
                    quality = qualityStr,
                    format = fmt,
                    bitRate = bitRate ?: track.bitRate,
                    fileSizeBytes = fileSizeBytes,
                    downloadedAt = System.currentTimeMillis()
                )
            )
            localMediaDao.removeFromQueue(trackId, taskServerId)
            updateDownloadStatus(trackId, DownloadStatus.Completed(pathInfo.relativePath))
        } else {
            pathInfo.targetFile.delete()
        }
    }

    private suspend fun downloadCoverArtIfMissing(serverId: String, coverArtId: String?, audioFile: File) {
        if (coverArtId.isNullOrBlank()) return
        val safeServerId = sanitize(serverId)
        val safeCoverId = sanitize(coverArtId)
        val coversDir = File(musicRootDir, "$safeServerId/covers").apply { if (!exists()) mkdirs() }
        val coverFile = File(coversDir, "$safeCoverId.jpg")
        if (coverFile.exists() && coverFile.length() > 0) return

        val coverUrl = subsonicRepository.getCoverArtUrl(coverArtId, size = 600)
        if (coverUrl != null) {
            try {
                val request = Request.Builder().url(coverUrl).get().build()
                val tempPart = File(coversDir, "$safeCoverId.part")
                downloadClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body
                        FileOutputStream(tempPart).use { out ->
                            body.byteStream().copyTo(out)
                            out.flush()
                        }
                        if (tempPart.length() > 0) {
                            moveSafely(tempPart, coverFile)
                            return
                        } else {
                            tempPart.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.d(e, "[Download] Failed to download cover art from network for $coverArtId")
            }
        }

        try {
            if (audioFile.exists() && audioFile.length() > 0) {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(audioFile.absolutePath)
                val pic = retriever.embeddedPicture
                retriever.release()
                if (pic != null && pic.isNotEmpty()) {
                    coverFile.writeBytes(pic)
                    Timber.d("[Download] Extracted embedded cover art for $coverArtId (${pic.size} bytes)")
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "[Download] Failed to extract embedded cover art for $coverArtId")
        }
    }

    companion object {
        fun getLocalCoverArtFile(context: Context, coverArtId: String?): File? {
            if (coverArtId.isNullOrBlank()) return null
            val safeCoverId = coverArtId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)
            val rootDir = context.getExternalFilesDir("music") ?: File(context.filesDir, "music")
            val serverFolders = rootDir.listFiles() ?: return null
            for (serverDir in serverFolders) {
                if (!serverDir.isDirectory) continue
                val coverFile = File(serverDir, "covers/$safeCoverId.jpg")
                if (coverFile.exists() && coverFile.length() > 0) {
                    return coverFile
                }
            }
            return null
        }
    }

    fun cancelTrack(trackId: String) {
        val serverId = subsonicRepository.currentServerId
        downloadJobs[trackId]?.cancel()
        downloadJobs.remove(trackId)
        _downloadStates.update { it + (trackId to DownloadStatus.Idle) }
        _activeQueue.update { q -> q.filterNot { it.track.id == trackId } }
        scope.launch {
            if (serverId.isNotBlank()) {
                localMediaDao.removeFromQueue(trackId, serverId)
            }
        }
    }

    fun cancelAll() {
        val serverId = subsonicRepository.currentServerId
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        _downloadStates.value = emptyMap()
        _activeQueue.value = emptyList()
        notificationLoopJob?.cancel()
        batchTotal.set(0)
        batchCompleted.set(0)
        batchFailed.set(0)
        notificationManager.hideAll()
        scope.launch {
            if (serverId.isNotBlank()) {
                localMediaDao.clearQueueForServer(serverId)
            }
        }
    }

    suspend fun deleteDownloadedTrack(trackId: String, scopeId: String? = null) = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext

        cancelTrack(trackId)

        if (scopeId != null) {
            localMediaDao.deleteScope(trackId, scopeId, serverId)
        } else {
            localMediaDao.deleteScopesForTrack(trackId, serverId)
        }

        val remainingScopes = localMediaDao.countScopesForTrack(trackId, serverId)
        if (remainingScopes == 0) {
            val record = localMediaDao.getLocalMedia(trackId, serverId)
            if (record != null) {
                val file = File(musicRootDir, record.relativePath)
                if (file.exists()) file.delete()
            }
            localMediaDao.deleteLocalMedia(trackId, serverId)
        }

        updateDownloadStatus(trackId, DownloadStatus.Idle)
        refreshStorageStats()
    }

    suspend fun deleteDownloadedScope(scopeId: String) = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext

        val scopes = localMediaDao.getScopesForScopeId(scopeId, serverId)
        localMediaDao.deleteScopesForScopeId(scopeId, serverId)

        val albumTrackIds = trackDao.getTracksForAlbum(scopeId, serverId).map { it.id }
        val playlistTrackIds = playlistDao.getTracksForPlaylist(scopeId, serverId).map { it.id }
        val scopeTrackIds = scopes.map { it.trackId }
        val allTrackIds = (scopeTrackIds + albumTrackIds + playlistTrackIds).distinct()

        for (trackId in allTrackIds) {
            cancelTrack(trackId)
            val count = localMediaDao.countScopesForTrack(trackId, serverId)
            if (count == 0) {
                val record = localMediaDao.getLocalMedia(trackId, serverId)
                if (record != null) {
                    val file = File(musicRootDir, record.relativePath)
                    if (file.exists()) file.delete()
                }
                localMediaDao.deleteLocalMedia(trackId, serverId)
                _downloadStates.update { it + (trackId to DownloadStatus.Idle) }
            }
        }

        refreshStorageStats()
    }

    suspend fun clearAllSessionDownloads() = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        cancelAll()
        if (serverId.isNotBlank()) {
            val mediaList = localMediaDao.getLocalMediaForServer(serverId)
            for (item in mediaList) {
                val file = File(musicRootDir, item.relativePath)
                if (file.exists()) file.delete()
            }
            val serverFolder = File(musicRootDir, sanitize(serverId))
            if (serverFolder.exists()) {
                serverFolder.deleteRecursively()
            }
            localMediaDao.clearServerLocalMedia(serverId)
            localMediaDao.clearScopesForServer(serverId)
            localMediaDao.clearQueueForServer(serverId)
        }
        refreshStorageStats()
    }

    suspend fun refreshStorageStats() = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        val mediaList = if (serverId.isNotBlank()) {
            localMediaDao.getLocalMediaForServer(serverId)
        } else {
            emptyList()
        }

        val totalBytes = mediaList.sumOf { it.fileSizeBytes }
        val count = mediaList.size

        val availableBytes = try {
            StatFs(musicRootDir.absolutePath).availableBytes
        } catch (_: Throwable) {
            musicRootDir.freeSpace
        }
        val totalDiskBytes = try {
            StatFs(musicRootDir.absolutePath).totalBytes
        } catch (_: Throwable) {
            musicRootDir.totalSpace
        }

        _storageStats.value = StorageStats(
            downloadedAudioBytes = totalBytes,
            downloadedTrackCount = count,
            availableDiskSpaceBytes = availableBytes,
            totalDiskSpaceBytes = totalDiskBytes
        )
    }

    private fun moveSafely(src: File, dest: File) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                java.nio.file.Files.move(
                    src.toPath(),
                    dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
                return
            } catch (_: Exception) {}
        }
        if (dest.exists()) dest.delete()
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
    }
}
