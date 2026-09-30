package com.notmugil.uta.ui.shared.actionsheet

import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.domain.model.TrackItem

sealed interface MediaTarget {
    data class TrackTarget(
        val track: TrackItem,
        val playlistId: String? = null,
        val playlistTrackIndex: Int? = null,
        val onRemoveFromPlaylist: (() -> Unit)? = null
    ) : MediaTarget

    data class AlbumTarget(val album: AlbumItem) : MediaTarget

    data class PlaylistTarget(val playlist: PlaylistItem) : MediaTarget

    data class ArtistTarget(val artist: ArtistItem) : MediaTarget
}
