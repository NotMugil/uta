package com.notmugil.uta.data.db

import androidx.room.Entity
import androidx.room.Index
import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.GenreItem
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.domain.model.TrackItem
import dev.zt64.subsonic.api.model.Album
import dev.zt64.subsonic.api.model.Artist
import dev.zt64.subsonic.api.model.Genre
import dev.zt64.subsonic.api.model.Playlist
import dev.zt64.subsonic.api.model.Song
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Entity(
    tableName = "albums",
    primaryKeys = ["id", "serverId"],
    indices = [
        Index(value = ["serverId", "artistId"]),
        Index(value = ["serverId", "name"]),
        Index(value = ["serverId", "createdAt"])
    ]
)
data class AlbumEntity(
    val id: String,
    val serverId: String,
    val name: String,
    val artistName: String? = null,
    val artistId: String? = null,
    val coverArtId: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val durationSeconds: Long? = null,
    val songCount: Int = 0,
    val userRating: Int? = null,
    val starredAt: Long? = null,
    val createdAt: Long? = null,
    val syncedAt: Long = System.currentTimeMillis(),
    val subsonicJson: String
)

@Entity(
    tableName = "tracks",
    primaryKeys = ["id", "serverId"],
    indices = [
        Index(value = ["serverId", "albumId"]),
        Index(value = ["serverId", "artistId"]),
        Index(value = ["serverId", "title"])
    ]
)
data class TrackEntity(
    val id: String,
    val serverId: String,
    val albumId: String? = null,
    val title: String,
    val artistName: String? = null,
    val artistId: String? = null,
    val albumTitle: String? = null,
    val coverArtId: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val durationSeconds: Long? = null,
    val genre: String? = null,
    val year: Int? = null,
    val bitRate: Int? = null,
    val suffix: String? = null,
    val userRating: Int? = null,
    val playCount: Long? = null,
    val playedAt: Long? = null,
    val createdAt: Long? = null,
    val starredAt: Long? = null,
    val syncedAt: Long = System.currentTimeMillis(),
    val subsonicJson: String
)

@Entity(
    tableName = "artists",
    primaryKeys = ["id", "serverId"],
    indices = [
        Index(value = ["serverId", "name"])
    ]
)
data class ArtistEntity(
    val id: String,
    val serverId: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArtId: String? = null,
    val artistImageUrl: String? = null,
    val starredAt: Long? = null,
    val syncedAt: Long = System.currentTimeMillis(),
    val subsonicJson: String
)

@Entity(
    tableName = "playlists",
    primaryKeys = ["id", "serverId"],
    indices = [
        Index(value = ["serverId", "name"])
    ]
)
data class PlaylistEntity(
    val id: String,
    val serverId: String,
    val name: String,
    val comment: String? = null,
    val owner: String? = null,
    val isPublic: Boolean = false,
    val songCount: Int = 0,
    val durationSeconds: Long = 0L,
    val coverArtId: String? = null,
    val changedAt: Long? = null,
    val createdAt: Long? = null,
    val syncedAt: Long = System.currentTimeMillis(),
    val subsonicJson: String
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId", "serverId", "sortOrder"],
    indices = [
        Index(value = ["serverId", "playlistId"]),
        Index(value = ["serverId", "trackId"])
    ]
)
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val serverId: String,
    val sortOrder: Int
)

@Entity(
    tableName = "genres",
    primaryKeys = ["name", "serverId"],
    indices = [
        Index(value = ["serverId", "name"])
    ]
)
data class GenreEntity(
    val name: String,
    val serverId: String,
    val albumCount: Int = 0,
    val songCount: Int = 0,
    val syncedAt: Long = System.currentTimeMillis(),
    val subsonicJson: String
)

@Entity(
    tableName = "local_media",
    primaryKeys = ["trackId", "serverId"],
    indices = [
        Index(value = ["serverId"])
    ]
)
data class LocalMediaEntity(
    val trackId: String,
    val serverId: String,
    val relativePath: String,
    val quality: String = "ORIGINAL",
    val format: String = "mp3",
    val bitRate: Int? = null,
    val fileSizeBytes: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "download_scopes",
    primaryKeys = ["scopeId", "trackId", "serverId"],
    indices = [
        Index(value = ["serverId", "scopeId"]),
        Index(value = ["serverId", "trackId"])
    ]
)
data class DownloadScopeEntity(
    val scopeId: String,
    val scopeType: String,
    val trackId: String,
    val serverId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "download_queue",
    primaryKeys = ["trackId", "serverId"],
    indices = [
        Index(value = ["serverId", "status"]),
        Index(value = ["queuedAt"])
    ]
)
data class DownloadQueueEntity(
    val trackId: String,
    val serverId: String,
    val scopeId: String,
    val scopeType: String = "TRACK",
    val quality: String = "ORIGINAL",
    val bitRate: Int? = null,
    val format: String? = null,
    val status: String = "QUEUED",
    val attempts: Int = 0,
    val lastError: String? = null,
    val queuedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_metadata",
    primaryKeys = ["syncKey", "serverId"]
)
data class SyncMetadataEntity(
    val syncKey: String,
    val serverId: String,
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS"
)

