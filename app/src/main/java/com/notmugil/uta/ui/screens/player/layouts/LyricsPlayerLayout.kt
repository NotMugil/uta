package com.notmugil.uta.ui.screens.player.layouts

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.*
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.notmugil.uta.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.data.preferences.LyricsProvider
import com.notmugil.uta.data.preferences.LyricsSourceMode
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.data.repository.LyricsData
import com.notmugil.uta.data.repository.LyricsRepository
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.SleepTimerMode
import com.notmugil.uta.ui.screens.player.components.PlaybackControlsSection
import com.notmugil.uta.ui.screens.player.components.PlayerSeekBarSection
import com.notmugil.uta.ui.screens.player.lyrics.KaraokeWord
import com.notmugil.uta.ui.shared.CoverArtImage
import kotlin.math.abs
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun LyricsPlayerLayout(
    track: TrackItem?,
    horizontalArtworkOffset: Float,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isStarred: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isDraggingSlider: Boolean,
    dragPositionMs: Float,
    onDraggingSliderChange: (Boolean) -> Unit,
    onDragPositionChange: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    sleepTimerMode: SleepTimerMode,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onBack: () -> Unit,
    onMoreOptions: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float? = null,
    onToggleDownload: () -> Unit = {},
    trackStats: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appPreferences = LocalAppPreferences.current

    val lyricsSourceMode by (appPreferences?.lyricsSourceMode?.collectAsState() ?: remember { mutableStateOf(LyricsSourceMode.BOTH) })
    val isLyricsDisabled = lyricsSourceMode == LyricsSourceMode.DISABLED
    val onlineProvidersConfig by (appPreferences?.onlineLyricsProviders?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val keepScreenOnLyrics by (appPreferences?.keepScreenOnLyrics?.collectAsState() ?: remember { mutableStateOf(false) })
    val blurInactiveLyrics by (appPreferences?.blurInactiveLyrics?.collectAsState() ?: remember { mutableStateOf(true) })

    val networkMonitor = com.notmugil.uta.data.LocalNetworkMonitor.current
    val isOnline by (networkMonitor?.isOnline?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(com.notmugil.uta.data.SubsonicSession.isOnline) })
    val isManualOffline by (appPreferences?.isOfflineModeManual?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(com.notmugil.uta.data.SubsonicSession.isManualOffline) })
    val isOffline = isManualOffline || !isOnline

    val currentView = LocalView.current
    DisposableEffect(keepScreenOnLyrics) {
        if (keepScreenOnLyrics) {
            currentView.keepScreenOn = true
        }
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    val enabledOnlineProviders = remember(onlineProvidersConfig) {
        onlineProvidersConfig.filter { it.enabled }.map { it.provider }
    }
    val availableProviderEntries = remember(lyricsSourceMode, enabledOnlineProviders, isOffline) {
        if (isOffline) {
            if (lyricsSourceMode == LyricsSourceMode.DISABLED) emptyList() else listOf(LyricsProvider.SUBSONIC)
        } else {
            when (lyricsSourceMode) {
                LyricsSourceMode.DISABLED -> emptyList()
                LyricsSourceMode.SERVER_ONLY -> listOf(LyricsProvider.SUBSONIC)
                LyricsSourceMode.BOTH -> listOf(LyricsProvider.AUTO, LyricsProvider.SUBSONIC) + enabledOnlineProviders
            }
        }
    }

    var lyricsData by remember { mutableStateOf<LyricsData?>(null) }
    var selectedProvider by remember { mutableStateOf(LyricsProvider.AUTO) }
    val effectiveSelectedProvider = if (isOffline || lyricsSourceMode == LyricsSourceMode.SERVER_ONLY) LyricsProvider.SUBSONIC else selectedProvider
    var availableProvidersMap by remember(track?.id) { mutableStateOf<Map<LyricsProvider, String>>(emptyMap()) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var userScrolledAway by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userScrolledAway = true
        }
    }

    LaunchedEffect(track?.id, effectiveSelectedProvider) {
        userScrolledAway = false
    }

    LaunchedEffect(track?.id, lyricsSourceMode, isOffline) {
        if (track != null && !isLyricsDisabled) {
            try {
                availableProvidersMap = LyricsRepository.checkAvailableProviders(context, track)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(track?.id, effectiveSelectedProvider, lyricsSourceMode, isOffline) {
        if (isLyricsDisabled) {
            lyricsData = null
            isLoading = false
            return@LaunchedEffect
        }
        if (track != null) {
            isLoading = true
            try {
                lyricsData = LyricsRepository.getLyrics(context, track, provider = effectiveSelectedProvider)
            } catch (_: Exception) {
                lyricsData = null
            } finally {
                isLoading = false
            }
        } else {
            lyricsData = null
            isLoading = false
        }
    }

    var smoothPositionMs by remember(track?.id) { androidx.compose.runtime.mutableLongStateOf(currentPositionMs) }

    LaunchedEffect(isPlaying, currentPositionMs) {
        if (!isPlaying) {
            smoothPositionMs = currentPositionMs
            return@LaunchedEffect
        }
        val startWallTime = android.os.SystemClock.elapsedRealtime()
        val startPos = currentPositionMs
        while (isActive) {
            androidx.compose.runtime.withFrameMillis {
                val elapsed = android.os.SystemClock.elapsedRealtime() - startWallTime
                smoothPositionMs = (startPos + elapsed).coerceAtLeast(0L)
            }
        }
    }

    val lines = lyricsData?.syncedLines.orEmpty()
    val plainLyrics = lyricsData?.plainLyrics

    val currentLineIndex = if (lines.isNotEmpty()) {
        lines.indexOfLast { it.startMs <= smoothPositionMs }.coerceAtLeast(0)
    } else {
        0
    }

    val focusedLineIndex by remember(lines, userScrolledAway, isDragged, currentLineIndex) {
        derivedStateOf {
            if (!userScrolledAway && !isDragged) {
                currentLineIndex
            } else {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) {
                    currentLineIndex
                } else {
                    val focalY = layoutInfo.viewportSize.height * 0.35f
                    val closestItem = visibleItems.minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2f
                        kotlin.math.abs(itemCenter - focalY)
                    }
                    closestItem?.index ?: currentLineIndex
                }
            }
        }
    }

    LaunchedEffect(currentLineIndex, userScrolledAway, lyricsData) {
        if (!userScrolledAway && lines.isNotEmpty() && currentLineIndex in lines.indices) {
            listState.animateScrollToItem((currentLineIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        translationX = horizontalArtworkOffset
                        val progress = (abs(horizontalArtworkOffset) / 900f).coerceIn(0f, 0.45f)
                        alpha = 1f - progress
                    }
            ) {
                com.notmugil.uta.ui.shared.AnimatedAlbumArtView(
                    track = track,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track?.title ?: stringResource(R.string.album_unknown_album),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = track?.artist.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .basicMarquee()
                        .clickable(enabled = track?.artistId != null) {
                            track?.artistId?.let { onNavigateToArtist(it) }
                        }
                )
            }

            if (track != null) {
                IconButton(
                    onClick = onMoreOptions,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Tabler.Outline.DotsVertical,
                        contentDescription = stringResource(R.string.action_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (lines.isNotEmpty()) {
                val listModifier = if (blurInactiveLyrics) {
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.08f to Color.Black,
                                    0.92f to Color.Black,
                                    1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                } else {
                    Modifier.fillMaxSize()
                }

                val isBrowsing = userScrolledAway || isDragged

                LazyColumn(
                    state = listState,
                    modifier = listModifier,
                    contentPadding = PaddingValues(top = 16.dp, bottom = 380.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        val isCurrent = index == currentLineIndex
                        val isFocused = index == focusedLineIndex
                        val focusDist = kotlin.math.abs(index - focusedLineIndex)
                        val targetLineAlpha = if (blurInactiveLyrics || !isBrowsing) {
                            when (focusDist) {
                                0 -> 1.0f
                                1 -> 0.75f
                                2 -> 0.55f
                                3 -> 0.40f
                                else -> 0.28f
                            }
                        } else {
                            if (isCurrent || isFocused) 1.0f else 0.78f
                        }
                        val lineAlpha by animateFloatAsState(
                            targetValue = targetLineAlpha,
                            animationSpec = tween(220),
                            label = "player_line_alpha_$index"
                        )
                        val accentColor = MaterialTheme.colorScheme.primary
                        val textColor = MaterialTheme.colorScheme.onBackground
                        val dimColor = textColor.copy(alpha = lineAlpha)

                        val targetBlurDp = if (blurInactiveLyrics && !isFocused) {
                            when (focusDist) {
                                1 -> 0.8.dp
                                2 -> 1.8.dp
                                3 -> 2.6.dp
                                else -> 3.2.dp
                            }
                        } else {
                            0.dp
                        }
                        val blurDp by animateDpAsState(
                            targetValue = targetBlurDp,
                            animationSpec = tween(220),
                            label = "player_line_blur_$index"
                        )
                        val lineBlurModifier = if (blurDp > 0.dp) {
                            Modifier.blur(blurDp)
                        } else {
                            Modifier
                        }

                        if (line.isWordSynced) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(lineBlurModifier)
                                    .padding(vertical = if (isCurrent) 4.dp else 2.dp)
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.Start,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            userScrolledAway = false
                                            onSeekTo(line.startMs)
                                        }
                                        .padding(horizontal = 8.dp, vertical = if (isCurrent) 8.dp else 4.dp)
                                ) {
                                    line.words.forEachIndexed { wordIdx, word ->
                                        val nextStartMs = if (wordIdx + 1 < line.words.size) {
                                            line.words[wordIdx + 1].startMs
                                        } else {
                                            line.endMs ?: (word.startMs + 700L)
                                        }
                                        val wordEnd = word.endMs ?: nextStartMs
                                        KaraokeWord(
                                            word = word,
                                            wordEnd = wordEnd,
                                            currentPositionMs = smoothPositionMs,
                                            accentColor = accentColor,
                                            textColor = textColor,
                                            dimColor = dimColor,
                                            fontSize = if (isCurrent) 24.sp else 18.sp,
                                            isCurrentLine = isCurrent
                                        )
                                    }
                                }
                            }
                        } else {
                            val lineColor = if (isCurrent) accentColor else dimColor
                            val lineFontWeight = if (isCurrent) FontWeight.ExtraBold else (if (focusDist <= 1) FontWeight.SemiBold else FontWeight.Normal)
                            val lineFontSize = if (isCurrent) 24.sp else 18.sp
                            val lineLineHeight = if (isCurrent) 34.sp else 26.sp

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(lineBlurModifier)
                                    .padding(vertical = if (isCurrent) 4.dp else 2.dp)
                            ) {
                                Text(
                                    text = line.text,
                                    fontSize = lineFontSize,
                                    fontWeight = lineFontWeight,
                                    color = lineColor,
                                    lineHeight = lineLineHeight,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            userScrolledAway = false
                                            onSeekTo(line.startMs)
                                        }
                                        .padding(horizontal = 12.dp, vertical = if (isCurrent) 8.dp else 6.dp)
                                )
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = userScrolledAway,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                ) {
                    Surface(
                        onClick = {
                            userScrolledAway = false
                            coroutineScope.launch {
                                listState.animateScrollToItem((currentLineIndex - 1).coerceAtLeast(0))
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.lyrics_resume_scroll),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            } else if (!plainLyrics.isNullOrBlank() && !plainLyrics.trim().equals("null", ignoreCase = true)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = plainLyrics,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 29.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.Music,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(R.string.lyrics_not_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PlayerSeekBarSection(
            isPlaying = isPlaying,
            currentPositionMs = smoothPositionMs,
            durationMs = durationMs,
            isDraggingSlider = isDraggingSlider,
            dragPositionMs = dragPositionMs,
            onDraggingSliderChange = onDraggingSliderChange,
            onDragPositionChange = onDragPositionChange,
            onSeekTo = onSeekTo,
            songKey = track?.id,
            trackStats = trackStats
        )

        Spacer(modifier = Modifier.height(10.dp))

        PlaybackControlsSection(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            onTogglePlayPause = onTogglePlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onToggleShuffle = onToggleShuffle,
            onToggleRepeat = onToggleRepeat
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenSleepTimer) {
                val isTimerRunning = sleepTimerMode !is SleepTimerMode.Disabled
                Icon(
                    imageVector = Tabler.Outline.Moon,
                    contentDescription = stringResource(R.string.sleep_timer),
                    tint = if (isTimerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onToggleDownload) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress ?: 0.1f },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = if (isDownloaded) Tabler.Outline.CircleCheck else Tabler.Outline.Download,
                        contentDescription = if (isDownloaded) stringResource(R.string.download_status_completed) else stringResource(R.string.action_download),
                        tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isStarred) Tabler.Filled.Heart else Tabler.Outline.Heart,
                    contentDescription = if (isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
                    tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isOffline && track != null && !isLyricsDisabled && availableProviderEntries.size > 1) {
                Box {
                    IconButton(onClick = { showProviderMenu = true }) {
                        Icon(
                            imageVector = Tabler.Outline.Refresh,
                            contentDescription = stringResource(R.string.lyrics_switch_provider_cd),
                            tint = if (effectiveSelectedProvider != LyricsProvider.AUTO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showProviderMenu,
                        onDismissRequest = { showProviderMenu = false },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .widthIn(min = 180.dp, max = 260.dp)
                            .heightIn(max = 320.dp)
                    ) {
                        availableProviderEntries.forEachIndexed { index, provider ->
                            val syncTag = availableProvidersMap[provider]
                            val isAvailable = availableProvidersMap.isEmpty() || syncTag != null || provider == LyricsProvider.AUTO
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                                    thickness = 0.5.dp
                                )
                            }
                            val isSelected = provider == effectiveSelectedProvider
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = provider.displayName,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isAvailable -> MaterialTheme.colorScheme.onSurface
                                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                }
                                            )

                                            if (syncTag != null) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    }
                                                ) {
                                                    Text(
                                                        text = syncTag,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isSelected) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Tabler.Outline.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedProvider = provider
                                    showProviderMenu = false
                                },
                                enabled = isAvailable,
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onOpenQueue) {
                Icon(
                    imageVector = Tabler.Outline.Playlist,
                    contentDescription = stringResource(R.string.queue_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}
