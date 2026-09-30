package com.notmugil.uta.data.sync

import dev.zt64.subsonic.api.model.Playlist
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.AlbumDao
import com.notmugil.uta.data.db.ArtistDao
import com.notmugil.uta.data.db.PlaylistDao
import com.notmugil.uta.data.db.PlaylistEntity
import com.notmugil.uta.data.db.PlaylistTrackCrossRef
import com.notmugil.uta.data.db.SyncActionDao
import com.notmugil.uta.data.db.SyncActionEntity
import com.notmugil.uta.data.db.TrackDao
import com.notmugil.uta.data.db.TrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MutationManagerTest {

    private val subsonicRepository = mockk<SubsonicRepository>(relaxed = true)
    private val trackDao = mockk<TrackDao>(relaxed = true)
    private val albumDao = mockk<AlbumDao>(relaxed = true)
    private val artistDao = mockk<ArtistDao>(relaxed = true)
    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val syncActionDao = mockk<SyncActionDao>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var mutationManager: MutationManager

    @Before
    fun setUp() {
        every { subsonicRepository.currentServerId } returns "srv_test"
        val networkMonitor = mockk<com.notmugil.uta.data.NetworkMonitor>(relaxed = true)
        every { networkMonitor.isOfflineModeActive } returns MutableStateFlow(false)

        mutationManager = MutationManager(
            subsonicRepository = subsonicRepository,
            trackDao = trackDao,
            albumDao = albumDao,
            artistDao = artistDao,
            playlistDao = playlistDao,
            syncActionDao = syncActionDao,
            networkMonitorProvider = { networkMonitor },
            json = json
        )
    }

    @Test
    fun `setTrackStarred true updates Room optimistically and calls starTrack`() = runTest {
        coEvery { subsonicRepository.starTrack("trk_1") } returns Unit

        mutationManager.setTrackStarred("trk_1", true)

        coVerify(exactly = 1) { trackDao.setTrackStarred("trk_1", "srv_test", any()) }
        coVerify(exactly = 1) { subsonicRepository.starTrack("trk_1") }
        coVerify(exactly = 0) { syncActionDao.upsertAction(any()) }
    }

    @Test
    fun `setTrackStarred false updates Room and calls unstarTrack`() = runTest {
        coEvery { subsonicRepository.unstarTrack("trk_1") } returns Unit

        mutationManager.setTrackStarred("trk_1", false)

        coVerify(exactly = 1) { trackDao.setTrackStarred("trk_1", "srv_test", null) }
        coVerify(exactly = 1) { subsonicRepository.unstarTrack("trk_1") }
    }

    @Test
    fun `setTrackStarred network failure enqueues to outbox`() = runTest {
        coEvery { subsonicRepository.starTrack("trk_1") } throws RuntimeException("Offline")

        val actionSlot = slot<SyncActionEntity>()
        coEvery { syncActionDao.upsertAction(capture(actionSlot)) } returns 1L

        mutationManager.setTrackStarred("trk_1", true)

        coVerify(exactly = 1) { trackDao.setTrackStarred("trk_1", "srv_test", any()) }
        coVerify(exactly = 1) { syncActionDao.upsertAction(any()) }
        assertEquals("STAR", actionSlot.captured.actionType)
        assertEquals("trk_1", actionSlot.captured.targetId)
    }

    @Test
    fun `setAlbumStarred uses typed Subsonic starAlbum endpoint`() = runTest {
        coEvery { subsonicRepository.starAlbum("alb_1") } returns Unit

        mutationManager.setAlbumStarred("alb_1", true)

        coVerify(exactly = 1) { albumDao.setAlbumStarred("alb_1", "srv_test", any()) }
        coVerify(exactly = 1) { subsonicRepository.starAlbum("alb_1") }
    }

    @Test
    fun `setArtistStarred uses typed Subsonic starArtist endpoint`() = runTest {
        coEvery { subsonicRepository.starArtist("art_1") } returns Unit

        mutationManager.setArtistStarred("art_1", true)

        coVerify(exactly = 1) { artistDao.setArtistStarred("art_1", "srv_test", any()) }
        coVerify(exactly = 1) { subsonicRepository.starArtist("art_1") }
    }

    @Test
    fun `deletePlaylist immediately removes playlist from Room and calls remote delete`() = runTest {
        coEvery { subsonicRepository.deletePlaylist("pl_1") } returns true

        val result = mutationManager.deletePlaylist("pl_1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { playlistDao.deletePlaylistAndTracks("pl_1", "srv_test") }
        coVerify(exactly = 1) { subsonicRepository.deletePlaylist("pl_1") }
    }

    @Test
    fun `removeTrackFromPlaylist removes track cross-refs by index and calls updatePlaylist`() = runTest {
        val track1 = TrackEntity(id = "trk_1", serverId = "srv_test", title = "T1", subsonicJson = "{}")
        val track2 = TrackEntity(id = "trk_2", serverId = "srv_test", title = "T2", subsonicJson = "{}")
        val track3 = TrackEntity(id = "trk_3", serverId = "srv_test", title = "T3", subsonicJson = "{}")

        val playlistEntity = PlaylistEntity(
            id = "pl_1",
            serverId = "srv_test",
            name = "My Playlist",
            comment = null,
            songCount = 3,
            durationSeconds = 300L,
            isPublic = false,
            coverArtId = null,
            subsonicJson = "{}"
        )
        coEvery { playlistDao.getTracksForPlaylist("pl_1", "srv_test") } returns listOf(track1, track2, track3)
        coEvery { playlistDao.getPlaylist("pl_1", "srv_test") } returns playlistEntity
        coEvery { subsonicRepository.updatePlaylist("pl_1", songIndexesToRemove = listOf(1)) } returns true

        val result = mutationManager.removeTrackFromPlaylist("pl_1", 1)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            subsonicRepository.updatePlaylist("pl_1", songIndexesToRemove = listOf(1))
        }
        coVerify(exactly = 1) {
            playlistDao.replacePlaylistTracks(
                playlistId = "pl_1",
                serverId = "srv_test",
                crossRefs = match { refs ->
                    refs.size == 2 && refs[0].trackId == "trk_1" && refs[0].sortOrder == 0 &&
                            refs[1].trackId == "trk_3" && refs[1].sortOrder == 1
                }
            )
        }
    }

    @Test
    fun `createPlaylist writes optimistic playlist to Room and calls remote createPlaylist`() = runTest {
        val mockCreated: Playlist = json.decodeFromString(
            """{"id":"pl_remote_1","name":"New List","owner":"testuser","songCount":2,"duration":200,"public":false,"created":"1970-01-01T00:00:00Z","changed":"1970-01-01T00:00:00Z"}"""
        )
        coEvery { subsonicRepository.createPlaylist("New List", listOf("trk_1", "trk_2")) } returns mockCreated

        val result = mutationManager.createPlaylist("New List", listOf("trk_1", "trk_2"))

        assertTrue(result.isSuccess)
        assertEquals("pl_remote_1", result.getOrNull()?.id)
        coVerify(exactly = 1) {
            subsonicRepository.createPlaylist("New List", listOf("trk_1", "trk_2"))
        }
    }
}
