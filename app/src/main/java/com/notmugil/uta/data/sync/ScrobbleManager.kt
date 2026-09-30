package com.notmugil.uta.data.sync

import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.SyncActionDao
import com.notmugil.uta.data.db.SyncActionEntity
import com.notmugil.uta.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Serializable
data class ScrobblePayload(
    val startTimeMs: Long
)

@Singleton
class ScrobbleManager @Inject constructor(
    private val subsonicRepository: SubsonicRepository,
    private val syncActionDao: SyncActionDao,
    private val json: Json,
    private val appPreferences: AppPreferences,
    private val networkMonitorProvider: Provider<com.notmugil.uta.data.NetworkMonitor>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()

    private val isOffline: Boolean
        get() = try {
            networkMonitorProvider.get().isOfflineModeActive.value
        } catch (_: Exception) {
            false
        }

    companion object {
        const val ACTION_SCROBBLE = "SCROBBLE"
        const val MAX_RETRIES = 5
        const val MAX_AGE_MS = 14 * 24 * 60 * 60 * 1000L // 14 days
    }

    suspend fun recordNowPlaying(trackId: String) {
        if (!appPreferences.isScrobblingEnabled.value) return
        if (isOffline) return
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return
        try {
            subsonicRepository.scrobble(trackId, submission = false)
        } catch (e: Exception) {
            Timber.d(e, "[Scrobble] Now playing call failed for $trackId")
        }
    }

    suspend fun submitScrobble(trackId: String, startTimeMs: Long) {
        if (!appPreferences.isScrobblingEnabled.value) return
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return

        if (isOffline) {
            Timber.d("[Scrobble] Offline mode active: saving scrobble for $trackId (start=$startTimeMs) to persistent outbox")
            val payload = json.encodeToString(ScrobblePayload.serializer(), ScrobblePayload(startTimeMs))
            syncActionDao.upsertAction(
                SyncActionEntity(
                    serverId = serverId,
                    actionType = ACTION_SCROBBLE,
                    targetId = trackId,
                    payloadJson = payload,
                    createdAt = System.currentTimeMillis()
                )
            )
            return
        }

        try {
            subsonicRepository.scrobble(trackId, submission = true, time = startTimeMs)
            Timber.d("[Scrobble] Successfully submitted scrobble for $trackId (start=$startTimeMs)")
        } catch (e: Exception) {
            Timber.w(e, "[Scrobble] Direct scrobble failed for $trackId, saving to persistent outbox")
            val payload = json.encodeToString(ScrobblePayload.serializer(), ScrobblePayload(startTimeMs))
            syncActionDao.upsertAction(
                SyncActionEntity(
                    serverId = serverId,
                    actionType = ACTION_SCROBBLE,
                    targetId = trackId,
                    payloadJson = payload,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun flushOutbox() {
        if (isOffline) {
            Timber.d("[Scrobble] Skipping flushOutbox: offline mode is active")
            return
        }
        scope.launch {
            flushMutex.withLock {
                if (isOffline) return@launch
                val serverId = subsonicRepository.currentServerId
                if (serverId.isBlank()) return@launch

                val pending = syncActionDao.getPendingActions(serverId)
                    .filter { it.actionType == ACTION_SCROBBLE }

                val now = System.currentTimeMillis()

                for (action in pending) {
                    // Drop stale entries older than 14 days or exceeded retry count
                    if (now - action.createdAt > MAX_AGE_MS || action.retryCount >= MAX_RETRIES) {
                        Timber.w("[Scrobble] Dropping stale/exhausted scrobble outbox entry: ${action.id}")
                        syncActionDao.deleteAction(action.id)
                        continue
                    }

                    val payload = try {
                        action.payloadJson?.let { json.decodeFromString(ScrobblePayload.serializer(), it) }
                    } catch (_: Exception) {
                        null
                    }
                    val startTime = payload?.startTimeMs ?: action.createdAt

                    try {
                        subsonicRepository.scrobble(action.targetId, submission = true, time = startTime)
                        syncActionDao.deleteAction(action.id)
                        Timber.d("[Scrobble] Flushed outbox scrobble ${action.targetId}")
                    } catch (e: Exception) {
                        Timber.w(e, "[Scrobble] Outbox flush retry failed for ${action.targetId}")
                        syncActionDao.upsertAction(
                            action.copy(
                                retryCount = action.retryCount + 1,
                                lastError = e.message
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun clearOutbox(serverId: String) {
        val pending = syncActionDao.getPendingActions(serverId)
            .filter { it.actionType == ACTION_SCROBBLE }
            .map { it.id }
        if (pending.isNotEmpty()) {
            syncActionDao.deleteActions(pending)
        }
    }
}
