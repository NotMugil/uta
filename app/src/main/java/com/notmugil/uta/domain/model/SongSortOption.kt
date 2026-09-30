package com.notmugil.uta.domain.model

import androidx.annotation.StringRes
import com.notmugil.uta.R

enum class SongSortOption(@StringRes val labelResId: Int) {
    ID(R.string.sort_id),
    TITLE(R.string.sort_title),
    ALBUM(R.string.sort_album),
    ARTIST(R.string.sort_artist),
    DURATION(R.string.sort_duration),
    FAVOURITES(R.string.sort_favourites),
    RATING(R.string.sort_rating),
    RECENTLY_ADDED(R.string.sort_recently_added),
    RECENTLY_PLAYED(R.string.sort_recently_played),
    MOST_PLAYED(R.string.sort_most_played),
    YEAR(R.string.sort_year);

    val label: String get() = name
}

fun List<TrackItem>.sortWithOption(option: SongSortOption, ascending: Boolean = true): List<TrackItem> {
    val sorted = when (option) {
        SongSortOption.ID -> return if (ascending) this else this.reversed()
        SongSortOption.TITLE -> this.sortedWith(
            compareBy<TrackItem> { it.title.lowercase() }
                .thenBy { it.album?.lowercase().orEmpty() }
                .thenBy { it.discNumber ?: 1 }
                .thenBy { it.trackNumber ?: 0 }
        )
        SongSortOption.ALBUM -> this.sortedWith(
            compareBy<TrackItem> { it.album?.lowercase().orEmpty() }
                .thenBy { it.discNumber ?: 1 }
                .thenBy { it.trackNumber ?: 0 }
                .thenBy { it.title.lowercase() }
        )
        SongSortOption.ARTIST -> this.sortedWith(
            compareBy<TrackItem> { it.artist.lowercase() }
                .thenBy { it.album?.lowercase().orEmpty() }
                .thenBy { it.discNumber ?: 1 }
                .thenBy { it.trackNumber ?: 0 }
                .thenBy { it.title.lowercase() }
        )
        SongSortOption.DURATION -> this.sortedWith(
            compareBy<TrackItem> { it.durationSeconds }
                .thenBy { it.title.lowercase() }
        )
        SongSortOption.FAVOURITES -> this.sortedWith(
            compareByDescending<TrackItem> { it.isStarred }
                .thenBy { it.title.lowercase() }
        )
        SongSortOption.RATING -> this.sortedWith(
            compareByDescending<TrackItem> { it.userRating ?: 0 }
                .thenBy { it.title.lowercase() }
        )
        SongSortOption.RECENTLY_ADDED -> this.sortedWith(
            compareByDescending<TrackItem> { it.createdAt ?: 0L }
                .thenBy { it.title.lowercase() }
        )
        SongSortOption.RECENTLY_PLAYED -> this.sortedWith(
            compareByDescending<TrackItem> { it.playedAt ?: 0L }
                .thenBy { it.title.lowercase() }
        )
        SongSortOption.MOST_PLAYED -> this.sortedWith(
            compareByDescending<TrackItem> { it.playCount ?: 0L }
                .thenBy { it.title.lowercase() }
        )
        SongSortOption.YEAR -> this.sortedWith(
            compareBy<TrackItem> { it.year ?: 0 }
                .thenBy { it.album?.lowercase().orEmpty() }
                .thenBy { it.discNumber ?: 1 }
                .thenBy { it.trackNumber ?: 0 }
                .thenBy { it.title.lowercase() }
        )
    }
    return if (ascending) sorted else sorted.reversed()
}
