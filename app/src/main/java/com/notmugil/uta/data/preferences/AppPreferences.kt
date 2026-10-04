package com.notmugil.uta.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    FRENCH("fr", "French", "Français");

    val label: String get() = "$nativeName ($code)"

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.find { it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) } ?: ENGLISH
    }
}

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    DARK("Dark"),
    LIGHT("Light (Experimental)")
}

enum class DynamicColorSource(val displayName: String) {
    COVER_ONLY("Cover art"),
    WALLPAPER_ONLY("Device wallpaper"),
    BOTH("Both");

    companion object {
        fun fromString(value: String?): DynamicColorSource =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: BOTH
    }
}

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = androidx.compose.ui.text.googlefonts.R.array.com_google_android_gms_fonts_certs
)

private fun createGoogleFontFamily(name: String): FontFamily {
    val font = GoogleFont(name)
    return FontFamily(
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Normal),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Medium),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.Bold),
        Font(googleFont = font, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold)
    )
}

enum class AppFont(val displayName: String, val fontFamily: FontFamily) {
    SYSTEM_DEFAULT("System Default", FontFamily.Default),
    ROBOTO("Roboto", createGoogleFontFamily("Roboto")),
    NOTO_SANS("Noto Sans", createGoogleFontFamily("Noto Sans")),
    NOTO_SERIF("Noto Serif", createGoogleFontFamily("Noto Serif")),
    OPEN_SANS("Open Sans", createGoogleFontFamily("Open Sans")),
    LATO("Lato", createGoogleFontFamily("Lato")),
    MONTSERRAT("Montserrat", createGoogleFontFamily("Montserrat")),
    POPPINS("Poppins", createGoogleFontFamily("Poppins")),
    JETBRAINS_MONO("JetBrains Mono", createGoogleFontFamily("JetBrains Mono")),
    SANS_SERIF("Sans-serif", FontFamily.SansSerif),
    SERIF("Serif", FontFamily.Serif),
    MONOSPACE("Monospace", FontFamily.Monospace)
}

enum class MiniPlayerStyle(val displayName: String) {
    DEFAULT("Default"),
    ROTATING_VINYL("Rotating"),
    CAPSULE_NEEDLE("Capsule"),
    FLUSH_COVER("Minimal"),
    AMBIENT("Ambient")
}

enum class SeekBarStyle(val displayName: String) {
    WAVY("Material"),
    STANDARD("Standard"),
    WAVEFORM("Waveform"),
    NEEDLE("Needle")
}

enum class PlayerStyle(val displayName: String) {
    DEFAULT("Default"),
    MODERN("Modern"),
    CINEMATIC("Cinematic"),
    LYRICS("Lyrics View"),
    COVER("Cover View")
}

enum class LyricsSourceMode(val displayName: String) {
    BOTH("Online + Server"),
    SERVER_ONLY("Server"),
    DISABLED("No lyrics");

    companion object {
        fun fromString(value: String?): LyricsSourceMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: BOTH
    }
}

enum class LyricsProvider(val displayName: String, val shortName: String) {
    AUTO("Auto", "Auto"),
    SUBSONIC("Server", "Server"),
    LRCLIB("LRCLIB", "LRCLIB"),
    PAXSENIX("Paxsenix", "Paxsenix")
}

data class LyricsProviderConfig(val provider: LyricsProvider, val enabled: Boolean = true)

enum class MiniPlayerPlacement(val displayName: String) {
    ISOLATED("Floating"),
    COMBINED("Docked")
}

enum class StreamingBitrate(val displayName: String, val kbps: Int) {
    AUTO("Auto", 0),
    BITRATE_128("128 kbps", 128),
    BITRATE_192("192 kbps", 192),
    BITRATE_256("256 kbps", 256),
    BITRATE_320("320 kbps", 320),
    UNLIMITED("No transcoding", 0)
}

