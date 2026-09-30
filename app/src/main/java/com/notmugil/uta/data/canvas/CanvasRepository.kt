package com.notmugil.uta.data.canvas

import android.content.Context
import android.util.LruCache
import com.notmugil.uta.data.SubsonicSession
import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.TrackItem
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

object CanvasRepository {
    private val memoryCache = LruCache<String, CanvasArtwork?>(150)
    private val inFlight = mutableMapOf<String, Deferred<CanvasArtwork?>>()
    private val mutex = Mutex()

    suspend fun getAlbumCanvas(album: AlbumItem, context: Context): CanvasArtwork? = withContext(Dispatchers.IO) {
        val title = cleanTitle(album.title)
        val artist = cleanArtist(album.artist)
        if (title.isEmpty() || artist.isEmpty()) return@withContext null

        val key = "album_${album.id}"

        synchronized(memoryCache) {
            val cached = memoryCache.get(key)
            if (cached != null) return@withContext cached
        }

        val diskCached = CanvasCache.getMetadata(context, key)
        if (diskCached != null) {
            synchronized(memoryCache) {
                memoryCache.put(key, diskCached)
            }
            return@withContext diskCached
        }

        if (SubsonicSession.isOfflineModeActive) {
            return@withContext null
        }

        val deferred = mutex.withLock {
            inFlight[key] ?: coroutineScope {
                val def = async(Dispatchers.IO) {
                    resolve(title, artist, title)
                }
                inFlight[key] = def
                def
            }
        }

        try {
            val result = deferred.await()
            synchronized(memoryCache) {
                if (result != null) {
                    memoryCache.put(key, result)
                }
            }
            if (result != null) {
                CanvasCache.putMetadata(context, key, result)
            }
            result
        } finally {
            mutex.withLock {
                inFlight.remove(key)
            }
        }
    }

    suspend fun getCanvas(track: TrackItem, context: Context): CanvasArtwork? = withContext(Dispatchers.IO) {
        val title = cleanTitle(track.title)
        val artist = cleanArtist(track.artist)
        if (title.isEmpty() || artist.isEmpty()) return@withContext null

        val album = track.album?.trim()?.ifEmpty { null }
        val key = "track_${track.id}"

        synchronized(memoryCache) {
            val cached = memoryCache.get(key)
            if (cached != null) return@withContext cached
        }

        val diskCached = CanvasCache.getMetadata(context, key)
        if (diskCached != null) {
            synchronized(memoryCache) {
                memoryCache.put(key, diskCached)
            }
            return@withContext diskCached
        }

        if (SubsonicSession.isOfflineModeActive) {
            return@withContext null
        }

        val deferred = mutex.withLock {
            inFlight[key] ?: coroutineScope {
                val def = async(Dispatchers.IO) {
                    resolve(title, artist, album)
                }
                inFlight[key] = def
                def
            }
        }

        try {
            val result = deferred.await()
            synchronized(memoryCache) {
                if (result != null) {
                    memoryCache.put(key, result)
                }
            }
            if (result != null) {
                CanvasCache.putMetadata(context, key, result)
            }
            result
        } finally {
            mutex.withLock {
                inFlight.remove(key)
            }
        }
    }

    private suspend fun resolve(title: String, artist: String, album: String?): CanvasArtwork? {
        try {
            val tidalHit = TidalCanvasService.search(title, artist, album)
            if (tidalHit != null) return tidalHit
        } catch (e: Exception) {
            Timber.w(e, "[CanvasRepository] Tidal error: ${e.message}")
        }

        try {
            val appleHit = AppleMusicCanvasService.search(title, artist, album)
            if (appleHit != null) return appleHit
        } catch (e: Exception) {
            Timber.w(e, "[CanvasRepository] Apple Music error: ${e.message}")
        }

        try {
            val communityHit = CommunityCanvasService.search(title, artist, album)
            if (communityHit != null) return communityHit
        } catch (e: Exception) {
            Timber.w(e, "[CanvasRepository] Community error: ${e.message}")
        }

        return null
    }

    fun cleanTitle(input: String): String {
        var s = input
            .split(" | ")
            .first()
            .replace(
                Regex(
                    """\((?:from|official|lyrical|video|audio|lyrics|music video|full song|visualizer|remastered|deluxe|explicit)[^)]*\)""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            .replace(
                Regex(
                    """\[(?:from|official|lyrical|video|audio|lyrics|music video|full song|visualizer|remastered|deluxe|explicit|hd|4k)[^\]]*\]""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            .replace(
                Regex(
                    """\b(?:official (?:video|audio|music video|lyric video)|lyrical|full song|4k video|hd video)\b""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (s.contains(" - ")) {
            val parts = s.split(" - ")
            if (parts.size == 2 && parts[1].trim().isNotEmpty()) {
                s = parts[1].trim()
            }
        }

        return if (s.isEmpty()) input.trim() else s
    }

    fun cleanArtist(input: String): String {
        val s = input
            .replace(Regex("""\s*-\s*Topic""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*VEVO""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Official\s*(?:Channel)?""", RegexOption.IGNORE_CASE), "")
            .trim()
        return if (s.isEmpty()) input.trim() else s
    }
}
