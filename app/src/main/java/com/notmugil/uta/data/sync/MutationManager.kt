package com.notmugil.uta.data.sync

import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.AlbumDao
import com.notmugil.uta.data.db.ArtistDao
import com.notmugil.uta.data.db.PlaylistDao
import com.notmugil.uta.data.db.PlaylistEntity
import com.notmugil.uta.data.db.PlaylistTrackCrossRef
import com.notmugil.uta.data.db.SyncActionDao
import com.notmugil.uta.data.db.SyncActionEntity
import com.notmugil.uta.data.db.TrackDao
import com.notmugil.uta.data.db.toDomain
import com.notmugil.uta.data.db.toEntity
import com.notmugil.uta.domain.model.PlaylistItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Serializable
data class CreatePlaylistPayload(
    val name: String,
    val songIds: List<String> = emptyList()
)

@Serializable
data class UpdatePlaylistPayload(
    val name: String? = null,
    val comment: String? = null,
    val isPublic: Boolean? = null,
    val songIdsToAdd: List<String> = emptyList(),
    val songIndexesToRemove: List<Int> = emptyList()
)

@Serializable
data class RatingPayload(
    val rating: Int
)

@Singleton
class MutationManager @Inject constructor(
    private val subsonicRepository: SubsonicRepository,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val playlistDao: PlaylistDao,
    private val syncActionDao: SyncActionDao,
    private val json: Json,
    private val networkMonitorProvider: Provider<com.notmugil.uta.data.NetworkMonitor>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val itemMutexes = ConcurrentHashMap<String, Mutex>()
    private val flushMutex = Mutex()

    private val isOffline: Boolean
        get() = try {
            networkMonitorProvider.get().isOfflineModeActive.value
        } catch (_: Exception) {
            false
        }

    companion object {
        const val ACTION_STAR = "STAR"
        const val ACTION_UNSTAR = "UNSTAR"
        const val ACTION_CREATE_PLAYLIST = "CREATE_PLAYLIST"
        const val ACTION_UPDATE_PLAYLIST = "UPDATE_PLAYLIST"
        const val ACTION_DELETE_PLAYLIST = "DELETE_PLAYLIST"
        const val ACTION_SET_RATING = "SET_RATING"

        const val TYPE_TRACK = "TRACK"
        const val TYPE_ALBUM = "ALBUM"
        const val TYPE_ARTIST = "ARTIST"
        const val TYPE_PLAYLIST = "PLAYLIST"

        const val MAX_RETRIES = 5
        const val MAX_AGE_MS = 14 * 24 * 60 * 60 * 1000L // 14 days
    }

    private fun getMutex(key: String): Mutex = itemMutexes.computeIfAbsent(key) { Mutex() }

    suspend fun toggleTrackStarred(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext false

        getMutex("track_$trackId").withLock {
            val track = trackDao.getTrack(trackId, serverId)
            val currentStarred = track?.starredAt != null
            val newStarred = !currentStarred
            setTrackStarredInternal(trackId, newStarred, serverId)
            newStarred
        }
    }

    suspend fun setTrackStarred(trackId: String, starred: Boolean) = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext

        getMutex("track_$trackId").withLock {
            setTrackStarredInternal(trackId, starred, serverId)
        }
    }

    private suspend fun setTrackStarredInternal(trackId: String, starred: Boolean, serverId: String) {
        val now = System.currentTimeMillis()
        trackDao.setTrackStarred(trackId, serverId, if (starred) now else null)

        syncActionDao.deleteConflictingActions(serverId, trackId, listOf(ACTION_STAR, ACTION_UNSTAR))

        if (isOffline) {
            Timber.d("[Mutation] Offline mode active: queued track favorite $trackId (starred=$starred) in outbox")
            syncActionDao.upsertAction(
                SyncActionEntity(
                    serverId = serverId,
                    actionType = if (starred) ACTION_STAR else ACTION_UNSTAR,
                    targetType = TYPE_TRACK,
                    targetId = trackId,
                    createdAt = now
                )
            )
            return
        }

        try {
            if (starred) {
                subsonicRepository.starTrack(trackId)
            } else {
                subsonicRepository.unstarTrack(trackId)
            }
            Timber.d("[Mutation] Successfully synced track favorite $trackId (starred=$starred)")
        } catch (e: CancellationException) {
            // Never rollback on cancellation
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Mutation] Failed to sync track favorite $trackId online, queuing in outbox")
            syncActionDao.upsertAction(
                SyncActionEntity(
                    serverId = serverId,
                    actionType = if (starred) ACTION_STAR else ACTION_UNSTAR,
                    targetType = TYPE_TRACK,
                    targetId = trackId,
                    createdAt = now
                )
            )
        }
    }

    suspend fun toggleAlbumStarred(albumId: String): Boolean = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext false

        getMutex("album_$albumId").withLock {
            val album = albumDao.getAlbum(albumId, serverId)
            val currentStarred = album?.starredAt != null
            val newStarred = !currentStarred
            setAlbumStarredInternal(albumId, newStarred, serverId)
            newStarred
        }
    }

    suspend fun setAlbumStarred(albumId: String, starred: Boolean) = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext

        getMutex("album_$albumId").withLock {
            setAlbumStarredInternal(albumId, starred, serverId)
        }
    }

    private suspend fun setAlbumStarredInternal(albumId: String, starred: Boolean, serverId: String) {
        val now = System.currentTimeMillis()
        albumDao.setAlbumStarred(albumId, serverId, if (starred) now else null)

        syncActionDao.deleteConflictingActions(serverId, albumId, listOf(ACTION_STAR, ACTION_UNSTAR))

        if (isOffline) {
            Timber.d("[Mutation] Offline mode active: queued album favorite $albumId (starred=$starred) in outbox")
            syncActionDao.upsertAction(
                SyncActionEntity(
                    serverId = serverId,
                    actionType = if (starred) ACTION_STAR else ACTION_UNSTAR,
                    targetType = TYPE_ALBUM,
                    targetId = albumId,
                    createdAt = now
                )
            )
            return
        }

        try {
            if (starred) {
                subsonicRepository.starAlbum(albumId)
            } else {
                subsonicRepository.unstarAlbum(albumId)
            }
            Timber.d("[Mutation] Successfully synced album favorite $albumId (starred=$starred)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Mutation] Failed to sync album favorite $albumId online, queuing in outbox")
            syncActionDao.upsertAction(
                SyncActionEntity(
                    serverId = serverId,
                    actionType = if (starred) ACTION_STAR else ACTION_UNSTAR,
                    targetType = TYPE_ALBUM,
                    targetId = albumId,
                    createdAt = now
                )
            )
        }
    }

    suspend fun toggleArtistStarred(artistId: String): Boolean = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext false

        getMutex("artist_$artistId").withLock {
            val artist = artistDao.getArtist(artistId, serverId)
            val currentStarred = artist?.starredAt != null
            val newStarred = !currentStarred
            setArtistStarredInternal(artistId, newStarred, serverId)
            newStarred
        }
    }

    suspend fun setArtistStarred(artistId: String, starred: Boolean) = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext

        getMutex("artist_$artistId").withLock {
            setArtistStarredInternal(artistId, starred, serverId)
        }
    }

    private suspend fun setArtistStarredInternal(artistId: String, starred: Boolean, serverId: String) {
        val now = System.currentTimeMillis()
        artistDao.setArtistStarred(artistId, serverId, if (starred) now else null)

        syncActionDao.deleteConflictingActions(serverId, artistId, listOf(ACTION_STAR, ACTION_UNSTAR))

        if (isOffline) {
            Timber.d("[Mutation] Offline mode active: queued artist favorite $artistId (starred=$starred) in outbox")
            syncActionDao.upsertAction(
                SyncActionEntity(
                    serverId = serverId,
                    actionType = if (starred) ACTION_STAR else ACTION_UNSTAR,
                    targetType = TYPE_ARTIST,
                    targetId = artistId,
                    createdAt = now
                )
            )
            return
        }

        try {
            if (starred) {
                subsonicRepository.starArtist(artistId)
            } else {
                subsonicRepository.unstarArtist(artistId)
            }
            Timber.d("[Mutation] Successfully synced artist favorite $artistId (starred=$starred)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Mutation] Failed to sync artist favorite $artistId online, queuing in outbox")
            syncActionDao.upsertAction(
                SyncActionEntity(
                    serverId = serverId,
                    actionType = if (starred) ACTION_STAR else ACTION_UNSTAR,
                    targetType = TYPE_ARTIST,
                    targetId = artistId,
                    createdAt = now
                )
            )
        }
    }

    suspend fun setRating(targetId: String, targetType: String, rating: Int) = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext

        val cleanRating = rating.coerceIn(0, 5)
        if (isOffline) {
            Timber.d("[Mutation] Offline mode active: queued setRating for $targetId ($targetType) in outbox")
            val payload = json.encodeToString(RatingPayload.serializer(), RatingPayload(cleanRating))
            syncActionDao.upsertAction(
                SyncActionEntity(
                    serverId = serverId,
                    actionType = ACTION_SET_RATING,
                    targetType = targetType,
                    targetId = targetId,
                    payloadJson = payload,
                    createdAt = System.currentTimeMillis()
                )
            )
            return@withContext
        }

        try {
            subsonicRepository.setRating(targetId, cleanRating)
            Timber.d("[Mutation] Set rating for $targetId ($targetType) to $cleanRating")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[Mutation] Failed to set rating for $targetId online, queuing in outbox")
            val payload = json.encodeToString(RatingPayload.serializer(), RatingPayload(cleanRating))
            syncActionDao.upsertAction(
                SyncActionEntity(
                    serverId = serverId,
                    actionType = ACTION_SET_RATING,
                    targetType = targetType,
                    targetId = targetId,
                    payloadJson = payload,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Result<PlaylistItem> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Playlist name cannot be empty"))
        }

        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No active server session"))
        }

        if (isOffline) {
            val localId = "local_" + UUID.randomUUID().toString()
            val stamp = System.currentTimeMillis()
            val entity = PlaylistEntity(
                id = localId,
                serverId = serverId,
                name = cleanName,
                songCount = songIds.size,
                durationSeconds = 0L,
                changedAt = stamp,
                createdAt = stamp,
                syncedAt = stamp,
                subsonicJson = "{}"
            )
            playlistDao.upsertPlaylist(entity)
            if (songIds.isNotEmpty()) {
                val crossRefs = songIds.mapIndexed { index, songId ->
                    PlaylistTrackCrossRef(
                        playlistId = localId,
                        trackId = songId,
                        serverId = serverId,
                        sortOrder = index
                    )
                }
                playlistDao.replacePlaylistTracks(localId, serverId, crossRefs)
            }
            val payload = json.encodeToString(CreatePlaylistPayload.serializer(), CreatePlaylistPayload(cleanName, songIds))
            syncActionDao.upsertAction(
                SyncActionEntity(
                    serverId = serverId,
                    actionType = ACTION_CREATE_PLAYLIST,
                    targetType = TYPE_PLAYLIST,
                    targetId = localId,
                    payloadJson = payload
                )
            )
            Timber.d("[Mutation] Offline mode active: created local playlist '$cleanName' ($localId)")
            return@withContext Result.success(entity.toDomain())
        }

        try {
            val remote = subsonicRepository.createPlaylist(cleanName, songIds)
            if (remote != null) {
                val stamp = System.currentTimeMillis()
                val entity = remote.toEntity(serverId, json, stamp)
                playlistDao.upsertPlaylist(entity)

                if (songIds.isNotEmpty()) {
                    val crossRefs = songIds.mapIndexed { index, songId ->
                        PlaylistTrackCrossRef(
                            playlistId = remote.id,
                            trackId = songId,
                            serverId = serverId,
                            sortOrder = index
                        )
                    }
                    playlistDao.replacePlaylistTracks(remote.id, serverId, crossRefs)
                }
                Timber.d("[Mutation] Created playlist '${remote.name}' (${remote.id}) on server")
                Result.success(remote.toDomain())
            } else {
                // Fallback local creation with outbox queuing
                val localId = "local_" + UUID.randomUUID().toString()
                val stamp = System.currentTimeMillis()
                val entity = PlaylistEntity(
                    id = localId,
                    serverId = serverId,
                    name = cleanName,
                    songCount = songIds.size,
                    durationSeconds = 0L,
                    changedAt = stamp,
                    createdAt = stamp,
                    syncedAt = stamp,
                    subsonicJson = "{}"
                )
                playlistDao.upsertPlaylist(entity)
                val payload = json.encodeToString(CreatePlaylistPayload.serializer(), CreatePlaylistPayload(cleanName, songIds))
                syncActionDao.upsertAction(
                    SyncActionEntity(
                        serverId = serverId,
                        actionType = ACTION_CREATE_PLAYLIST,
                        targetType = TYPE_PLAYLIST,
                        targetId = localId,
                        payloadJson = payload
                    )
                )
                Result.success(entity.toDomain())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[Mutation] Error creating playlist '$cleanName'")
            Result.failure(e)
        }
    }

    suspend fun addTracksToPlaylist(playlistId: String, songIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        if (songIds.isEmpty()) return@withContext Result.success(Unit)
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext Result.failure(IllegalStateException("No active server session"))

        getMutex("playlist_$playlistId").withLock {
            val existing = playlistDao.getTracksForPlaylist(playlistId, serverId)
            val startSort = existing.size
            val newRefs = songIds.mapIndexed { index, trackId ->
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                    serverId = serverId,
                    sortOrder = startSort + index
                )
            }
            playlistDao.upsertPlaylistTracks(newRefs)
            val playlist = playlistDao.getPlaylist(playlistId, serverId)
            if (playlist != null) {
                playlistDao.upsertPlaylist(playlist.copy(songCount = playlist.songCount + songIds.size))
            }

            if (isOffline) {
                Timber.d("[Mutation] Offline mode active: queued addTracksToPlaylist for $playlistId in outbox")
                val payload = json.encodeToString(
                    UpdatePlaylistPayload.serializer(),
                    UpdatePlaylistPayload(songIdsToAdd = songIds)
                )
                syncActionDao.upsertAction(
                    SyncActionEntity(
                        serverId = serverId,
                        actionType = ACTION_UPDATE_PLAYLIST,
                        targetType = TYPE_PLAYLIST,
                        targetId = playlistId,
                        payloadJson = payload
                    )
                )
                return@withLock Result.success(Unit)
            }

            try {
                val success = subsonicRepository.updatePlaylist(
                    playlistId = playlistId,
                    songIdsToAdd = songIds
                )
                if (success) {
                    Timber.d("[Mutation] Added ${songIds.size} songs to playlist $playlistId")
                    Result.success(Unit)
                } else {
                    val payload = json.encodeToString(
                        UpdatePlaylistPayload.serializer(),
                        UpdatePlaylistPayload(songIdsToAdd = songIds)
                    )
                    syncActionDao.upsertAction(
                        SyncActionEntity(
                            serverId = serverId,
                            actionType = ACTION_UPDATE_PLAYLIST,
                            targetType = TYPE_PLAYLIST,
                            targetId = playlistId,
                            payloadJson = payload
                        )
                    )
                    Result.success(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Mutation] Failed to add tracks to playlist $playlistId")
                Result.failure(e)
            }
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, songIndexToRemove: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext Result.failure(IllegalStateException("No active server session"))

        getMutex("playlist_$playlistId").withLock {
            val currentTracks = playlistDao.getTracksForPlaylist(playlistId, serverId).toMutableList()
            if (songIndexToRemove in currentTracks.indices) {
                currentTracks.removeAt(songIndexToRemove)
                val newRefs = currentTracks.mapIndexed { index, track ->
                    PlaylistTrackCrossRef(
                        playlistId = playlistId,
                        trackId = track.id,
                        serverId = serverId,
                        sortOrder = index
                    )
                }
                playlistDao.replacePlaylistTracks(playlistId, serverId, newRefs)
                val playlist = playlistDao.getPlaylist(playlistId, serverId)
                if (playlist != null) {
                    playlistDao.upsertPlaylist(playlist.copy(songCount = currentTracks.size))
                }
            }

            if (isOffline) {
                Timber.d("[Mutation] Offline mode active: queued removeTrackFromPlaylist for $playlistId at index $songIndexToRemove in outbox")
                val payload = json.encodeToString(
                    UpdatePlaylistPayload.serializer(),
                    UpdatePlaylistPayload(songIndexesToRemove = listOf(songIndexToRemove))
                )
                syncActionDao.upsertAction(
                    SyncActionEntity(
                        serverId = serverId,
                        actionType = ACTION_UPDATE_PLAYLIST,
                        targetType = TYPE_PLAYLIST,
                        targetId = playlistId,
                        payloadJson = payload
                    )
                )
                return@withLock Result.success(Unit)
            }

            try {
                val success = subsonicRepository.updatePlaylist(
                    playlistId = playlistId,
                    songIndexesToRemove = listOf(songIndexToRemove)
                )

                if (!success) {
                    val payload = json.encodeToString(
                        UpdatePlaylistPayload.serializer(),
                        UpdatePlaylistPayload(songIndexesToRemove = listOf(songIndexToRemove))
                    )
                    syncActionDao.upsertAction(
                        SyncActionEntity(
                            serverId = serverId,
                            actionType = ACTION_UPDATE_PLAYLIST,
                            targetType = TYPE_PLAYLIST,
                            targetId = playlistId,
                            payloadJson = payload
                        )
                    )
                }

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Mutation] Failed to remove track at index $songIndexToRemove from playlist $playlistId")
                Result.failure(e)
            }
        }
    }

    suspend fun setPlaylistSongs(playlistId: String, songIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext Result.failure(IllegalStateException("No active server session"))

        getMutex("playlist_$playlistId").withLock {
            val newRefs = songIds.mapIndexed { index, trackId ->
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                    serverId = serverId,
                    sortOrder = index
                )
            }
            playlistDao.replacePlaylistTracks(playlistId, serverId, newRefs)
            val playlist = playlistDao.getPlaylist(playlistId, serverId)
            if (playlist != null) {
                playlistDao.upsertPlaylist(playlist.copy(songCount = songIds.size))
            }

            if (isOffline) {
                Timber.d("[Mutation] Offline mode active: updated playlist tracks locally for $playlistId")
                return@withLock Result.success(Unit)
            }

            try {
                val success = subsonicRepository.setPlaylistSongs(playlistId, songIds)
                if (success) {
                    Timber.d("[Mutation] Successfully updated songs for playlist $playlistId (${songIds.size} songs)")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to update playlist songs on server"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Mutation] Failed to set songs for playlist $playlistId")
                Result.failure(e)
            }
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext Result.failure(IllegalStateException("No active server session"))

        getMutex("playlist_$playlistId").withLock {
            playlistDao.deletePlaylistAndTracks(playlistId, serverId)

            if (isOffline) {
                Timber.d("[Mutation] Offline mode active: queued deletePlaylist for $playlistId in outbox")
                syncActionDao.upsertAction(
                    SyncActionEntity(
                        serverId = serverId,
                        actionType = ACTION_DELETE_PLAYLIST,
                        targetType = TYPE_PLAYLIST,
                        targetId = playlistId
                    )
                )
                return@withLock Result.success(Unit)
            }

            try {
                val success = subsonicRepository.deletePlaylist(playlistId)
                if (!success) {
                    syncActionDao.upsertAction(
                        SyncActionEntity(
                            serverId = serverId,
                            actionType = ACTION_DELETE_PLAYLIST,
                            targetType = TYPE_PLAYLIST,
                            targetId = playlistId
                        )
                    )
                }
                Timber.d("[Mutation] Deleted playlist $playlistId")
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Mutation] Error deleting playlist $playlistId")
                Result.failure(e)
            }
        }
    }

    suspend fun updatePlaylistMeta(
        playlistId: String,
        name: String,
        comment: String? = null,
        isPublic: Boolean? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val serverId = subsonicRepository.currentServerId
        if (serverId.isBlank()) return@withContext Result.failure(IllegalStateException("No active server session"))

        getMutex("playlist_$playlistId").withLock {
            // Optimistic Room update
            playlistDao.updatePlaylistMeta(
                id = playlistId,
                serverId = serverId,
                name = name,
                comment = comment,
                isPublic = isPublic ?: false
            )

            if (isOffline) {
                Timber.d("[Mutation] Offline mode active: queued updatePlaylistMeta for $playlistId in outbox")
                val payload = json.encodeToString(
                    UpdatePlaylistPayload.serializer(),
                    UpdatePlaylistPayload(name = name, comment = comment, isPublic = isPublic)
                )
                syncActionDao.upsertAction(
                    SyncActionEntity(
                        serverId = serverId,
                        actionType = ACTION_UPDATE_PLAYLIST,
                        targetType = TYPE_PLAYLIST,
                        targetId = playlistId,
                        payloadJson = payload
                    )
                )
                return@withLock Result.success(Unit)
            }

            try {
                val success = subsonicRepository.updatePlaylist(
                    playlistId = playlistId,
                    name = name,
                    comment = comment,
                    isPublic = isPublic
                )
                if (!success) {
                    val payload = json.encodeToString(
                        UpdatePlaylistPayload.serializer(),
                        UpdatePlaylistPayload(name = name, comment = comment, isPublic = isPublic)
                    )
                    syncActionDao.upsertAction(
                        SyncActionEntity(
                            serverId = serverId,
                            actionType = ACTION_UPDATE_PLAYLIST,
                            targetType = TYPE_PLAYLIST,
                            targetId = playlistId,
                            payloadJson = payload
                        )
                    )
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Mutation] Failed to update playlist metadata for $playlistId")
                Result.failure(e)
            }
        }
    }

    fun flushOutbox() {
        if (isOffline) {
            Timber.d("[Mutation] Skipping flushOutbox: offline mode is active")
            return
        }
        scope.launch {
            flushMutex.withLock {
                if (isOffline) return@launch
                val serverId = subsonicRepository.currentServerId
                if (serverId.isBlank()) return@launch

                val pending = syncActionDao.getPendingActions(serverId)
                    .filter { it.actionType != ScrobbleManager.ACTION_SCROBBLE }

                val now = System.currentTimeMillis()

                for (action in pending) {
                    if (now - action.createdAt > MAX_AGE_MS || action.retryCount >= MAX_RETRIES) {
                        Timber.w("[Mutation] Dropping stale/exhausted outbox entry ${action.id} (${action.actionType})")
                        syncActionDao.deleteAction(action.id)
                        continue
                    }

                    try {
                        when (action.actionType) {
                            ACTION_STAR -> {
                                when (action.targetType) {
                                    TYPE_TRACK -> subsonicRepository.starTrack(action.targetId)
                                    TYPE_ALBUM -> subsonicRepository.starAlbum(action.targetId)
                                    TYPE_ARTIST -> subsonicRepository.starArtist(action.targetId)
                                }
                                syncActionDao.deleteAction(action.id)
                            }
                            ACTION_UNSTAR -> {
                                when (action.targetType) {
                                    TYPE_TRACK -> subsonicRepository.unstarTrack(action.targetId)
                                    TYPE_ALBUM -> subsonicRepository.unstarAlbum(action.targetId)
                                    TYPE_ARTIST -> subsonicRepository.unstarArtist(action.targetId)
                                }
                                syncActionDao.deleteAction(action.id)
                            }
                            ACTION_SET_RATING -> {
                                val payload = action.payloadJson?.let {
                                    json.decodeFromString(RatingPayload.serializer(), it)
                                }
                                if (payload != null) {
                                    subsonicRepository.setRating(action.targetId, payload.rating)
                                }
                                syncActionDao.deleteAction(action.id)
                            }
                            ACTION_CREATE_PLAYLIST -> {
                                val payload = action.payloadJson?.let {
                                    json.decodeFromString(CreatePlaylistPayload.serializer(), it)
                                }
                                if (payload != null) {
                                    val created = subsonicRepository.createPlaylist(payload.name, payload.songIds)
                                    if (created != null) {
                                        // Replace local playlist ID with real server ID
                                        playlistDao.deletePlaylistAndTracks(action.targetId, serverId)
                                        val entity = created.toEntity(serverId, json)
                                        playlistDao.upsertPlaylist(entity)
                                        if (payload.songIds.isNotEmpty()) {
                                            val crossRefs = payload.songIds.mapIndexed { idx, sId ->
                                                PlaylistTrackCrossRef(created.id, sId, serverId, idx)
                                            }
                                            playlistDao.replacePlaylistTracks(created.id, serverId, crossRefs)
                                        }
                                    }
                                }
                                syncActionDao.deleteAction(action.id)
                            }
                            ACTION_UPDATE_PLAYLIST -> {
                                val payload = action.payloadJson?.let {
                                    json.decodeFromString(UpdatePlaylistPayload.serializer(), it)
                                }
                                if (payload != null) {
                                    try {
                                        val ok = subsonicRepository.updatePlaylist(
                                            playlistId = action.targetId,
                                            name = payload.name,
                                            comment = payload.comment,
                                            isPublic = payload.isPublic,
                                            songIdsToAdd = payload.songIdsToAdd,
                                            songIndexesToRemove = payload.songIndexesToRemove
                                        )
                                        if (ok) {
                                            syncActionDao.deleteAction(action.id)
                                        } else {
                                            Timber.w("[Mutation] updatePlaylist returned false for playlist ${action.targetId}, disregarding action")
                                            syncActionDao.deleteAction(action.id)
                                        }
                                    } catch (e: Exception) {
                                        val msg = e.message ?: ""
                                        if (msg.contains("not found", ignoreCase = true) ||
                                            msg.contains("70") ||
                                            msg.contains("404") ||
                                            msg.contains("does not exist", ignoreCase = true)
                                        ) {
                                            Timber.w("[Mutation] Playlist ${action.targetId} no longer exists or conflict occurred, gracefully disregarding outbox action")
                                            syncActionDao.deleteAction(action.id)
                                        } else {
                                            throw e
                                        }
                                    }
                                } else {
                                    syncActionDao.deleteAction(action.id)
                                }
                            }
                            ACTION_DELETE_PLAYLIST -> {
                                try {
                                    subsonicRepository.deletePlaylist(action.targetId)
                                } catch (_: Exception) {
                                    // If already deleted or doesn't exist, ignore
                                }
                                syncActionDao.deleteAction(action.id)
                            }
                        }
                        Timber.d("[Mutation] Successfully flushed outbox action ${action.id} (${action.actionType})")
                    } catch (e: Exception) {
                        Timber.w(e, "[Mutation] Outbox flush failed for action ${action.id}")
                        syncActionDao.upsertAction(
                            action.copy(
                                retryCount = action.retryCount + 1,
                                lastError = e.message
                            )
                        )
                    }
                }
            }
        }
    }
}