enum class TranscodingFormat(val displayName: String, val format: String) {
    RAW("Server Default", "raw"),
    OPUS("Opus", "opus"),
    MP3("MP3", "mp3"),
    AAC("AAC", "aac"),
    FLAC("FLAC", "flac")
}

enum class DownloadQualityPreference(val displayName: String, val kbps: Int) {
    ORIGINAL("Original", 0),
    BITRATE_320("320 kbps", 320),
    BITRATE_256("256 kbps", 256),
    BITRATE_192("192 kbps", 192),
    BITRATE_128("128 kbps", 128)
}

enum class DownloadFormatPreference(val displayName: String, val extension: String) {
    ORIGINAL("Server Default", "raw"),
    OPUS("Opus", "opus"),
    MP3("MP3", "mp3"),
    FLAC("FLAC", "flac"),
    AAC("AAC", "aac")
}

enum class BottomNavIndicatorStyle(val displayName: String) {
    PILL("Pill"),
    DOT("Dot"),
    NONE("None")
}

enum class PlaylistCoverStyle(val displayName: String) {
    GRID("Grid (2x2)"),
    SINGLE("First Song"),
    MOSAIC("Mosaic")
}

enum class TrackSwipeAction(val displayName: String) {
    PLAY_NEXT("Play Next"),
    ADD_TO_QUEUE("Add to Queue"),
    TOGGLE_LIKE("Like / Favorite"),
    DOWNLOAD("Download"),
    NONE("None")
}

enum class CoverArtQuality(val displayName: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}

