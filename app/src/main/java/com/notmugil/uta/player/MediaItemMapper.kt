package com.notmugil.uta.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.notmugil.uta.domain.model.QueueItem
import com.notmugil.uta.domain.model.TrackItem
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import java.util.UUID

@OptIn(UnstableApi::class)
object MediaItemMapper {

    const val EXTRA_ENTRY_ID = "queue_entry_id"
    const val EXTRA_TRACK_ID = "track_id"
    const val EXTRA_ARTIST_ID = "artist_id"
    const val EXTRA_ALBUM_ID = "album_id"
    const val EXTRA_COVER_ART_ID = "cover_art_id"
    const val EXTRA_DURATION_SECONDS = "duration_seconds"
    const val EXTRA_BIT_RATE = "bit_rate"
    const val EXTRA_SUFFIX = "suffix"
    const val EXTRA_IS_STARRED = "is_starred"

    fun toMediaItem(
        track: TrackItem,
        streamUrl: String,
        coverArtUrl: String? = null,
        entryId: String = UUID.randomUUID().toString()
    ): MediaItem {
        val extras = Bundle().apply {
            putString(EXTRA_ENTRY_ID, entryId)
            putString(EXTRA_TRACK_ID, track.id)
            putString("id", track.id)
            putString("title", track.title)
            putString("artist", track.artist)
            putString(EXTRA_ARTIST_ID, track.artistId)
            putString("album", track.album)
            putString(EXTRA_ALBUM_ID, track.albumId)
            putString(EXTRA_COVER_ART_ID, track.coverArtId)
            putLong(EXTRA_DURATION_SECONDS, track.durationSeconds)
            putInt(EXTRA_BIT_RATE, track.bitRate ?: 0)
            putString(EXTRA_SUFFIX, track.suffix)
            putBoolean(EXTRA_IS_STARRED, track.isStarred)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(coverArtUrl?.let { Uri.parse(it) })
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.discNumber)
            .setIsPlayable(true)
            .setExtras(extras)
            .build()

        val parsedUri = try { Uri.parse(streamUrl) } catch (_: Exception) { null }
        val reqFormat = parsedUri?.getQueryParameter("format")?.takeIf { it.isNotBlank() && it != "raw" }
            ?: (if (streamUrl.startsWith("file://") || streamUrl.startsWith("/")) {
                val fileExt = streamUrl.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it != "media" }
                fileExt ?: track.suffix ?: "raw"
            } else {
                track.suffix ?: "raw"
            })
        val reqBitRate = parsedUri?.getQueryParameter("maxBitRate")?.toIntOrNull() ?: track.bitRate ?: 0
        val cacheKey = "${track.id}::$reqFormat::$reqBitRate"

        val isHls = streamUrl.endsWith(".m3u8", ignoreCase = true) ||
            streamUrl.contains(".m3u8?", ignoreCase = true) ||
            streamUrl.contains("/hls", ignoreCase = true)

        val builder = MediaItem.Builder()
            .setMediaId(entryId)
            .setCustomCacheKey(cacheKey)
            .setUri(Uri.parse(streamUrl))
            .setMediaMetadata(metadata)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse(streamUrl))
                    .build()
            )

        if (isHls) {
            builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }

        return builder.build()
    }

    fun toTrackItem(mediaItem: MediaItem?): TrackItem? {
        if (mediaItem == null) return null
        val extras = mediaItem.mediaMetadata.extras ?: Bundle.EMPTY
        val metadata = mediaItem.mediaMetadata

        val id = extras.getString(EXTRA_TRACK_ID)
            ?: extras.getString("id")
            ?: mediaItem.localConfiguration?.customCacheKey?.substringBefore("::")
            ?: mediaItem.mediaId
        val title = metadata.title?.toString() ?: extras.getString("title") ?: "Unknown Title"
        val artist = metadata.artist?.toString() ?: extras.getString("artist") ?: "Unknown Artist"

        return TrackItem(
            id = id,
            title = title,
            artist = artist,
            artistId = extras.getString(EXTRA_ARTIST_ID) ?: extras.getString("artistId"),
            album = metadata.albumTitle?.toString() ?: extras.getString("album"),
            albumId = extras.getString(EXTRA_ALBUM_ID) ?: extras.getString("albumId"),
            coverArtId = extras.getString(EXTRA_COVER_ART_ID) ?: extras.getString("coverArtId"),
            durationSeconds = extras.getLong(EXTRA_DURATION_SECONDS, extras.getLong("durationSeconds", 0L)),
            trackNumber = metadata.trackNumber ?: extras.getInt("trackNumber").takeIf { it > 0 },
            discNumber = metadata.discNumber ?: extras.getInt("discNumber").takeIf { it > 0 },
            bitRate = extras.getInt(EXTRA_BIT_RATE).takeIf { it > 0 } ?: extras.getInt("bitRate").takeIf { it > 0 },
            suffix = extras.getString(EXTRA_SUFFIX) ?: extras.getString("suffix"),
            isStarred = extras.getBoolean(EXTRA_IS_STARRED, extras.getBoolean("isStarred", false))
        )
    }

    fun toQueueItem(mediaItem: MediaItem?): QueueItem? {
        val track = toTrackItem(mediaItem) ?: return null
        val entryId = getEntryId(mediaItem)
        return QueueItem(entryId = entryId, track = track)
    }

    fun getEntryId(mediaItem: MediaItem?): String {
        if (mediaItem == null) return UUID.randomUUID().toString()
        val extras = mediaItem.mediaMetadata.extras
        return extras?.getString(EXTRA_ENTRY_ID) ?: mediaItem.mediaId
    }

    fun getTrackId(mediaItem: MediaItem?): String {
        if (mediaItem == null) return ""
        val extras = mediaItem.mediaMetadata.extras
        return extras?.getString(EXTRA_TRACK_ID)
            ?: mediaItem.localConfiguration?.customCacheKey?.substringBefore("::")
            ?: mediaItem.mediaId
    }
}