// Mapping from Subsonic API to Entity
fun Album.toEntity(serverId: String, json: Json, stamp: Long = System.currentTimeMillis()): AlbumEntity = AlbumEntity(
    id = id,
    serverId = serverId,
    name = name.orEmpty(),
    artistName = artistName,
    artistId = artistId,
    coverArtId = coverArtId,
    year = year,
    genre = genre,
    durationSeconds = duration?.inWholeSeconds,
    songCount = songCount.takeIf { it > 0 } ?: songs.size,
    userRating = userRating,
    starredAt = starredAt?.toEpochMilliseconds(),
    createdAt = try {
        createdAt.toEpochMilliseconds()
    } catch (_: Exception) {
        null
    },
    syncedAt = stamp,
    subsonicJson = json.encodeToString(this)
)

fun Song.toEntity(serverId: String, json: Json, stamp: Long = System.currentTimeMillis()): TrackEntity = TrackEntity(
    id = id,
    serverId = serverId,
    albumId = albumId,
    title = title,
    artistName = artistName,
    artistId = artistId,
    albumTitle = albumTitle,
    coverArtId = coverArtId,
    trackNumber = trackNumber,
    discNumber = discNumber,
    durationSeconds = duration?.inWholeSeconds,
    genre = genre,
    year = year,
    bitRate = bitRate,
    suffix = null,
    userRating = userRating,
    playCount = playCount.toLong(),
    playedAt = null,
    createdAt = stamp,
    starredAt = starredAt?.toEpochMilliseconds(),
    syncedAt = stamp,
    subsonicJson = json.encodeToString(this)
)

fun Artist.toEntity(serverId: String, json: Json, stamp: Long = System.currentTimeMillis()): ArtistEntity = ArtistEntity(
    id = id,
    serverId = serverId,
    name = name,
    albumCount = albumCount,
    coverArtId = coverArtId,
    artistImageUrl = artistImageUrl,
    starredAt = starredAt?.toEpochMilliseconds(),
    syncedAt = stamp,
    subsonicJson = json.encodeToString(this)
)

fun Playlist.toEntity(serverId: String, json: Json, stamp: Long = System.currentTimeMillis()): PlaylistEntity = PlaylistEntity(
    id = id,
    serverId = serverId,
    name = name,
    comment = comment,
    owner = owner,
    isPublic = public == true,
    songCount = songCount.takeIf { it > 0 } ?: songs.size,
    durationSeconds = duration.inWholeSeconds,
    coverArtId = coverArtId,
    changedAt = stamp,
    createdAt = stamp,
    syncedAt = stamp,
    subsonicJson = json.encodeToString(this)
)

fun Genre.toEntity(serverId: String, json: Json, stamp: Long = System.currentTimeMillis()): GenreEntity = GenreEntity(
    name = name,
    serverId = serverId,
    albumCount = albumCount,
    songCount = songCount,
    syncedAt = stamp,
    subsonicJson = json.encodeToString(this)
)

// Mapping from Entity to Domain Models
fun AlbumEntity.toDomain(): AlbumItem = AlbumItem(
    id = id,
    title = name,
    artist = artistName ?: "Unknown Artist",
    artistId = artistId,
    coverArtId = coverArtId,
    year = year,
    songCount = songCount,
    durationSeconds = durationSeconds ?: 0L,
    isStarred = starredAt != null,
    genre = genre,
    userRating = userRating
)

fun TrackEntity.toDomain(): TrackItem = TrackItem(
    id = id,
    title = title,
    artist = artistName ?: "Unknown Artist",
    artistId = artistId,
    album = albumTitle,
    albumId = albumId,
    coverArtId = coverArtId,
    durationSeconds = durationSeconds ?: 0L,
    trackNumber = trackNumber,
    discNumber = discNumber,
    bitRate = bitRate,
    suffix = suffix,
    isStarred = starredAt != null,
    genre = genre,
    year = year,
    userRating = userRating,
    playCount = playCount,
    playedAt = playedAt,
    createdAt = createdAt,
    starredAt = starredAt
)

