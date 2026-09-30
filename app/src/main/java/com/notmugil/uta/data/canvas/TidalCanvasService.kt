package com.notmugil.uta.data.canvas

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

object TidalCanvasService {
    private const val SEARCH_URL = "https://api.tidal.com/v1/search"
    private const val EMBED_TOKEN = "vNVdglQOjFJJGG2U"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(title: String, artist: String, album: String? = null): CanvasArtwork? = withContext(Dispatchers.IO) {
        var result = executeTrackSearch("$artist $title", title, artist, album)
        if (result != null) return@withContext result

        val primaryArtist = artist.split(Regex("[,&]")).first().trim()
        if (primaryArtist.isNotEmpty() && !primaryArtist.equals(artist, ignoreCase = true)) {
            result = executeTrackSearch("$primaryArtist $title", title, artist, album)
            if (result != null) return@withContext result
        }

        if (!album.isNullOrBlank()) {
            result = executeTrackSearch("$album $primaryArtist $title", title, artist, album)
            if (result != null) return@withContext result
        }

        null
    }

    private suspend fun executeTrackSearch(
        query: String,
        wantTitle: String,
        wantArtist: String,
        wantAlbum: String?
    ): CanvasArtwork? {
        val client = HttpClient(OkHttp)
        try {
            val response = client.get(SEARCH_URL) {
                header("X-Tidal-Token", EMBED_TOKEN)
                header("User-Agent", USER_AGENT)
                parameter("query", query)
                parameter("limit", "10")
                parameter("types", "TRACKS")
                parameter("countryCode", "US")
            }

            if (!response.status.isSuccess()) return null

            val body = response.bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val tracks = root["tracks"]?.jsonObject
            val items = tracks?.get("items")?.jsonArray ?: return null

            for (itemEl in items) {
                val item = itemEl.jsonObject
                val trackTitle = item["title"]?.jsonPrimitive?.content ?: continue

                val artistsList = item["artists"]?.jsonArray
                val artistNames = artistsList?.mapNotNull {
                    it.jsonObject["name"]?.jsonPrimitive?.content
                } ?: emptyList()

                val albumObj = item["album"]?.jsonObject
                val videoCover = albumObj?.get("videoCover")?.jsonPrimitive?.content
                if (videoCover.isNullOrBlank()) continue

                val videoUrl = coverUrl(videoCover.trim()) ?: continue

                val candidate = CanvasArtwork(
                    url = videoUrl,
                    title = trackTitle,
                    artist = artistNames.joinToString(", "),
                    album = albumObj["title"]?.jsonPrimitive?.content,
                    source = CanvasSource.TIDAL
                )

                if (candidate.matches(wantTitle, wantArtist, wantAlbum)) {
                    Timber.d("[TidalCanvas] Found video cover for '$trackTitle' by ${artistNames.joinToString()}")
                    return candidate
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[TidalCanvas] Search failed: ${e.message}")
        } finally {
            client.close()
        }
        return null
    }

    private fun coverUrl(id: String): String? {
        val parts = id.split("-")
        if (parts.size != 5) return null
        return "https://resources.tidal.com/videos/${parts.joinToString("/")}/1280x1280.mp4"
    }
}
