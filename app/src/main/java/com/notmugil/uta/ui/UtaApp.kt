package com.notmugil.uta.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.data.preferences.MiniPlayerPlacement
import com.notmugil.uta.player.PlaybackController
import com.notmugil.uta.ui.screens.album.AlbumDetailScreen
import com.notmugil.uta.ui.screens.artist.ArtistDetailScreen
import com.notmugil.uta.ui.screens.downloads.DownloadsScreen
import com.notmugil.uta.ui.screens.genre.GenreDetailScreen
import com.notmugil.uta.ui.screens.home.HomeScreen
import com.notmugil.uta.ui.screens.history.HistoryScreen
import com.notmugil.uta.ui.screens.library.LibraryScreen
import com.notmugil.uta.ui.screens.library.LibraryTab
import com.notmugil.uta.ui.screens.login.LoginScreen
import com.notmugil.uta.ui.screens.info.MediaInfoScreen
import com.notmugil.uta.ui.screens.player.PlayerSheet
import com.notmugil.uta.ui.screens.playlist.EditPlaylistScreen
import com.notmugil.uta.ui.screens.playlist.PlaylistDetailScreen
import com.notmugil.uta.ui.screens.search.SearchScreen
import com.notmugil.uta.ui.screens.settings.SettingsScreen
import com.notmugil.uta.ui.shared.AppAmbientBackground
import com.notmugil.uta.ui.shared.BottomStatusBar
import com.notmugil.uta.ui.shared.LocalToastHostState
import com.notmugil.uta.ui.shared.MiniPlayer
import com.notmugil.uta.ui.shared.ToastHost
import com.notmugil.uta.ui.shared.ToastHostState
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import com.notmugil.uta.ui.theme.UtaTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