fun ArtistEntity.toDomain(): ArtistItem = ArtistItem(
    id = id,
    name = name,
    coverArtId = coverArtId,
    albumCount = albumCount,
    isStarred = starredAt != null
)

fun PlaylistEntity.toDomain(navidromeInfo: com.notmugil.uta.data.NavidromePlaylistInfo? = null): PlaylistItem {
    val isSmartInfer = navidromeInfo?.isSmart ?: (
        name.endsWith(".nsp", ignoreCase = true) ||
        comment?.contains(".nsp", ignoreCase = true) == true ||
        (subsonicJson.contains("\"rules\"") && !subsonicJson.contains("\"rules\":null"))
    )
    val isSyncInfer = navidromeInfo?.isSync ?: (
        name.endsWith(".m3u", ignoreCase = true) ||
        name.endsWith(".m3u8", ignoreCase = true) ||
        comment?.contains(".m3u", ignoreCase = true) == true ||
        subsonicJson.contains("\"sync\":true")
    )
    val isOwnerInfer = navidromeInfo?.isOwner ?: true
    return PlaylistItem(
        id = id,
        name = name,
        comment = comment,
        songCount = songCount,
        durationSeconds = durationSeconds,
        coverArtId = coverArtId,
        isPublic = isPublic,
        isSmart = isSmartInfer,
        isSync = isSyncInfer,
        isOwner = isOwnerInfer,
        changedAt = changedAt,
        createdAt = createdAt
    )
}

fun GenreEntity.toDomain(): GenreItem = GenreItem(
    name = name,
    albumCount = albumCount,
    songCount = songCount
)

// Mapping from API DTOs directly to Domain Models
fun Album.toDomain(): AlbumItem = AlbumItem(
    id = id,
    title = name.orEmpty(),
    artist = artistName,
    artistId = artistId,
    coverArtId = coverArtId,
    year = year,
    songCount = songCount.takeIf { it > 0 } ?: songs.size,
    durationSeconds = duration?.inWholeSeconds ?: 0L,
    isStarred = starredAt != null,
    genre = genre,
    userRating = userRating
)

fun Song.toDomain(): TrackItem = TrackItem(
    id = id,
    title = title,
    artist = artistName,
    artistId = artistId,
    album = albumTitle,
    albumId = albumId,
    coverArtId = coverArtId,
    durationSeconds = duration?.inWholeSeconds ?: 0L,
    trackNumber = trackNumber,
    discNumber = discNumber,
    bitRate = bitRate,
    suffix = null,
    isStarred = starredAt != null,
    genre = genre,
    year = year,
    userRating = userRating,
    playCount = playCount.toLong(),
    playedAt = null,
    createdAt = null,
    starredAt = starredAt?.toEpochMilliseconds()
)

fun Artist.toDomain(): ArtistItem = ArtistItem(
    id = id,
    name = name,
    coverArtId = coverArtId,
    albumCount = albumCount,
    isStarred = starredAt != null
)

fun Playlist.toDomain(navidromeInfo: com.notmugil.uta.data.NavidromePlaylistInfo? = null): PlaylistItem = PlaylistItem(
    id = id,
    name = name,
    comment = comment,
    songCount = songCount.takeIf { it > 0 } ?: songs.size,
    durationSeconds = duration.inWholeSeconds,
    coverArtId = coverArtId,
    isPublic = public == true,
    isSmart = navidromeInfo?.isSmart ?: (name.endsWith(".nsp", ignoreCase = true) || comment?.contains(".nsp", ignoreCase = true) == true),
    isSync = navidromeInfo?.isSync ?: (name.endsWith(".m3u", ignoreCase = true) || name.endsWith(".m3u8", ignoreCase = true) || comment?.contains(".m3u", ignoreCase = true) == true),
    isOwner = navidromeInfo?.isOwner ?: owner.isNotBlank(),
    changedAt = null,
    createdAt = null
)

fun sanitizeSubsonicJson(rawJson: String, json: Json): String = try {
    val root = json.parseToJsonElement(rawJson)
    val sanitized = sanitizeJsonElement(root)
    sanitized.toString()
} catch (_: Exception) {
    rawJson
}

