package com.notmugil.uta.player

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlaybackQueueStoreTest {

    private val inMemoryPrefs = mutableMapOf<String, String>()
    private val json = Json { ignoreUnknownKeys = true }

    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var store: PlaybackQueueStore

    @Before
    fun setUp() {
        inMemoryPrefs.clear()

        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns editor

        every { sharedPrefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<String?>()
            inMemoryPrefs[key] ?: default
        }

        every { editor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            inMemoryPrefs[key] = value
            editor
        }

        every { editor.remove(any()) } answers {
            val key = firstArg<String>()
            inMemoryPrefs.remove(key)
            editor
        }

        every { editor.clear() } answers {
            inMemoryPrefs.clear()
            editor
        }

        store = PlaybackQueueStore(context, json)
    }

    @Test
    fun `saveQueue and loadQueue persists and restores state accurately`() {
        val serverId = "srv_1"
        val entries = listOf(
            PersistedQueueEntry(entryId = "e1", trackId = "t1"),
            PersistedQueueEntry(entryId = "e2", trackId = "t2")
        )

        store.saveQueue(
            serverId = serverId,
            entries = entries,
            currentIndex = 1,
            positionMs = 45000L,
            repeatMode = 2,
            isShuffleEnabled = true
        )

        val restored = store.loadQueue(serverId)
        assertNotNull(restored)
        assertEquals(serverId, restored?.serverId)
        assertEquals(2, restored?.entries?.size)
        assertEquals("e1", restored?.entries?.get(0)?.entryId)
        assertEquals("t1", restored?.entries?.get(0)?.trackId)
        assertEquals("e2", restored?.entries?.get(1)?.entryId)
        assertEquals("t2", restored?.entries?.get(1)?.trackId)
        assertEquals(1, restored?.currentIndex)
        assertEquals(45000L, restored?.positionMs)
        assertEquals(2, restored?.repeatMode)
        assertEquals(true, restored?.isShuffleEnabled)
    }

    @Test
    fun `clearQueue removes saved state for specific server`() {
        val serverId = "srv_2"
        val entries = listOf(PersistedQueueEntry(entryId = "e1", trackId = "t1"))

        store.saveQueue(serverId, entries, 0, 1000L, 0, false)
        assertNotNull(store.loadQueue(serverId))

        store.clearQueue(serverId)
        assertNull(store.loadQueue(serverId))
    }
}
