package com.notmugil.uta.data.download

import android.content.Context
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.DownloadScopeEntity
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.db.PlaylistDao
import com.notmugil.uta.data.db.TrackDao
import com.notmugil.uta.domain.model.TrackItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflineDownloadManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val subsonicRepository: SubsonicRepository = mockk(relaxed = true)
    private val localMediaDao: LocalMediaDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val notificationManager: DownloadNotificationManager = mockk(relaxed = true)
    private val networkMonitor: com.notmugil.uta.data.NetworkMonitor = mockk(relaxed = true)
    private val isOfflineFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val appPreferences: com.notmugil.uta.data.preferences.AppPreferences = mockk(relaxed = true)
    private val qualityFlow = kotlinx.coroutines.flow.MutableStateFlow(com.notmugil.uta.data.preferences.DownloadQualityPreference.ORIGINAL)
    private val formatFlow = kotlinx.coroutines.flow.MutableStateFlow(com.notmugil.uta.data.preferences.DownloadFormatPreference.ORIGINAL)
    private val wifiOnlyFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    private lateinit var musicDir: File

    @Before
    fun setUp() {
        isOfflineFlow.value = false
        qualityFlow.value = com.notmugil.uta.data.preferences.DownloadQualityPreference.ORIGINAL
        formatFlow.value = com.notmugil.uta.data.preferences.DownloadFormatPreference.ORIGINAL
        wifiOnlyFlow.value = false

        musicDir = tempFolder.newFolder("music")
        every { context.getExternalFilesDir("music") } returns musicDir
        every { context.filesDir } returns tempFolder.root
        every { subsonicRepository.currentServerId } returns "server123"
        every { networkMonitor.isOfflineModeActive } returns isOfflineFlow
        every { appPreferences.downloadQuality } returns qualityFlow
        every { appPreferences.downloadFormat } returns formatFlow
        every { appPreferences.wifiOnlyDownloads } returns wifiOnlyFlow
    }

    @Test
    fun `fake server returning XML error body is rejected and fails download`() = runBlocking {
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "application/xml; charset=utf-8")
                .body(
                    """<subsonic-response status="failed" version="1.16.1">
                        <error code="70" message="The requested data was not found"/>
                    </subsonic-response>""".toResponseBody("application/xml".toMediaType())
                )
                .build()
        }

        val testClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        coEvery { subsonicRepository.getStreamUrl(any(), any(), any()) } returns "http://example.com/rest/stream.view?id=track1"
        coEvery { localMediaDao.getDownloadedTrackIds("server123") } returns emptyList()
        coEvery { localMediaDao.getDownloadedTrackIdsFlow("server123") } returns flowOf(emptyList())

        val manager = OfflineDownloadManager(
            context = context,
            subsonicRepository = subsonicRepository,
            localMediaDao = localMediaDao,
            trackDao = trackDao,
            playlistDao = playlistDao,
            okHttpClient = testClient,
            notificationManager = notificationManager,
            networkMonitorProvider = { networkMonitor },
            appPreferences = appPreferences
        )

        val track = TrackItem(
            id = "track1",
            title = "Test Song",
            artist = "Test Artist",
            albumId = "album1"
        )

        manager.enqueueTrack(track, scopeId = "album1")

        // Await the failure status
        withTimeout(3000) {
            manager.downloadStates.filter { it["track1"] is DownloadStatus.Failed }.first()
        }

        // LocalMediaEntity should NEVER be saved for XML error body
        coVerify(exactly = 0) { localMediaDao.upsertLocalMedia(any()) }

        val status = manager.downloadStates.value["track1"]
        assertTrue(status is DownloadStatus.Failed)
    }

    @Test
    fun `successful audio download creates atomic file and saves local media record`() = runBlocking {
        // Create 2KB fake audio bytes
        val fakeAudioBytes = ByteArray(2048) { it.toByte() }

        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "audio/mpeg")
                .body(fakeAudioBytes.toResponseBody("audio/mpeg".toMediaType()))
                .build()
        }

        val testClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        coEvery { subsonicRepository.getStreamUrl(any(), any(), any()) } returns "http://example.com/rest/stream.view?id=track2"
        coEvery { localMediaDao.getDownloadedTrackIds("server123") } returns emptyList()
        coEvery { localMediaDao.getDownloadedTrackIdsFlow("server123") } returns flowOf(emptyList())

        val manager = OfflineDownloadManager(
            context = context,
            subsonicRepository = subsonicRepository,
            localMediaDao = localMediaDao,
            trackDao = trackDao,
            playlistDao = playlistDao,
            okHttpClient = testClient,
            notificationManager = notificationManager,
            networkMonitorProvider = { networkMonitor },
            appPreferences = appPreferences
        )

        val track = TrackItem(
            id = "track2",
            title = "Valid Audio",
            artist = "Artist",
            albumId = "album2"
        )

        manager.enqueueTrack(track, scopeId = "album2")

        withTimeout(5000) {
            manager.downloadStates.filter { it["track2"] is DownloadStatus.Completed }.first()
        }

        coVerify(atLeast = 1) {
            localMediaDao.upsertLocalMedia(
                match { it.trackId == "track2" && it.serverId == "server123" }
            )
        }

        val status = manager.downloadStates.value["track2"]
        assertTrue(status is DownloadStatus.Completed)
    }

    @Test
    fun `deleting one scope preserves downloaded file when another scope holds it`() = runBlocking {
        val serverId = "server123"
        val trackId = "trackShared"
        val playlistScopeId = "playlist1"

        coEvery { localMediaDao.getScopesForScopeId(playlistScopeId, serverId) } returns listOf(
            DownloadScopeEntity(scopeId = playlistScopeId, scopeType = "PLAYLIST", trackId = trackId, serverId = serverId)
        )
        // Another scope (album1) still references the track
        coEvery { localMediaDao.countScopesForTrack(trackId, serverId) } returns 1

        val manager = OfflineDownloadManager(
            context = context,
            subsonicRepository = subsonicRepository,
            localMediaDao = localMediaDao,
            trackDao = trackDao,
            playlistDao = playlistDao,
            okHttpClient = OkHttpClient(),
            notificationManager = notificationManager,
            networkMonitorProvider = { networkMonitor },
            appPreferences = appPreferences
        )

        manager.deleteDownloadedScope(playlistScopeId)

        // Scopes for playlist should be deleted
        coVerify(exactly = 1) { localMediaDao.deleteScopesForScopeId(playlistScopeId, serverId) }
        // BUT local media record should NOT be deleted because album1 still references it!
        coVerify(exactly = 0) { localMediaDao.deleteLocalMedia(trackId, serverId) }
    }

    @Test
    fun `enqueueTrack does nothing when offline mode is active`() = runBlocking {
        isOfflineFlow.value = true

        val manager = OfflineDownloadManager(
            context = context,
            subsonicRepository = subsonicRepository,
            localMediaDao = localMediaDao,
            trackDao = trackDao,
            playlistDao = playlistDao,
            okHttpClient = OkHttpClient(),
            notificationManager = notificationManager,
            networkMonitorProvider = { networkMonitor },
            appPreferences = appPreferences
        )

        val track = TrackItem(
            id = "trackOffline",
            title = "Offline Song",
            artist = "Artist",
            albumId = "albumOffline"
        )

        manager.enqueueTrack(track, scopeId = "albumOffline")

        // No tasks should be queued
        org.junit.Assert.assertEquals(emptyList<DownloadTask>(), manager.activeQueue.value)
        org.junit.Assert.assertEquals(emptyMap<String, DownloadStatus>(), manager.downloadStates.value)
    }

    @Test
    fun `enqueueTrack respects appPreferences for download quality and format`() = runBlocking {
        qualityFlow.value = com.notmugil.uta.data.preferences.DownloadQualityPreference.BITRATE_256
        formatFlow.value = com.notmugil.uta.data.preferences.DownloadFormatPreference.OPUS

        coEvery { subsonicRepository.getStreamUrl("trackCustom", 256, "opus") } returns "http://example.com/stream?id=trackCustom"
        coEvery { localMediaDao.getDownloadedTrackIds("server123") } returns emptyList()
        coEvery { localMediaDao.getDownloadedTrackIdsFlow("server123") } returns flowOf(emptyList())

        val manager = OfflineDownloadManager(
            context = context,
            subsonicRepository = subsonicRepository,
            localMediaDao = localMediaDao,
            trackDao = trackDao,
            playlistDao = playlistDao,
            okHttpClient = OkHttpClient(),
            notificationManager = notificationManager,
            networkMonitorProvider = { networkMonitor },
            appPreferences = appPreferences
        )

        val track = TrackItem(
            id = "trackCustom",
            title = "Custom Song",
            artist = "Artist",
            albumId = "albumCustom"
        )

        manager.enqueueTrack(track, scopeId = "albumCustom")

        withTimeout(3000) {
            manager.downloadStates.filter { it.containsKey("trackCustom") }.first()
        }

        coVerify(timeout = 3000) {
            subsonicRepository.getStreamUrl("trackCustom", 256, "opus")
        }
    }

    @Test
    fun `audio download stores file directly in music root directory with artist and title filename`() = runBlocking {
        val fakeAudioBytes = ByteArray(2048) { it.toByte() }

        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "audio/flac")
                .body(fakeAudioBytes.toResponseBody("audio/flac".toMediaType()))
                .build()
        }

        val testClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        coEvery { subsonicRepository.getStreamUrl(any(), any(), any()) } returns "http://example.com/stream?id=track123"
        coEvery { localMediaDao.getDownloadedTrackIds("server123") } returns emptyList()
        coEvery { localMediaDao.getDownloadedTrackIdsFlow("server123") } returns flowOf(emptyList())

        val manager = OfflineDownloadManager(
            context = context,
            subsonicRepository = subsonicRepository,
            localMediaDao = localMediaDao,
            trackDao = trackDao,
            playlistDao = playlistDao,
            okHttpClient = testClient,
            notificationManager = notificationManager,
            networkMonitorProvider = { networkMonitor },
            appPreferences = appPreferences
        )

        val track = TrackItem(
            id = "track123",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            albumId = "albumQueen"
        )

        manager.enqueueTrack(track, scopeId = "albumQueen")

        withTimeout(5000) {
            manager.downloadStates.filter { it["track123"] is DownloadStatus.Completed }.first()
        }

        coVerify {
            localMediaDao.upsertLocalMedia(
                match { it.trackId == "track123" && it.relativePath == "Queen - Bohemian Rhapsody.flac" }
            )
        }

        val expectedFile = File(musicDir, "Queen - Bohemian Rhapsody.flac")
        assertTrue(expectedFile.exists())
    }
}
