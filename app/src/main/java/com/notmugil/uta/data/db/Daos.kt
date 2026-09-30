package com.notmugil.uta.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Upsert
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Upsert
    suspend fun upsertAlbum(album: AlbumEntity)

    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY name COLLATE NOCASE ASC")
    fun getAlbumsFlow(serverId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAlbums(serverId: String): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE id = :id AND serverId = :serverId LIMIT 1")
    suspend fun getAlbum(id: String, serverId: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE id = :id AND serverId = :serverId LIMIT 1")
    fun getAlbumFlow(id: String, serverId: String): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE artistId = :artistId AND serverId = :serverId ORDER BY year DESC, name ASC")
    suspend fun getAlbumsByArtist(artistId: String, serverId: String): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE genre LIKE :genre AND serverId = :serverId ORDER BY year DESC, name ASC")
    suspend fun getAlbumsByGenre(genre: String, serverId: String): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE genre LIKE :genre AND serverId = :serverId ORDER BY year DESC, name ASC")
    fun getAlbumsByGenreFlow(genre: String, serverId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY createdAt DESC, starredAt DESC LIMIT :limit")
    fun getRecentlyAddedAlbumsFlow(serverId: String, limit: Int = 20): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY RANDOM() LIMIT :limit")
    fun getRandomAlbumsFlow(serverId: String, limit: Int = 20): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomAlbums(serverId: String, limit: Int = 20): List<AlbumEntity>

    @Query(
        "SELECT * FROM albums WHERE serverId = :serverId AND (name LIKE '%' || :query || '%' OR artistName LIKE '%' || :query || '%') ORDER BY name COLLATE NOCASE ASC LIMIT :limit"
    )
    suspend fun searchAlbums(query: String, serverId: String, limit: Int = 20): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE serverId = :serverId AND starredAt IS NOT NULL ORDER BY starredAt DESC")
    fun getStarredAlbumsFlow(serverId: String): Flow<List<AlbumEntity>>

    @Query("UPDATE albums SET starredAt = :starredAt WHERE id = :id AND serverId = :serverId")
    suspend fun setAlbumStarred(id: String, serverId: String, starredAt: Long?)

    @Query("DELETE FROM albums WHERE serverId = :serverId AND syncedAt < :stamp")
    suspend fun deleteStale(serverId: String, stamp: Long)

    @Query("DELETE FROM albums WHERE serverId = :serverId")
    suspend fun clearServerAlbums(serverId: String)
}

@Dao
interface TrackDao {
    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query(
        "SELECT * FROM tracks WHERE albumId = :albumId AND serverId = :serverId ORDER BY COALESCE(discNumber, 1) ASC, COALESCE(trackNumber, 0) ASC, title COLLATE NOCASE ASC"
    )
    suspend fun getTracksForAlbum(albumId: String, serverId: String): List<TrackEntity>

    @Query(
        "SELECT * FROM tracks WHERE albumId = :albumId AND serverId = :serverId ORDER BY COALESCE(discNumber, 1) ASC, COALESCE(trackNumber, 0) ASC, title COLLATE NOCASE ASC"
    )
    fun getTracksForAlbumFlow(albumId: String, serverId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id AND serverId = :serverId LIMIT 1")
    suspend fun getTrack(id: String, serverId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids) AND serverId = :serverId")
    suspend fun getTracksByIds(ids: List<String>, serverId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackGlobal(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE serverId = :serverId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomTracks(serverId: String, limit: Int = 20): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId ORDER BY RANDOM() LIMIT :limit")
    fun getRandomTracksFlow(serverId: String, limit: Int = 20): Flow<List<TrackEntity>>

    @Query(
        "SELECT * FROM tracks WHERE serverId = :serverId AND (title LIKE '%' || :query || '%' OR artistName LIKE '%' || :query || '%' OR albumTitle LIKE '%' || :query || '%') ORDER BY title COLLATE NOCASE ASC LIMIT :limit"
    )
    suspend fun searchTracks(query: String, serverId: String, limit: Int = 50): List<TrackEntity>

    @Query(
        "SELECT * FROM tracks WHERE artistName LIKE :artistName AND serverId = :serverId ORDER BY title COLLATE NOCASE ASC"
    )
    suspend fun getTracksByArtistName(artistName: String, serverId: String): List<TrackEntity>

    @Query(
        "SELECT * FROM tracks WHERE genre LIKE :genre AND serverId = :serverId ORDER BY title COLLATE NOCASE ASC"
    )
    suspend fun getTracksByGenre(genre: String, serverId: String): List<TrackEntity>

    @Query(
        "SELECT * FROM tracks WHERE genre LIKE :genre AND serverId = :serverId ORDER BY title COLLATE NOCASE ASC"
    )
    fun getTracksByGenreFlow(genre: String, serverId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId AND starredAt IS NOT NULL ORDER BY starredAt DESC")
    fun getStarredTracksFlow(serverId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId ORDER BY title COLLATE NOCASE ASC")
    fun getTracksFlow(serverId: String): Flow<List<TrackEntity>>

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN local_media lm ON t.id = lm.trackId AND t.serverId = lm.serverId WHERE t.serverId = :serverId ORDER BY t.title COLLATE NOCASE ASC"
    )
    fun getDownloadedTracksFlow(serverId: String): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET starredAt = :starredAt WHERE id = :id AND serverId = :serverId")
    suspend fun setTrackStarred(id: String, serverId: String, starredAt: Long?)

    @Query("DELETE FROM tracks WHERE serverId = :serverId")
    suspend fun clearServerTracks(serverId: String)
}

@Dao
interface ArtistDao {
    @Upsert
    suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Query("SELECT * FROM artists WHERE serverId = :serverId ORDER BY name COLLATE NOCASE ASC")
    fun getArtistsFlow(serverId: String): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE serverId = :serverId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getArtists(serverId: String): List<ArtistEntity>

    @Query("SELECT * FROM artists WHERE serverId = :serverId AND starredAt IS NOT NULL ORDER BY starredAt DESC")
    fun getStarredArtistsFlow(serverId: String): Flow<List<ArtistEntity>>

    @Query("UPDATE artists SET starredAt = :starredAt WHERE id = :id AND serverId = :serverId")
    suspend fun setArtistStarred(id: String, serverId: String, starredAt: Long?)

    @Query("SELECT * FROM artists WHERE id = :id AND serverId = :serverId LIMIT 1")
    suspend fun getArtist(id: String, serverId: String): ArtistEntity?

    @Query(
        "SELECT * FROM artists WHERE serverId = :serverId AND name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE ASC LIMIT :limit"
    )
    suspend fun searchArtists(query: String, serverId: String, limit: Int = 20): List<ArtistEntity>

    @Query("DELETE FROM artists WHERE serverId = :serverId AND syncedAt < :stamp")
    suspend fun deleteStale(serverId: String, stamp: Long)

    @Query("DELETE FROM artists WHERE serverId = :serverId")
    suspend fun clearServerArtists(serverId: String)
}

@Dao
interface PlaylistDao {
    @Upsert
    suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE serverId = :serverId ORDER BY name COLLATE NOCASE ASC")
    fun getPlaylistsFlow(serverId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE serverId = :serverId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getPlaylists(serverId: String): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id AND serverId = :serverId LIMIT 1")
    suspend fun getPlaylist(id: String, serverId: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id AND serverId = :serverId LIMIT 1")
    fun getPlaylistFlow(id: String, serverId: String): Flow<PlaylistEntity?>

    @Upsert
    suspend fun upsertPlaylistTracks(crossRefs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND serverId = :serverId")
    suspend fun clearPlaylistTracks(playlistId: String, serverId: String)

    @Transaction
    suspend fun replacePlaylistTracks(playlistId: String, serverId: String, crossRefs: List<PlaylistTrackCrossRef>) {
        clearPlaylistTracks(playlistId, serverId)
        upsertPlaylistTracks(crossRefs)
    }

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON t.id = pt.trackId AND t.serverId = pt.serverId WHERE pt.playlistId = :playlistId AND pt.serverId = :serverId ORDER BY pt.sortOrder ASC"
    )
    suspend fun getTracksForPlaylist(playlistId: String, serverId: String): List<TrackEntity>

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON t.id = pt.trackId AND t.serverId = pt.serverId WHERE pt.playlistId = :playlistId AND pt.serverId = :serverId ORDER BY pt.sortOrder ASC"
    )
    fun getTracksForPlaylistFlow(playlistId: String, serverId: String): Flow<List<TrackEntity>>

    @Query("DELETE FROM playlists WHERE id = :id AND serverId = :serverId")
    suspend fun deletePlaylist(id: String, serverId: String)

    @Transaction
    suspend fun deletePlaylistAndTracks(playlistId: String, serverId: String) {
        deletePlaylist(playlistId, serverId)
        clearPlaylistTracks(playlistId, serverId)
    }

    @Query("UPDATE playlists SET name = :name, comment = :comment, isPublic = :isPublic WHERE id = :id AND serverId = :serverId")
    suspend fun updatePlaylistMeta(id: String, serverId: String, name: String, comment: String?, isPublic: Boolean)

    @Query("DELETE FROM playlists WHERE serverId = :serverId AND syncedAt < :stamp")
    suspend fun deleteStale(serverId: String, stamp: Long)

    @Query("DELETE FROM playlists WHERE serverId = :serverId")
    suspend fun clearServerPlaylists(serverId: String)
}

@Dao
interface GenreDao {
    @Upsert
    suspend fun upsertGenres(genres: List<GenreEntity>)

    @Query("SELECT * FROM genres WHERE serverId = :serverId ORDER BY name COLLATE NOCASE ASC")
    fun getGenresFlow(serverId: String): Flow<List<GenreEntity>>

    @Query("SELECT * FROM genres WHERE serverId = :serverId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getGenres(serverId: String): List<GenreEntity>

    @Query("DELETE FROM genres WHERE serverId = :serverId AND syncedAt < :stamp")
    suspend fun deleteStale(serverId: String, stamp: Long)

    @Query("DELETE FROM genres WHERE serverId = :serverId")
    suspend fun clearServerGenres(serverId: String)
}

@Dao
interface LocalMediaDao {
    @Upsert
    suspend fun upsertLocalMedia(media: LocalMediaEntity)

    @Query("SELECT * FROM local_media WHERE trackId = :trackId AND serverId = :serverId LIMIT 1")
    suspend fun getLocalMedia(trackId: String, serverId: String): LocalMediaEntity?

    @Query("SELECT * FROM local_media WHERE serverId = :serverId")
    fun getLocalMediaFlow(serverId: String): Flow<List<LocalMediaEntity>>

    @Query("SELECT * FROM local_media WHERE serverId = :serverId")
    suspend fun getLocalMediaForServer(serverId: String): List<LocalMediaEntity>

    @Query("SELECT trackId FROM local_media WHERE serverId = :serverId")
    fun getDownloadedTrackIdsFlow(serverId: String): Flow<List<String>>

    @Query("SELECT trackId FROM local_media WHERE serverId = :serverId")
    suspend fun getDownloadedTrackIds(serverId: String): List<String>

    @Query(
        "SELECT t.albumId " +
        "FROM tracks t " +
        "INNER JOIN local_media lm ON t.id = lm.trackId AND t.serverId = lm.serverId " +
        "WHERE t.serverId = :serverId AND t.albumId IS NOT NULL " +
        "GROUP BY t.albumId " +
        "HAVING COUNT(DISTINCT t.id) = (" +
        "    SELECT COUNT(*) FROM tracks t_all WHERE t_all.albumId = t.albumId AND t_all.serverId = :serverId" +
        ") AND COUNT(DISTINCT t.id) > 0"
    )
    fun getDownloadedAlbumIdsFlow(serverId: String): Flow<List<String>>

    @Query(
        "SELECT pt.playlistId " +
        "FROM playlist_tracks pt " +
        "INNER JOIN local_media lm ON pt.trackId = lm.trackId AND pt.serverId = lm.serverId " +
        "WHERE pt.serverId = :serverId " +
        "GROUP BY pt.playlistId " +
        "HAVING COUNT(DISTINCT pt.trackId) = (" +
        "    SELECT COUNT(DISTINCT pt_all.trackId) FROM playlist_tracks pt_all WHERE pt_all.playlistId = pt.playlistId AND pt_all.serverId = :serverId" +
        ") AND COUNT(DISTINCT pt.trackId) > 0"
    )
    fun getDownloadedPlaylistIdsFlow(serverId: String): Flow<List<String>>

    @Query("DELETE FROM local_media WHERE trackId = :trackId AND serverId = :serverId")
    suspend fun deleteLocalMedia(trackId: String, serverId: String)

    @Query("DELETE FROM local_media WHERE serverId = :serverId")
    suspend fun clearServerLocalMedia(serverId: String)

    @Upsert
    suspend fun upsertScope(scope: DownloadScopeEntity)

    @Query("SELECT * FROM download_scopes WHERE serverId = :serverId AND scopeId = :scopeId")
    suspend fun getScopesForScopeId(scopeId: String, serverId: String): List<DownloadScopeEntity>

    @Query("SELECT COUNT(*) FROM download_scopes WHERE serverId = :serverId AND trackId = :trackId")
    suspend fun countScopesForTrack(trackId: String, serverId: String): Int

    @Query("DELETE FROM download_scopes WHERE serverId = :serverId AND scopeId = :scopeId")
    suspend fun deleteScopesForScopeId(scopeId: String, serverId: String)

    @Query("DELETE FROM download_scopes WHERE serverId = :serverId AND trackId = :trackId")
    suspend fun deleteScopesForTrack(trackId: String, serverId: String)

    @Query("DELETE FROM download_scopes WHERE serverId = :serverId AND trackId = :trackId AND scopeId = :scopeId")
    suspend fun deleteScope(trackId: String, scopeId: String, serverId: String)

    @Query("DELETE FROM download_scopes WHERE serverId = :serverId")
    suspend fun clearScopesForServer(serverId: String)

    @Upsert
    suspend fun upsertQueueItem(item: DownloadQueueEntity)

    @Query("SELECT * FROM download_queue WHERE serverId = :serverId ORDER BY queuedAt ASC")
    suspend fun getQueueForServer(serverId: String): List<DownloadQueueEntity>

    @Query("UPDATE download_queue SET status = :status, lastError = :error WHERE trackId = :trackId AND serverId = :serverId")
    suspend fun updateQueueStatus(trackId: String, serverId: String, status: String, error: String? = null)

    @Query("UPDATE download_queue SET status = 'QUEUED' WHERE serverId = :serverId AND status = 'DOWNLOADING'")
    suspend fun resetDownloadingToQueued(serverId: String)

    @Query("DELETE FROM download_queue WHERE trackId = :trackId AND serverId = :serverId")
    suspend fun removeFromQueue(trackId: String, serverId: String)

    @Query("DELETE FROM download_queue WHERE serverId = :serverId")
    suspend fun clearQueueForServer(serverId: String)
}

@Dao
interface SyncMetadataDao {
    @Upsert
    suspend fun upsertSyncMeta(meta: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE syncKey = :key AND serverId = :serverId LIMIT 1")
    fun getSyncMetaFlow(key: String, serverId: String): Flow<SyncMetadataEntity?>
}