val LocalAppPreferences = staticCompositionLocalOf<AppPreferences?> {
    null
}

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "uta_app_preferences"
        private const val KEY_SCROBBLING_ENABLED = "scrobbling_enabled"
        private const val KEY_SCROBBLE_THRESHOLD_PERCENT = "scrobble_threshold_percent"
        private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_OFFLINE_MODE = "offline_mode_manual"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_DYNAMIC_ACCENT_MODE = "dynamic_accent_mode"
        private const val KEY_CUSTOM_ACCENT_COLOR = "custom_accent_color"
        private const val KEY_ALBUM_VIEW_MODE = "album_view_mode"
        private const val KEY_PLAYLIST_VIEW_MODE = "playlist_view_mode"
        private const val KEY_FONT_PREFERENCE = "app_font_preference"
        private const val KEY_MINI_PLAYER_STYLE = "mini_player_style"
        private const val KEY_MINI_PLAYER_PLACEMENT = "mini_player_placement"
        private const val KEY_TINT_MINIPLAYER_ACCENT = "tint_miniplayer_accent"
        private const val KEY_PLAYER_STYLE = "player_style"
        private const val KEY_SEEK_BAR_STYLE = "seek_bar_style"
        private const val KEY_LYRICS_SOURCE_MODE = "lyrics_source_mode"
        private const val KEY_ONLINE_LYRICS_PROVIDERS = "online_lyrics_providers"
        private const val KEY_WIFI_STREAMING_BITRATE = "wifi_streaming_bitrate"
        private const val KEY_CELLULAR_STREAMING_BITRATE = "cellular_streaming_bitrate"
        private const val KEY_TRANSCODING_FORMAT = "transcoding_format"
        private const val KEY_AUTO_NEXT_ENABLED = "auto_next_enabled"
        private const val KEY_PAUSE_ON_DISCONNECT = "pause_on_disconnect"
        private const val KEY_AUTO_RESUME = "auto_resume"
        private const val KEY_DOWNLOAD_QUALITY = "download_quality"
        private const val KEY_DOWNLOAD_FORMAT = "download_format"
        private const val KEY_PREFETCH_UPCOMING_ENABLED = "prefetch_upcoming_enabled"
        private const val KEY_PREFETCH_TRACK_COUNT = "prefetch_track_count"
        private const val KEY_PREFETCH_ONLY_ON_WIFI = "prefetch_only_on_wifi"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_LANGUAGE_PREFERENCE = "language_preference"
        private const val KEY_APP_LANGUAGE = "app_language_preference"
        private const val KEY_DYNAMIC_COLOR_SOURCE = "dynamic_color_source"
        private const val KEY_BOTTOM_NAV_INDICATOR_STYLE = "bottom_nav_indicator_style"
        private const val KEY_PLAYLIST_COVER_STYLE = "playlist_cover_style"
        private const val KEY_SWIPE_RIGHT_ACTION = "swipe_right_action"
        private const val KEY_SWIPE_LEFT_ACTION = "swipe_left_action"
        private const val KEY_ALWAYS_SHOW_NAV_BAR = "always_show_nav_bar"
        private const val KEY_ANIMATED_ARTWORK_ENABLED = "animated_artwork_enabled"
        private const val KEY_FALLBACK_SERVER_URL = "fallback_server_url"
        private const val KEY_SHOW_EXTERNAL_LINKS = "show_external_links"
        private const val KEY_SHOW_LASTFM_LINKS = "show_lastfm_links"
        private const val KEY_SHOW_MUSICBRAINZ_LINKS = "show_musicbrainz_links"
        private const val KEY_COVER_ART_QUALITY = "cover_art_quality"
        private const val KEY_KEEP_SCREEN_ON_LYRICS = "keep_screen_on_lyrics"
        private const val KEY_BLUR_INACTIVE_LYRICS = "blur_inactive_lyrics"
    }

    private inline fun <reified T : Enum<T>> getEnumPreference(key: String, defaultValue: T): T {
        val name = prefs.getString(key, defaultValue.name) ?: return defaultValue
        return enumValues<T>().find { it.name.equals(name, ignoreCase = true) } ?: defaultValue
    }

    private val _fontPreference = MutableStateFlow(getEnumPreference(KEY_FONT_PREFERENCE, AppFont.SYSTEM_DEFAULT))
    val fontPreference: StateFlow<AppFont> = _fontPreference.asStateFlow()

    private val _miniPlayerStyle = MutableStateFlow(getEnumPreference(KEY_MINI_PLAYER_STYLE, MiniPlayerStyle.DEFAULT))
    val miniPlayerStyle: StateFlow<MiniPlayerStyle> = _miniPlayerStyle.asStateFlow()

    private val _miniPlayerPlacement = MutableStateFlow(getEnumPreference(KEY_MINI_PLAYER_PLACEMENT, MiniPlayerPlacement.ISOLATED))
    val miniPlayerPlacement: StateFlow<MiniPlayerPlacement> = _miniPlayerPlacement.asStateFlow()

    private val _tintMiniPlayerAccent = MutableStateFlow(
        prefs.getBoolean(KEY_TINT_MINIPLAYER_ACCENT, true)
    )
    val tintMiniPlayerAccent: StateFlow<Boolean> = _tintMiniPlayerAccent.asStateFlow()

    private val _isScrobblingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SCROBBLING_ENABLED, true)
    )
    val isScrobblingEnabled: StateFlow<Boolean> = _isScrobblingEnabled.asStateFlow()

    private val _scrobbleThresholdPercent = MutableStateFlow(
        prefs.getInt(KEY_SCROBBLE_THRESHOLD_PERCENT, 50).coerceIn(1, 100)
    )
    val scrobbleThresholdPercent: StateFlow<Int> = _scrobbleThresholdPercent.asStateFlow()

    private val _wifiOnlyDownloads = MutableStateFlow(
        prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, false)
    )
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()

    private val _autoSyncEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
    )
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    private val _isOfflineModeManual = MutableStateFlow(
        prefs.getBoolean(KEY_OFFLINE_MODE, false)
    )
    val isOfflineModeManual: StateFlow<Boolean> = _isOfflineModeManual.asStateFlow()

    private val _themeMode = MutableStateFlow(getEnumPreference(KEY_THEME_MODE, AppThemeMode.SYSTEM))
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColorSource = MutableStateFlow(getEnumPreference(KEY_DYNAMIC_COLOR_SOURCE, DynamicColorSource.BOTH))
    val dynamicColorSource: StateFlow<DynamicColorSource> = _dynamicColorSource.asStateFlow()

    private val _customAccentColor = MutableStateFlow<Int?>(
        valStoredColor(prefs.getInt(KEY_CUSTOM_ACCENT_COLOR, -1))
    )
    val customAccentColor: StateFlow<Int?> = _customAccentColor.asStateFlow()

    private val _albumViewMode = MutableStateFlow(getEnumPreference(KEY_ALBUM_VIEW_MODE, com.notmugil.uta.domain.model.ViewDisplayMode.TEXT_ONLY))
    val albumViewMode: StateFlow<com.notmugil.uta.domain.model.ViewDisplayMode> = _albumViewMode.asStateFlow()

    private val _playlistViewMode = MutableStateFlow(getEnumPreference(KEY_PLAYLIST_VIEW_MODE, com.notmugil.uta.domain.model.ViewDisplayMode.LIST))
    val playlistViewMode: StateFlow<com.notmugil.uta.domain.model.ViewDisplayMode> = _playlistViewMode.asStateFlow()

    private val _playerStyle = MutableStateFlow(getEnumPreference(KEY_PLAYER_STYLE, PlayerStyle.DEFAULT))
    val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()

    private val _seekBarStyle = MutableStateFlow(getEnumPreference(KEY_SEEK_BAR_STYLE, SeekBarStyle.WAVY))
    val seekBarStyle: StateFlow<SeekBarStyle> = _seekBarStyle.asStateFlow()

    private val _lyricsSourceMode = MutableStateFlow(getEnumPreference(KEY_LYRICS_SOURCE_MODE, LyricsSourceMode.BOTH))
    val lyricsSourceMode: StateFlow<LyricsSourceMode> = _lyricsSourceMode.asStateFlow()

    private val _onlineLyricsProviders = MutableStateFlow(
        loadOnlineLyricsProviders()
    )
    val onlineLyricsProviders: StateFlow<List<LyricsProviderConfig>> = _onlineLyricsProviders.asStateFlow()

    private fun loadOnlineLyricsProviders(): List<LyricsProviderConfig> {
        val raw = prefs.getString(KEY_ONLINE_LYRICS_PROVIDERS, null)
        val defaultList = listOf(
            LyricsProviderConfig(LyricsProvider.LRCLIB, true),
            LyricsProviderConfig(LyricsProvider.PAXSENIX, true)
        )
        if (raw.isNullOrBlank()) return defaultList
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            val provider = LyricsProvider.entries.find { it.name.equals(parts.getOrNull(0), ignoreCase = true) } ?: return@mapNotNull null
            val enabled = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true
            LyricsProviderConfig(provider, enabled)
        }.ifEmpty { defaultList }
    }

    private fun saveOnlineLyricsProviders(configs: List<LyricsProviderConfig>) {
        val str = configs.joinToString(";") { "${it.provider.name}:${it.enabled}" }
        prefs.edit().putString(KEY_ONLINE_LYRICS_PROVIDERS, str).apply()
    }

    private val _wifiStreamingBitrate = MutableStateFlow(getEnumPreference(KEY_WIFI_STREAMING_BITRATE, StreamingBitrate.AUTO))
    val wifiStreamingBitrate: StateFlow<StreamingBitrate> = _wifiStreamingBitrate.asStateFlow()

    private val _cellularStreamingBitrate = MutableStateFlow(getEnumPreference(KEY_CELLULAR_STREAMING_BITRATE, StreamingBitrate.BITRATE_192))
    val cellularStreamingBitrate: StateFlow<StreamingBitrate> = _cellularStreamingBitrate.asStateFlow()

    private val _transcodingFormat = MutableStateFlow(getEnumPreference(KEY_TRANSCODING_FORMAT, TranscodingFormat.RAW))
    val transcodingFormat: StateFlow<TranscodingFormat> = _transcodingFormat.asStateFlow()

    private val _autoNextEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_NEXT_ENABLED, true)
    )
    val autoNextEnabled: StateFlow<Boolean> = _autoNextEnabled.asStateFlow()

    private val _pauseOnDisconnect = MutableStateFlow(
        prefs.getBoolean(KEY_PAUSE_ON_DISCONNECT, true)
    )
    val pauseOnDisconnect: StateFlow<Boolean> = _pauseOnDisconnect.asStateFlow()

    private val _autoResume = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_RESUME, false)
    )
    val autoResume: StateFlow<Boolean> = _autoResume.asStateFlow()

    private val _downloadQuality = MutableStateFlow(getEnumPreference(KEY_DOWNLOAD_QUALITY, DownloadQualityPreference.BITRATE_192))
    val downloadQuality: StateFlow<DownloadQualityPreference> = _downloadQuality.asStateFlow()

    private val _downloadFormat = MutableStateFlow(getEnumPreference(KEY_DOWNLOAD_FORMAT, DownloadFormatPreference.ORIGINAL))
    val downloadFormat: StateFlow<DownloadFormatPreference> = _downloadFormat.asStateFlow()

    private val _prefetchUpcomingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_PREFETCH_UPCOMING_ENABLED, true)
    )
    val prefetchUpcomingEnabled: StateFlow<Boolean> = _prefetchUpcomingEnabled.asStateFlow()

    private val _prefetchTrackCount = MutableStateFlow(
        prefs.getInt(KEY_PREFETCH_TRACK_COUNT, 2)
    )
    val prefetchTrackCount: StateFlow<Int> = _prefetchTrackCount.asStateFlow()

    private val _prefetchOnlyOnWifi = MutableStateFlow(
        prefs.getBoolean(KEY_PREFETCH_ONLY_ON_WIFI, false)
    )
    val prefetchOnlyOnWifi: StateFlow<Boolean> = _prefetchOnlyOnWifi.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(
        prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
    )
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    private val _languagePreference = MutableStateFlow(
        prefs.getString(KEY_LANGUAGE_PREFERENCE, "English") ?: "English"
    )
    val languagePreference: StateFlow<String> = _languagePreference.asStateFlow()

    private val _appLanguage = MutableStateFlow(
        AppLanguage.fromCode(prefs.getString(KEY_APP_LANGUAGE, prefs.getString(KEY_LANGUAGE_PREFERENCE, "")))
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _bottomNavIndicatorStyle = MutableStateFlow(getEnumPreference(KEY_BOTTOM_NAV_INDICATOR_STYLE, BottomNavIndicatorStyle.PILL))
    val bottomNavIndicatorStyle: StateFlow<BottomNavIndicatorStyle> = _bottomNavIndicatorStyle.asStateFlow()

    private val _playlistCoverStyle = MutableStateFlow(getEnumPreference(KEY_PLAYLIST_COVER_STYLE, PlaylistCoverStyle.GRID))
    val playlistCoverStyle: StateFlow<PlaylistCoverStyle> = _playlistCoverStyle.asStateFlow()

    private val _swipeRightAction = MutableStateFlow(getEnumPreference(KEY_SWIPE_RIGHT_ACTION, TrackSwipeAction.PLAY_NEXT))
    val swipeRightAction: StateFlow<TrackSwipeAction> = _swipeRightAction.asStateFlow()

    private val _swipeLeftAction = MutableStateFlow(getEnumPreference(KEY_SWIPE_LEFT_ACTION, TrackSwipeAction.ADD_TO_QUEUE))
    val swipeLeftAction: StateFlow<TrackSwipeAction> = _swipeLeftAction.asStateFlow()

    private val _alwaysShowNavBar = MutableStateFlow(
        prefs.getBoolean(KEY_ALWAYS_SHOW_NAV_BAR, false)
    )
    val alwaysShowNavBar: StateFlow<Boolean> = _alwaysShowNavBar.asStateFlow()

    private val _animatedArtworkEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_ANIMATED_ARTWORK_ENABLED, true)
    )
    val animatedArtworkEnabled: StateFlow<Boolean> = _animatedArtworkEnabled.asStateFlow()

    private val _fallbackServerUrl = MutableStateFlow(
        prefs.getString(KEY_FALLBACK_SERVER_URL, null)
    )
    val fallbackServerUrl: StateFlow<String?> = _fallbackServerUrl.asStateFlow()

    private val _showExternalLinks = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_EXTERNAL_LINKS, true)
    )
    val showExternalLinks: StateFlow<Boolean> = _showExternalLinks.asStateFlow()

    private val _showLastFmLinks = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_LASTFM_LINKS, true)
    )
    val showLastFmLinks: StateFlow<Boolean> = _showLastFmLinks.asStateFlow()

    private val _showMusicBrainzLinks = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_MUSICBRAINZ_LINKS, true)
    )
    val showMusicBrainzLinks: StateFlow<Boolean> = _showMusicBrainzLinks.asStateFlow()

    private val _coverArtQuality = MutableStateFlow(getEnumPreference(KEY_COVER_ART_QUALITY, CoverArtQuality.HIGH))
    val coverArtQuality: StateFlow<CoverArtQuality> = _coverArtQuality.asStateFlow()

    private val _keepScreenOnLyrics = MutableStateFlow(
        prefs.getBoolean(KEY_KEEP_SCREEN_ON_LYRICS, false)
    )
    val keepScreenOnLyrics: StateFlow<Boolean> = _keepScreenOnLyrics.asStateFlow()

    private val _blurInactiveLyrics = MutableStateFlow(
        prefs.getBoolean(KEY_BLUR_INACTIVE_LYRICS, true)
    )
    val blurInactiveLyrics: StateFlow<Boolean> = _blurInactiveLyrics.asStateFlow()

    fun setShowExternalLinks(enabled: Boolean) {
        _showExternalLinks.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_EXTERNAL_LINKS, enabled).apply()
    }

    fun setShowLastFmLinks(enabled: Boolean) {
        _showLastFmLinks.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_LASTFM_LINKS, enabled).apply()
    }

    fun setShowMusicBrainzLinks(enabled: Boolean) {
        _showMusicBrainzLinks.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_MUSICBRAINZ_LINKS, enabled).apply()
    }

    fun setCoverArtQuality(quality: CoverArtQuality) {
        _coverArtQuality.value = quality
        prefs.edit().putString(KEY_COVER_ART_QUALITY, quality.name).apply()
    }

    fun setKeepScreenOnLyrics(enabled: Boolean) {
        _keepScreenOnLyrics.value = enabled
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON_LYRICS, enabled).apply()
    }

    fun setBlurInactiveLyrics(enabled: Boolean) {
        _blurInactiveLyrics.value = enabled
        prefs.edit().putBoolean(KEY_BLUR_INACTIVE_LYRICS, enabled).apply()
    }

    fun setPlayerStyle(style: PlayerStyle) {
        _playerStyle.value = style
        prefs.edit().putString(KEY_PLAYER_STYLE, style.name).apply()
    }

    fun setSeekBarStyle(style: SeekBarStyle) {
        _seekBarStyle.value = style
        prefs.edit().putString(KEY_SEEK_BAR_STYLE, style.name).apply()
    }

    fun setLyricsSourceMode(mode: LyricsSourceMode) {
        _lyricsSourceMode.value = mode
        prefs.edit().putString(KEY_LYRICS_SOURCE_MODE, mode.name).apply()
    }

    fun setOnlineLyricsProviders(configs: List<LyricsProviderConfig>) {
        _onlineLyricsProviders.value = configs
        saveOnlineLyricsProviders(configs)
    }

    fun setOnlineLyricsProviderEnabled(provider: LyricsProvider, enabled: Boolean) {
        val current = _onlineLyricsProviders.value.toMutableList()
        val index = current.indexOfFirst { it.provider == provider }
        if (index != -1) {
            current[index] = current[index].copy(enabled = enabled)
        } else {
            current.add(LyricsProviderConfig(provider, enabled))
        }
        setOnlineLyricsProviders(current)
    }

    private fun valStoredColor(value: Int): Int? = if (value != -1) value else null

    fun setScrobblingEnabled(enabled: Boolean) {
        _isScrobblingEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SCROBBLING_ENABLED, enabled).apply()
    }

    fun setScrobbleThresholdPercent(percent: Int) {
        val clamped = percent.coerceIn(1, 100)
        _scrobbleThresholdPercent.value = clamped
        prefs.edit().putInt(KEY_SCROBBLE_THRESHOLD_PERCENT, clamped).apply()
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        _wifiOnlyDownloads.value = enabled
        prefs.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOADS, enabled).apply()
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        _autoSyncEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
    }

    fun setOfflineModeManual(enabled: Boolean) {
        _isOfflineModeManual.value = enabled
        prefs.edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setDynamicColorSource(source: DynamicColorSource) {
        _dynamicColorSource.value = source
        prefs.edit().putString(KEY_DYNAMIC_COLOR_SOURCE, source.name).apply()
    }

    fun setCustomAccentColor(color: Int?) {
        _customAccentColor.value = color
        if (color != null) {
            prefs.edit().putInt(KEY_CUSTOM_ACCENT_COLOR, color).apply()
        } else {
            prefs.edit().remove(KEY_CUSTOM_ACCENT_COLOR).apply()
        }
    }

    fun setAlbumViewMode(mode: com.notmugil.uta.domain.model.ViewDisplayMode) {
        _albumViewMode.value = mode
        prefs.edit().putString(KEY_ALBUM_VIEW_MODE, mode.name).apply()
    }

    fun setPlaylistViewMode(mode: com.notmugil.uta.domain.model.ViewDisplayMode) {
        _playlistViewMode.value = mode
        prefs.edit().putString(KEY_PLAYLIST_VIEW_MODE, mode.name).apply()
    }

    fun setFontPreference(font: AppFont) {
        _fontPreference.value = font
        prefs.edit().putString(KEY_FONT_PREFERENCE, font.name).apply()
    }

    fun setMiniPlayerStyle(style: MiniPlayerStyle) {
        _miniPlayerStyle.value = style
        prefs.edit().putString(KEY_MINI_PLAYER_STYLE, style.name).apply()
    }

    fun setMiniPlayerPlacement(placement: MiniPlayerPlacement) {
        _miniPlayerPlacement.value = placement
        prefs.edit().putString(KEY_MINI_PLAYER_PLACEMENT, placement.name).apply()
    }

    fun setTintMiniPlayerAccent(enabled: Boolean) {
        _tintMiniPlayerAccent.value = enabled
        prefs.edit().putBoolean(KEY_TINT_MINIPLAYER_ACCENT, enabled).apply()
    }

    fun getLibraryTabColumnCount(tab: String, default: Int = 2): Int {
        return prefs.getInt("library_col_count_$tab", default)
    }

    fun setLibraryTabColumnCount(tab: String, count: Int) {
        prefs.edit().putInt("library_col_count_$tab", count).apply()
    }

    fun setWifiStreamingBitrate(bitrate: StreamingBitrate) {
        _wifiStreamingBitrate.value = bitrate
        prefs.edit().putString(KEY_WIFI_STREAMING_BITRATE, bitrate.name).apply()
    }

    fun setCellularStreamingBitrate(bitrate: StreamingBitrate) {
        _cellularStreamingBitrate.value = bitrate
        prefs.edit().putString(KEY_CELLULAR_STREAMING_BITRATE, bitrate.name).apply()
    }

    fun setTranscodingFormat(format: TranscodingFormat) {
        _transcodingFormat.value = format
        prefs.edit().putString(KEY_TRANSCODING_FORMAT, format.name).apply()
    }

    fun setAutoNextEnabled(enabled: Boolean) {
        _autoNextEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_NEXT_ENABLED, enabled).apply()
    }

    fun setPauseOnDisconnect(enabled: Boolean) {
        _pauseOnDisconnect.value = enabled
        prefs.edit().putBoolean(KEY_PAUSE_ON_DISCONNECT, enabled).apply()
    }

    fun setAutoResume(enabled: Boolean) {
        _autoResume.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_RESUME, enabled).apply()
    }

    fun setDownloadQuality(quality: DownloadQualityPreference) {
        _downloadQuality.value = quality
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, quality.name).apply()
    }

    fun setDownloadFormat(format: DownloadFormatPreference) {
        _downloadFormat.value = format
        prefs.edit().putString(KEY_DOWNLOAD_FORMAT, format.name).apply()
    }

    fun setPrefetchUpcomingEnabled(enabled: Boolean) {
        _prefetchUpcomingEnabled.value = enabled
        prefs.edit().putBoolean(KEY_PREFETCH_UPCOMING_ENABLED, enabled).apply()
    }

    fun setPrefetchTrackCount(count: Int) {
        _prefetchTrackCount.value = count
        prefs.edit().putInt(KEY_PREFETCH_TRACK_COUNT, count).apply()
    }

    fun setPrefetchOnlyOnWifi(enabled: Boolean) {
        _prefetchOnlyOnWifi.value = enabled
        prefs.edit().putBoolean(KEY_PREFETCH_ONLY_ON_WIFI, enabled).apply()
    }

    fun setHapticFeedback(enabled: Boolean) {
        _hapticFeedback.value = enabled
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
    }

    fun setLanguagePreference(language: String) {
        _languagePreference.value = language
        prefs.edit().putString(KEY_LANGUAGE_PREFERENCE, language).apply()
    }

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        _languagePreference.value = language.name
        prefs.edit()
            .putString(KEY_APP_LANGUAGE, language.code)
            .putString(KEY_LANGUAGE_PREFERENCE, language.name)
            .apply()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            try {
                context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales =
                    android.os.LocaleList.forLanguageTags(language.code)
            } catch (_: Exception) {}
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.code))
    }

    fun setBottomNavIndicatorStyle(style: BottomNavIndicatorStyle) {
        _bottomNavIndicatorStyle.value = style
        prefs.edit().putString(KEY_BOTTOM_NAV_INDICATOR_STYLE, style.name).apply()
    }

    fun setPlaylistCoverStyle(style: PlaylistCoverStyle) {
        _playlistCoverStyle.value = style
        prefs.edit().putString(KEY_PLAYLIST_COVER_STYLE, style.name).apply()
    }

    fun setSwipeRightAction(action: TrackSwipeAction) {
        _swipeRightAction.value = action
        prefs.edit().putString(KEY_SWIPE_RIGHT_ACTION, action.name).apply()
    }

    fun setSwipeLeftAction(action: TrackSwipeAction) {
        _swipeLeftAction.value = action
        prefs.edit().putString(KEY_SWIPE_LEFT_ACTION, action.name).apply()
    }

    fun setAlwaysShowNavBar(enabled: Boolean) {
        _alwaysShowNavBar.value = enabled
        prefs.edit().putBoolean(KEY_ALWAYS_SHOW_NAV_BAR, enabled).apply()
    }

    fun setAnimatedArtworkEnabled(enabled: Boolean) {
        _animatedArtworkEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ANIMATED_ARTWORK_ENABLED, enabled).apply()
    }

    fun setFallbackServerUrl(url: String?) {
        _fallbackServerUrl.value = url
        if (url != null) {
            prefs.edit().putString(KEY_FALLBACK_SERVER_URL, url).apply()
        } else {
            prefs.edit().remove(KEY_FALLBACK_SERVER_URL).apply()
        }
    }
}
