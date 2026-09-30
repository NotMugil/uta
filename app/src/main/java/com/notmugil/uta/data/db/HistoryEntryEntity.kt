package com.notmugil.uta.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.notmugil.uta.domain.model.HistoryItem
import com.notmugil.uta.domain.model.TrackItem

@Entity(
    tableName = "history_entries",
    indices = [
        Index(value = ["serverId", "playedAt"]),
        Index(value = ["serverId", "trackId"])
    ]
)
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: String,
    val trackId: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val albumTitle: String? = null,
    val albumId: String? = null,
    val coverArtId: String? = null,
    val durationSeconds: Long = 0,
    val playedAt: Long = System.currentTimeMillis()
)

fun HistoryEntryEntity.toDomain(): HistoryItem = HistoryItem(
    id = id,
    track = TrackItem(
        id = trackId,
        title = title,
        artist = artistName,
        artistId = artistId,
        album = albumTitle,
        albumId = albumId,
        coverArtId = coverArtId,
        durationSeconds = durationSeconds
    ),
    playedAt = playedAt
)

fun TrackItem.toHistoryEntity(serverId: String): HistoryEntryEntity = HistoryEntryEntity(
    serverId = serverId,
    trackId = id,
    title = title,
    artistName = artist,
    artistId = artistId,
    albumTitle = album,
    albumId = albumId,
    coverArtId = coverArtId,
    durationSeconds = durationSeconds,
    playedAt = System.currentTimeMillis()
)
