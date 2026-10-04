package com.notmugil.uta.data

import dev.zt64.subsonic.api.model.SubsonicErrorCode
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubsonicRepositoryTest {

    private val credentialStore = mockk<CredentialStore>(relaxed = true)
    private val okHttpClient = mockk<OkHttpClient>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var repository: SubsonicRepository

    private val networkMonitor = mockk<NetworkMonitor>(relaxed = true)

    @Before
    fun setUp() {
        repository = SubsonicRepository(credentialStore, okHttpClient, json, networkMonitorProvider = { networkMonitor })
    }

    @Test
    fun `normalizeUrl handles missing scheme`() {
        assertEquals("http://music.example.com", repository.normalizeUrl("music.example.com"))
        assertEquals("http://192.168.1.100:4533", repository.normalizeUrl("192.168.1.100:4533"))
    }

    @Test
    fun `normalizeUrl strips trailing slashes`() {
        assertEquals("https://music.example.com", repository.normalizeUrl("https://music.example.com/"))
        assertEquals("http://music.example.com:4533", repository.normalizeUrl("http://music.example.com:4533///"))
    }

    @Test
    fun `normalizeUrl strips rest suffix`() {
        assertEquals("https://music.example.com", repository.normalizeUrl("https://music.example.com/rest"))
        assertEquals("https://music.example.com", repository.normalizeUrl("https://music.example.com/rest/"))
        assertEquals("http://music.example.com:4533/subsonic", repository.normalizeUrl("http://music.example.com:4533/subsonic/rest/"))
    }

    @Test
    fun `normalizeUrl handles reverse proxy subpath and custom ports`() {
        assertEquals("https://example.com:8443/music", repository.normalizeUrl("https://example.com:8443/music/"))
    }

    @Test
    fun `normalizeUrl handles IPv6 addresses`() {
        assertEquals("http://[::1]:4533", repository.normalizeUrl("[::1]:4533"))
        assertEquals("https://[2001:db8::1]:4533", repository.normalizeUrl("https://[2001:db8::1]:4533/"))
    }

    @Test
    fun `normalizeUrl lowercases hostnames while keeping path`() {
        assertEquals("https://my-server.com:4533/Subsonic", repository.normalizeUrl("HTTPS://MY-SERVER.COM:4533/Subsonic/rest/"))
    }

    @Test
    fun `isAuthError identifies authentication error codes`() {
        assertTrue(repository.isAuthError(SubsonicErrorCode.WRONG_USERNAME_OR_PASSWORD))
        assertTrue(repository.isAuthError(SubsonicErrorCode.UNAUTHORIZED))
        assertTrue(repository.isAuthError(SubsonicErrorCode.AUTH_METHOD_NOT_SUPPORTED))
        assertFalse(repository.isAuthError(SubsonicErrorCode.DATA_NOT_FOUND))
        assertFalse(repository.isAuthError(SubsonicErrorCode.GENERIC))
    }

    @Test
    fun `getStreamUrl constructs valid url`() {
        io.mockk.every { credentialStore.load() } returns StoredCredentials(
            serverUrl = "https://music.example.com",
            username = "testuser",
            password = "testpassword"
        )
        val url = repository.getStreamUrl("track-123")
        assertTrue(url != null)
        assertTrue(url!!.startsWith("https://music.example.com/rest/stream"))
        assertTrue(url.contains("id=track-123"))
        assertTrue(url.contains("u=testuser"))
        assertTrue(url.contains("t="))
        assertTrue(url.contains("s="))
    }

    @Test
    fun `getStreamUrl includes format and maxBitRate`() {
        io.mockk.every { credentialStore.load() } returns StoredCredentials(
            serverUrl = "https://music.example.com",
            username = "testuser",
            password = "testpassword"
        )
        val url = repository.getStreamUrl("track-123", maxBitRate = 192, format = "mp3")
        assertTrue(url != null)
        assertTrue(url!!.contains("format=mp3"))
        assertTrue(url.contains("maxBitRate=192"))
    }

    @Test
    fun `isUsingFallback detects when active server matches fallback URL`() {
        val appPreferences = mockk<com.notmugil.uta.data.preferences.AppPreferences>(relaxed = true)
        io.mockk.every { appPreferences.fallbackServerUrl } returns kotlinx.coroutines.flow.MutableStateFlow("https://fallback.example.com")
        io.mockk.every { credentialStore.load() } returns StoredCredentials(
            serverUrl = "https://primary.example.com",
            username = "testuser",
            password = "testpassword"
        )

        val repo = SubsonicRepository(
            credentialStore = credentialStore,
            okHttpClient = okHttpClient,
            json = json,
            networkMonitorProvider = { networkMonitor },
            appPreferencesProvider = { appPreferences }
        )

        assertEquals("https://primary.example.com", repo.primaryServerUrl)
        assertEquals("https://fallback.example.com", repo.fallbackServerUrl)
        assertFalse(repo.isUsingFallback)
    }
}
