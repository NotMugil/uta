package com.notmugil.uta.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notmugil.uta.data.AuthState
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.data.preferences.AppFont
import com.notmugil.uta.data.preferences.AppLanguage
import com.notmugil.uta.data.preferences.AppPreferences
import com.notmugil.uta.data.preferences.AppThemeMode
import com.notmugil.uta.data.preferences.BottomNavIndicatorStyle
import com.notmugil.uta.data.preferences.CoverArtQuality
import com.notmugil.uta.data.preferences.DownloadFormatPreference
import com.notmugil.uta.data.preferences.DownloadQualityPreference
import com.notmugil.uta.data.preferences.DynamicColorSource
import com.notmugil.uta.data.preferences.HomeSectionConfig
import com.notmugil.uta.data.preferences.LyricsProvider
import com.notmugil.uta.data.preferences.LyricsProviderConfig
import com.notmugil.uta.data.preferences.LyricsSourceMode
import com.notmugil.uta.data.preferences.MiniPlayerButtonConfig
import com.notmugil.uta.data.preferences.MiniPlayerPlacement
import com.notmugil.uta.data.preferences.MiniPlayerStyle
import com.notmugil.uta.data.preferences.NavBarItemConfig
import com.notmugil.uta.data.preferences.PlayerStyle
import com.notmugil.uta.data.preferences.PlaylistCoverStyle
import com.notmugil.uta.data.preferences.SeekBarStyle
import com.notmugil.uta.data.preferences.StreamingBitrate
import com.notmugil.uta.data.preferences.TrackSwipeAction
import com.notmugil.uta.data.preferences.TranscodingFormat
import com.notmugil.uta.data.repository.LibraryRepository
import com.notmugil.uta.data.sync.LibrarySyncEngine
import com.notmugil.uta.data.sync.SyncState
import com.notmugil.uta.player.PlaybackController
import com.notmugil.uta.player.SleepTimerManager
import com.notmugil.uta.player.SleepTimerMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val subsonicRepository: SubsonicRepository,
    private val libraryRepository: LibraryRepository,
    private val syncEngine: LibrarySyncEngine,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val playbackController: PlaybackController,
    private val appPreferences: AppPreferences,
    private val networkMonitor: com.notmugil.uta.data.NetworkMonitor,
    val sleepTimerManager: SleepTimerManager
) : ViewModel() {

    private val _currentSubPage = MutableStateFlow<SettingsSubPage?>(null)
    val currentSubPage: StateFlow<SettingsSubPage?> = _currentSubPage.asStateFlow()

    fun navigateToSubPage(subPage: SettingsSubPage?) {
        _currentSubPage.value = subPage
    }

    fun navigateBackFromSubPage() {
        _currentSubPage.value = when (_currentSubPage.value) {
            SettingsSubPage.LYRICS_PROVIDERS,
            SettingsSubPage.ONLINE_LYRICS_PROVIDERS,
            SettingsSubPage.HOME_SECTIONS,
            SettingsSubPage.NAVBAR_SECTIONS,
            SettingsSubPage.MINIPLAYER_BUTTONS -> SettingsSubPage.GENERAL
            else -> null
        }
    }

    val authState: StateFlow<AuthState> = subsonicRepository.authState
    val syncState: StateFlow<SyncState> = syncEngine.syncState
    val storageStats: StateFlow<com.notmugil.uta.data.download.StorageStats> = offlineDownloadManager.storageStats

    val isOfflineModeManual: StateFlow<Boolean> = appPreferences.isOfflineModeManual
    val isOfflineModeActive: StateFlow<Boolean> = networkMonitor.isOfflineModeActive
    val offlineReason: StateFlow<com.notmugil.uta.data.OfflineReason> = networkMonitor.offlineReason

    // Appearance
    val themeMode: StateFlow<AppThemeMode> = appPreferences.themeMode
    val dynamicColorSource: StateFlow<DynamicColorSource> = appPreferences.dynamicColorSource
    val customAccentColor: StateFlow<Int?> = appPreferences.customAccentColor
    val fontPreference: StateFlow<AppFont> = appPreferences.fontPreference
    val languagePreference: StateFlow<String> = appPreferences.languagePreference
    val appLanguage: StateFlow<AppLanguage> = appPreferences.appLanguage
    val hapticFeedback: StateFlow<Boolean> = appPreferences.hapticFeedback
    val bottomNavIndicatorStyle: StateFlow<BottomNavIndicatorStyle> = appPreferences.bottomNavIndicatorStyle
    val playlistCoverStyle: StateFlow<PlaylistCoverStyle> = appPreferences.playlistCoverStyle
    val swipeRightAction: StateFlow<TrackSwipeAction> = appPreferences.swipeRightAction
    val swipeLeftAction: StateFlow<TrackSwipeAction> = appPreferences.swipeLeftAction
    val alwaysShowNavBar: StateFlow<Boolean> = appPreferences.alwaysShowNavBar
    val homeSectionConfigs: StateFlow<List<HomeSectionConfig>> = appPreferences.homeSectionConfigs
    val navBarItemConfigs: StateFlow<List<NavBarItemConfig>> = appPreferences.navBarItemConfigs
    val miniPlayerButtonConfigs: StateFlow<List<MiniPlayerButtonConfig>> = appPreferences.miniPlayerButtonConfigs

    // Player
    val playerStyle: StateFlow<PlayerStyle> = appPreferences.playerStyle
    val seekBarStyle: StateFlow<SeekBarStyle> = appPreferences.seekBarStyle
    val miniPlayerStyle: StateFlow<MiniPlayerStyle> = appPreferences.miniPlayerStyle
    val miniPlayerPlacement: StateFlow<MiniPlayerPlacement> = appPreferences.miniPlayerPlacement
    val tintMiniPlayerAccent: StateFlow<Boolean> = appPreferences.tintMiniPlayerAccent
    val animatedArtworkEnabled: StateFlow<Boolean> = appPreferences.animatedArtworkEnabled
    val coverArtQuality: StateFlow<CoverArtQuality> = appPreferences.coverArtQuality
    val lyricsSourceMode: StateFlow<LyricsSourceMode> = appPreferences.lyricsSourceMode
    val onlineLyricsProviders: StateFlow<List<LyricsProviderConfig>> = appPreferences.onlineLyricsProviders
    val keepScreenOnLyrics: StateFlow<Boolean> = appPreferences.keepScreenOnLyrics
    val blurInactiveLyrics: StateFlow<Boolean> = appPreferences.blurInactiveLyrics

    // External Links
    val showExternalLinks: StateFlow<Boolean> = appPreferences.showExternalLinks
    val showLastFmLinks: StateFlow<Boolean> = appPreferences.showLastFmLinks
    val showMusicBrainzLinks: StateFlow<Boolean> = appPreferences.showMusicBrainzLinks

    // Playback
    val wifiStreamingBitrate: StateFlow<StreamingBitrate> = appPreferences.wifiStreamingBitrate
    val cellularStreamingBitrate: StateFlow<StreamingBitrate> = appPreferences.cellularStreamingBitrate
    val transcodingFormat: StateFlow<TranscodingFormat> = appPreferences.transcodingFormat
    val autoNextEnabled: StateFlow<Boolean> = appPreferences.autoNextEnabled
    val pauseOnDisconnect: StateFlow<Boolean> = appPreferences.pauseOnDisconnect
    val autoResume: StateFlow<Boolean> = appPreferences.autoResume
    val sleepTimerMode: StateFlow<SleepTimerMode> = sleepTimerManager.activeMode
    val sleepTimerSeconds: StateFlow<Long?> = sleepTimerManager.remainingSeconds

    // Downloads & Storage
    val downloadQuality: StateFlow<DownloadQualityPreference> = appPreferences.downloadQuality
    val downloadFormat: StateFlow<DownloadFormatPreference> = appPreferences.downloadFormat
    val wifiOnlyDownloads: StateFlow<Boolean> = appPreferences.wifiOnlyDownloads
    val prefetchUpcomingEnabled: StateFlow<Boolean> = appPreferences.prefetchUpcomingEnabled
    val prefetchTrackCount: StateFlow<Int> = appPreferences.prefetchTrackCount
    val prefetchOnlyOnWifi: StateFlow<Boolean> = appPreferences.prefetchOnlyOnWifi

    // Network & Sync
    val fallbackServerUrl: StateFlow<String?> = appPreferences.fallbackServerUrl
    val isScrobblingEnabled: StateFlow<Boolean> = appPreferences.isScrobblingEnabled
    val scrobbleThresholdPercent: StateFlow<Int> = appPreferences.scrobbleThresholdPercent
    val autoSyncEnabled: StateFlow<Boolean> = appPreferences.autoSyncEnabled
    val lastFullSyncTime: StateFlow<Long?> = libraryRepository.getLastFullSyncFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            offlineDownloadManager.refreshStorageStats()
        }
    }

    fun getAvatarUrl(username: String?): String? = subsonicRepository.getAvatarUrl(username)

    fun setOfflineModeManual(enabled: Boolean) = appPreferences.setOfflineModeManual(enabled)
    fun setThemeMode(mode: AppThemeMode) = appPreferences.setThemeMode(mode)
    fun setDynamicColorSource(source: DynamicColorSource) = appPreferences.setDynamicColorSource(source)
    fun setCustomAccentColor(color: Int?) = appPreferences.setCustomAccentColor(color)
    fun setFontPreference(font: AppFont) = appPreferences.setFontPreference(font)
    fun setLanguagePreference(language: String) = appPreferences.setLanguagePreference(language)
    fun setAppLanguage(language: AppLanguage) = appPreferences.setAppLanguage(language)
    fun setHapticFeedback(enabled: Boolean) = appPreferences.setHapticFeedback(enabled)
    fun setBottomNavIndicatorStyle(style: BottomNavIndicatorStyle) = appPreferences.setBottomNavIndicatorStyle(style)
    fun setPlaylistCoverStyle(style: PlaylistCoverStyle) = appPreferences.setPlaylistCoverStyle(style)
    fun setSwipeRightAction(action: TrackSwipeAction) = appPreferences.setSwipeRightAction(action)
    fun setSwipeLeftAction(action: TrackSwipeAction) = appPreferences.setSwipeLeftAction(action)
    fun setAlwaysShowNavBar(enabled: Boolean) = appPreferences.setAlwaysShowNavBar(enabled)
    fun setHomeSectionConfigs(configs: List<HomeSectionConfig>) = appPreferences.setHomeSectionConfigs(configs)
    fun resetHomeSections() = appPreferences.resetHomeSections()
    fun setNavBarItemConfigs(configs: List<NavBarItemConfig>) = appPreferences.setNavBarItemConfigs(configs)
    fun resetNavBarItems() = appPreferences.resetNavBarItems()
    fun setMiniPlayerButtonConfigs(configs: List<MiniPlayerButtonConfig>) = appPreferences.setMiniPlayerButtonConfigs(configs)
    fun resetMiniPlayerButtons() = appPreferences.resetMiniPlayerButtons()

    fun setPlayerStyle(style: PlayerStyle) = appPreferences.setPlayerStyle(style)
    fun setSeekBarStyle(style: SeekBarStyle) = appPreferences.setSeekBarStyle(style)
    fun setMiniPlayerStyle(style: MiniPlayerStyle) = appPreferences.setMiniPlayerStyle(style)
    fun setMiniPlayerPlacement(placement: MiniPlayerPlacement) = appPreferences.setMiniPlayerPlacement(placement)
    fun setTintMiniPlayerAccent(enabled: Boolean) = appPreferences.setTintMiniPlayerAccent(enabled)
    fun setAnimatedArtworkEnabled(enabled: Boolean) = appPreferences.setAnimatedArtworkEnabled(enabled)
    fun setCoverArtQuality(quality: CoverArtQuality) = appPreferences.setCoverArtQuality(quality)
    fun setLyricsSourceMode(mode: LyricsSourceMode) = appPreferences.setLyricsSourceMode(mode)
    fun setOnlineLyricsProviders(configs: List<LyricsProviderConfig>) = appPreferences.setOnlineLyricsProviders(configs)
    fun setOnlineLyricsProviderEnabled(provider: LyricsProvider, enabled: Boolean) =
        appPreferences.setOnlineLyricsProviderEnabled(provider, enabled)
    fun setKeepScreenOnLyrics(enabled: Boolean) = appPreferences.setKeepScreenOnLyrics(enabled)
    fun setBlurInactiveLyrics(enabled: Boolean) = appPreferences.setBlurInactiveLyrics(enabled)

    fun setShowExternalLinks(enabled: Boolean) = appPreferences.setShowExternalLinks(enabled)
    fun setShowLastFmLinks(enabled: Boolean) = appPreferences.setShowLastFmLinks(enabled)
    fun setShowMusicBrainzLinks(enabled: Boolean) = appPreferences.setShowMusicBrainzLinks(enabled)

    fun setWifiStreamingBitrate(bitrate: StreamingBitrate) = appPreferences.setWifiStreamingBitrate(bitrate)
    fun setCellularStreamingBitrate(bitrate: StreamingBitrate) = appPreferences.setCellularStreamingBitrate(bitrate)
    fun setTranscodingFormat(format: TranscodingFormat) = appPreferences.setTranscodingFormat(format)
    fun setAutoNextEnabled(enabled: Boolean) = appPreferences.setAutoNextEnabled(enabled)
    fun setPauseOnDisconnect(enabled: Boolean) = appPreferences.setPauseOnDisconnect(enabled)
    fun setAutoResume(enabled: Boolean) = appPreferences.setAutoResume(enabled)

    fun setDownloadQuality(quality: DownloadQualityPreference) = appPreferences.setDownloadQuality(quality)
    fun setDownloadFormat(format: DownloadFormatPreference) = appPreferences.setDownloadFormat(format)
    fun setWifiOnlyDownloads(enabled: Boolean) = appPreferences.setWifiOnlyDownloads(enabled)
    fun setPrefetchUpcomingEnabled(enabled: Boolean) = appPreferences.setPrefetchUpcomingEnabled(enabled)
    fun setPrefetchTrackCount(count: Int) = appPreferences.setPrefetchTrackCount(count)
    fun setPrefetchOnlyOnWifi(enabled: Boolean) = appPreferences.setPrefetchOnlyOnWifi(enabled)

    fun setFallbackServerUrl(url: String?) {
        appPreferences.setFallbackServerUrl(url)
        subsonicRepository.setFallbackServerUrl(url)
    }
    fun setScrobblingEnabled(enabled: Boolean) = appPreferences.setScrobblingEnabled(enabled)
    fun setScrobbleThresholdPercent(percent: Int) = appPreferences.setScrobbleThresholdPercent(percent)
    fun setAutoSyncEnabled(enabled: Boolean) = appPreferences.setAutoSyncEnabled(enabled)

    fun probeReachability() {
        viewModelScope.launch {
            networkMonitor.probeReachability()
        }
    }

    fun triggerSync() {
        libraryRepository.triggerSync(force = true)
    }

    fun clearDownloads() {
        viewModelScope.launch {
            offlineDownloadManager.clearAllSessionDownloads()
        }
    }

    fun logout() {
        viewModelScope.launch {
            syncEngine.cancelAndJoin()
            playbackController.stopAndClearQueue()
            offlineDownloadManager.clearAllSessionDownloads()
            libraryRepository.clearCache()
            subsonicRepository.logout()
        }
    }
}
