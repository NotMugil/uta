package com.notmugil.uta.data.sync

import androidx.room.withTransaction
import com.notmugil.uta.data.AuthState
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.AlbumDao
import com.notmugil.uta.data.db.ArtistDao
import com.notmugil.uta.data.db.GenreDao
import com.notmugil.uta.data.db.PlaylistDao
import com.notmugil.uta.data.db.PlaylistTrackCrossRef
import com.notmugil.uta.data.db.SyncMetadataDao
import com.notmugil.uta.data.db.SyncMetadataEntity
import com.notmugil.uta.data.db.TrackDao
import com.notmugil.uta.data.db.UtaDatabase
import com.notmugil.uta.data.db.toDomain
import com.notmugil.uta.data.db.toEntity
import com.notmugil.uta.domain.model.TrackItem
import dev.zt64.subsonic.api.model.AlbumListType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import timber.log.Timber

sealed interface SyncState {
    data object Idle : SyncState
    data class Syncing(val stage: String) : SyncState
    data class Error(val message: String) : SyncState
}

@Singleton
class LibrarySyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subsonicRepository: SubsonicRepository,
    private val database: UtaDatabase,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val artistDao: ArtistDao,
    private val playlistDao: PlaylistDao,
    private val genreDao: GenreDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val json: Json,
    private val okHttpClient: OkHttpClient,
    private val networkMonitorProvider: Provider<com.notmugil.uta.data.NetworkMonitor>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var currentSyncJob: Job? = null

    private val isOffline: Boolean
        get() = try {
            networkMonitorProvider.get().isOfflineModeActive.value
        } catch (_: Exception) {
            false
        }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        scope.launch {
            subsonicRepository.authState.collect { authState ->
                when (authState) {
                    is AuthState.Authenticated -> {
                        Timber.d("[Sync] User authenticated, triggering background sync")
                        triggerSync(force = false)
                    }
                    is AuthState.Unauthenticated -> {
                        Timber.d("[Sync] User unauthenticated, cancelling active sync")
                        cancelAndJoin()
                        _syncState.value = SyncState.Idle
                    }
                    is AuthState.Loading -> {}
                }
            }
        }
    }

    fun triggerSync(force: Boolean = false) {
        if (currentSyncJob?.isActive == true && !force) {
            Timber.d("[Sync] Sync already active and force=false; skipping trigger")
            return
        }
        currentSyncJob = scope.launch {
            syncLibrary(force)
        }
    }

    suspend fun cancelAndJoin() {
        currentSyncJob?.cancel()
        currentSyncJob?.join()
        currentSyncJob = null
        _syncState.value = SyncState.Idle
    }

    suspend fun clearServerData(serverId: String) {
        Timber.i("[Sync] Clearing local database cache for serverId=$serverId")
        try {
            database.withTransaction {
                albumDao.clearServerAlbums(serverId)
                trackDao.clearServerTracks(serverId)
                artistDao.clearServerArtists(serverId)
                playlistDao.clearServerPlaylists(serverId)
                genreDao.clearServerGenres(serverId)
            }
            Timber.i("[Sync] Cleared local database cache successfully")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[Sync] Failed to clear server data for $serverId")
        }
    }

    suspend fun refreshPlaylists(): Result<Unit> {
        val client = subsonicRepository.currentClient
            ?: return Result.failure(IllegalStateException("No active session"))
        val serverId = subsonicRepository.currentServerId
        Timber.d("[Sync] Refreshing playlists metadata for serverId=$serverId")

        val remote = try {
            client.getPlaylists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[Sync] Failed to fetch playlists list from server")
            if (subsonicRepository.isNetworkOrTimeoutException(e)) {
                networkMonitorProvider.get().markServerUnreachable()
            }
            return Result.failure(e)
        }

        val stamp = System.currentTimeMillis()
        if (serverId != subsonicRepository.currentServerId) {
            Timber.w("[Sync] Session changed during playlists list fetch")
            return Result.failure(CancellationException("Session changed"))
        }

        try {
            subsonicRepository.fetchAllNavidromePlaylists()
        } catch (_: Exception) {}

        database.withTransaction {
            val entities = remote.map { it.toEntity(serverId, json, stamp) }
            if (entities.isNotEmpty()) {
                entities.chunked(250).forEach { chunk ->
                    playlistDao.upsertPlaylists(chunk)
                }
            }
            playlistDao.deleteStale(serverId, stamp)
        }
        Timber.d("[Sync] Upserted ${remote.size} playlists metadata into Room")

        val coverIds = remote.mapNotNull { it.coverArtId }
        if (coverIds.isNotEmpty()) {
            scope.launch { prefetchThumbnails(coverIds, serverId) }
        }

        // Pre-fetch track contents for up to top 10 playlists (starred / top)
        // Tracks for remaining playlists are hydrated on-demand when opened
        val prefetchTargets = remote.take(10)
        val semaphore = Semaphore(4)
        coroutineScope {
            prefetchTargets.forEach { pl ->
                launch {
                    semaphore.withPermit {
                        try {
                            if (serverId == subsonicRepository.currentServerId) {
                                val detail = try {
                                    client.getPlaylist(pl.id)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    subsonicRepository.getPlaylistRaw(pl.id)
                                }
                                val songs = detail.songs
                                val detailStamp = System.currentTimeMillis()
                                database.withTransaction {
                                    val trackEntities = songs.map { it.toEntity(serverId, json, detailStamp) }
                                    if (trackEntities.isNotEmpty()) {
                                        trackEntities.chunked(250).forEach { trackChunk ->
                                            trackDao.upsertTracks(trackChunk)
                                        }
                                    }
                                    playlistDao.upsertPlaylist(detail.toEntity(serverId, json, detailStamp))
                                    val crossRefs = songs.mapIndexed { index, song ->
                                        PlaylistTrackCrossRef(
                                            playlistId = pl.id,
                                            trackId = song.id,
                                            serverId = serverId,
                                            sortOrder = index
                                        )
                                    }
                                    playlistDao.replacePlaylistTracks(pl.id, serverId, crossRefs)
                                }
                                Timber.d("[Sync] Pre-cached ${songs.size} tracks for playlist '${pl.name}' (${pl.id})")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(e, "[Sync] Could not pre-fetch tracks for playlist '${pl.name}' (${pl.id})")
                            if (subsonicRepository.isNetworkOrTimeoutException(e)) {
                                networkMonitorProvider.get().markServerUnreachable()
                            }
                        }
                    }
                }
            }
        }

        return Result.success(Unit)
    }

    private val musicRootDir: File
        get() {
            val dir = context.getExternalFilesDir("music") ?: File(context.filesDir, "music")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private fun sanitize(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)
    }

    suspend fun prefetchThumbnails(coverArtIds: List<String>, serverId: String) = withContext(Dispatchers.IO) {
        if (isOffline || coverArtIds.isEmpty() || serverId.isBlank()) return@withContext
        val safeServerId = sanitize(serverId)
        val coversDir = File(musicRootDir, "$safeServerId/covers").apply { if (!exists()) mkdirs() }
        val semaphore = Semaphore(4)

        coroutineScope {
            coverArtIds.distinct().forEach { coverArtId ->
                if (coverArtId.isBlank()) return@forEach
                val safeCoverId = sanitize(coverArtId)
                val targetFile = File(coversDir, "$safeCoverId.jpg")
                if (targetFile.exists() && targetFile.length() > 0) return@forEach

                launch {
                    semaphore.withPermit {
                        try {
                            if (serverId != subsonicRepository.currentServerId || isOffline) return@withPermit
                            val coverUrl = subsonicRepository.getCoverArtUrl(coverArtId, size = 300) ?: return@withPermit
                            val request = Request.Builder().url(coverUrl).get().build()
                            val tempFile = File(coversDir, "$safeCoverId.part")
                            okHttpClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val body = response.body
                                    FileOutputStream(tempFile).use { out ->
                                        body.byteStream().copyTo(out)
                                        out.flush()
                                    }
                                    if (tempFile.length() > 0) {
                                        moveSafely(tempFile, targetFile)
                                    } else {
                                        tempFile.delete()
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (subsonicRepository.isNetworkOrTimeoutException(e)) {
                                networkMonitorProvider.get().markServerUnreachable()
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun fetchPlaylistTracks(playlistId: String): Result<List<TrackItem>> = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (isOffline) {
            val localTracks = playlistDao.getTracksForPlaylist(playlistId, serverId).map { it.toDomain() }
            Timber.d("[Sync] Offline mode active: returning ${localTracks.size} local tracks for playlist $playlistId")
            return@withContext Result.success(localTracks)
        }

        val client = subsonicRepository.currentClient
            ?: return@withContext Result.failure(IllegalStateException("No active session"))
        Timber.d("[Sync] Fetching playlist tracks for playlistId=$playlistId on serverId=$serverId")

        try {
            val playlist = try {
                client.getPlaylist(playlistId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("[Sync] client.getPlaylist parse failed for $playlistId, using raw fallback: ${e.message}")
                subsonicRepository.getPlaylistRaw(playlistId)
            }
            val songs = playlist.songs
            val stamp = System.currentTimeMillis()

            if (serverId != subsonicRepository.currentServerId) {
                Timber.w("[Sync] Session changed during playlist tracks fetch for $playlistId")
                return@withContext Result.failure(CancellationException("Session changed"))
            }

            database.withTransaction {
                val trackEntities = songs.map { it.toEntity(serverId, json, stamp) }
                if (trackEntities.isNotEmpty()) {
                    trackEntities.chunked(250).forEach { chunk ->
                        trackDao.upsertTracks(chunk)
                    }
                }
                playlistDao.upsertPlaylist(playlist.toEntity(serverId, json, stamp))
                val crossRefs = songs.mapIndexed { index, song ->
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = song.id,
                        serverId = serverId,
                        sortOrder = index
                    )
                }
                playlistDao.replacePlaylistTracks(playlistId, serverId, crossRefs)
            }
            Timber.d("[Sync] Successfully fetched and cached ${songs.size} tracks for playlist '${playlist.name}' ($playlistId)")
            Result.success(songs.map { it.toDomain() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[Sync] Failed to fetch playlist tracks for $playlistId")
            if (subsonicRepository.isNetworkOrTimeoutException(e)) {
                networkMonitorProvider.get().markServerUnreachable()
            }
            Result.failure(e)
        }
    }

    suspend fun refreshArtists(): Result<Unit> {
        val client = subsonicRepository.currentClient
            ?: return Result.failure(IllegalStateException("No active session"))
        val serverId = subsonicRepository.currentServerId
        Timber.d("[Sync] Refreshing artists for serverId=$serverId")

        val remote = try {
            client.getArtists().flatMap { it.artists }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[Sync] Failed to fetch artists from server")
            if (subsonicRepository.isNetworkOrTimeoutException(e)) {
                networkMonitorProvider.get().markServerUnreachable()
            }
            return Result.failure(e)
        }

        val stamp = System.currentTimeMillis()
        if (serverId != subsonicRepository.currentServerId) {
            Timber.w("[Sync] Session changed during artists fetch")
            return Result.failure(CancellationException("Session changed"))
        }

        database.withTransaction {
            val entities = remote.map { it.toEntity(serverId, json, stamp) }
            if (entities.isNotEmpty()) {
                entities.chunked(250).forEach { chunk ->
                    artistDao.upsertArtists(chunk)
                }
            }
            artistDao.deleteStale(serverId, stamp)
        }
        Timber.d("[Sync] Upserted ${remote.size} artists into Room")
        return Result.success(Unit)
    }

    suspend fun refreshAlbums(force: Boolean = false): Result<Unit> {
        val client = subsonicRepository.currentClient
            ?: return Result.failure(IllegalStateException("No active session"))
        val serverId = subsonicRepository.currentServerId
        val stamp = System.currentTimeMillis()
        Timber.d("[Sync] Refreshing albums (force=$force) for serverId=$serverId")

        if (force) {
            // Full paged sync - stream each page directly into Room
            var offset = 0
            val pageSize = 500
            var totalAlbums = 0

            while (true) {
                Timber.d("[Sync] Fetching album page offset=$offset, size=$pageSize")
                val page = try {
                    client.getAlbums(
                        type = AlbumListType.AlphabeticalByArtist,
                        size = pageSize,
                        offset = offset
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "[Sync] Failed to fetch album page at offset=$offset")
                    if (subsonicRepository.isNetworkOrTimeoutException(e)) {
                        networkMonitorProvider.get().markServerUnreachable()
                    }
                    return Result.failure(e)
                }

                if (serverId != subsonicRepository.currentServerId) {
                    Timber.w("[Sync] Session changed during paged album sync")
                    return Result.failure(CancellationException("Session changed"))
                }

                database.withTransaction {
                    val entities = page.map { it.toEntity(serverId, json, stamp) }
                    if (entities.isNotEmpty()) {
                        entities.chunked(250).forEach { chunk ->
                            albumDao.upsertAlbums(chunk)
                        }
                    }
                }

                val coverIds = page.mapNotNull { it.coverArtId }
                if (coverIds.isNotEmpty()) {
                    scope.launch { prefetchThumbnails(coverIds, serverId) }
                }

                totalAlbums += page.size
                Timber.d("[Sync] Persisted page of ${page.size} albums to Room (total: $totalAlbums)")
                if (page.size < pageSize) break
                offset += pageSize
            }

            if (serverId != subsonicRepository.currentServerId) {
                return Result.failure(CancellationException("Session changed"))
            }

            database.withTransaction {
                albumDao.deleteStale(serverId, stamp)
            }
            Timber.d("[Sync] Finished full album sync ($totalAlbums total) and pruned stale albums")
        } else {
            // Quick newest sync
            Timber.d("[Sync] Fetching 100 newest albums...")
            val newest = try {
                client.getAlbums(type = AlbumListType.Newest, size = 100)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Sync] Failed to fetch newest albums")
                if (subsonicRepository.isNetworkOrTimeoutException(e)) {
                    networkMonitorProvider.get().markServerUnreachable()
                }
                return Result.failure(e)
            }

            if (serverId != subsonicRepository.currentServerId) {
                Timber.w("[Sync] Session changed during newest albums sync")
                return Result.failure(CancellationException("Session changed"))
            }

            database.withTransaction {
                val entities = newest.map { it.toEntity(serverId, json, stamp) }
                if (entities.isNotEmpty()) {
                    entities.chunked(250).forEach { chunk ->
                        albumDao.upsertAlbums(chunk)
                    }
                }
            }
            Timber.d("[Sync] Saved ${newest.size} newest albums into Room")

            val coverIds = newest.mapNotNull { it.coverArtId }
            if (coverIds.isNotEmpty()) {
                scope.launch { prefetchThumbnails(coverIds, serverId) }
            }
        }

        return Result.success(Unit)
    }

    suspend fun refreshGenres(): Result<Unit> {
        val client = subsonicRepository.currentClient
            ?: return Result.failure(IllegalStateException("No active session"))
        val serverId = subsonicRepository.currentServerId
        Timber.d("[Sync] Refreshing genres for serverId=$serverId")

        val remote = try {
            client.getGenres()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[Sync] Failed to fetch genres from server")
            if (subsonicRepository.isNetworkOrTimeoutException(e)) {
                networkMonitorProvider.get().markServerUnreachable()
            }
            return Result.failure(e)
        }

        val stamp = System.currentTimeMillis()
        if (serverId != subsonicRepository.currentServerId) {
            Timber.w("[Sync] Session changed during genres fetch")
            return Result.failure(CancellationException("Session changed"))
        }

        database.withTransaction {
            val entities = remote.map { it.toEntity(serverId, json, stamp) }
            if (entities.isNotEmpty()) {
                entities.chunked(250).forEach { chunk ->
                    genreDao.upsertGenres(chunk)
                }
            }
            genreDao.deleteStale(serverId, stamp)
        }
        Timber.d("[Sync] Upserted ${remote.size} genres into Room")
        return Result.success(Unit)
    }

    suspend fun syncLibrary(force: Boolean = false): Boolean = syncMutex.withLock {
        if (isOffline) {
            Timber.d("[Sync] Offline mode active: skipping library sync")
            _syncState.value = SyncState.Idle
            return false
        }

        val serverId = subsonicRepository.currentServerId
        val startTime = System.currentTimeMillis()
        Timber.i("[Sync] === Starting library sync (force=$force, serverId=$serverId) ===")
        _syncState.value = SyncState.Syncing("Connecting to server...")

        try {
            _syncState.value = SyncState.Syncing("Syncing playlists...")
            val playlistResult = refreshPlaylists()

            _syncState.value = SyncState.Syncing("Syncing albums...")
            val albumResult = refreshAlbums(force)

            _syncState.value = SyncState.Syncing("Syncing artists...")
            val artistResult = refreshArtists()

            _syncState.value = SyncState.Syncing("Syncing genres...")
            val genreResult = refreshGenres()

            val failures = listOf(playlistResult, albumResult, artistResult, genreResult)
                .filter { it.isFailure }

            val status = when {
                failures.isEmpty() -> "SUCCESS"
                failures.size < 4 -> "PARTIAL"
                else -> "FAILED"
            }

            val duration = System.currentTimeMillis() - startTime
            Timber.i("[Sync] === Finished library sync in ${duration}ms (status=$status, failures=${failures.size}) ===")

            syncMetadataDao.upsertSyncMeta(
                SyncMetadataEntity(
                    syncKey = "full_catalog_sync",
                    serverId = serverId,
                    lastSyncedAt = System.currentTimeMillis(),
                    status = status
                )
            )

            if (failures.size == 4) {
                val errorMsg = failures.first().exceptionOrNull()?.localizedMessage ?: "Sync failed"
                Timber.e("[Sync] All sync stages failed: $errorMsg")
                _syncState.value = SyncState.Error(errorMsg)
                false
            } else {
                _syncState.value = SyncState.Idle
                true
            }
        } catch (e: CancellationException) {
            Timber.d("[Sync] Library sync cancelled")
            _syncState.value = SyncState.Idle
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[Sync] Library sync failed unexpectedly")
            if (subsonicRepository.isNetworkOrTimeoutException(e)) {
                networkMonitorProvider.get().markServerUnreachable()
            }
            _syncState.value = SyncState.Error(e.localizedMessage ?: "Sync failed")
            false
        }
    }

    suspend fun fetchAlbumTracks(albumId: String): Result<List<TrackItem>> = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (isOffline) {
            val localTracks = trackDao.getTracksForAlbum(albumId, serverId).map { it.toDomain() }
            Timber.d("[Sync] Offline mode active: returning ${localTracks.size} local tracks for album $albumId")
            return@withContext Result.success(localTracks)
        }

        val client = subsonicRepository.currentClient
            ?: return@withContext Result.failure(IllegalStateException("No active session"))
        Timber.d("[Sync] Fetching album tracks and metadata for albumId=$albumId on serverId=$serverId")

        try {
            val album = try {
                client.getAlbum(albumId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("[Sync] client.getAlbum parse failed for $albumId, using raw fallback: ${e.message}")
                subsonicRepository.getAlbumRaw(albumId)
            }
            val songs = album.songs
            val stamp = System.currentTimeMillis()

            if (serverId != subsonicRepository.currentServerId) {
                Timber.w("[Sync] Session changed during album fetch for $albumId")
                return@withContext Result.failure(CancellationException("Session changed"))
            }

            database.withTransaction {
                albumDao.upsertAlbum(album.toEntity(serverId, json, stamp))
                val entities = songs.map { it.toEntity(serverId, json, stamp) }
                if (entities.isNotEmpty()) {
                    trackDao.upsertTracks(entities)
                }
            }
            Timber.d("[Sync] Successfully fetched album '${album.name}' ($albumId) with ${songs.size} tracks and saved to Room")

            album.coverArtId?.let { coverId ->
                scope.launch { prefetchThumbnails(listOf(coverId), serverId) }
            }

            Result.success(songs.map { it.toDomain() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[Sync] Failed to fetch album tracks for $albumId")
            if (subsonicRepository.isNetworkOrTimeoutException(e)) {
                networkMonitorProvider.get().markServerUnreachable()
            }
            Result.failure(e)
        }
    }

    private fun moveSafely(src: File, dest: File) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                java.nio.file.Files.move(
                    src.toPath(),
                    dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
                return
            } catch (_: Exception) {}
        }
        if (dest.exists()) dest.delete()
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
    }
}
