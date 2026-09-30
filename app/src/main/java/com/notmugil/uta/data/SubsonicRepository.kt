package com.notmugil.uta.data

import android.content.Context
import dev.zt64.subsonic.api.model.Album
import dev.zt64.subsonic.api.model.Artist
import dev.zt64.subsonic.api.model.Playlist
import dev.zt64.subsonic.api.model.StructuredLyrics
import dev.zt64.subsonic.api.model.SubsonicErrorCode
import dev.zt64.subsonic.api.model.SubsonicException
import dev.zt64.subsonic.client.SubsonicAuth
import dev.zt64.subsonic.client.SubsonicClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface AuthState {
    data object Loading : AuthState
    data class Authenticated(
        val client: SubsonicClient,
        val credentials: StoredCredentials
    ) : AuthState
    data class Unauthenticated(val message: String? = null) : AuthState
}

data class NavidromePlaylistInfo(
    val id: String,
    val isSmart: Boolean = false,
    val isSync: Boolean = false,
    val isOwner: Boolean = true,
    val canEditTracks: Boolean = true,
    val description: String? = null
)

data class ServerPlayQueue(
    val currentTrackId: String? = null,
    val currentIndex: Int? = null,
    val positionMs: Long = 0L,
    val trackIds: List<String> = emptyList(),
    val changed: String? = null,
    val changedBy: String? = null
)

data class SubsonicLyrics(
    val artist: String? = null,
    val title: String? = null,
    val value: String? = null
)

