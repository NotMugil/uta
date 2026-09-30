package com.notmugil.uta.player

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PersistedQueueEntry(
    val entryId: String,
    val trackId: String
)

@Serializable
data class PersistedPlaybackState(
    val serverId: String,
    val entries: List<PersistedQueueEntry>,
    val currentIndex: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val isShuffleEnabled: Boolean,
    val savedAt: Long = System.currentTimeMillis()
)

@Singleton
class PlaybackQueueStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "playback_queue_state"
        private const val KEY_STATE_PREFIX = "queue_state_"
    }

    fun saveQueue(
        serverId: String,
        entries: List<PersistedQueueEntry>,
        currentIndex: Int,
        positionMs: Long,
        repeatMode: Int,
        isShuffleEnabled: Boolean
    ) {
        if (serverId.isBlank()) return
        try {
            val state = PersistedPlaybackState(
                serverId = serverId,
                entries = entries,
                currentIndex = currentIndex.coerceAtLeast(0),
                positionMs = positionMs.coerceAtLeast(0L),
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled
            )
            val serialized = json.encodeToString(state)
            prefs.edit().putString(KEY_STATE_PREFIX + serverId, serialized).apply()
        } catch (e: Exception) {
            Timber.e(e, "[PlaybackStore] Failed to save queue for $serverId")
        }
    }

    fun savePosition(serverId: String, positionMs: Long, currentIndex: Int) {
        if (serverId.isBlank()) return
        val current = loadQueue(serverId) ?: return
        saveQueue(
            serverId = serverId,
            entries = current.entries,
            currentIndex = currentIndex,
            positionMs = positionMs,
            repeatMode = current.repeatMode,
            isShuffleEnabled = current.isShuffleEnabled
        )
    }

    fun loadQueue(serverId: String): PersistedPlaybackState? {
        if (serverId.isBlank()) return null
        val serialized = prefs.getString(KEY_STATE_PREFIX + serverId, null) ?: return null
        return try {
            json.decodeFromString<PersistedPlaybackState>(serialized)
        } catch (e: Exception) {
            Timber.e(e, "[PlaybackStore] Failed to decode persisted queue for $serverId")
            null
        }
    }

    fun clearQueue(serverId: String?) {
        if (serverId.isNullOrBlank()) {
            prefs.edit().clear().apply()
        } else {
            prefs.edit().remove(KEY_STATE_PREFIX + serverId).apply()
        }
    }
}
