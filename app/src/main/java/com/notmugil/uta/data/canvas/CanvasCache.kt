package com.notmugil.uta.data.canvas

import android.content.Context
import android.util.LruCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@Serializable
data class CachedCanvasMetadata(
    val url: String,
    val fallbackUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val source: String
)

object CanvasCache {
    private const val TAG = "CanvasCache"
    private val memoryMetaCache = LruCache<String, CanvasArtwork>(100)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "canvas")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun hashString(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }

    fun getMetadata(context: Context, key: String): CanvasArtwork? {
        memoryMetaCache.get(key)?.let { return it }

        try {
            val file = File(getCacheDir(context), "meta_${hashString(key)}.json")
            if (file.exists() && file.length() > 0) {
                val text = file.readText(Charsets.UTF_8)
                val meta = json.decodeFromString<CachedCanvasMetadata>(text)
                val source = CanvasSource.entries.find { it.name.equals(meta.source, ignoreCase = true) } ?: CanvasSource.COMMUNITY
                val artwork = CanvasArtwork(
                    url = meta.url,
                    fallbackUrl = meta.fallbackUrl,
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    source = source
                )
                memoryMetaCache.put(key, artwork)
                return artwork
            }
        } catch (e: Exception) {
            Timber.w(e, "[$TAG] Failed to read canvas metadata cache for $key")
        }
        return null
    }

    fun putMetadata(context: Context, key: String, artwork: CanvasArtwork) {
        memoryMetaCache.put(key, artwork)
        try {
            val file = File(getCacheDir(context), "meta_${hashString(key)}.json")
            val meta = CachedCanvasMetadata(
                url = artwork.url,
                fallbackUrl = artwork.fallbackUrl,
                title = artwork.title,
                artist = artwork.artist,
                album = artwork.album,
                source = artwork.source.name
            )
            file.writeText(json.encodeToString(meta), Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.w(e, "[$TAG] Failed to save canvas metadata cache for $key")
        }
    }

    fun getCachedVideoFile(context: Context, url: String): File? {
        val file = File(getCacheDir(context), "video_${hashString(url)}.mp4")
        return if (file.exists() && file.length() > 1024) file else null
    }

    suspend fun downloadAndCacheVideo(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        val targetFile = File(getCacheDir(context), "video_${hashString(url)}.mp4")
        if (targetFile.exists() && targetFile.length() > 1024) {
            return@withContext targetFile
        }

        val tempFile = File(getCacheDir(context), "temp_${hashString(url)}_${System.currentTimeMillis()}.mp4")
        val client = HttpClient(OkHttp)
        try {
            val response = client.get(url)
            if (response.status.isSuccess()) {
                val bytes = response.readRawBytes()
                if (bytes.isNotEmpty()) {
                    FileOutputStream(tempFile).use { fos ->
                        fos.write(bytes)
                        fos.flush()
                    }
                    if (tempFile.exists() && tempFile.length() > 1024) {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        return@withContext targetFile
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[$TAG] Failed to download canvas video: $url")
        } finally {
            client.close()
            if (tempFile.exists()) tempFile.delete()
        }
        null
    }

    fun invalidateVideo(context: Context, url: String) {
        try {
            val file = File(getCacheDir(context), "video_${hashString(url)}.mp4")
            if (file.exists()) {
                file.delete()
                Timber.d("[$TAG] Invalidated corrupt/broken video cache for $url")
            }
        } catch (e: Exception) {
            Timber.w(e, "[$TAG] Failed to invalidate video cache: $url")
        }
    }

    fun clear(context: Context) {
        memoryMetaCache.evictAll()
        try {
            getCacheDir(context).deleteRecursively()
        } catch (e: Exception) {
            Timber.w(e, "[$TAG] Failed to clear canvas cache")
        }
    }
}
