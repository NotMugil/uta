package com.notmugil.uta.data.repository

import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.AlbumDao
import com.notmugil.uta.data.db.ArtistDao
import com.notmugil.uta.data.db.GenreDao
import com.notmugil.uta.data.db.PlaylistDao
import com.notmugil.uta.data.db.SyncMetadataDao
import com.notmugil.uta.data.db.TrackDao
import com.notmugil.uta.data.db.toDomain
import com.notmugil.uta.data.sync.LibrarySyncEngine
import com.notmugil.uta.data.sync.MutationManager
import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.GenreItem
import com.notmugil.uta.domain.model.PlaylistItem
import com.notmugil.uta.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val subsonicRepository: SubsonicRepository,
    private val syncEngine: LibrarySyncEngine,
    private val mutationManager: MutationManager,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val artistDao: ArtistDao,
    private val playlistDao: PlaylistDao,
    private val genreDao: GenreDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val networkMonitorProvider: Provider<com.notmugil.uta.data.NetworkMonitor>
) {
    private val serverId: String
        get() = subsonicRepository.currentServerId

    private val isOffline: Boolean
        get() = try {
            networkMonitorProvider.get().isOfflineModeActive.value
        } catch (_: Exception) {
            false
        }

    fun getLastFullSyncFlow(): Flow<Long?> {
        return syncMetadataDao.getSyncMetaFlow("full_catalog_sync", serverId)
            .map { it?.lastSyncedAt }
    }

    fun getRecentlyAddedAlbumsFlow(limit: Int = 20): Flow<List<AlbumItem>> {
        return albumDao.getRecentlyAddedAlbumsFlow(serverId, limit)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getMostPlayedAlbumsFlow(limit: Int = 20): Flow<List<AlbumItem>> {
        return albumDao.getMostPlayedAlbumsFlow(serverId, limit)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getRecentlyPlayedAlbumsFlow(limit: Int = 20): Flow<List<AlbumItem>> {
        return albumDao.getRecentlyPlayedAlbumsFlow(serverId, limit)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getMostPlayedTracksFlow(limit: Int = 20): Flow<List<TrackItem>> {
        return trackDao.getMostPlayedTracksFlow(serverId, limit)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getRandomAlbumsFlow(limit: Int = 20): Flow<List<AlbumItem>> {
        return albumDao.getRandomAlbumsFlow(serverId, limit)
            .map { list -> list.map { it.toDomain() } }
    }

    suspend fun getRandomAlbums(limit: Int = 20): List<AlbumItem> {
        return albumDao.getRandomAlbums(serverId, limit).map { it.toDomain() }
    }

    fun getRandomTracksFlow(limit: Int = 20): Flow<List<TrackItem>> {
        return trackDao.getRandomTracksFlow(serverId, limit)
            .map { list -> list.map { it.toDomain() } }
    }

    suspend fun getRandomTracks(limit: Int = 20): List<TrackItem> {
        return trackDao.getRandomTracks(serverId, limit).map { it.toDomain() }
    }

    fun getAlbumsFlow(): Flow<List<AlbumItem>> {
        return albumDao.getAlbumsFlow(serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getAlbumFlow(id: String): Flow<AlbumItem?> {
        return albumDao.getAlbumFlow(id, serverId)
            .map { it?.toDomain() }
    }

    fun getTracksForAlbumFlow(albumId: String): Flow<List<TrackItem>> {
        return trackDao.getTracksForAlbumFlow(albumId, serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getTracksFlow(): Flow<List<TrackItem>> {
        return trackDao.getTracksFlow(serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getDownloadedTracksFlow(): Flow<List<TrackItem>> {
        return trackDao.getDownloadedTracksFlow(serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getArtistsFlow(): Flow<List<ArtistItem>> {
        return artistDao.getArtistsFlow(serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getPlaylistsFlow(): Flow<List<PlaylistItem>> {
        return playlistDao.getPlaylistsFlow(serverId)
            .map { list ->
                list.map { entity ->
                    val info = subsonicRepository.getNavidromePlaylistInfoCached(entity.id)
                    entity.toDomain(info)
                }
            }
    }

    fun getPlaylistFlow(id: String): Flow<PlaylistItem?> {
        return playlistDao.getPlaylistFlow(id, serverId)
            .map { entity ->
                val info = subsonicRepository.getNavidromePlaylistInfoCached(id)
                entity?.toDomain(info)
            }
    }

    fun getTracksForPlaylistFlow(playlistId: String): Flow<List<TrackItem>> {
        return playlistDao.getTracksForPlaylistFlow(playlistId, serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getAlbumsForGenreFlow(genre: String): Flow<List<AlbumItem>> {
        return albumDao.getAlbumsByGenreFlow(genre, serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getTracksForGenreFlow(genre: String): Flow<List<TrackItem>> {
        return trackDao.getTracksByGenreFlow(genre, serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getGenresFlow(): Flow<List<GenreItem>> {
        return genreDao.getGenresFlow(serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getStarredAlbumsFlow(): Flow<List<AlbumItem>> {
        return albumDao.getStarredAlbumsFlow(serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getStarredArtistsFlow(): Flow<List<ArtistItem>> {
        return artistDao.getStarredArtistsFlow(serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    fun getStarredTracksFlow(): Flow<List<TrackItem>> {
        return trackDao.getStarredTracksFlow(serverId)
            .map { list -> list.map { it.toDomain() } }
    }

    suspend fun getTrack(trackId: String): TrackItem? = trackDao.getTrack(trackId, serverId)?.toDomain()
    suspend fun getAlbum(albumId: String): AlbumItem? = albumDao.getAlbum(albumId, serverId)?.toDomain()
    suspend fun getArtist(artistId: String): ArtistItem? = artistDao.getArtist(artistId, serverId)?.toDomain()
    suspend fun getPlaylist(playlistId: String): PlaylistItem? = playlistDao.getPlaylist(playlistId, serverId)?.toDomain()

    suspend fun toggleTrackStarred(trackId: String): Boolean = mutationManager.toggleTrackStarred(trackId)
    suspend fun setTrackStarred(trackId: String, starred: Boolean) = mutationManager.setTrackStarred(trackId, starred)

    suspend fun toggleAlbumStarred(albumId: String): Boolean = mutationManager.toggleAlbumStarred(albumId)
    suspend fun setAlbumStarred(albumId: String, starred: Boolean) = mutationManager.setAlbumStarred(albumId, starred)

    suspend fun toggleArtistStarred(artistId: String): Boolean = mutationManager.toggleArtistStarred(artistId)
    suspend fun setArtistStarred(artistId: String, starred: Boolean) = mutationManager.setArtistStarred(artistId, starred)

    suspend fun setRating(targetId: String, targetType: String, rating: Int) = mutationManager.setRating(targetId, targetType, rating)

    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Result<PlaylistItem> =
        mutationManager.createPlaylist(name, songIds)

    suspend fun addTracksToPlaylist(playlistId: String, songIds: List<String>): Result<Unit> =
        mutationManager.addTracksToPlaylist(playlistId, songIds)

    suspend fun removeTrackFromPlaylist(playlistId: String, songIndexToRemove: Int): Result<Unit> =
        mutationManager.removeTrackFromPlaylist(playlistId, songIndexToRemove)

    suspend fun setPlaylistSongs(playlistId: String, songIds: List<String>): Result<Unit> =
        mutationManager.setPlaylistSongs(playlistId, songIds)

    suspend fun deletePlaylist(playlistId: String): Result<Unit> =
        mutationManager.deletePlaylist(playlistId)

    suspend fun updatePlaylistMeta(playlistId: String, name: String, comment: String? = null, isPublic: Boolean? = null): Result<Unit> =
        mutationManager.updatePlaylistMeta(playlistId, name, comment, isPublic)

    fun triggerSync(force: Boolean = false) {
        syncEngine.triggerSync(force)
        mutationManager.flushOutbox()
    }

    suspend fun fetchAlbumTracks(albumId: String): Result<List<TrackItem>> {
        return syncEngine.fetchAlbumTracks(albumId)
    }

    suspend fun fetchPlaylistTracks(playlistId: String): Result<List<TrackItem>> {
        return syncEngine.fetchPlaylistTracks(playlistId)
    }

    suspend fun clearCache() {
        syncEngine.clearServerData(serverId)
    }

    suspend fun getShareContent(
        type: String,
        id: String,
        title: String,
        artist: String? = null
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = subsonicRepository.currentServerUrl ?: ""
        val cleanUrl = if (baseUrl.isNotBlank()) {
            var raw = baseUrl.trim().trimEnd('/')
            if (raw.endsWith("/rest", ignoreCase = true)) {
                raw = raw.substring(0, raw.length - 5).trimEnd('/')
            }
            raw
        } else ""

        val shareLink = when (type) {
            "TRACK" -> {
                val desc = if (artist != null) "$title by $artist" else title
                subsonicRepository.createShareLink(id, desc)
                    ?: if (cleanUrl.isNotBlank()) "$cleanUrl/#/song/$id" else null
            }
            "ALBUM" -> {
                val desc = if (artist != null) "$title by $artist" else title
                subsonicRepository.createShareLink(id, desc)
                    ?: if (cleanUrl.isNotBlank()) "$cleanUrl/#/album/$id" else null
            }
            "PLAYLIST" -> {
                subsonicRepository.createShareLink(id, title)
                    ?: if (cleanUrl.isNotBlank()) "$cleanUrl/#/playlist/$id" else null
            }
            "ARTIST" -> {
                if (cleanUrl.isNotBlank()) "$cleanUrl/#/artist/$id" else null
            }
            else -> null
        }

        when (type) {
            "TRACK" -> {
                val artistPrefix = if (!artist.isNullOrBlank()) " • $artist" else ""
                if (shareLink != null) "$title$artistPrefix\n$shareLink" else "$title$artistPrefix"
            }
            "ALBUM" -> {
                val artistPrefix = if (!artist.isNullOrBlank()) " • $artist" else ""
                if (shareLink != null) "$title$artistPrefix\n$shareLink" else "$title$artistPrefix"
            }
            "PLAYLIST" -> {
                if (shareLink != null) "$title\n$shareLink" else "Playlist: $title"
            }
            "ARTIST" -> {
                if (shareLink != null) "$title\n$shareLink" else "Artist: $title"
            }
            else -> title
        }
    }

    suspend fun search(query: String): SearchResults {
        val clean = query.trim()
        if (clean.isBlank()) return SearchResults()
        timber.log.Timber.d("[Search] Searching locally (clean='$clean')")

        val localAlbums = albumDao.searchAlbums(clean, serverId, limit = 20).map { it.toDomain() }
        val localArtists = artistDao.searchArtists(clean, serverId, limit = 15).map { it.toDomain() }
        val localTracks = trackDao.searchTracks(clean, serverId, limit = 30).map { it.toDomain() }

        if (isOffline) {
            timber.log.Timber.d("[Search] Offline mode active: returning local Room results for '$clean'")
            return SearchResults(
                albums = localAlbums,
                artists = localArtists,
                tracks = localTracks
            )
        }

        return try {
            val remote = subsonicRepository.execute { client ->
                client.search(
                    query = clean,
                    artistCount = 15,
                    albumCount = 20,
                    songCount = 30
                )
            }
            val remoteAlbums = remote.albums.map { it.toDomain() }
            val remoteArtists = remote.artists.map { it.toDomain() }
            val remoteTracks = remote.songs.map { it.toDomain() }

            val mergedAlbums = (localAlbums + remoteAlbums).distinctBy { it.id }
            val mergedArtists = (localArtists + remoteArtists).distinctBy { it.id }
            val mergedTracks = (localTracks + remoteTracks).distinctBy { it.id }

            timber.log.Timber.d("[Search] Merged results for '$clean': ${mergedTracks.size} tracks, ${mergedAlbums.size} albums, ${mergedArtists.size} artists")
            SearchResults(
                albums = mergedAlbums,
                artists = mergedArtists,
                tracks = mergedTracks
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            timber.log.Timber.w(e, "[Search] Remote search failed for '$clean', using local Room results")
            SearchResults(
                albums = localAlbums,
                artists = localArtists,
                tracks = localTracks
            )
        }
    }

    suspend fun fetchArtistDetails(artistId: String): ArtistDetailResult {
        Timber.d("[Artist] Fetching details for artistId=$artistId")
        val localArtist = artistDao.getArtist(artistId, serverId)?.toDomain()
        val localAlbums = if (localArtist != null) {
            albumDao.getAlbumsByArtist(artistId, localArtist.name, serverId).map { it.toDomain() }
        } else {
            albumDao.getAlbumsByArtist(artistId, serverId).map { it.toDomain() }
        }
        val localTracks = if (localArtist != null) {
            trackDao.getTracksByArtistName(localArtist.name, serverId).map { it.toDomain() }
        } else emptyList()

        if (isOffline) {
            Timber.d("[Artist] Offline mode active: returning local details for artist $artistId")
            return ArtistDetailResult(
                artist = localArtist,
                albums = localAlbums,
                topTracks = localTracks.sortedByDescending { it.playCount }.take(10)
            )
        }

        return try {
            val remoteArtist = try {
                subsonicRepository.execute { client ->
                    client.getArtist(artistId)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.d("[Artist] subsonic-kotlin getArtist failed (${e.message}), falling back to sanitized raw fetch")
                subsonicRepository.getArtistRaw(artistId)
            }
            val artistItem = remoteArtist.toDomain()
            val remoteAlbums = remoteArtist.album.map { album ->
                val domainAlbum = album.toDomain()
                domainAlbum.copy(
                    artist = if (domainAlbum.artist.isBlank() || domainAlbum.artist == "Unknown Artist") artistItem.name else domainAlbum.artist,
                    artistId = if (domainAlbum.artistId.isNullOrBlank()) artistItem.id else domainAlbum.artistId
                )
            }
            val mergedAlbums = (localAlbums + remoteAlbums)
                .distinctBy { it.id }
                .filter { it.artistId == artistId || it.artist.equals(artistItem.name, ignoreCase = true) || it.artist.isBlank() || it.artist == "Unknown Artist" }

            val topSongs = try {
                val remoteTop = subsonicRepository.getTopSongs(artistItem.name, count = 20).map { it.toDomain() }
                if (remoteTop.isNotEmpty()) {
                    remoteTop
                } else {
                    localTracks.sortedByDescending { it.playCount }.take(10)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.d("[Artist] Remote getTopSongs unavailable, using local tracks: ${e.message}")
                localTracks.sortedByDescending { it.playCount }.take(10)
            }

            val biography = try {
                subsonicRepository.execute { client ->
                    client.getArtistInfo(artistId).biography?.trim()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.d("[Artist] Remote getArtistInfo unavailable: ${e.message}")
                null
            }

            Timber.d("[Artist] Loaded artist '${artistItem.name}' with ${mergedAlbums.size} albums, ${topSongs.size} top tracks")
            ArtistDetailResult(
                artist = artistItem,
                albums = mergedAlbums,
                topTracks = topSongs,
                biography = biography
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "[Artist] Remote fetch failed for $artistId, using local cache")
            ArtistDetailResult(
                artist = localArtist,
                albums = localAlbums,
                topTracks = localTracks.take(10)
            )
        }
    }

    suspend fun fetchGenreDetails(genreName: String): GenreDetailResult {
        Timber.d("[Genre] Fetching details for genreName=$genreName")
        val localAlbums = albumDao.getAlbumsByGenre(genreName, serverId).map { it.toDomain() }
        val localTracks = trackDao.getTracksByGenre(genreName, serverId).map { it.toDomain() }

        if (isOffline) {
            Timber.d("[Genre] Offline mode active: returning local details for genre $genreName")
            return GenreDetailResult(
                genreName = genreName,
                albums = localAlbums,
                songs = localTracks
            )
        }

        return try {
            val remoteAlbums = try {
                subsonicRepository.execute { client ->
                    client.getAlbums(
                        type = dev.zt64.subsonic.api.model.AlbumListType.ByGenre(genreName),
                        size = 50
                    ).map { it.toDomain() }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.d("[Genre] Remote getAlbumsByGenre unavailable: ${e.message}")
                emptyList()
            }

            val remoteSongs = try {
                subsonicRepository.execute { client ->
                    client.getSongs(
                        genre = genreName,
                        count = 100
                    ).map { it.toDomain() }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.d("[Genre] Remote getSongsByGenre unavailable: ${e.message}")
                emptyList()
            }

            val mergedAlbums = (localAlbums + remoteAlbums).distinctBy { it.id }
            val mergedSongs = (localTracks + remoteSongs).distinctBy { it.id }

            GenreDetailResult(
                genreName = genreName,
                albums = mergedAlbums,
                songs = mergedSongs
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "[Genre] Remote fetch failed for $genreName, using local cache")
            GenreDetailResult(
                genreName = genreName,
                albums = localAlbums,
                songs = localTracks
            )
        }
    }
}

data class SearchResults(
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val tracks: List<TrackItem> = emptyList()
)

data class ArtistDetailResult(
    val artist: ArtistItem? = null,
    val albums: List<AlbumItem> = emptyList(),
    val topTracks: List<TrackItem> = emptyList(),
    val biography: String? = null
)

data class GenreDetailResult(
    val genreName: String,
    val albums: List<AlbumItem> = emptyList(),
    val songs: List<TrackItem> = emptyList()
)
