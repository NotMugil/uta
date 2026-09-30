package com.notmugil.uta.player

import android.net.Uri
import com.notmugil.uta.domain.model.TrackItem
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

class MediaItemMapperTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        val dummyUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns dummyUri
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `toMediaItem preserves track metadata and generates unique entryId`() {
        val track = TrackItem(
            id = "track_123",
            title = "Starboy",
            artist = "The Weeknd",
            artistId = "art_1",
            album = "Starboy",
            albumId = "alb_1",
            coverArtId = "cov_1",
            durationSeconds = 230L,
            trackNumber = 1,
            discNumber = 1,
            bitRate = 320,
            suffix = "flac",
            isStarred = true
        )

        val entryId1 = UUID.randomUUID().toString()
        val entryId2 = UUID.randomUUID().toString()

        val mediaItem1 = MediaItemMapper.toMediaItem(track, "http://stream/1", "http://cover/1", entryId = entryId1)
        val mediaItem2 = MediaItemMapper.toMediaItem(track, "http://stream/1", "http://cover/1", entryId = entryId2)

        // mediaId matches entryId and customCacheKey includes track ID, format and bitrate
        assertEquals(entryId1, mediaItem1.mediaId)
        assertEquals(entryId2, mediaItem2.mediaId)
        assertEquals("track_123::flac::320", mediaItem1.localConfiguration?.customCacheKey)
        assertEquals("track_123::flac::320", mediaItem2.localConfiguration?.customCacheKey)

        // entry IDs are unique even for same track
        assertNotEquals(MediaItemMapper.getEntryId(mediaItem1), MediaItemMapper.getEntryId(mediaItem2))
        assertEquals(entryId1, MediaItemMapper.getEntryId(mediaItem1))
        assertEquals(entryId2, MediaItemMapper.getEntryId(mediaItem2))
        assertEquals("track_123", MediaItemMapper.getTrackId(mediaItem1))

        // Conversion back to domain model retains attributes
        val mappedTrack = MediaItemMapper.toTrackItem(mediaItem1)
        assertNotNull(mappedTrack)
        assertEquals("track_123", mappedTrack?.id)
        assertEquals("Starboy", mappedTrack?.title)
        assertEquals("The Weeknd", mappedTrack?.artist)
        assertEquals("Starboy", mappedTrack?.album)
        assertEquals(1, mappedTrack?.trackNumber)
        assertEquals(1, mappedTrack?.discNumber)
    }

    @Test
    fun `toQueueItem pairs entryId with TrackItem`() {
        val track = TrackItem(
            id = "t1",
            title = "Title 1",
            artist = "Artist 1"
        )
        val entryId = "entry_xyz"
        val mediaItem = MediaItemMapper.toMediaItem(track, "http://stream/t1", entryId = entryId)

        val queueItem = MediaItemMapper.toQueueItem(mediaItem)
        assertNotNull(queueItem)
        assertEquals(entryId, queueItem?.entryId)
        assertEquals("t1", queueItem?.track?.id)
    }

    @Test
    fun `toMediaItem incorporates transcoding format and maxBitRate into cacheKey`() {
        val track = TrackItem(
            id = "t_trans",
            title = "Song",
            artist = "Artist",
            suffix = "flac",
            bitRate = 1411
        )
        val transUri = mockk<Uri>(relaxed = true)
        every { Uri.parse("http://stream/trans") } returns transUri
        every { transUri.getQueryParameter("format") } returns "opus"
        every { transUri.getQueryParameter("maxBitRate") } returns "192"

        val mediaItem = MediaItemMapper.toMediaItem(track, "http://stream/trans")
        assertEquals("t_trans::opus::192", mediaItem.localConfiguration?.customCacheKey)
    }
}