import com.notmugil.uta.data.NetworkMonitor
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.db.LocalMediaDao
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler
import com.notmugil.uta.ui.shared.actionsheet.MediaActionBottomSheet
import com.notmugil.uta.ui.shared.actionsheet.MediaActionState
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget
import androidx.compose.runtime.CompositionLocalProvider

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UtaAppEntryPoint {
    fun playbackController(): PlaybackController
    fun libraryRepository(): com.notmugil.uta.data.repository.LibraryRepository
    fun offlineDownloadManager(): OfflineDownloadManager
    fun networkMonitor(): NetworkMonitor
    fun localMediaDao(): LocalMediaDao
    fun subsonicRepository(): SubsonicRepository
    fun sleepTimerManager(): com.notmugil.uta.player.SleepTimerManager
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun UtaApp() {
    val context = LocalContext.current

    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            UtaAppEntryPoint::class.java
        )
    }
    val playbackController = remember(entryPoint) { entryPoint.playbackController() }
    val libraryRepository = remember(entryPoint) { entryPoint.libraryRepository() }
    val offlineDownloadManager = remember(entryPoint) { entryPoint.offlineDownloadManager() }
    val networkMonitor = remember(entryPoint) { entryPoint.networkMonitor() }
    val localMediaDao = remember(entryPoint) { entryPoint.localMediaDao() }
    val subsonicRepository = remember(entryPoint) { entryPoint.subsonicRepository() }
    val sleepTimerManager = remember(entryPoint) { entryPoint.sleepTimerManager() }

    val mediaActionState = remember { MediaActionState() }
    val isOfflineModeActive by networkMonitor.isOfflineModeActive.collectAsStateWithLifecycle(initialValue = false)
    val offlineReason by networkMonitor.offlineReason.collectAsStateWithLifecycle()
    val downloadedTrackIds by localMediaDao.getDownloadedTrackIdsFlow(subsonicRepository.currentServerId).collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadedAlbumIds by localMediaDao.getDownloadedAlbumIdsFlow(subsonicRepository.currentServerId).collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadedPlaylistIds by localMediaDao.getDownloadedPlaylistIdsFlow(subsonicRepository.currentServerId).collectAsStateWithLifecycle(initialValue = emptyList())

    val coroutineScope = rememberCoroutineScope()

    val currentTrack by playbackController.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by playbackController.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by playbackController.isBuffering.collectAsStateWithLifecycle()
    val queue by playbackController.queue.collectAsStateWithLifecycle()
    val currentQueueIndex by playbackController.currentQueueIndex.collectAsStateWithLifecycle()
    val currentPositionMs by playbackController.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by playbackController.durationMs.collectAsStateWithLifecycle()
    val repeatMode by playbackController.repeatMode.collectAsStateWithLifecycle()
    val isShuffleEnabled by playbackController.isShuffleEnabled.collectAsStateWithLifecycle()
    val sleepTimerMode by sleepTimerManager.activeMode.collectAsStateWithLifecycle()
    val remoteQueuePrompt by playbackController.remoteQueuePrompt.collectAsStateWithLifecycle()

    var isPlayerSheetVisible by rememberSaveable { mutableStateOf(false) }
    var isQueueSheetVisible by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(isPlayerSheetVisible) {
        val activity = context.findActivity()
        if (activity != null) {
            if (isPlayerSheetVisible) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        onDispose {
            val act = context.findActivity()
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val prefs = LocalAppPreferences.current
    val dynamicThemeManager = LocalDynamicThemeManager.current

    val miniPlayerPlacement by prefs?.miniPlayerPlacement?.collectAsState() ?: remember { mutableStateOf(MiniPlayerPlacement.ISOLATED) }

    val accentColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dockedBgColor = if (isDark) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh

    val isFloatingMiniPlayer = miniPlayerPlacement == MiniPlayerPlacement.ISOLATED
    val isDockedPlacement = miniPlayerPlacement == MiniPlayerPlacement.COMBINED
    val isDocked = isDockedPlacement && currentTrack != null

    val activeItemColors = NavigationBarItemDefaults.colors(
        indicatorColor = accentColor.copy(alpha = 0.22f),
        selectedIconColor = accentColor,
        selectedTextColor = accentColor,
        unselectedIconColor = if (isDockedPlacement && isDark) Color.White.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = if (isDockedPlacement && isDark) Color.White.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurfaceVariant
    )

    val toastHostState = remember { ToastHostState() }

    CompositionLocalProvider(
        LocalMediaActionHandler provides mediaActionState,
        LocalToastHostState provides toastHostState
    ) {
        UtaTheme {
            val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val rootRoutes = listOf(NavItems.HOME.name, NavItems.SEARCH.name, NavItems.DOWNLOADS.name, NavItems.LIBRARY.name, NavItems.SETTINGS.name)
        val shouldShowNavBar = currentRoute != null && currentRoute != "LOGIN"

        // Determine which root tab is active by finding the nearest root route in the back stack.
        // This keeps the correct tab highlighted when on detail pages (album, artist, etc.).
        val selectedRootTab = remember(navBackStackEntry) {
            val entry = navBackStackEntry
            if (entry != null) {
                val route = entry.destination.route
                if (route in rootRoutes) route
                else if (route?.startsWith(NavItems.LIBRARY.name) == true) NavItems.LIBRARY.name
                else {
                    // Walk up the back stack to find the parent root tab
                    entry.destination.parent?.route
                        ?.takeIf { it in rootRoutes }
                        ?: NavItems.HOME.name
                }
            } else {
                NavItems.HOME.name
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AppAmbientBackground(
                playbackController = playbackController
            )

            NavHost(
                navController = navController,
                startDestination = NavItems.HOME.name,
                enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) },
                exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
                popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) },
                popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
                modifier = Modifier.fillMaxSize()
            ) {
                composable(NavItems.HOME.name) {
                    HomeScreen(
                        onNavigateToAlbum = { albumId -> navController.navigate("album/$albumId") },
                        onNavigateToArtist = { artistId -> navController.navigate("artist/$artistId") },
                        onNavigateToPlaylist = { playlistId -> navController.navigate("playlist/$playlistId") },
                        onNavigateToLibraryTab = { tab -> navController.navigate("${NavItems.LIBRARY.name}?tab=${tab.name}") },
                        onNavigateToHistory = { navController.navigate("history") }
                    )
                }
                composable(route = NavItems.SEARCH.name) {
                    SearchScreen(
                        onNavigateToAlbum = { albumId -> navController.navigate("album/$albumId") },
                        onNavigateToArtist = { artistId -> navController.navigate("artist/$artistId") },
                        onNavigateToGenre = { genreName -> navController.navigate("genre/$genreName") }
                    )
                }
                composable(route = NavItems.DOWNLOADS.name) {
                    DownloadsScreen(
                        onNavigateToAlbum = { albumId -> navController.navigate("album/$albumId") },
                        onNavigateToPlaylist = { playlistId -> navController.navigate("playlist/$playlistId") },
                        onNavigateBack = null,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying
                    )
                }
                composable(
                    route = "${NavItems.LIBRARY.name}?tab={tab}",
                    arguments = listOf(navArgument("tab") {
                        nullable = true
                        defaultValue = null
                        type = NavType.StringType
                    })
                ) { backStackEntry ->
                    val tabArg = backStackEntry.arguments?.getString("tab")
                    val initialTab = tabArg?.let { runCatching { LibraryTab.valueOf(it) }.getOrNull() }
                    LibraryScreen(
                        initialTab = initialTab,
                        onNavigateToAlbum = { albumId -> navController.navigate("album/$albumId") },
                        onNavigateToArtist = { artistId -> navController.navigate("artist/$artistId") },
                        onNavigateToPlaylist = { playlistId -> navController.navigate("playlist/$playlistId") },
                        onNavigateToDownloads = { navController.navigate(NavItems.DOWNLOADS.name) },
                        onNavigateToHistory = { navController.navigate("history") }
                    )
                }
                composable(NavItems.SETTINGS.name) {
                    SettingsScreen(
                        onNavigateToDownloads = { navController.navigate(NavItems.DOWNLOADS.name) }
                    )
                }
                composable(route = "downloads") {
                    DownloadsScreen(
                        onNavigateToAlbum = { albumId -> navController.navigate("album/$albumId") },
                        onNavigateToPlaylist = { playlistId -> navController.navigate("playlist/$playlistId") },
                        onNavigateBack = { navController.popBackStack() },
                        currentTrack = currentTrack,
                        isPlaying = isPlaying
                    )
                }
                composable(route = "LOGIN") {
                    LoginScreen(onLoginSuccess = {
                        navController.navigate(NavItems.HOME.name) {
                            popUpTo("LOGIN") { inclusive = true }
                        }
                    })
                }
                composable(
                    route = "album/{albumId}",
                    arguments = listOf(navArgument("albumId") { type = NavType.StringType })
                ) {
                    AlbumDetailScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToArtist = { artistId -> navController.navigate("artist/$artistId") }
                    )
                }
                composable(
                    route = "artist/{artistId}",
                    arguments = listOf(navArgument("artistId") { type = NavType.StringType })
                ) {
                    ArtistDetailScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToAlbum = { albumId -> navController.navigate("album/$albumId") }
                    )
                }
                composable(
                    route = "playlist/{playlistId}",
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                ) {
                    PlaylistDetailScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToArtist = { artistId -> navController.navigate("artist/$artistId") },
                        onNavigateToEditPlaylist = { playlistId -> navController.navigate("edit_playlist/$playlistId") }
                    )
                }
                composable(
                    route = "edit_playlist/{playlistId}",
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                ) {
                    EditPlaylistScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onPlaylistDeleted = {
                            navController.popBackStack("playlist/{playlistId}", inclusive = true)
                            if (navController.currentBackStackEntry?.destination?.route?.startsWith("edit_playlist") == true) {
                                navController.popBackStack()
                            }
                        }
                    )
                }
                composable(
                    route = "genre/{genreName}",
                    arguments = listOf(navArgument("genreName") { type = NavType.StringType })
                ) {
                    GenreDetailScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToAlbum = { albumId -> navController.navigate("album/$albumId") },
                        onNavigateToArtist = { artistId -> navController.navigate("artist/$artistId") }
                    )
                }
                composable(route = "history") {
                    HistoryScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "info/{type}/{id}",
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("id") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val type = backStackEntry.arguments?.getString("type") ?: ""
                    val id = backStackEntry.arguments?.getString("id") ?: ""
                    MediaInfoScreen(
                        type = type,
                        id = id,
                        libraryRepository = libraryRepository,
                        offlineDownloadManager = offlineDownloadManager,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            // Bottom Navigation Bar & MiniPlayer Overlay
            val navFadeColor = MaterialTheme.colorScheme.background
            val bottomBgModifier = when {
                isDockedPlacement -> Modifier.background(dockedBgColor)
                shouldShowNavBar -> Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            navFadeColor.copy(alpha = 0.15f),
                            navFadeColor.copy(alpha = 0.45f),
                            navFadeColor.copy(alpha = 0.75f),
                            navFadeColor.copy(alpha = 0.92f),
                            navFadeColor,
                            navFadeColor
                        )
                    )
                )
                else -> Modifier
            }

            val effectiveDurationMs = if (durationMs > 0) durationMs else ((currentTrack?.durationSeconds ?: 0L) * 1000L)
            val progress = if (effectiveDurationMs > 0) (currentPositionMs.toFloat() / effectiveDurationMs.toFloat()).coerceIn(0f, 1f) else 0f

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isFloatingMiniPlayer && currentTrack != null) {
                    MiniPlayer(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        progress = progress,
                        onTogglePlayPause = { playbackController.togglePlayPause() },
                        onClick = { isPlayerSheetVisible = true },
                        onDismiss = { playbackController.stopAndClearQueue() },
                        isStarred = currentTrack?.isStarred == true,
                        onToggleFavorite = { playbackController.toggleFavorite() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (shouldShowNavBar || isDocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(bottomBgModifier)
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isDocked && currentTrack != null) {
                            MiniPlayer(
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                progress = progress,
                                onTogglePlayPause = { playbackController.togglePlayPause() },
                                onClick = { isPlayerSheetVisible = true },
                                onDismiss = { playbackController.stopAndClearQueue() },
                                isStarred = currentTrack?.isStarred == true,
                                onToggleFavorite = { playbackController.toggleFavorite() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (shouldShowNavBar) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                contentColor = if (isDockedPlacement && isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                NavItems.entries.forEach { navItem ->
                                    val isSelected = selectedRootTab == navItem.name
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            com.notmugil.uta.util.HapticFeedbackHelper.perform(context, com.notmugil.uta.util.HapticFeedbackHelper.HapticType.LIGHT)
                                            if (navItem == NavItems.HOME) {
                                                // Always navigate to HOME, clearing any detail pages
                                                navController.navigate(NavItems.HOME.name) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = false
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = false
                                                }
                                            } else {
                                                navController.navigate(navItem.name) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = false
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = false
                                                }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) navItem.activeIcon else navItem.icon,
                                                contentDescription = androidx.compose.ui.res.stringResource(navItem.labelResId),
                                                tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        label = null,
                                        alwaysShowLabel = false,
                                        colors = activeItemColors
                                    )
                                }
                            }
                        }

                        BottomStatusBar(
                            offlineReason = offlineReason,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (isFloatingMiniPlayer && !shouldShowNavBar) {
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }

        if (isPlayerSheetVisible && currentTrack != null) {
            PlayerSheet(
                track = currentTrack,
                queue = queue,
                currentIndex = currentQueueIndex,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                sleepTimerMode = sleepTimerMode,
                isBuffering = isBuffering,
                onDismiss = { isPlayerSheetVisible = false },
                onTogglePlayPause = { playbackController.togglePlayPause() },
                onSeekTo = { playbackController.seekTo(it) },
                onSkipNext = { playbackController.skipToNext() },
                onSkipPrevious = { playbackController.skipToPrevious() },
                onToggleRepeat = { playbackController.toggleRepeatMode() },
                onToggleShuffle = { playbackController.toggleShuffle() },
                onToggleFavorite = { playbackController.toggleFavorite() },
                onPlayQueueIndex = { playbackController.seekToQueueIndex(it) },
                onRemoveQueueIndex = { playbackController.removeQueueItem(it) },
                onMoveQueueItem = { from, to -> playbackController.moveQueueItem(from, to) },
                onClearQueue = { playbackController.stopAndClearQueue() },
                onUndoQueueAction = { playbackController.undoLastQueueAction() },
                onNavigateToAlbum = { albumId ->
                    navController.navigate("album/$albumId")
                    isPlayerSheetVisible = false
                },
                onNavigateToArtist = { artistId ->
                    navController.navigate("artist/$artistId")
                    isPlayerSheetVisible = false
                },
                onMoreOptions = {
                    currentTrack?.let { mediaActionState.show(MediaTarget.TrackTarget(it)) }
                },
                offlineDownloadManager = offlineDownloadManager,
                sleepTimerManager = sleepTimerManager
            )
        }

        if (isQueueSheetVisible) {
            com.notmugil.uta.ui.screens.player.QueueSheet(
                queue = queue,
                currentIndex = currentQueueIndex,
                isPlaying = isPlaying,
                onDismiss = { isQueueSheetVisible = false },
                onItemClick = { index -> playbackController.seekToQueueIndex(index) },
                onRemoveItem = { index -> playbackController.removeQueueItem(index) },
                onMoveItem = { fromIndex, toIndex -> playbackController.moveQueueItem(fromIndex, toIndex) },
                onClearQueue = {
                    playbackController.stopAndClearQueue()
                },
                onUndo = {
                    playbackController.undoLastQueueAction()
                },
                onSaveQueueAsPlaylist = { name ->
                    coroutineScope.launch {
                        libraryRepository.createPlaylist(name, queue.map { it.track.id })
                    }
                }
            )
        }

        remoteQueuePrompt?.let { prompt ->
            com.notmugil.uta.ui.shared.ActionConfirmDialog(
                title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.dialog_resume_queue_title),
                message = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.dialog_resume_queue_message, prompt.trackCount, prompt.changedBy.orEmpty()),
                confirmText = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.dialog_resume),
                dismissText = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.dialog_dismiss),
                onConfirm = prompt.onResume,
                onDismiss = prompt.onDismiss
            )
        }

        if (mediaActionState.currentTarget != null) {
            val currentTarget = mediaActionState.currentTarget
            val isTargetDownloaded = when (currentTarget) {
                is MediaTarget.TrackTarget -> downloadedTrackIds.contains(currentTarget.track.id)
                is MediaTarget.AlbumTarget -> downloadedAlbumIds.contains(currentTarget.album.id)
                is MediaTarget.PlaylistTarget -> downloadedPlaylistIds.contains(currentTarget.playlist.id)
                else -> false
            }
            MediaActionBottomSheet(
                state = mediaActionState,
                playbackController = playbackController,
                libraryRepository = libraryRepository,
                offlineDownloadManager = offlineDownloadManager,
                isDownloaded = isTargetDownloaded,
                isOffline = isOfflineModeActive,
                onNavigateToArtist = { artistId ->
                    navController.navigate("artist/$artistId")
                    mediaActionState.dismiss()
                    isPlayerSheetVisible = false
                },
                onNavigateToAlbum = { albumId ->
                    navController.navigate("album/$albumId")
                    mediaActionState.dismiss()
                    isPlayerSheetVisible = false
                },
                onNavigateToEditPlaylist = { playlistId ->
                    navController.navigate("edit_playlist/$playlistId")
                    mediaActionState.dismiss()
                    isPlayerSheetVisible = false
                },
                onNavigateToInfo = { type, id ->
                    navController.navigate("info/$type/$id")
                    mediaActionState.dismiss()
                    isPlayerSheetVisible = false
                }
            )
        }

        ToastHost(hostState = toastHostState)
        }
    }
}

enum class NavItems(
    @androidx.annotation.StringRes val labelResId: Int,
    val icon: ImageVector,
    val activeIcon: ImageVector = icon,
) {
    HOME(com.notmugil.uta.R.string.nav_home, Tabler.Outline.Home, Tabler.Filled.Home),
    SEARCH(com.notmugil.uta.R.string.nav_search, Tabler.Outline.Search),
    DOWNLOADS(com.notmugil.uta.R.string.nav_downloads, Tabler.Outline.Download),
    LIBRARY(com.notmugil.uta.R.string.nav_library, Tabler.Outline.Books),
    SETTINGS(com.notmugil.uta.R.string.nav_settings, Tabler.Outline.Settings, Tabler.Filled.Settings),
}
