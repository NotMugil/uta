package com.notmugil.uta.data.canvas

import android.util.Base64
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
import kotlinx.serialization.json.longOrNull
import timber.log.Timber

object AppleMusicCanvasService {
    private const val AMP_BASE = "https://amp-api.music.apple.com/v1/catalog"
    private const val WEB_PLAYER_URL = "https://music.apple.com/us/browse"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true }

    private var cachedToken: String? = null
    private var tokenExpiresAtMs: Long = 0
    private var retryTokenAfterMs: Long = 0

    suspend fun search(title: String, artist: String, album: String? = null): CanvasArtwork? = withContext(Dispatchers.IO) {
        val client = HttpClient(OkHttp)
        try {
            val token = getToken(client) ?: return@withContext null

            val termBuilder = StringBuilder()
            if (!title.contains(artist, ignoreCase = true)) {
                termBuilder.append("$artist ")
            }
            termBuilder.append(title)
            if (!album.isNullOrBlank() && !title.contains(album, ignoreCase = true)) {
                termBuilder.append(" $album")
            }

            val searchResponse = client.get("$AMP_BASE/us/search") {
                header("Authorization", "Bearer $token")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", USER_AGENT)
                parameter("term", termBuilder.toString().trim())
                parameter("types", "songs")
                parameter("limit", "10")
                parameter("extend", "editorialVideo")
                parameter("include", "albums")
            }

            if (searchResponse.status.value == 401) {
                cachedToken = null
                tokenExpiresAtMs = 0
                return@withContext null
            }
            if (!searchResponse.status.isSuccess()) return@withContext null

            val body = searchResponse.bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val results = root["results"]?.jsonObject
            val songs = results?.get("songs")?.jsonObject
            val hits = songs?.get("data")?.jsonArray ?: return@withContext null

            for (hitEl in hits) {
                val hit = hitEl.jsonObject
                val attributes = hit["attributes"]?.jsonObject ?: continue

                val songName = attributes["name"]?.jsonPrimitive?.content ?: continue
                val songArtist = attributes["artistName"]?.jsonPrimitive?.content ?: continue
                val songAlbum = attributes["albumName"]?.jsonPrimitive?.content

                val candidateMatch = CanvasArtwork(
                    url = "",
                    title = songName,
                    artist = songArtist,
                    source = CanvasSource.APPLE_MUSIC
                )
                if (!candidateMatch.matches(title, artist, album)) continue

                val editorialVideo = attributes["editorialVideo"]?.jsonObject
                if (editorialVideo != null) {
                    val urls = extractMotionUrls(editorialVideo)
                    if (urls != null) {
                        Timber.d("[AppleMusicCanvas] Found inline motion artwork for '$songName'")
                        return@withContext CanvasArtwork(
                            url = urls.first,
                            fallbackUrl = urls.second,
                            title = songName,
                            artist = songArtist,
                            album = songAlbum,
                            source = CanvasSource.APPLE_MUSIC
                        )
                    }
                }

                val albumId = extractAlbumId(hit)
                if (albumId != null) {
                    val albumMotion = fetchAlbumMotion(client, albumId, token, songName, songArtist)
                    if (albumMotion != null) {
                        return@withContext albumMotion
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[AppleMusicCanvas] Search error: ${e.message}")
        } finally {
            client.close()
        }
        null
    }

    suspend fun searchAlbum(albumTitle: String, artist: String): CanvasArtwork? = withContext(Dispatchers.IO) {
        val client = HttpClient(OkHttp)
        try {
            val token = getToken(client) ?: return@withContext null

            val termBuilder = StringBuilder()
            if (!albumTitle.contains(artist, ignoreCase = true)) {
                termBuilder.append("$artist ")
            }
            termBuilder.append(albumTitle)

            val searchResponse = client.get("$AMP_BASE/us/search") {
                header("Authorization", "Bearer $token")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", USER_AGENT)
                parameter("term", termBuilder.toString().trim())
                parameter("types", "albums")
                parameter("limit", "10")
                parameter("extend", "editorialVideo")
            }

            if (searchResponse.status.value == 401) {
                cachedToken = null
                tokenExpiresAtMs = 0
                return@withContext null
            }
            if (!searchResponse.status.isSuccess()) return@withContext null

            val body = searchResponse.bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val results = root["results"]?.jsonObject
            val albums = results?.get("albums")?.jsonObject
            val hits = albums?.get("data")?.jsonArray ?: return@withContext null

            for (hitEl in hits) {
                val hit = hitEl.jsonObject
                val attributes = hit["attributes"]?.jsonObject ?: continue

                val albumName = attributes["name"]?.jsonPrimitive?.content ?: continue
                val albumArtist = attributes["artistName"]?.jsonPrimitive?.content ?: continue

                val candidateMatch = CanvasArtwork(
                    url = "",
                    title = albumName,
                    artist = albumArtist,
                    source = CanvasSource.APPLE_MUSIC
                )
                if (!candidateMatch.matches(albumTitle, artist)) continue

                val editorialVideo = attributes["editorialVideo"]?.jsonObject
                if (editorialVideo != null) {
                    val urls = extractMotionUrls(editorialVideo)
                    if (urls != null) {
                        Timber.d("[AppleMusicCanvas] Found album editorial video for '$albumName'")
                        return@withContext CanvasArtwork(
                            url = urls.first,
                            fallbackUrl = urls.second,
                            title = albumName,
                            artist = albumArtist,
                            album = albumName,
                            source = CanvasSource.APPLE_MUSIC
                        )
                    }
                }

                val albumId = hit["id"]?.jsonPrimitive?.content
                if (albumId != null) {
                    val albumMotion = fetchAlbumMotion(client, albumId, token, albumName, albumArtist)
                    if (albumMotion != null) {
                        return@withContext albumMotion
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[AppleMusicCanvas] Album search error: ${e.message}")
        } finally {
            client.close()
        }
        null
    }

    private fun extractAlbumId(songHit: kotlinx.serialization.json.JsonObject): String? {
        try {
            val relationships = songHit["relationships"]?.jsonObject
            val albums = relationships?.get("albums")?.jsonObject
            val data = albums?.get("data")?.jsonArray
            val firstId = data?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
            if (!firstId.isNullOrBlank() && !firstId.startsWith("pl.")) {
                return firstId
            }
        } catch (_: Exception) {}

        val url = songHit["attributes"]?.jsonObject?.get("url")?.jsonPrimitive?.content
        if (url != null && url.contains("/album/")) {
            val afterAlbum = url.substringAfter("/album/").substringBefore("?")
            val seg = afterAlbum.substringAfterLast("/")
            if (seg.all { it.isDigit() }) {
                return seg
            }
        }
        return null
    }

    private suspend fun fetchAlbumMotion(
        client: HttpClient,
        albumId: String,
        bearer: String,
        songName: String,
        songArtist: String
    ): CanvasArtwork? {
        try {
            val response = client.get("$AMP_BASE/us/albums/$albumId") {
                header("Authorization", "Bearer $bearer")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", USER_AGENT)
                parameter("extend", "editorialVideo")
            }

            if (!response.status.isSuccess()) return null

            val data = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val albumObj = data["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("attributes")?.jsonObject
                ?: return null

            val albumName = albumObj["name"]?.jsonPrimitive?.content.orEmpty()
            if (isCompilation(albumName)) return null

            val albumVideo = albumObj["editorialVideo"]?.jsonObject ?: return null
            val urls = extractMotionUrls(albumVideo) ?: return null

            Timber.d("[AppleMusicCanvas] Found album motion artwork on '$albumName' for '$songName'")
            return CanvasArtwork(
                url = urls.first,
                fallbackUrl = urls.second,
                title = songName,
                artist = songArtist,
                album = albumName,
                source = CanvasSource.APPLE_MUSIC
            )
        } catch (e: Exception) {
            Timber.w(e, "[AppleMusicCanvas] Fetch album motion error: ${e.message}")
            return null
        }
    }

    private fun isCompilation(name: String): Boolean {
        val lower = name.lowercase()
        val markers = listOf(
            "playlist",
            "set list",
            "essentials",
            "dj mix",
            "mixed",
            "apple music",
            "today's hits",
            "session"
        )
        return markers.any { lower.contains(it) }
    }

    private fun extractMotionUrls(video: kotlinx.serialization.json.JsonObject): Pair<String, String?>? {
        fun link(key: String): String? {
            val asset = video[key]?.jsonObject ?: return null
            return asset["video"]?.jsonPrimitive?.content
                ?: asset["videoUrl"]?.jsonPrimitive?.content
                ?: asset["hlsUrl"]?.jsonPrimitive?.content
                ?: asset["url"]?.jsonPrimitive?.content
        }

        val square = link("motionDetailSquare") ?: link("motionSquareVideo1x1")
        val raw = link("motionDetailRaw")
        val tall = link("motionDetailTall") ?: link("motionTallVideo3x4")

        val primary = square ?: raw ?: tall ?: return null
        val alternate = listOfNotNull(square, raw, tall).firstOrNull { it != primary && it.isNotEmpty() }

        return Pair(primary, alternate)
    }

    private suspend fun getToken(client: HttpClient): String? {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiresAtMs - 60_000) {
            return cachedToken
        }

        if (now < retryTokenAfterMs) {
            return null
        }

        try {
            val htmlResp = client.get(WEB_PLAYER_URL) {
                header("User-Agent", USER_AGENT)
            }

            if (!htmlResp.status.isSuccess()) {
                retryTokenAfterMs = now + 15 * 60_000
                return null
            }

            val html = htmlResp.bodyAsText()
            val scriptMatches = Regex("""/assets/index(?:-legacy)?[~-][A-Za-z0-9_-]+\.js""")
                .findAll(html)
                .map { it.value }
                .toSet()

            for (scriptPath in scriptMatches) {
                val scriptResp = client.get("https://music.apple.com$scriptPath") {
                    header("User-Agent", USER_AGENT)
                }
                if (!scriptResp.status.isSuccess()) continue

                val scriptBody = scriptResp.bodyAsText()
                val jwtMatches = Regex("""ey[A-Za-z0-9_-]+\.ey[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
                    .findAll(scriptBody)
                    .map { it.value }

                for (jwt in jwtMatches) {
                    val exp = extractJwtExpiry(jwt)
                    if (exp != null && exp > now) {
                        cachedToken = jwt
                        tokenExpiresAtMs = exp
                        Timber.d("[AppleMusicCanvas] Scraped Apple Music token")
                        return jwt
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[AppleMusicCanvas] Token scraping error: ${e.message}")
        }

        retryTokenAfterMs = now + 15 * 60_000
        return null
    }

    private fun extractJwtExpiry(jwt: String): Long? {
        return try {
            val parts = jwt.split(".")
            if (parts.size != 3) return null
            val payloadJson =
                String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
            val root = json.parseToJsonElement(payloadJson).jsonObject
            val expSec = root["exp"]?.jsonPrimitive?.longOrNull
            if (expSec != null) expSec * 1000 else null
        } catch (_: Exception) {
            null
        }
    }
}