private fun sanitizeDateElement(element: JsonElement?): JsonElement? {
    if (element == null || element is kotlinx.serialization.json.JsonNull) return null
    if (element is JsonPrimitive) {
        val text = element.content
        return if (text.isBlank()) null else element
    }
    if (element is JsonObject) {
        val year = element["year"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        if (year == null || year <= 0) return null
        val month = element["month"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 12) ?: 1
        val day = element["day"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 31) ?: 1
        return buildJsonObject {
            put("year", year)
            put("month", month)
            put("day", day)
        }
    }
    return null
}

private fun sanitizeJsonElement(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> {
        val map = element.toMutableMap()
        val genresElem = map["genres"]
        if (genresElem is JsonArray) {
            map["genres"] = JsonArray(
                genresElem.map { elem ->
                    if (elem is JsonObject) {
                        sanitizeJsonElement(elem)
                    } else {
                        buildJsonObject { put("name", elem.jsonPrimitive.content) }
                    }
                }
            )
        }

        if (map.containsKey("title") && map.containsKey("id")) {
            val rawContentType = map["contentType"]?.jsonPrimitive?.contentOrNull
            if (rawContentType.isNullOrBlank()) {
                val suffix = map["suffix"]?.jsonPrimitive?.contentOrNull?.lowercase()
                val mime = when (suffix) {
                    "flac" -> "audio/flac"
                    "opus" -> "audio/opus"
                    "m4a", "aac" -> "audio/mp4"
                    "ogg" -> "audio/ogg"
                    "wav" -> "audio/wav"
                    else -> "audio/mpeg"
                }
                map["contentType"] = JsonPrimitive(mime)
            }
            if (!map.containsKey("suffix") || map["suffix"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
                map["suffix"] = JsonPrimitive("mp3")
            }
            if (!map.containsKey("path")) {
                map["path"] = JsonPrimitive("")
            }
            if (!map.containsKey("isDir")) {
                map["isDir"] = JsonPrimitive(false)
            }
        }

        if (map.containsKey("name") && map.containsKey("id") && !map.containsKey("title")) {
            if (!map.containsKey("created") || map["created"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
                map["created"] = JsonPrimitive("1970-01-01T00:00:00Z")
            }
            if (!map.containsKey("changed") || map["changed"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
                map["changed"] = JsonPrimitive("1970-01-01T00:00:00Z")
            }
        }

        // Sanitize releaseDate / originalReleaseDate for LocalDateComponentSerializer
        if (map.containsKey("releaseDate")) {
            val sanitizedDate = sanitizeDateElement(map["releaseDate"])
            if (sanitizedDate != null) {
                map["releaseDate"] = sanitizedDate
            } else {
                map.remove("releaseDate")
            }
        }
        if (map.containsKey("originalReleaseDate")) {
            val sanitizedDate = sanitizeDateElement(map["originalReleaseDate"])
            if (sanitizedDate != null) {
                map["originalReleaseDate"] = sanitizedDate
            } else {
                map.remove("originalReleaseDate")
            }
        }

        for ((key, value) in element) {
            if (key != "genres" && key != "contentType" && key != "suffix" && key != "path" && key != "isDir" &&
                key != "created" && key != "changed" && key != "releaseDate" && key != "originalReleaseDate") {
                map[key] = sanitizeJsonElement(value)
            }
        }
        JsonObject(map)
    }
    is JsonArray -> JsonArray(element.map { sanitizeJsonElement(it) })
    else -> element
}

fun AlbumEntity.toAlbum(json: Json): Album = try {
    json.decodeFromString(subsonicJson)
} catch (_: Exception) {
    val sanitized = sanitizeSubsonicJson(subsonicJson, json)
    json.decodeFromString(sanitized)
}

fun TrackEntity.toSong(json: Json): Song = try {
    json.decodeFromString(subsonicJson)
} catch (_: Exception) {
    val sanitized = sanitizeSubsonicJson(subsonicJson, json)
    json.decodeFromString(sanitized)
}

fun ArtistEntity.toArtist(json: Json): Artist = json.decodeFromString(subsonicJson)

fun PlaylistEntity.toPlaylist(json: Json): Playlist = try {
    json.decodeFromString(subsonicJson)
} catch (_: Exception) {
    val sanitized = sanitizeSubsonicJson(subsonicJson, json)
    json.decodeFromString(sanitized)
}

fun GenreEntity.toGenre(json: Json): Genre = json.decodeFromString(subsonicJson)
