package com.notmugil.uta.data.canvas

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

private data class CommunityEntry(val song: String, val artist: String, val album: String, val url: String)

object CommunityCanvasService {
    private const val MANIFEST_URL = "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json"
    private const val TTL_MS = 30 * 60 * 1000L

    private var entries: List<CommunityEntry> = emptyList()
    private var fetchedAtMs: Long = 0
    private var isLoading = false

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(title: String, artist: String, album: String? = null): CanvasArtwork? = withContext(Dispatchers.IO) {
        try {
            val manifest = getManifest()
            if (manifest.isEmpty()) return@withContext null

            val wantTitle = CanvasArtwork.normalizeForMatch(title)
            val wantArtist = CanvasArtwork.normalizeForMatch(artist)
            val wantAlbum = if (album != null) CanvasArtwork.normalizeForMatch(album) else null

            for (entry in manifest) {
                val song = CanvasArtwork.normalizeForMatch(entry.song)
                val credited = CanvasArtwork.normalizeForMatch(entry.artist)
                val listedAlbum = CanvasArtwork.normalizeForMatch(entry.album)

                val titleOk =
                    song.isNotEmpty() && (wantTitle.contains(song) || song.contains(wantTitle) || wantTitle == song)
                val artistOk =
                    credited.isNotEmpty() &&
                        (wantArtist.contains(credited) || credited.contains(wantArtist) || wantArtist == credited)
                val albumOk = listedAlbum.isEmpty() || wantAlbum.isNullOrEmpty() || listedAlbum == wantAlbum

                if (titleOk && artistOk && albumOk) {
                    Timber.d("[CommunityCanvas] Manifest hit for '${entry.song}' by '${entry.artist}'")
                    return@withContext CanvasArtwork(
                        url = entry.url,
                        title = entry.song,
                        artist = entry.artist,
                        album = entry.album.ifEmpty { null },
                        source = CanvasSource.COMMUNITY
                    )
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[CommunityCanvas] Search error: ${e.message}")
        }
        null
    }

    private suspend fun getManifest(): List<CommunityEntry> {
        val now = System.currentTimeMillis()
        if (entries.isNotEmpty() && now - fetchedAtMs < TTL_MS) {
            return entries
        }

        if (isLoading) return entries
        isLoading = true

        val client = HttpClient(OkHttp)
        try {
            val response = client.get(MANIFEST_URL) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }

            if (response.status.isSuccess()) {
                val parsedList = mutableListOf<CommunityEntry>()
                val body = response.bodyAsText()
                val element = json.parseToJsonElement(body)
                val items = if (element is kotlinx.serialization.json.JsonObject) {
                    element["items"]?.jsonArray
                } else if (element is kotlinx.serialization.json.JsonArray) {
                    element
                } else {
                    null
                }

                if (items != null) {
                    for (itemEl in items) {
                        val item = itemEl.jsonObject
                        val song = item["song"]?.jsonPrimitive?.content
                        val artist = item["artist"]?.jsonPrimitive?.content
                        val url = item["url"]?.jsonPrimitive?.content
                        if (song.isNullOrBlank() || artist.isNullOrBlank() || url.isNullOrBlank()) continue

                        parsedList.add(
                            CommunityEntry(
                                song = song,
                                artist = artist,
                                album = item["album"]?.jsonPrimitive?.content.orEmpty(),
                                url = url
                            )
                        )
                    }

                    if (parsedList.isNotEmpty()) {
                        entries = parsedList
                        fetchedAtMs = now
                        Timber.d("[CommunityCanvas] Loaded ${entries.size} canvas entries")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[CommunityCanvas] Failed to fetch manifest: ${e.message}")
        } finally {
            isLoading = false
            client.close()
        }

        return entries
    }
}
