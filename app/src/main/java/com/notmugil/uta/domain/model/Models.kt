package com.notmugil.uta.domain.model

data class AlbumItem(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val coverArtId: String? = null,
    val year: Int? = null,
    val songCount: Int = 0,
    val durationSeconds: Long = 0,
    val isStarred: Boolean = false,
    val genre: String? = null,
    val userRating: Int? = null
)

data class TrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val coverArtId: String? = null,
    val durationSeconds: Long = 0,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val bitRate: Int? = null,
    val suffix: String? = null,
    val isStarred: Boolean = false,
    val genre: String? = null,
    val year: Int? = null,
    val userRating: Int? = null,
    val playCount: Long? = null,
    val playedAt: Long? = null,
    val createdAt: Long? = null,
    val starredAt: Long? = null
)

data class ArtistItem(
    val id: String,
    val name: String,
    val coverArtId: String? = null,
    val albumCount: Int = 0,
    val isStarred: Boolean = false
)

data class PlaylistItem(
    val id: String,
    val name: String,
    val comment: String? = null,
    val songCount: Int = 0,
    val durationSeconds: Long = 0,
    val coverArtId: String? = null,
    val isPublic: Boolean = false,
    val isSmart: Boolean = false,
    val isSync: Boolean = false,
    val isOwner: Boolean = true,
    val changedAt: Long? = null,
    val createdAt: Long? = null
) {
    val canEditTracks: Boolean
        get() = !isSmart && !isSync && isOwner
}

data class GenreItem(
    val name: String,
    val albumCount: Int = 0,
    val songCount: Int = 0
)

data class QueueItem(
    val entryId: String,
    val track: TrackItem
)