@Singleton
class SubsonicRepository @Inject constructor(
    private val credentialStore: CredentialStore,
    val okHttpClient: OkHttpClient,
    val json: Json,
    private val networkMonitorProvider: javax.inject.Provider<NetworkMonitor>,
    private val appPreferencesProvider: javax.inject.Provider<com.notmugil.uta.data.preferences.AppPreferences>? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _activeServerUrl = MutableStateFlow<String?>(null)
    val activeServerUrl: StateFlow<String?> = _activeServerUrl.asStateFlow()

    val currentClient: SubsonicClient?
        get() = (_authState.value as? AuthState.Authenticated)?.client

    val fallbackServerUrl: String?
        get() = try {
            appPreferencesProvider?.get()?.fallbackServerUrl?.value?.trim()?.ifBlank { null }
        } catch (_: Exception) {
            null
        }

    val primaryServerUrl: String?
        get() = (_authState.value as? AuthState.Authenticated)?.credentials?.serverUrl
            ?: credentialStore.load()?.serverUrl

    val currentServerUrl: String?
        get() = _activeServerUrl.value ?: primaryServerUrl

    val isUsingFallback: Boolean
        get() {
            val fallback = fallbackServerUrl ?: return false
            val active = currentServerUrl ?: return false
            return try {
                normalizeUrl(active) == normalizeUrl(fallback)
            } catch (_: Exception) {
                false
            }
        }

    val currentServerId: String
        get() = (primaryServerUrl ?: currentServerUrl)?.let { hashServerUrl(it) } ?: "default"

    init {
        restoreSession()
    }

    fun restoreSession() {
        scope.launch(Dispatchers.IO) {
            val credentials = try {
                credentialStore.load()
            } catch (e: Exception) {
                Timber.e(e, "[Subsonic] Failed to load stored credentials")
                null
            }

            if (credentials == null) {
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.Unauthenticated()
                    _activeServerUrl.value = null
                    SubsonicSession.client = null
                }
                return@launch
            }

            try {
                val targetUrl = _activeServerUrl.value ?: credentials.serverUrl
                val client = buildClient(credentials, serverUrlOverride = targetUrl)
                SubsonicSession.client = client
                withContext(Dispatchers.Main) {
                    _activeServerUrl.value = targetUrl
                    _authState.value = AuthState.Authenticated(client, credentials)
                }

                // Non-blocking ping verification in background to validate active session
                try {
                    client.ping()
                } catch (e: SubsonicException) {
                    if (isAuthError(e.code)) {
                        withContext(Dispatchers.Main) {
                            logout(reason = e.message ?: "Session expired or credentials changed")
                        }
                    }
                } catch (e: Exception) {
                    if (isNetworkOrTimeoutException(e)) {
                        val fallback = fallbackServerUrl
                        if (fallback != null) {
                            val cleanFallback = runCatching { normalizeUrl(fallback) }.getOrNull()
                            if (cleanFallback != null && cleanFallback != targetUrl) {
                                Timber.i("[Subsonic] Primary ping failed on restore, attempting fallback to $cleanFallback")
                                try {
                                    val fallbackClient = buildClient(credentials, serverUrlOverride = cleanFallback)
                                    fallbackClient.ping()
                                    withContext(Dispatchers.Main) {
                                        _activeServerUrl.value = cleanFallback
                                        SubsonicSession.client = fallbackClient
                                        _authState.value = AuthState.Authenticated(fallbackClient, credentials)
                                    }
                                    Timber.i("[Subsonic] Restored session with fallback server URL: $cleanFallback")
                                    return@launch
                                } catch (fallbackError: Exception) {
                                    Timber.w(fallbackError, "[Subsonic] Fallback ping also failed on restore")
                                }
                            }
                        }
                        networkMonitorProvider.get().markServerUnreachable()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.Unauthenticated(e.localizedMessage)
                }
            }
        }
    }

    suspend fun login(serverUrl: String, username: String, password: String, fallbackServerUrl: String? = null): SubsonicClient =
        withContext(Dispatchers.IO) {
            val normalizedUrl = normalizeUrl(serverUrl)
            val normalizedFallback = fallbackServerUrl?.trim()?.takeIf { it.isNotBlank() }?.let { normalizeUrl(it) }
            val credentials = StoredCredentials(
                serverUrl = normalizedUrl,
                username = username.trim(),
                password = password
            )

            // Ping test using OkHttp
            pingWithOkHttp(credentials.serverUrl, credentials.username, credentials.password)

            val client = buildClient(credentials, serverUrlOverride = normalizedUrl)
            client.ping()

            credentialStore.save(credentials)
            if (normalizedFallback != null) {
                appPreferencesProvider?.get()?.setFallbackServerUrl(normalizedFallback)
            }
            SubsonicSession.client = client
            withContext(Dispatchers.Main) {
                _activeServerUrl.value = normalizedUrl
                _authState.value = AuthState.Authenticated(client, credentials)
            }
            client
        }

    suspend fun pingWithOkHttp(serverUrl: String, username: String, password: String): Unit = withContext(Dispatchers.IO) {
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val httpUrl = "${serverUrl.trimEnd('/')}/rest/ping.view".toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Enter a valid server URL")

        val pingUrl = httpUrl.newBuilder()
            .addQueryParameter("u", username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")
            .build()

        val request = Request.Builder()
            .url(pingUrl)
            .get()
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            if (isNetworkOrTimeoutException(e)) {
                networkMonitorProvider.get().markServerUnreachable()
            }
            throw e
        }

        if (!response.isSuccessful) {
            throw java.io.IOException("Server returned HTTP ${response.code}")
        }
        val responseBody = response.body.string()
        val root = json.parseToJsonElement(responseBody).jsonObject["subsonic-response"]?.jsonObject
            ?: throw java.io.IOException("Invalid Subsonic response")
        val status = root["status"]?.jsonPrimitive?.content
        if (status != "ok") {
            val errorMsg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: "Subsonic authentication failed"
            throw java.io.IOException(errorMsg)
        }
    }

    suspend fun testConnection(url: String): Boolean = withContext(Dispatchers.IO) {
        val creds = (_authState.value as? AuthState.Authenticated)?.credentials ?: credentialStore.load() ?: return@withContext false
        try {
            val normalized = normalizeUrl(url)
            pingWithOkHttp(normalized, creds.username, creds.password)
            true
        } catch (e: Exception) {
            Timber.w(e, "[Subsonic] Test connection failed for $url")
            false
        }
    }

    fun logout(reason: String? = null) {
        try {
            currentClient?.close()
        } catch (_: Exception) {}

        SubsonicSession.client = null
        _activeServerUrl.value = null
        credentialStore.clear()
        _authState.value = AuthState.Unauthenticated(reason)
    }

    suspend fun <T> execute(block: suspend (SubsonicClient) -> T): T {
        val client = currentClient
            ?: throw IllegalStateException("Not authenticated with Subsonic server")

        return try {
            block(client)
        } catch (e: SubsonicException) {
            if (isAuthError(e.code)) {
                withContext(Dispatchers.Main) {
                    logout(reason = e.message ?: "Authentication failed")
                }
            }
            throw e
        } catch (e: Exception) {
            if (isNetworkOrTimeoutException(e)) {
                val creds = (_authState.value as? AuthState.Authenticated)?.credentials ?: credentialStore.load()
                val primary = creds?.serverUrl
                val fallback = fallbackServerUrl?.let { runCatching { normalizeUrl(it) }.getOrNull() }
                val active = currentServerUrl

                if (creds != null && fallback != null && active != fallback) {
                    Timber.w("[SubsonicRepository] Primary call failed (${e.message}), attempting failover to fallback URL: $fallback")
                    try {
                        val fallbackClient = buildClient(creds, serverUrlOverride = fallback)
                        val result = block(fallbackClient)
                        withContext(Dispatchers.Main) {
                            _activeServerUrl.value = fallback
                            SubsonicSession.client = fallbackClient
                            _authState.value = AuthState.Authenticated(fallbackClient, creds)
                        }
                        Timber.i("[SubsonicRepository] Failover to fallback URL succeeded.")
                        return result
                    } catch (failoverError: Exception) {
                        Timber.w(failoverError, "[SubsonicRepository] Failover attempt to fallback URL also failed.")
                    }
                } else if (creds != null && primary != null && active == fallback) {
                    Timber.w("[SubsonicRepository] Fallback call failed (${e.message}), attempting failback to primary URL: $primary")
                    try {
                        val primaryClient = buildClient(creds, serverUrlOverride = primary)
                        val result = block(primaryClient)
                        withContext(Dispatchers.Main) {
                            _activeServerUrl.value = primary
                            SubsonicSession.client = primaryClient
                            _authState.value = AuthState.Authenticated(primaryClient, creds)
                        }
                        Timber.i("[SubsonicRepository] Failback to primary URL succeeded.")
                        return result
                    } catch (failoverError: Exception) {
                        Timber.w(failoverError, "[SubsonicRepository] Failback to primary URL also failed.")
                    }
                }

                Timber.w("[SubsonicRepository] Network timeout or connection error -> marking server unreachable: ${e.message}")
                networkMonitorProvider.get().markServerUnreachable()
            }
            throw e
        }
    }

    fun isNetworkOrTimeoutException(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            if (current is java.net.SocketTimeoutException ||
                current is java.net.ConnectException ||
                current is java.net.UnknownHostException ||
                current is java.net.NoRouteToHostException ||
                current is java.net.PortUnreachableException ||
                current is java.io.InterruptedIOException ||
                current is kotlinx.coroutines.TimeoutCancellationException ||
                current.javaClass.name.contains("Timeout", ignoreCase = true) ||
                current.javaClass.name.contains("Connect", ignoreCase = true) ||
                current.message?.contains("timed out", ignoreCase = true) == true ||
                current.message?.contains("timeout", ignoreCase = true) == true ||
                current.message?.contains("failed to connect", ignoreCase = true) == true ||
                current.message?.contains("Unable to resolve host", ignoreCase = true) == true
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun isAuthError(code: SubsonicErrorCode): Boolean = when (code) {
        SubsonicErrorCode.WRONG_USERNAME_OR_PASSWORD,
        SubsonicErrorCode.UNAUTHORIZED,
        SubsonicErrorCode.AUTH_METHOD_NOT_SUPPORTED -> true
        else -> false
    }

    fun buildClient(credentials: StoredCredentials, serverUrlOverride: String? = null): SubsonicClient {
        val targetUrl = serverUrlOverride ?: currentServerUrl ?: credentials.serverUrl
        val normalizedUrl = normalizeUrl(targetUrl)
        return SubsonicClient(
            baseUrl = normalizedUrl,
            auth = SubsonicAuth.Token(
                username = credentials.username,
                password = credentials.password
            ),
            client = "uta"
        )
    }

    fun normalizeUrl(url: String): String {
        var raw = url.trim()
        if (raw.isBlank()) throw IllegalArgumentException("Server URL cannot be blank")

        if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
            raw = "http://$raw"
        }

        while (raw.endsWith("/")) {
            raw = raw.dropLast(1)
        }

        if (raw.endsWith("/rest", ignoreCase = true)) {
            raw = raw.substring(0, raw.length - 5)
        }

        while (raw.endsWith("/")) {
            raw = raw.dropLast(1)
        }

        val httpUrl = raw.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Enter a valid server URL")
        require(httpUrl.scheme == "http" || httpUrl.scheme == "https") {
            "Server URL must use HTTP or HTTPS"
        }
        return httpUrl.toString().trimEnd('/')
    }

    fun getCoverArtUrl(coverArtId: String?, size: Int? = null): String? {
        val baseUrl = currentServerUrl ?: return null
        val id = coverArtId ?: return null
        val creds = credentialStore.load() ?: return null
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val builder = "${baseUrl.trimEnd('/')}/rest/getCoverArt.view".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("u", creds.username)
            ?.addQueryParameter("t", token)
            ?.addQueryParameter("s", salt)
            ?.addQueryParameter("v", "1.16.1")
            ?.addQueryParameter("c", "Uta")
            ?.addQueryParameter("id", id)

        if (size != null) {
            builder?.addQueryParameter("size", size.toString())
        }

        return builder?.build()?.toString()
    }

    fun getStreamUrl(id: String, maxBitRate: Int? = null, format: String? = null): String? {
        val client = currentClient
        val validMaxBitRate = if (maxBitRate != null && maxBitRate > 0) maxBitRate else 0
        val validFormat = if (!format.isNullOrBlank() && format != "raw" && format != "original") format else null
        if (client != null) {
            return client.getStreamUrl(
                id = id,
                maxBitRate = validMaxBitRate,
                format = validFormat
            )
        }

        val baseUrl = currentServerUrl ?: return null
        val creds = credentialStore.load() ?: return null
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val builder = "${baseUrl.trimEnd('/')}/rest/stream.view".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("u", creds.username)
            ?.addQueryParameter("t", token)
            ?.addQueryParameter("s", salt)
            ?.addQueryParameter("v", "1.16.1")
            ?.addQueryParameter("c", "Uta")
            ?.addQueryParameter("id", id)

        if (validMaxBitRate > 0) {
            builder?.addQueryParameter("maxBitRate", validMaxBitRate.toString())
        }
        if (validFormat != null) {
            builder?.addQueryParameter("format", validFormat)
        }

        return builder?.build()?.toString()
    }

    suspend fun getAlbumRaw(albumId: String): Album = withContext(Dispatchers.IO) {
        val baseUrl = currentServerUrl ?: throw IllegalStateException("No active server URL")
        val creds = credentialStore.load() ?: throw IllegalStateException("No active credentials")
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val httpUrl = "${baseUrl.trimEnd('/')}/rest/getAlbum.view".toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid server URL")

        val url = httpUrl.newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")
            .addQueryParameter("id", albumId)
            .build()

        val request = Request.Builder().url(url).get().build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw java.io.IOException("Server returned HTTP ${response.code}")
        }
        val rawString = response.body.string()
        val sanitized = com.notmugil.uta.data.db.sanitizeSubsonicJson(rawString, json)
        val root = json.parseToJsonElement(sanitized).jsonObject["subsonic-response"]?.jsonObject
            ?: throw java.io.IOException("Invalid Subsonic response")

        val status = root["status"]?.jsonPrimitive?.content
        if (status != "ok") {
            val errorMsg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: "Failed to get album"
            throw java.io.IOException(errorMsg)
        }

        val albumJson = root["album"] ?: throw java.io.IOException("Album not found in response")
        json.decodeFromJsonElement(Album.serializer(), albumJson)
    }

    suspend fun getPlaylistRaw(playlistId: String): Playlist = withContext(Dispatchers.IO) {
        val baseUrl = currentServerUrl ?: throw IllegalStateException("No active server URL")
        val creds = credentialStore.load() ?: throw IllegalStateException("No active credentials")
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val httpUrl = "${baseUrl.trimEnd('/')}/rest/getPlaylist.view".toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid server URL")

        val url = httpUrl.newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")
            .addQueryParameter("id", playlistId)
            .build()

        val request = Request.Builder().url(url).get().build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw java.io.IOException("Server returned HTTP ${response.code}")
        }
        val rawString = response.body.string()
        val sanitized = com.notmugil.uta.data.db.sanitizeSubsonicJson(rawString, json)
        val root = json.parseToJsonElement(sanitized).jsonObject["subsonic-response"]?.jsonObject
            ?: throw java.io.IOException("Invalid Subsonic response")

        val status = root["status"]?.jsonPrimitive?.content
        if (status != "ok") {
            val errorMsg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: "Failed to get playlist"
            throw java.io.IOException(errorMsg)
        }

        val playlistJson = root["playlist"] ?: throw java.io.IOException("Playlist not found in response")
        json.decodeFromJsonElement(Playlist.serializer(), playlistJson)
    }

    suspend fun getArtistRaw(artistId: String): Artist = withContext(Dispatchers.IO) {
        val baseUrl = currentServerUrl ?: throw IllegalStateException("No active server URL")
        val creds = credentialStore.load() ?: throw IllegalStateException("No active credentials")
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val httpUrl = "${baseUrl.trimEnd('/')}/rest/getArtist.view".toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid server URL")

        val url = httpUrl.newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")
            .addQueryParameter("id", artistId)
            .build()

        val request = Request.Builder().url(url).get().build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw java.io.IOException("Server returned HTTP ${response.code}")
        }
        val rawString = response.body.string()
        val sanitized = com.notmugil.uta.data.db.sanitizeSubsonicJson(rawString, json)
        val root = json.parseToJsonElement(sanitized).jsonObject["subsonic-response"]?.jsonObject
            ?: throw java.io.IOException("Invalid Subsonic response")

        val status = root["status"]?.jsonPrimitive?.content
        if (status != "ok") {
            val errorMsg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: "Failed to get artist"
            throw java.io.IOException(errorMsg)
        }

        val artistJson = root["artist"] ?: throw java.io.IOException("Artist not found in response")
        json.decodeFromJsonElement(Artist.serializer(), artistJson)
    }

    suspend fun starTrack(id: String) = withContext(Dispatchers.IO) {
        val client = currentClient
        try {
            client?.star(id)
        } catch (e: Exception) {
            Timber.w(e, "[Subsonic] Client starTrack failed for $id, trying REST fallback")
            executeRestAction("star.view", listOf("id" to id))
        }
    }

    suspend fun unstarTrack(id: String) = withContext(Dispatchers.IO) {
        val client = currentClient
        try {
            client?.unstar(id)
        } catch (e: Exception) {
            Timber.w(e, "[Subsonic] Client unstarTrack failed for $id, trying REST fallback")
            executeRestAction("unstar.view", listOf("id" to id))
        }
    }

    suspend fun starAlbum(albumId: String) = withContext(Dispatchers.IO) {
        executeRestAction("star.view", listOf("albumId" to albumId))
    }

    suspend fun unstarAlbum(albumId: String) = withContext(Dispatchers.IO) {
        executeRestAction("unstar.view", listOf("albumId" to albumId))
    }

    suspend fun starArtist(artistId: String) = withContext(Dispatchers.IO) {
        executeRestAction("star.view", listOf("artistId" to artistId))
    }

    suspend fun unstarArtist(artistId: String) = withContext(Dispatchers.IO) {
        executeRestAction("unstar.view", listOf("artistId" to artistId))
    }

    suspend fun star(id: String) = starTrack(id)

    suspend fun unstar(id: String) = unstarTrack(id)

    suspend fun setRating(id: String, rating: Int) = withContext(Dispatchers.IO) {
        executeRestAction("setRating.view", listOf("id" to id, "rating" to rating.coerceIn(0, 5).toString()))
    }

    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Playlist? = withContext(Dispatchers.IO) {
        val baseUrl = currentServerUrl ?: return@withContext null
        val creds = credentialStore.load() ?: return@withContext null
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val httpUrl = "${baseUrl.trimEnd('/')}/rest/createPlaylist.view".toHttpUrlOrNull() ?: return@withContext null
        val queryUrl = httpUrl.newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")
            .build()

        val formBuilder = FormBody.Builder()
        formBuilder.add("name", name)
        songIds.forEach { songId ->
            formBuilder.add("songId", songId)
        }

        val request = Request.Builder()
            .url(queryUrl)
            .post(formBuilder.build())
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }
            val rawString = response.body.string()
            val sanitized = com.notmugil.uta.data.db.sanitizeSubsonicJson(rawString, json)
            val root = json.parseToJsonElement(sanitized).jsonObject["subsonic-response"]?.jsonObject
                ?: return@withContext null
            val playlistJson = root["playlist"] ?: return@withContext null
            json.decodeFromJsonElement(Playlist.serializer(), playlistJson)
        } catch (e: Exception) {
            Timber.w(e, "[Subsonic] Failed to create playlist '$name'")
            null
        }
    }

    suspend fun updatePlaylist(
        playlistId: String,
        name: String? = null,
        comment: String? = null,
        isPublic: Boolean? = null,
        songIdsToAdd: List<String> = emptyList(),
        songIndexesToRemove: List<Int> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        val formParams = mutableListOf<Pair<String, String>>()
        formParams.add("playlistId" to playlistId)
        if (!name.isNullOrBlank()) formParams.add("name" to name)
        if (comment != null) formParams.add("comment" to comment)
        if (isPublic != null) formParams.add("public" to isPublic.toString())
        songIdsToAdd.forEach { formParams.add("songIdToAdd" to it) }
        songIndexesToRemove.forEach { formParams.add("songIndexToRemove" to it.toString()) }

        executeRestPost("updatePlaylist.view", formParams)
    }

    suspend fun setPlaylistSongs(playlistId: String, songIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        val formParams = mutableListOf<Pair<String, String>>()
        formParams.add("playlistId" to playlistId)
        songIds.forEach { formParams.add("songId" to it) }
        executeRestPost("createPlaylist.view", formParams)
    }

    private fun cleanBaseUrl(url: String): String {
        var raw = url.trim().trimEnd('/')
        if (raw.endsWith("/rest", ignoreCase = true)) {
            raw = raw.substring(0, raw.length - 5).trimEnd('/')
        }
        return raw
    }

    suspend fun uploadPlaylistImage(playlistId: String, imageBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val auth = _authState.value as? AuthState.Authenticated
        val credentials = auth?.credentials ?: credentialStore.load() ?: return@withContext false
        try {
            var token = getNavidromeToken(credentials) ?: return@withContext false
            val cleanUrl = cleanBaseUrl(credentials.serverUrl)
            val mediaType = "image/jpeg".toMediaTypeOrNull()

            fun buildRequest(url: String, ndToken: String): Request {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", "cover.jpg", imageBytes.toRequestBody(mediaType))
                    .addFormDataPart("file", "cover.jpg", imageBytes.toRequestBody(mediaType))
                    .build()
                return Request.Builder()
                    .url(url)
                    .header("User-Agent", "Uta/1.0")
                    .header("Accept", "application/json")
                    .header("x-nd-authorization", "Bearer $ndToken")
                    .header("x-nd-token", ndToken)
                    .header("Authorization", "Bearer $ndToken")
                    .header("Cookie", "token=$ndToken")
                    .post(requestBody)
                    .build()
            }

            val baseUploadUrl = "$cleanUrl/api/playlist/$playlistId/image"
            var request = buildRequest(baseUploadUrl, token)
            var response = okHttpClient.newCall(request).execute()

            // If 404, attempt fallback to host root if serverUrl had a subpath
            if (response.code == 404) {
                response.close()
                val uri = runCatching { java.net.URI(credentials.serverUrl) }.getOrNull()
                if (uri != null) {
                    val rootUrl = "${uri.scheme}://${uri.authority}"
                    if (rootUrl != cleanUrl) {
                        val fallbackUploadUrl = "$rootUrl/api/playlist/$playlistId/image"
                        request = buildRequest(fallbackUploadUrl, token)
                        response = okHttpClient.newCall(request).execute()
                    }
                }
            }

            if (response.code == 401) {
                response.close()
                cachedNavidromeToken = null
                token = getNavidromeToken(credentials) ?: return@withContext false
                request = buildRequest(baseUploadUrl, token)
                response = okHttpClient.newCall(request).execute()
            }

            val success = response.isSuccessful
            Timber.d("[Subsonic] uploadPlaylistImage status=${response.code} success=$success")
            response.close()
            playlistInfoCache.remove(playlistId)
            success
        } catch (e: Exception) {
            Timber.w(e, "[Subsonic] uploadPlaylistImage failed on Navidrome")
            false
        }
    }

    suspend fun deletePlaylistImage(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        val auth = _authState.value as? AuthState.Authenticated
        val credentials = auth?.credentials ?: credentialStore.load() ?: return@withContext false
        try {
            var token = getNavidromeToken(credentials) ?: return@withContext false
            val cleanUrl = cleanBaseUrl(credentials.serverUrl)

            fun buildRequest(url: String, ndToken: String): Request {
                return Request.Builder()
                    .url(url)
                    .header("User-Agent", "Uta/1.0")
                    .header("Accept", "application/json")
                    .header("x-nd-authorization", "Bearer $ndToken")
                    .header("x-nd-token", ndToken)
                    .header("Authorization", "Bearer $ndToken")
                    .header("Cookie", "token=$ndToken")
                    .delete()
                    .build()
            }

            val baseDeleteUrl = "$cleanUrl/api/playlist/$playlistId/image"
            var request = buildRequest(baseDeleteUrl, token)
            var response = okHttpClient.newCall(request).execute()

            if (response.code == 404) {
                response.close()
                val uri = runCatching { java.net.URI(credentials.serverUrl) }.getOrNull()
                if (uri != null) {
                    val rootUrl = "${uri.scheme}://${uri.authority}"
                    if (rootUrl != cleanUrl) {
                        val fallbackDeleteUrl = "$rootUrl/api/playlist/$playlistId/image"
                        request = buildRequest(fallbackDeleteUrl, token)
                        response = okHttpClient.newCall(request).execute()
                    }
                }
            }

            if (response.code == 401) {
                response.close()
                cachedNavidromeToken = null
                token = getNavidromeToken(credentials) ?: return@withContext false
                request = buildRequest(baseDeleteUrl, token)
                response = okHttpClient.newCall(request).execute()
            }

            val success = response.isSuccessful
            Timber.d("[Subsonic] deletePlaylistImage status=${response.code} success=$success")
            response.close()
            playlistInfoCache.remove(playlistId)
            success
        } catch (e: Exception) {
            Timber.w(e, "[Subsonic] deletePlaylistImage failed on Navidrome")
            false
        }
    }

    private val playlistInfoCache = java.util.concurrent.ConcurrentHashMap<String, NavidromePlaylistInfo>()
    private var cachedNavidromeToken: String? = null
    private var cachedNavidromeTokenServer: String? = null

    fun getNavidromePlaylistInfoCached(playlistId: String): NavidromePlaylistInfo? = playlistInfoCache[playlistId]

    private suspend fun getNavidromeToken(credentials: StoredCredentials): String? = withContext(Dispatchers.IO) {
        if (cachedNavidromeToken != null && cachedNavidromeTokenServer == credentials.serverUrl) {
            return@withContext cachedNavidromeToken
        }
        try {
            val jsonBody = buildJsonObject {
                put("username", credentials.username)
                put("password", credentials.password)
            }.toString()

            val cleanUrl = cleanBaseUrl(credentials.serverUrl)
            val baseLoginUrl = "$cleanUrl/auth/login"
            var request = Request.Builder()
                .url(baseLoginUrl)
                .header("User-Agent", "Uta/1.0")
                .header("Accept", "application/json")
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()

            var response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                // Try fallback to host root
                val uri = runCatching { java.net.URI(credentials.serverUrl) }.getOrNull()
                if (uri != null) {
                    val rootUrl = "${uri.scheme}://${uri.authority}"
                    if (rootUrl != cleanUrl) {
                        request = Request.Builder()
                            .url("$rootUrl/auth/login")
                            .header("User-Agent", "Uta/1.0")
                            .header("Accept", "application/json")
                            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                            .build()
                        response = okHttpClient.newCall(request).execute()
                    }
                }
            }

            if (response.isSuccessful) {
                val bodyText = response.body.string()
                response.close()
                val jsonElement = json.parseToJsonElement(bodyText)
                val token = jsonElement.jsonObject["token"]?.jsonPrimitive?.content
                if (token != null) {
                    cachedNavidromeToken = token
                    cachedNavidromeTokenServer = credentials.serverUrl
                    return@withContext token
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Timber.d(e, "[Subsonic] Failed to login to Navidrome API")
        }
        null
    }

    suspend fun getNavidromePlaylistInfo(playlistId: String): NavidromePlaylistInfo? = withContext(Dispatchers.IO) {
        val cached = playlistInfoCache[playlistId]
        val auth = _authState.value as? AuthState.Authenticated
        val credentials = auth?.credentials ?: credentialStore.load()
        if (credentials == null) return@withContext cached

        try {
            val token = getNavidromeToken(credentials)
            if (token != null) {
                var request = Request.Builder()
                    .url("${credentials.serverUrl.trimEnd('/')}/api/playlist/$playlistId")
                    .header("x-nd-authorization", "Bearer $token")
                    .get()
                    .build()

                var response = okHttpClient.newCall(request).execute()
                if (response.code == 401) {
                    response.close()
                    cachedNavidromeToken = null
                    val freshToken = getNavidromeToken(credentials)
                    if (freshToken != null) {
                        request = Request.Builder()
                            .url("${credentials.serverUrl.trimEnd('/')}/api/playlist/$playlistId")
                            .header("x-nd-authorization", "Bearer $freshToken")
                            .get()
                            .build()
                        response = okHttpClient.newCall(request).execute()
                    }
                }

                if (response.isSuccessful) {
                    val bodyText = response.body.string()
                    response.close()
                    val obj = json.parseToJsonElement(bodyText).jsonObject
                    val rules = obj["rules"]
                    val isSmart = rules != null && rules !is kotlinx.serialization.json.JsonNull
                    val sync = obj["sync"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val ownerName = obj["ownerName"]?.jsonPrimitive?.content
                    val isOwner = ownerName == null || ownerName.equals(credentials.username, ignoreCase = true)
                    val canEditTracks = !isSmart && !sync && isOwner

                    val info = NavidromePlaylistInfo(
                        id = playlistId,
                        isSmart = isSmart,
                        isSync = sync,
                        isOwner = isOwner,
                        canEditTracks = canEditTracks,
                        description = obj["comment"]?.jsonPrimitive?.content
                    )
                    playlistInfoCache[playlistId] = info
                    return@withContext info
                } else {
                    response.close()
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "[Subsonic] Could not fetch native Navidrome playlist info: ${e.message}")
        }

        cached
    }

    suspend fun fetchAllNavidromePlaylists(): List<NavidromePlaylistInfo> = withContext(Dispatchers.IO) {
        val auth = _authState.value as? AuthState.Authenticated
        val credentials = auth?.credentials ?: credentialStore.load() ?: return@withContext emptyList()

        try {
            val token = getNavidromeToken(credentials) ?: return@withContext emptyList()
            var request = Request.Builder()
                .url("${credentials.serverUrl.trimEnd('/')}/api/playlist")
                .header("x-nd-authorization", "Bearer $token")
                .get()
                .build()

            var response = okHttpClient.newCall(request).execute()
            if (response.code == 401) {
                response.close()
                cachedNavidromeToken = null
                val freshToken = getNavidromeToken(credentials)
                if (freshToken != null) {
                    request = Request.Builder()
                        .url("${credentials.serverUrl.trimEnd('/')}/api/playlist")
                        .header("x-nd-authorization", "Bearer $freshToken")
                        .get()
                        .build()
                    response = okHttpClient.newCall(request).execute()
                }
            }

            if (response.isSuccessful) {
                val bodyText = response.body.string()
                response.close()
                val array = json.parseToJsonElement(bodyText).jsonArray
                val list = array.mapNotNull { element ->
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val rules = obj["rules"]
                    val isSmart = rules != null && rules !is kotlinx.serialization.json.JsonNull
                    val sync = obj["sync"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val ownerName = obj["ownerName"]?.jsonPrimitive?.content
                    val isOwner = ownerName == null || ownerName.equals(credentials.username, ignoreCase = true)
                    val canEditTracks = !isSmart && !sync && isOwner

                    val info = NavidromePlaylistInfo(
                        id = id,
                        isSmart = isSmart,
                        isSync = sync,
                        isOwner = isOwner,
                        canEditTracks = canEditTracks,
                        description = obj["comment"]?.jsonPrimitive?.content
                    )
                    playlistInfoCache[id] = info
                    info
                }
                return@withContext list
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Timber.d(e, "[Subsonic] Could not fetch native Navidrome playlists list")
        }
        emptyList()
    }

    suspend fun deletePlaylist(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        executeRestPost("deletePlaylist.view", listOf("id" to playlistId))
    }

    suspend fun createShareLink(id: String, description: String? = null): String? = withContext(Dispatchers.IO) {
        val baseUrl = currentServerUrl ?: credentialStore.load()?.serverUrl ?: return@withContext null
        val creds = credentialStore.load() ?: return@withContext null
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val cleanUrl = cleanBaseUrl(baseUrl)
        val httpUrl = "${cleanUrl}/rest/createShare.view".toHttpUrlOrNull() ?: return@withContext null
        val builder = httpUrl.newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")
            .addQueryParameter("id", id)

        if (!description.isNullOrBlank()) {
            builder.addQueryParameter("description", description)
        }

        val request = Request.Builder().url(builder.build()).get().build()
        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }
            val rawString = response.body.string()
            response.close()
            val sanitized = com.notmugil.uta.data.db.sanitizeSubsonicJson(rawString, json)
            val root = json.parseToJsonElement(sanitized).jsonObject["subsonic-response"]?.jsonObject
                ?: return@withContext null
            val sharesObj = root["shares"]?.jsonObject
            val shareElement = sharesObj?.get("share")
            val shareUrl = when (shareElement) {
                is JsonArray -> shareElement.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                is JsonObject -> shareElement["url"]?.jsonPrimitive?.content
                else -> null
            }
            if (!shareUrl.isNullOrBlank()) {
                if (shareUrl.startsWith("http://") || shareUrl.startsWith("https://")) {
                    return@withContext shareUrl
                } else {
                    return@withContext "$cleanUrl/${shareUrl.removePrefix("/")}"
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "[Subsonic] createShare failed for id=$id")
        }
        null
    }

    private suspend fun executeRestPost(endpoint: String, params: List<Pair<String, String>>): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = currentServerUrl ?: return@withContext false
        val creds = credentialStore.load() ?: return@withContext false
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val httpUrl = "${baseUrl.trimEnd('/')}/rest/$endpoint".toHttpUrlOrNull() ?: return@withContext false
        val queryUrl = httpUrl.newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")
            .build()

        val formBuilder = FormBody.Builder()
        params.forEach { (key, value) ->
            formBuilder.add(key, value)
        }

        val request = Request.Builder()
            .url(queryUrl)
            .post(formBuilder.build())
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val successful = response.isSuccessful
            response.close()
            successful
        } catch (e: Exception) {
            Timber.w(e, "[Subsonic] Failed to execute POST $endpoint")
            false
        }
    }

    private fun executeRestAction(endpoint: String, params: List<Pair<String, String>>) {
        val baseUrl = currentServerUrl ?: return
        val creds = credentialStore.load() ?: return
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val httpUrl = "${baseUrl.trimEnd('/')}/rest/$endpoint".toHttpUrlOrNull() ?: return
        val builder = httpUrl.newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")

        params.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }

        val request = Request.Builder().url(builder.build()).get().build()
        try {
            okHttpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            Timber.w(e, "[Subsonic] Failed to execute $endpoint")
        }
    }

    suspend fun scrobble(id: String, submission: Boolean, time: Long? = null) = withContext(Dispatchers.IO) {
        val client = currentClient ?: return@withContext
        try {
            val instant = Instant.fromEpochMilliseconds(time ?: java.lang.System.currentTimeMillis())
            client.scrobble(id = id, time = instant, submission = submission)
        } catch (e: Exception) {
            timber.log.Timber.w("[Subsonic] Scrobble failed for $id (submission=$submission): ${e.message}")
        }
    }

    suspend fun getOpenSubsonicExtensions(): List<String> = withContext(Dispatchers.IO) {
        val baseUrl = currentServerUrl ?: return@withContext emptyList()
        val creds = credentialStore.load() ?: return@withContext emptyList()
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val httpUrl = "${baseUrl.trimEnd('/')}/rest/getOpenSubsonicExtensions.view".toHttpUrlOrNull()
            ?: return@withContext emptyList()

        val url = httpUrl.newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")
            .build()

        try {
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val rawString = response.body.string()
            val root = json.parseToJsonElement(rawString).jsonObject["subsonic-response"]?.jsonObject
                ?: return@withContext emptyList()

            val extensionsElem = root["openSubsonicExtensions"] ?: return@withContext emptyList()
            val extList = mutableListOf<String>()
            when {
                extensionsElem is kotlinx.serialization.json.JsonArray -> {
                    extensionsElem.forEach { elem ->
                        elem.jsonObject["name"]?.jsonPrimitive?.content?.let { extList.add(it) }
                    }
                }
                extensionsElem is kotlinx.serialization.json.JsonObject -> {
                    val array = extensionsElem["extension"]?.let {
                        if (it is kotlinx.serialization.json.JsonArray) it else kotlinx.serialization.json.JsonArray(listOf(it))
                    }
                    array?.forEach { elem ->
                        elem.jsonObject["name"]?.jsonPrimitive?.content?.let { extList.add(it) }
                    }
                }
            }
            extList
        } catch (e: Exception) {
            Timber.d(e, "[Subsonic] getOpenSubsonicExtensions not supported or failed")
            emptyList()
        }
    }

    suspend fun getServerPlayQueue(): ServerPlayQueue? = withContext(Dispatchers.IO) {
        val baseUrl = currentServerUrl ?: return@withContext null
        val creds = credentialStore.load() ?: return@withContext null
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        // Attempt getPlayQueue.view
        val httpUrl = "${baseUrl.trimEnd('/')}/rest/getPlayQueue.view".toHttpUrlOrNull() ?: return@withContext null
        val url = httpUrl.newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")
            .build()

        try {
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val rawString = response.body.string()
            val root = json.parseToJsonElement(rawString).jsonObject["subsonic-response"]?.jsonObject
                ?: return@withContext null

            val pq = root["playQueue"]?.jsonObject ?: return@withContext null
            val current = pq["current"]?.jsonPrimitive?.content
            val currentIndex = pq["currentIndex"]?.jsonPrimitive?.intOrNull
            val position = pq["position"]?.jsonPrimitive?.longOrNull ?: 0L
            val changed = pq["changed"]?.jsonPrimitive?.content
            val changedBy = pq["changedBy"]?.jsonPrimitive?.content

            val entries = pq["entry"]?.let {
                if (it is kotlinx.serialization.json.JsonArray) it else kotlinx.serialization.json.JsonArray(listOf(it))
            } ?: kotlinx.serialization.json.JsonArray(emptyList())

            val trackIds = entries.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }

            ServerPlayQueue(
                currentTrackId = current,
                currentIndex = currentIndex,
                positionMs = position,
                trackIds = trackIds,
                changed = changed,
                changedBy = changedBy
            )
        } catch (e: Exception) {
            Timber.w(e, "[Subsonic] Failed to fetch server play queue")
            null
        }
    }

    suspend fun saveServerPlayQueue(
        trackIds: List<String>,
        currentId: String? = null,
        currentIndex: Int? = null,
        positionMs: Long? = null
    ) = withContext(Dispatchers.IO) {
        val baseUrl = currentServerUrl ?: return@withContext
        val creds = credentialStore.load() ?: return@withContext
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((creds.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val httpUrl = "${baseUrl.trimEnd('/')}/rest/savePlayQueue.view".toHttpUrlOrNull() ?: return@withContext
        val queryUrl = httpUrl.newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")
            .build()

        // Use FormBody POST to avoid URL length limitations with large 1000+ queues
        val formBuilder = FormBody.Builder()
        trackIds.forEach { trackId ->
            formBuilder.add("id", trackId)
        }
        if (!currentId.isNullOrBlank()) {
            formBuilder.add("current", currentId)
        }
        if (currentIndex != null && currentIndex >= 0) {
            formBuilder.add("currentIndex", currentIndex.toString())
        }
        if (positionMs != null && positionMs >= 0L) {
            formBuilder.add("position", positionMs.toString())
        }

        val request = Request.Builder()
            .url(queryUrl)
            .post(formBuilder.build())
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.close()
        } catch (e: Exception) {
            Timber.w(e, "[Subsonic] savePlayQueue network call failed")
        }
    }

    suspend fun getLyrics(artist: String, title: String): SubsonicLyrics? = withContext(Dispatchers.IO) {
        val auth = _authState.value as? AuthState.Authenticated
        val credentials = auth?.credentials ?: credentialStore.load() ?: return@withContext null
        val rawBaseUrl = currentServerUrl ?: credentials.serverUrl
        val baseUrl = rawBaseUrl.trimEnd('/')

        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((credentials.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val urlBuilder = (baseUrl + "/rest/getLyrics.view").toHttpUrlOrNull()?.newBuilder()
            ?: (baseUrl + "/rest/getLyrics").toHttpUrlOrNull()?.newBuilder()
            ?: return@withContext null

        urlBuilder
            .addQueryParameter("u", credentials.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Uta")
            .addQueryParameter("f", "json")
            .addQueryParameter("artist", artist)
            .addQueryParameter("title", title)

        val request = Request.Builder().url(urlBuilder.build()).get().build()
        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }
            val rawString = response.body.string()
            response.close()

            val sanitized = com.notmugil.uta.data.db.sanitizeSubsonicJson(rawString, json)
            val root = json.parseToJsonElement(sanitized).jsonObject["subsonic-response"]?.jsonObject
                ?: return@withContext null
            val lyricsObj = root["lyrics"]?.jsonObject ?: return@withContext null
            val art = lyricsObj["artist"]?.jsonPrimitive?.content
            val tit = lyricsObj["title"]?.jsonPrimitive?.content
            val value = lyricsObj["value"]?.jsonPrimitive?.content
                ?: lyricsObj["content"]?.jsonPrimitive?.content
                ?: lyricsObj["text"]?.jsonPrimitive?.content
            if (!value.isNullOrBlank()) {
                SubsonicLyrics(artist = art, title = tit, value = value)
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.d(e, "[Subsonic] Plain lyrics not found or error for $title - $artist")
            null
        }
    }

    suspend fun getStructuredLyrics(songId: String): List<StructuredLyrics> = withContext(Dispatchers.IO) {
        try {
            execute { it.getLyrics(id = songId) }
        } catch (e: Exception) {
            Timber.w(e, "[Subsonic] Failed to fetch structured lyrics for $songId")
            emptyList()
        }
    }

    suspend fun getTopSongs(artist: String, count: Int = 20): List<dev.zt64.subsonic.api.model.Song> = withContext(Dispatchers.IO) {
        try {
            execute { it.getTopSongs(artist = artist, count = count) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "[Subsonic] Failed to fetch top songs for artist $artist")
            emptyList()
        }
    }

    fun getAvatarUrl(username: String?): String? {
        val user = username?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        val auth = _authState.value as? AuthState.Authenticated
        val credentials = auth?.credentials ?: credentialStore.load() ?: return null
        val rawBaseUrl = currentServerUrl ?: credentials.serverUrl
        val baseUrl = rawBaseUrl.trimEnd('/')
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md5 = MessageDigest.getInstance("MD5")
        val token = md5.digest((credentials.password + salt).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$baseUrl/rest/getAvatar?username=${android.net.Uri.encode(user)}&u=${android.net.Uri.encode(credentials.username)}&t=$token&s=$salt&v=1.16.1&c=Uta"
    }

    fun setFallbackServerUrl(url: String?) {
        // Fallback server URL is stored and managed via AppPreferences
    }

    private fun hashServerUrl(url: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(url.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
