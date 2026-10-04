package com.notmugil.uta.data.repository

import android.content.Context
import com.notmugil.uta.data.NetworkMonitor
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.preferences.AppPreferences
import com.notmugil.uta.data.preferences.LyricsProvider
import com.notmugil.uta.data.preferences.LyricsProviderConfig
import com.notmugil.uta.data.preferences.LyricsSourceMode
import com.notmugil.uta.data.SubsonicLyrics
import com.notmugil.uta.domain.model.TrackItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LyricsRepositoryTest {

    private val context: Context = mockk(relaxed = true)
    private val appPreferences: AppPreferences = mockk(relaxed = true)
    private val subsonicRepository: SubsonicRepository = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)

    private val lyricsSourceModeFlow = MutableStateFlow(LyricsSourceMode.BOTH)
    private val onlineLyricsProvidersFlow = MutableStateFlow(
        listOf(
            LyricsProviderConfig(LyricsProvider.LRCLIB, enabled = true),
            LyricsProviderConfig(LyricsProvider.PAXSENIX, enabled = true)
        )
    )
    private val isOfflineModeManualFlow = MutableStateFlow(false)
    private val isOnlineFlow = MutableStateFlow(true)
    private val isServerUnreachableFlow = MutableStateFlow(false)

    private lateinit var tempCacheDir: File
    private lateinit var repository: LyricsRepository

    private val sampleTrack = TrackItem(
        id = "track_123",
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        albumId = "album_123",
        durationSeconds = 200,
        coverArtId = "cover_123"
    )

    @Before
    fun setUp() {
        tempCacheDir = Files.createTempDirectory("uta_lyrics_test").toFile()
        every { context.cacheDir } returns tempCacheDir

        every { appPreferences.lyricsSourceMode } returns lyricsSourceModeFlow
        every { appPreferences.onlineLyricsProviders } returns onlineLyricsProvidersFlow
        every { appPreferences.isOfflineModeManual } returns isOfflineModeManualFlow
        every { networkMonitor.isOnline } returns isOnlineFlow
        every { networkMonitor.isServerUnreachable } returns isServerUnreachableFlow

        isOfflineModeManualFlow.value = false
        isOnlineFlow.value = true
        isServerUnreachableFlow.value = false
        lyricsSourceModeFlow.value = LyricsSourceMode.BOTH

        repository = LyricsRepository(
            context = context,
            appPreferences = appPreferences,
            subsonicRepository = subsonicRepository,
            networkMonitor = networkMonitor
        )
    }

    @Test
    fun `LyricsSourceMode fromString maps legacy ONLINE to BOTH`() {
        assertEquals(LyricsSourceMode.BOTH, LyricsSourceMode.fromString("ONLINE_ONLY"))
        assertEquals(LyricsSourceMode.BOTH, LyricsSourceMode.fromString("ONLINE"))
        assertEquals(LyricsSourceMode.BOTH, LyricsSourceMode.fromString("BOTH"))
        assertEquals(LyricsSourceMode.SERVER_ONLY, LyricsSourceMode.fromString("SERVER_ONLY"))
        assertEquals(LyricsSourceMode.DISABLED, LyricsSourceMode.fromString("DISABLED"))
    }

    @Test
    fun `getLyrics in offline mode uses server only and does not query online`() = runBlocking {
        isOfflineModeManualFlow.value = true
        coEvery { subsonicRepository.getStructuredLyrics(any()) } returns emptyList()
        coEvery { subsonicRepository.getLyrics("Test Artist", "Test Song") } returns SubsonicLyrics(
            artist = "Test Artist",
            title = "Test Song",
            value = "[00:10.00]Offline Server Lyric"
        )

        val result = repository.getLyrics(sampleTrack, provider = LyricsProvider.AUTO)

        assertNotNull(result)
        assertEquals("Server (LRC)", result.source)
        assertEquals(1, result.syncedLines.size)
        assertEquals("Offline Server Lyric", result.syncedLines.first().text)
    }

    @Test
    fun `checkAvailableProviders in offline mode returns only Subsonic provider`() = runBlocking {
        isOnlineFlow.value = false
        coEvery { subsonicRepository.getStructuredLyrics(any()) } returns emptyList()
        coEvery { subsonicRepository.getLyrics("Test Artist", "Test Song") } returns SubsonicLyrics(
            artist = "Test Artist",
            title = "Test Song",
            value = "[00:10.00]Offline Server Lyric"
        )

        val available = repository.checkAvailableProviders(sampleTrack)

        assertEquals(1, available.size)
        assertTrue(available.containsKey(LyricsProvider.SUBSONIC))
    }

    @Test
    fun `checkAvailableProviders when server unreachable does not include Subsonic`() = runBlocking {
        isServerUnreachableFlow.value = true
        isOnlineFlow.value = true
        isOfflineModeManualFlow.value = false

        val available = repository.checkAvailableProviders(sampleTrack)
        org.junit.Assert.assertFalse(available.containsKey(LyricsProvider.SUBSONIC))
    }

    @Test
    fun `getLyrics in SERVER_ONLY mode fetches only from Subsonic`() = runBlocking {
        lyricsSourceModeFlow.value = LyricsSourceMode.SERVER_ONLY
        isOnlineFlow.value = true
        isOfflineModeManualFlow.value = false

        coEvery { subsonicRepository.getStructuredLyrics(any()) } returns emptyList()
        coEvery { subsonicRepository.getLyrics("Test Artist", "Test Song") } returns SubsonicLyrics(
            artist = "Test Artist",
            title = "Test Song",
            value = "[00:05.00]Server Only Lyric"
        )

        val result = repository.getLyrics(sampleTrack, provider = LyricsProvider.AUTO)

        assertNotNull(result)
        assertEquals("Server (LRC)", result.source)
        assertEquals("Server Only Lyric", result.syncedLines.first().text)
    }

    @Test
    fun `checkAvailableProviders in SERVER_ONLY mode returns only Subsonic`() = runBlocking {
        lyricsSourceModeFlow.value = LyricsSourceMode.SERVER_ONLY
        isOnlineFlow.value = true
        isOfflineModeManualFlow.value = false

        coEvery { subsonicRepository.getStructuredLyrics(any()) } returns emptyList()
        coEvery { subsonicRepository.getLyrics("Test Artist", "Test Song") } returns SubsonicLyrics(
            artist = "Test Artist",
            title = "Test Song",
            value = "[00:05.00]Server Only Lyric"
        )

        val available = repository.checkAvailableProviders(sampleTrack)

        assertEquals(1, available.size)
        assertTrue(available.containsKey(LyricsProvider.SUBSONIC))
        org.junit.Assert.assertFalse(available.containsKey(LyricsProvider.AUTO))
        org.junit.Assert.assertFalse(available.containsKey(LyricsProvider.LRCLIB))
        org.junit.Assert.assertFalse(available.containsKey(LyricsProvider.PAXSENIX))
    }
}
