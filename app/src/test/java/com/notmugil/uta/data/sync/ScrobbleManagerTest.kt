package com.notmugil.uta.data.sync

import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.SyncActionDao
import com.notmugil.uta.data.db.SyncActionEntity
import com.notmugil.uta.data.preferences.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ScrobbleManagerTest {

    private val subsonicRepository = mockk<SubsonicRepository>(relaxed = true)
    private val syncActionDao = mockk<SyncActionDao>(relaxed = true)
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var scrobbleManager: ScrobbleManager

    @Before
    fun setUp() {
        every { subsonicRepository.currentServerId } returns "srv_test"
        every { appPreferences.isScrobblingEnabled } returns MutableStateFlow(true)
        val networkMonitor = mockk<com.notmugil.uta.data.NetworkMonitor>(relaxed = true)
        every { networkMonitor.isOfflineModeActive } returns MutableStateFlow(false)

        scrobbleManager = ScrobbleManager(
            subsonicRepository = subsonicRepository,
            syncActionDao = syncActionDao,
            json = json,
            appPreferences = appPreferences,
            networkMonitorProvider = { networkMonitor }
        )
    }

    @Test
    fun `submitScrobble does nothing when scrobbling is disabled`() = runTest {
        every { appPreferences.isScrobblingEnabled } returns MutableStateFlow(false)

        scrobbleManager.submitScrobble("track_1", 100000L)
        scrobbleManager.recordNowPlaying("track_1")

        coVerify(exactly = 0) { subsonicRepository.scrobble(any(), any(), any()) }
        coVerify(exactly = 0) { syncActionDao.upsertAction(any()) }
    }

    @Test
    fun `submitScrobble direct success calls subsonic scrobble with start time`() = runTest {
        coEvery { subsonicRepository.scrobble("track_1", submission = true, time = 100000L) } returns Unit

        scrobbleManager.submitScrobble("track_1", 100000L)

        coVerify(exactly = 1) {
            subsonicRepository.scrobble("track_1", submission = true, time = 100000L)
        }
        coVerify(exactly = 0) {
            syncActionDao.upsertAction(any())
        }
    }

    @Test
    fun `submitScrobble network failure writes to syncActionDao outbox`() = runTest {
        coEvery { subsonicRepository.scrobble("track_2", submission = true, time = 200000L) } throws RuntimeException("Network error")

        val actionSlot = slot<SyncActionEntity>()
        coEvery { syncActionDao.upsertAction(capture(actionSlot)) } returns 1L

        scrobbleManager.submitScrobble("track_2", 200000L)

        coVerify(exactly = 1) {
            syncActionDao.upsertAction(any())
        }
        val captured = actionSlot.captured
        assertEquals("srv_test", captured.serverId)
        assertEquals("SCROBBLE", captured.actionType)
        assertEquals("track_2", captured.targetId)
        assertNotNull(captured.payloadJson)
    }
}
