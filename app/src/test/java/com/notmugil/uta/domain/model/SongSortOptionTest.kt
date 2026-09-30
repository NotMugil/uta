package com.notmugil.uta.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SongSortOptionTest {

    private val track1 = TrackItem(
        id = "1",
        title = "Bravo",
        artist = "Charlie",
        album = "Delta",
        durationSeconds = 200,
        trackNumber = 2,
        discNumber = 1,
        year = 2020,
        isStarred = false,
        userRating = 3,
        playCount = 10,
        playedAt = 1000L,
        createdAt = 5000L
    )

    private val track2 = TrackItem(
        id = "2",
        title = "Alpha",
        artist = "Bravo",
        album = "Alpha",
        durationSeconds = 150,
        trackNumber = 1,
        discNumber = 1,
        year = 2015,
        isStarred = true,
        userRating = 5,
        playCount = 50,
        playedAt = 2000L,
        createdAt = 1000L
    )

    private val track3 = TrackItem(
        id = "3",
        title = "Charlie",
        artist = "Alpha",
        album = "Bravo",
        durationSeconds = 300,
        trackNumber = 3,
        discNumber = 1,
        year = 2024,
        isStarred = false,
        userRating = 4,
        playCount = 5,
        playedAt = 500L,
        createdAt = 9000L
    )

    private val list = listOf(track1, track2, track3)

    @Test
    fun testSortById() {
        val sortedAsc = list.sortWithOption(SongSortOption.ID, ascending = true)
        assertEquals(listOf("1", "2", "3"), sortedAsc.map { it.id })

        val sortedDesc = list.sortWithOption(SongSortOption.ID, ascending = false)
        assertEquals(listOf("3", "2", "1"), sortedDesc.map { it.id })
    }

    @Test
    fun testSortByTitle() {
        val sorted = list.sortWithOption(SongSortOption.TITLE, ascending = true)
        assertEquals(listOf("2", "1", "3"), sorted.map { it.id }) // Alpha, Bravo, Charlie
    }

    @Test
    fun testSortByAlbum() {
        val sorted = list.sortWithOption(SongSortOption.ALBUM, ascending = true)
        assertEquals(listOf("2", "3", "1"), sorted.map { it.id }) // Alpha, Bravo, Delta
    }

    @Test
    fun testSortByArtist() {
        val sorted = list.sortWithOption(SongSortOption.ARTIST, ascending = true)
        assertEquals(listOf("3", "2", "1"), sorted.map { it.id }) // Alpha, Bravo, Charlie
    }

    @Test
    fun testSortByDuration() {
        val sorted = list.sortWithOption(SongSortOption.DURATION, ascending = true)
        assertEquals(listOf("2", "1", "3"), sorted.map { it.id }) // 150, 200, 300
    }

    @Test
    fun testSortByFavourites() {
        val sorted = list.sortWithOption(SongSortOption.FAVOURITES, ascending = true)
        assertEquals("2", sorted.first().id) // Starred first
    }

    @Test
    fun testSortByRating() {
        val sorted = list.sortWithOption(SongSortOption.RATING, ascending = true)
        assertEquals(listOf("2", "3", "1"), sorted.map { it.id }) // 5, 4, 3
    }

    @Test
    fun testSortByRecentlyAdded() {
        val sorted = list.sortWithOption(SongSortOption.RECENTLY_ADDED, ascending = true)
        assertEquals(listOf("3", "1", "2"), sorted.map { it.id }) // 9000, 5000, 1000
    }

    @Test
    fun testSortByRecentlyPlayed() {
        val sorted = list.sortWithOption(SongSortOption.RECENTLY_PLAYED, ascending = true)
        assertEquals(listOf("2", "1", "3"), sorted.map { it.id }) // 2000, 1000, 500
    }

    @Test
    fun testSortByMostPlayed() {
        val sorted = list.sortWithOption(SongSortOption.MOST_PLAYED, ascending = true)
        assertEquals(listOf("2", "1", "3"), sorted.map { it.id }) // 50, 10, 5
    }

    @Test
    fun testSortByYear() {
        val sorted = list.sortWithOption(SongSortOption.YEAR, ascending = true)
        assertEquals(listOf("2", "1", "3"), sorted.map { it.id }) // 2015, 2020, 2024
    }
}
