package com.notmugil.uta.ui.screens.player.layouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.data.preferences.LyricsProvider
import com.notmugil.uta.data.preferences.LyricsSourceMode
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.data.repository.LyricsData
import com.notmugil.uta.data.repository.LyricsRepository
import com.notmugil.uta.domain.model.QueueItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.SleepTimerMode
import com.notmugil.uta.ui.screens.player.components.MiniWaveformEqualizer
import com.notmugil.uta.ui.screens.player.components.PlaybackControlsSection
import com.notmugil.uta.ui.screens.player.components.PlayerSeekBarSection
import com.notmugil.uta.ui.screens.player.lyrics.KaraokeWord
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.util.Formatters
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun LandscapePlayerLayout(
    track: TrackItem?,
    queue: List<QueueItem>,
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
    onPlayQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (from: Int, to: Int) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float? = null,
    onToggleDownload: () -> Unit = {},
    trackStats: String? = null,
    currentEntryId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appPreferences = LocalAppPreferences.current

    val lyricsSourceMode by (appPreferences?.lyricsSourceMode?.collectAsState() ?: remember { mutableStateOf(LyricsSourceMode.BOTH) })
    val isLyricsDisabled = lyricsSourceMode == LyricsSourceMode.DISABLED
    val onlineProvidersConfig by (appPreferences?.onlineLyricsProviders?.collectAsState() ?: remember { mutableStateOf(emptyList()) })

    val networkMonitor = com.notmugil.uta.data.LocalNetworkMonitor.current
    val isOnline by (networkMonitor?.isOnline?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(com.notmugil.uta.data.SubsonicSession.isOnline) })
    val isManualOffline by (appPreferences?.isOfflineModeManual?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(com.notmugil.uta.data.SubsonicSession.isManualOffline) })
    val isOffline = isManualOffline || !isOnline

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

    var showQueue by remember { mutableStateOf(false) }
    val queueListState = rememberLazyListState()
    val queueReorderState = rememberReorderableLazyListState(queueListState) { from, to ->
        onMoveQueueItem(from.index, to.index)
    }

    LaunchedEffect(showQueue, track?.id) {
        if (showQueue) {
            val currentIndex = queue.indexOfFirst { it.track.id == track?.id }
            if (currentIndex > 0) {
                queueListState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
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

    val lines = lyricsData?.syncedLines.orEmpty()
    val plainLyrics = lyricsData?.plainLyrics

    val currentLineIndex = if (lines.isNotEmpty()) {
        lines.indexOfLast { it.startMs <= currentPositionMs }.coerceAtLeast(0)
    } else {
        0
    }

    LaunchedEffect(currentLineIndex, userScrolledAway, lyricsData) {
        if (!userScrolledAway && lines.isNotEmpty() && currentLineIndex in lines.indices) {
            listState.animateScrollToItem((currentLineIndex - 1).coerceAtLeast(0))
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Tabler.Outline.ArrowLeft,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer {
                        translationX = horizontalArtworkOffset
                    },
                contentAlignment = Alignment.Center
            ) {
                com.notmugil.uta.ui.shared.AnimatedAlbumArtView(
                    track = track,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = track?.title ?: stringResource(R.string.album_unknown_album),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (track != null) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .basicMarquee()
                            .clickable(enabled = track.artistId != null) {
                                track.artistId?.let { onNavigateToArtist(it) }
                            }
                    )
                }
            }

            PlayerSeekBarSection(
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                isDraggingSlider = isDraggingSlider,
                dragPositionMs = dragPositionMs,
                onDraggingSliderChange = onDraggingSliderChange,
                onDragPositionChange = onDragPositionChange,
                onSeekTo = onSeekTo,
                songKey = track?.id,
                trackStats = trackStats,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            PlaybackControlsSection(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                modifier = Modifier.padding(horizontal = 2.dp),
                compact = true
            )
        }

        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            if (showQueue) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.Playlist,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.queue_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${stringResource(R.string.playlist_songs_count, queue.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        thickness = 0.5.dp
                    )

                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(
                            items = queue,
                            key = { _, s -> s.entryId }
                        ) { index, queueItem ->
                            val isCurrent = if (currentEntryId != null) queueItem.entryId == currentEntryId else queueItem.track.id == track?.id
                            val accentColor = MaterialTheme.colorScheme.primary

                            ReorderableItem(queueReorderState, key = queueItem.entryId) { isDragging ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(
                                            fadeInSpec = null,
                                            fadeOutSpec = null,
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                        .background(
                                            if (isDragging) {
                                                accentColor.copy(alpha = 0.35f)
                                            } else if (isCurrent) {
                                                accentColor.copy(alpha = 0.16f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable {
                                            if (!isDragging) {
                                                onPlayQueueIndex(index)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CoverArtImage(
                                            coverArtId = queueItem.track.coverArtId,
                                            contentDescription = queueItem.track.title,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .then(if (isCurrent) Modifier.alpha(0.55f) else Modifier)
                                        )

                                        if (isCurrent) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.35f))
                                            )
                                            MiniWaveformEqualizer(
                                                isPlaying = isPlaying,
                                                color = accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = queueItem.track.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = queueItem.track.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isCurrent) {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = Formatters.formatDurationSeconds(queueItem.track.durationSeconds),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = { onRemoveQueueIndex(index) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Tabler.Outline.X,
                                            contentDescription = stringResource(R.string.action_remove_from_queue),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier
                                            .draggableHandle()
                                            .size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Tabler.Outline.GripVertical,
                                            contentDescription = stringResource(R.string.queue_drag_reorder_cd),
                                            tint = if (isDragging) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (index < queue.size - 1) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 60.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        }
                    } else if (lines.isNotEmpty()) {
                        val isBrowsing = userScrolledAway || isDragged

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 320.dp)
                        ) {
                            itemsIndexed(lines) { index, line ->
                                val isCurrent = index == currentLineIndex
                                val isPastLine = index < currentLineIndex
                                val accentColor = MaterialTheme.colorScheme.primary
                                val textColor = MaterialTheme.colorScheme.onBackground
                                val targetDimAlpha = if (isBrowsing) 0.78f else 0.38f
                                val dimAlpha by animateFloatAsState(
                                    targetValue = targetDimAlpha,
                                    animationSpec = tween(220),
                                    label = "landscape_dim_alpha_$index"
                                )
                                val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = dimAlpha)

                                if (isCurrent && line.isWordSynced) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
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
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
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
                                                    currentPositionMs = currentPositionMs,
                                                    accentColor = accentColor,
                                                    textColor = textColor,
                                                    dimColor = dimColor,
                                                    fontSize = 20.sp,
                                                    isCurrentLine = isCurrent
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    val lineColor = when {
                                        isCurrent -> accentColor
                                        isPastLine -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                        else -> dimColor
                                    }
                                    val lineFontWeight = when {
                                        isCurrent -> FontWeight.ExtraBold
                                        isPastLine -> FontWeight.Normal
                                        else -> FontWeight.Normal
                                    }
                                    val lineFontSize = if (isCurrent) 20.sp else 16.sp
                                    val lineLineHeight = if (isCurrent) 28.sp else 22.sp

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = if (isCurrent) 3.dp else 1.dp)
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
                                                .padding(horizontal = 8.dp, vertical = if (isCurrent) 6.dp else 4.dp)
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
                                .padding(bottom = 4.dp)
                        ) {
                            Surface(
                                onClick = {
                                    userScrolledAway = false
                                    coroutineScope.launch {
                                        listState.animateScrollToItem((currentLineIndex - 1).coerceAtLeast(0))
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Tabler.Outline.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.lyrics_resume_scroll),
                                        style = MaterialTheme.typography.labelSmall,
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
                                .padding(vertical = 10.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                text = plainLyrics,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 24.sp,
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
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.Music,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = stringResource(R.string.lyrics_not_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { showQueue = !showQueue },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (showQueue) Tabler.Outline.Microphone2 else Tabler.Outline.Playlist,
                    contentDescription = if (showQueue) stringResource(R.string.lyrics) else stringResource(R.string.queue_title),
                    tint = if (showQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (!isOffline && !showQueue && track != null && !isLyricsDisabled && availableProviderEntries.size > 1) {
                Box {
                    IconButton(
                        onClick = { showProviderMenu = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.Refresh,
                            contentDescription = stringResource(R.string.lyrics_switch_provider_cd),
                            tint = if (effectiveSelectedProvider != LyricsProvider.AUTO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
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
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = provider.displayName,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isAvailable -> MaterialTheme.colorScheme.onSurface
                                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                },
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp
                                            )
                                            if (syncTag != null) {
                                                Text(
                                                    text = syncTag,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Tabler.Outline.Check,
                                                contentDescription = stringResource(R.string.settings_selected_cd),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedProvider = provider
                                    showProviderMenu = false
                                }
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isStarred) Tabler.Filled.Heart else Tabler.Outline.Heart,
                    contentDescription = if (isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
                    tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onToggleDownload,
                modifier = Modifier.size(40.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress ?: 0.1f },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = if (isDownloaded) Tabler.Outline.CircleCheck else Tabler.Outline.Download,
                        contentDescription = if (isDownloaded) stringResource(R.string.download_status_completed) else stringResource(R.string.action_download),
                        tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            IconButton(
                onClick = onOpenSleepTimer,
                modifier = Modifier.size(40.dp)
            ) {
                val isTimerRunning = sleepTimerMode !is SleepTimerMode.Disabled
                Icon(
                    imageVector = Tabler.Outline.Moon,
                    contentDescription = stringResource(R.string.sleep_timer),
                    tint = if (isTimerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
