package com.notmugil.uta.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.notmugil.uta.data.NetworkMonitor
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.data.preferences.AppPreferences
import com.notmugil.uta.domain.model.TrackItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class QueuePrefetchManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subsonicRepository: SubsonicRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val appPreferences: AppPreferences,
    private val networkMonitorProvider: Provider<NetworkMonitor>
) {
    companion object {
        private const val TAG = "QueuePrefetchManager"
        private const val PREFETCH_DIR_NAME = "uta_queue_prefetch"
        private const val MAX_PREFETCH_CACHE_BYTES = 300L * 1024L * 1024L // 300 MB limit
        private const val MIN_VALID_AUDIO_BYTES = 32L * 1024L // 32 KB min to be considered playable
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Unhandled error in QueuePrefetchManager: ${throwable.message}")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pruneMutex = Mutex()
    private var prefetchJob: Job? = null

    init {
        scope.launch {
            try {
                networkMonitorProvider.get().isOfflineModeActive.collect { isOffline ->
                    if (isOffline) {
                        cancelAllPrefetches()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun getPrefetchDir(): File {
        val dir = File(context.cacheDir, PREFETCH_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getPrefetchedAudioUri(trackId: String): Uri? {
        val dir = getPrefetchDir()
        val formatPref = appPreferences.transcodingFormat.value
        val isWifi = isConnectedToWifi()
        val bitratePref = if (isWifi) appPreferences.wifiStreamingBitrate.value else appPreferences.cellularStreamingBitrate.value
        val params = StreamParamsResolver.resolve(bitratePref, formatPref)
        val formatKey = params.format ?: "raw"
        val bitrateKey = params.maxBitRate ?: 0
        val file = File(dir, "${trackId}_${formatKey}_${bitrateKey}.media")
        if (file.exists() && file.length() >= MIN_VALID_AUDIO_BYTES) {
            return Uri.fromFile(file)
        }
        return null
    }

    fun isSongPrefetched(trackId: String): Boolean = getPrefetchedAudioUri(trackId) != null

    private fun isConnectedToWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false

        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            return false
        }

        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun prefetchUpcomingTracks(queue: List<TrackItem>, currentIndex: Int) {
        val isOffline = try { networkMonitorProvider.get().isOfflineModeActive.value } catch (_: Exception) { false }
        if (isOffline) {
            cancelAllPrefetches()
            return
        }

        if (!appPreferences.prefetchUpcomingEnabled.value) {
            cancelAllPrefetches()
            return
        }

        if (appPreferences.prefetchOnlyOnWifi.value && !isConnectedToWifi()) {
            cancelAllPrefetches()
            return
        }

        val depth = appPreferences.prefetchTrackCount.value.coerceIn(1, 10)
        val upcomingTracks = queue.drop(currentIndex + 1).take(depth)
        val currentTrackId = queue.getOrNull(currentIndex)?.id
        val protectedTrackIds = (upcomingTracks.map { it.id } + listOfNotNull(currentTrackId)).toSet()

        activeJobs.keys.forEach { trackId ->
            if (trackId !in protectedTrackIds) {
                activeJobs.remove(trackId)?.cancel()
            }
        }

        prefetchJob?.cancel()
        if (upcomingTracks.isEmpty()) return

        prefetchJob = scope.launch {
            for (track in upcomingTracks) {
                if (!isActive) break

                if (offlineDownloadManager.getLocalUriForTrack(track.id) != null) {
                    continue
                }

                if (isSongPrefetched(track.id)) {
                    continue
                }

                val job = launch {
                    downloadTrackToPrefetch(track)
                }
                activeJobs[track.id] = job
                try {
                    job.join()
                } finally {
                    activeJobs.remove(track.id)
                }
            }

            pruneCacheIfNeeded(protectedTrackIds)
        }
    }

    private suspend fun downloadTrackToPrefetch(track: TrackItem) {
        val isOffline = try { networkMonitorProvider.get().isOfflineModeActive.value } catch (_: Exception) { false }
        if (isOffline) return

        val isWifi = isConnectedToWifi()
        val bitratePref = if (isWifi) appPreferences.wifiStreamingBitrate.value else appPreferences.cellularStreamingBitrate.value
        val formatPref = appPreferences.transcodingFormat.value
        val params = StreamParamsResolver.resolve(
            bitrateSetting = bitratePref,
            formatSetting = formatPref,
            sourceSuffix = track.suffix,
            sourceBitRate = track.bitRate
        )

        val streamUrl = subsonicRepository.getStreamUrl(
            id = track.id,
            maxBitRate = params.maxBitRate,
            format = params.format
        ) ?: return

        val formatKey = params.format ?: "raw"
        val bitrateKey = params.maxBitRate ?: 0
        val prefetchDir = getPrefetchDir()
        val destFile = File(prefetchDir, "${track.id}_${formatKey}_${bitrateKey}.media")
        val tempFile = File(prefetchDir, "${track.id}_${formatKey}_${bitrateKey}_${System.currentTimeMillis()}.tmp")

        var conn: HttpURLConnection? = null
        try {
            val url = URL(streamUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 20000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Uta/1.0")
            }
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                Timber.w("[$TAG] Prefetch HTTP error $responseCode for track ${track.id}")
                return
            }

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!scope.isActive) throw CancellationException("Prefetch cancelled")
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            if (tempFile.exists() && tempFile.length() >= MIN_VALID_AUDIO_BYTES) {
                if (destFile.exists()) destFile.delete()
                tempFile.renameTo(destFile)
                Timber.d("[$TAG] Successfully prefetched: ${track.title} (${destFile.length()} bytes)")
            } else {
                tempFile.delete()
            }
        } catch (_: CancellationException) {
            tempFile.delete()
        } catch (t: Throwable) {
            tempFile.delete()
            Timber.w("[$TAG] Failed prefetching ${track.title}: ${t.message}")
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }

    private suspend fun pruneCacheIfNeeded(protectedTrackIds: Set<String> = emptySet()) {
        pruneMutex.withLock {
            val dir = getPrefetchDir()
            val files = dir.listFiles { _, name -> name.endsWith(".media") } ?: return@withLock
            var totalBytes = files.sumOf { it.length() }

            if (totalBytes > MAX_PREFETCH_CACHE_BYTES || files.size > 25) {
                val sortedFiles = files.sortedBy { it.lastModified() }
                for (file in sortedFiles) {
                    val trackId = file.nameWithoutExtension.substringBefore('_')
                    if (trackId !in protectedTrackIds) {
                        val fileLength = file.length()
                        if (file.delete()) {
                            totalBytes -= fileLength
                            if (totalBytes <= MAX_PREFETCH_CACHE_BYTES * 0.75 && files.size <= 15) {
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    fun clearCache() {
        cancelAllPrefetches()
        scope.launch {
            pruneMutex.withLock {
                val dir = getPrefetchDir()
                dir.listFiles()?.forEach { file ->
                    try {
                        file.delete()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun cancelAllPrefetches() {
        prefetchJob?.cancel()
        prefetchJob = null
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
}
