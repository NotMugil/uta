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
        val title = cleanAlbumTitle(album.title, album.artist)
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

        if (SubsonicSession.isManualOffline || !SubsonicSession.isOnline) {
            return@withContext null
        }

        val deferred = mutex.withLock {
            inFlight[key] ?: coroutineScope {
                val def = async(Dispatchers.IO) {
                    resolveAlbum(title, artist, album.title)
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

        if (SubsonicSession.isManualOffline || !SubsonicSession.isOnline) {
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

    private suspend fun resolveAlbum(title: String, artist: String, rawTitle: String): CanvasArtwork? {
        try {
            val tidalHit = TidalCanvasService.searchAlbum(title, artist)
            if (tidalHit != null) return tidalHit
        } catch (e: Exception) {
            Timber.w(e, "[CanvasRepository] Tidal album error: ${e.message}")
        }

        try {
            val appleHit = AppleMusicCanvasService.searchAlbum(title, artist)
            if (appleHit != null) return appleHit
        } catch (e: Exception) {
            Timber.w(e, "[CanvasRepository] Apple Music album error: ${e.message}")
        }

        try {
            val communityHit = CommunityCanvasService.searchAlbum(title, artist)
            if (communityHit != null) return communityHit
        } catch (e: Exception) {
            Timber.w(e, "[CanvasRepository] Community album error: ${e.message}")
        }

        if (!title.equals(rawTitle.trim(), ignoreCase = true)) {
            val rawClean = cleanTitle(rawTitle, artist)
            if (!rawClean.equals(title, ignoreCase = true)) {
                try {
                    val tidalHit = TidalCanvasService.searchAlbum(rawClean, artist)
                    if (tidalHit != null) return tidalHit
                } catch (_: Exception) {}

                try {
                    val appleHit = AppleMusicCanvasService.searchAlbum(rawClean, artist)
                    if (appleHit != null) return appleHit
                } catch (_: Exception) {}
            }
        }

        return resolve(title, artist, title)
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

    fun cleanAlbumTitle(input: String, artist: String = ""): String {
        var s = input
            .split(" | ")
            .first()
            .replace(
                Regex(
                    """\((?:deluxe|super deluxe|special edition|expanded|anniversary|remaster|remastered|bonus track|target exclusive|collector|tour edition|explicit|edition|re-issue|version|live|ost|original motion picture soundtrack|soundtrack)[^)]*\)""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            .replace(
                Regex(
                    """\[(?:deluxe|super deluxe|special edition|expanded|anniversary|remaster|remastered|bonus track|target exclusive|collector|tour edition|explicit|edition|re-issue|version|live|ost|original motion picture soundtrack|soundtrack|hd|4k)[^\]]*\]""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            .replace(
                Regex(
                    """\b(?:deluxe edition|super deluxe|expanded edition|anniversary edition|special edition|collector's edition|tour edition|bonus track version)\b""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (s.contains(" - ")) {
            val parts = s.split(" - ")
            if (parts.size == 2) {
                val p0 = parts[0].trim()
                val p1 = parts[1].trim()
                val normArtist = CanvasArtwork.normalizeForMatch(artist)
                val normP0 = CanvasArtwork.normalizeForMatch(p0)
                if (normArtist.isNotEmpty() && normP0.isNotEmpty() && (normArtist == normP0 || normP0.contains(normArtist) || normArtist.contains(normP0))) {
                    s = p1
                } else {
                    val p1Lower = p1.lowercase()
                    if (p1Lower.contains("deluxe") || p1Lower.contains("remaster") || p1Lower.contains("edition") ||
                        p1Lower.contains("version") || p1Lower.contains("anniversary") || p1Lower.contains("expanded") ||
                        p1Lower.contains("bonus") || p1Lower.contains("special") || p1Lower.contains("live") ||
                        p1Lower.contains("vol.") || p1Lower.contains("volume") || p1Lower.contains("ost") ||
                        p1Lower.contains("soundtrack")
                    ) {
                        s = p0
                    }
                }
            }
        }

        return if (s.isEmpty()) input.trim() else s
    }

    fun cleanTitle(input: String, artist: String = ""): String {
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
                val p0 = parts[0].trim()
                val p1 = parts[1].trim()
                val normArtist = CanvasArtwork.normalizeForMatch(artist)
                val normP0 = CanvasArtwork.normalizeForMatch(p0)
                if (normArtist.isNotEmpty() && (normArtist == normP0 || normP0.contains(normArtist) || normArtist.contains(normP0))) {
                    s = p1
                }
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
