package com.notmugil.uta.data.sync

import androidx.room.withTransaction
import com.notmugil.uta.data.AuthState
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.AlbumDao
import com.notmugil.uta.data.db.ArtistDao
import com.notmugil.uta.data.db.GenreDao
import com.notmugil.uta.data.db.PlaylistDao
import com.notmugil.uta.data.db.PlaylistTrackCrossRef
import com.notmugil.uta.data.db.SyncMetadataDao
import com.notmugil.uta.data.db.TrackDao
import com.notmugil.uta.data.db.UtaDatabase
import dev.zt64.subsonic.api.model.Album
import dev.zt64.subsonic.api.model.Playlist
import dev.zt64.subsonic.client.SubsonicClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibrarySyncEngineTest {

    private val subsonicRepository: SubsonicRepository = mockk(relaxed = true)
    private val database: UtaDatabase = mockk(relaxed = true)
    private val albumDao: AlbumDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val artistDao: ArtistDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val genreDao: GenreDao = mockk(relaxed = true)
    private val syncMetadataDao: SyncMetadataDao = mockk(relaxed = true)
    private val subsonicClient: SubsonicClient = mockk(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private lateinit var syncEngine: LibrarySyncEngine

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        val blockSlot = slot<suspend () -> Any?>()
        coEvery { database.withTransaction(capture(blockSlot)) } coAnswers {
            blockSlot.captured.invoke()
        }

        every { subsonicRepository.currentServerId } returns "server123"
        every { subsonicRepository.currentClient } returns subsonicClient
        every { subsonicRepository.authState } returns MutableStateFlow<AuthState>(AuthState.Unauthenticated())

        val networkMonitor = mockk<com.notmugil.uta.data.NetworkMonitor>(relaxed = true)
        every { networkMonitor.isOfflineModeActive } returns MutableStateFlow(false)
        val context = mockk<android.content.Context>(relaxed = true)
        val okHttpClient = mockk<okhttp3.OkHttpClient>(relaxed = true)

        syncEngine = LibrarySyncEngine(
            context = context,
            subsonicRepository = subsonicRepository,
            database = database,
            albumDao = albumDao,
            trackDao = trackDao,
            artistDao = artistDao,
            playlistDao = playlistDao,
            genreDao = genreDao,
            syncMetadataDao = syncMetadataDao,
            networkMonitorProvider = { networkMonitor },
            okHttpClient = okHttpClient,
            json = json
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `fetchPlaylistTracks upserts tracks, playlist and cross references`() = runBlocking {
        val playlistJson = """
            {
                "id": "pl123",
                "name": "Favorites",
                "comment": "My favorite tracks",
                "owner": "user",
                "public": true,
                "songCount": 2,
                "duration": 380,
                "created": "2023-01-01T00:00:00Z",
                "changed": "2023-01-01T00:00:00Z",
                "entry": [
                    {
                        "id": "song1",
                        "parent": "album1",
                        "isDir": false,
                        "title": "Track One",
                        "album": "Album Name",
                        "artist": "Artist Name",
                        "duration": 180,
                        "suffix": "mp3",
                        "contentType": "audio/mpeg",
                        "path": "Artist/Album/song1.mp3"
                    },
                    {
                        "id": "song2",
                        "parent": "album1",
                        "isDir": false,
                        "title": "Track Two",
                        "album": "Album Name",
                        "artist": "Artist Name",
                        "duration": 200,
                        "suffix": "mp3",
                        "contentType": "audio/mpeg",
                        "path": "Artist/Album/song2.mp3"
                    }
                ]
            }
        """.trimIndent()

        val testPlaylist = json.decodeFromString<Playlist>(playlistJson)
        coEvery { subsonicClient.getPlaylist("pl123") } returns testPlaylist

        val result = syncEngine.fetchPlaylistTracks("pl123")

        assertTrue(result.isSuccess)
        val tracks = result.getOrThrow()
        assertEquals(2, tracks.size)
        assertEquals("Track One", tracks[0].title)
        assertEquals("Track Two", tracks[1].title)

        coVerify { trackDao.upsertTracks(match { it.size == 2 && it[0].id == "song1" && it[1].id == "song2" }) }
        coVerify { playlistDao.upsertPlaylist(match { it.id == "pl123" && it.name == "Favorites" }) }
        coVerify {
            playlistDao.replacePlaylistTracks(
                "pl123",
                "server123",
                match { crossRefs ->
                    crossRefs.size == 2 &&
                            crossRefs[0] == PlaylistTrackCrossRef("pl123", "song1", "server123", 0) &&
                            crossRefs[1] == PlaylistTrackCrossRef("pl123", "song2", "server123", 1)
                }
            )
        }
    }

    @Test
    fun `fetchAlbumTracks upserts album entity alongside track entities`() = runBlocking {
        val albumJson = """
            {
                "id": "al1",
                "name": "Album One",
                "artist": "Artist One",
                "artistId": "art1",
                "songCount": 1,
                "duration": 150,
                "created": "2023-01-01T00:00:00Z",
                "song": [
                    {
                        "id": "s1",
                        "parent": "al1",
                        "isDir": false,
                        "title": "Song One",
                        "album": "Album One",
                        "artist": "Artist One",
                        "duration": 150,
                        "suffix": "mp3",
                        "contentType": "audio/mpeg",
                        "path": "Artist/Album/s1.mp3"
                    }
                ]
            }
        """.trimIndent()

        val testAlbum = json.decodeFromString<Album>(albumJson)
        coEvery { subsonicClient.getAlbum("al1") } returns testAlbum

        val result = syncEngine.fetchAlbumTracks("al1")

        assertTrue(result.isSuccess)
        val tracks = result.getOrThrow()
        assertEquals(1, tracks.size)
        assertEquals("Song One", tracks[0].title)

        coVerify { albumDao.upsertAlbum(match { it.id == "al1" && it.name == "Album One" }) }
        coVerify { trackDao.upsertTracks(match { it.size == 1 && it[0].id == "s1" }) }
    }

    @Test
    fun `fetchAlbumTracks falls back to getAlbumRaw when getAlbum throws exception`() = runBlocking {
        val albumJson = """
            {
                "id": "al_corrupt",
                "name": "Corrupt Album",
                "artist": "Artist One",
                "artistId": "art1",
                "songCount": 1,
                "duration": 150,
                "created": "2023-01-01T00:00:00Z",
                "song": [
                    {
                        "id": "s_corrupt",
                        "parent": "al_corrupt",
                        "isDir": false,
                        "title": "Song Corrupt",
                        "album": "Corrupt Album",
                        "artist": "Artist One",
                        "duration": 150,
                        "suffix": "mp3",
                        "contentType": "audio/mpeg",
                        "path": "Artist/Album/s1.mp3"
                    }
                ]
            }
        """.trimIndent()

        val testAlbum = json.decodeFromString<Album>(albumJson)
        coEvery { subsonicClient.getAlbum("al_corrupt") } throws RuntimeException("JsonConvertException")
        coEvery { subsonicRepository.getAlbumRaw("al_corrupt") } returns testAlbum

        val result = syncEngine.fetchAlbumTracks("al_corrupt")

        assertTrue(result.isSuccess)
        val tracks = result.getOrThrow()
        assertEquals(1, tracks.size)
        assertEquals("Song Corrupt", tracks[0].title)

        coVerify { subsonicRepository.getAlbumRaw("al_corrupt") }
        coVerify { albumDao.upsertAlbum(match { it.id == "al_corrupt" }) }
    }
}
