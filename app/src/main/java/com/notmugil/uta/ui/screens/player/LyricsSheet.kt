package com.notmugil.uta.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.notmugil.uta.R
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.data.preferences.LyricsProvider
import com.notmugil.uta.data.preferences.LyricsSourceMode
import com.notmugil.uta.data.preferences.LyricsStyle
import com.notmugil.uta.data.repository.LyricsData
import com.notmugil.uta.data.repository.LyricsRepository
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.screens.player.components.PlaybackSeekBar
import com.notmugil.uta.ui.screens.player.lyrics.KaraokeWord
import com.notmugil.uta.ui.screens.player.lyrics.LineSyncedLyricText
import com.notmugil.uta.ui.screens.player.lyrics.parseWordSyncedLine
import com.notmugil.uta.ui.screens.player.lyrics.buildLyricsItems
import com.notmugil.uta.ui.screens.player.lyrics.InstrumentalGapItem
import com.notmugil.uta.ui.screens.player.lyrics.LyricsItem
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import com.notmugil.uta.ui.theme.UtaTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    track: TrackItem,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appPreferences = LocalAppPreferences.current

    val lyricsSourceMode by (appPreferences?.lyricsSourceMode?.collectAsState() ?: remember { mutableStateOf(LyricsSourceMode.BOTH) })
    val onlineLyricsProviders by (appPreferences?.onlineLyricsProviders?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val keepScreenOnLyrics by (appPreferences?.keepScreenOnLyrics?.collectAsState() ?: remember { mutableStateOf(false) })
    val blurInactiveLyrics by (appPreferences?.blurInactiveLyrics?.collectAsState() ?: remember { mutableStateOf(true) })
    val lyricsStyle by (appPreferences?.lyricsStyle?.collectAsState() ?: remember { mutableStateOf(LyricsStyle.DEFAULT) })

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

    val availableProviderEntries = remember(onlineLyricsProviders, lyricsSourceMode, isOffline) {
        if (isOffline) {
            if (lyricsSourceMode == LyricsSourceMode.DISABLED) emptyList() else listOf(LyricsProvider.SUBSONIC)
        } else {
            val enabledOnline = onlineLyricsProviders.filter { it.enabled }.map { it.provider }
            when (lyricsSourceMode) {
                LyricsSourceMode.DISABLED -> emptyList()
                LyricsSourceMode.SERVER_ONLY -> listOf(LyricsProvider.SUBSONIC)
                LyricsSourceMode.BOTH -> listOf(LyricsProvider.AUTO, LyricsProvider.SUBSONIC) + enabledOnline
            }
        }
    }

    var lyricsData by remember { mutableStateOf<LyricsData?>(null) }
    var selectedProvider by remember { mutableStateOf(LyricsProvider.AUTO) }
    val effectiveSelectedProvider = if (isOffline || lyricsSourceMode == LyricsSourceMode.SERVER_ONLY) LyricsProvider.SUBSONIC else selectedProvider
    var availableProvidersMap by remember(track.id) { mutableStateOf<Map<LyricsProvider, String>>(emptyMap()) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var userScrolledAway by remember { mutableStateOf(false) }

    val accentColor = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()

    // Detect user manual drag gesture to pause auto-scrolling
    LaunchedEffect(isDragged) {
        if (isDragged) {
            userScrolledAway = true
        }
    }

    // Reset user scroll state on new song or provider change
    LaunchedEffect(track.id, effectiveSelectedProvider) {
        userScrolledAway = false
    }

    // Check available providers
    LaunchedEffect(track.id, lyricsSourceMode, isOffline) {
        try {
            availableProvidersMap = LyricsRepository.checkAvailableProviders(context, track)
        } catch (_: Exception) {}
    }

    LaunchedEffect(track.id, effectiveSelectedProvider, lyricsSourceMode, isOffline) {
        isLoading = true
        try {
            lyricsData = LyricsRepository.getLyrics(context, track, provider = effectiveSelectedProvider)
        } catch (_: Exception) {
            lyricsData = null
        } finally {
            isLoading = false
        }
    }

    var smoothPositionMs by remember(track.id) { androidx.compose.runtime.mutableLongStateOf(currentPositionMs) }

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
    val effectiveDurationMs = if (durationMs > 0) durationMs else (track.durationSeconds * 1000L).coerceAtLeast(1L)

    val lyricsItems = remember(lines, effectiveDurationMs) {
        buildLyricsItems(lines, effectiveDurationMs)
    }

    val activeItemIndices = remember(lyricsItems, smoothPositionMs) {
        if (lyricsItems.isEmpty()) emptyList()
        else {
            lyricsItems.indices.filter { idx ->
                val item = lyricsItems[idx]
                smoothPositionMs >= item.startMs && smoothPositionMs < item.endMs
            }
        }
    }

    val currentItemIndex = if (lyricsItems.isNotEmpty()) {
        activeItemIndices.firstOrNull() ?: lyricsItems.indexOfLast { it.startMs <= smoothPositionMs }.coerceAtLeast(0)
    } else {
        0
    }

    val focusedItemIndex by remember(lyricsItems, userScrolledAway, isDragged, currentItemIndex) {
        derivedStateOf {
            if (!userScrolledAway && !isDragged) {
                currentItemIndex
            } else {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) {
                    currentItemIndex
                } else {
                    val focalY = layoutInfo.viewportSize.height * 0.35f
                    val closestItem = visibleItems.minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2f
                        kotlin.math.abs(itemCenter - focalY)
                    }
                    closestItem?.index ?: currentItemIndex
                }
            }
        }
    }

    LaunchedEffect(currentItemIndex, userScrolledAway, lyricsData) {
        if (!userScrolledAway && lyricsItems.isNotEmpty() && currentItemIndex in lyricsItems.indices) {
            listState.animateScrollToItem((currentItemIndex - 1).coerceAtLeast(0))
        }
    }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val sliderValue = if (isDraggingSlider) {
        dragPositionMs
    } else {
        smoothPositionMs.toFloat().coerceIn(0f, effectiveDurationMs.toFloat())
    }

    UtaTheme {
        val isLightMode = (0.2126f * MaterialTheme.colorScheme.background.red +
                0.7152f * MaterialTheme.colorScheme.background.green +
                0.0722f * MaterialTheme.colorScheme.background.blue) > 0.5f
        val dynamicThemeManager = LocalDynamicThemeManager.current
        val dynamicDarkBgColor by (dynamicThemeManager?.dynamicDarkBgColor?.collectAsState() ?: remember { mutableStateOf(null) })
        val sheetBgColor = if (isLightMode) MaterialTheme.colorScheme.background else (dynamicDarkBgColor ?: Color(0xFF101014))

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = null,
            shape = RectangleShape,
            containerColor = sheetBgColor,
            scrimColor = if (isLightMode) Color.Black.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.5f),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                LyricsAmbientBackground(
                    track = track,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Tabler.Outline.ChevronDown,
                                contentDescription = stringResource(R.string.lyrics_collapse_cd),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = track.artist.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }

                    if (!isOffline && availableProviderEntries.size > 1) {
                        Box {
                            IconButton(
                                onClick = { showProviderMenu = true },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.Refresh,
                                    contentDescription = stringResource(R.string.lyrics_switch_provider_cd),
                                    tint = if (effectiveSelectedProvider != LyricsProvider.AUTO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp)
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
                                    enabled = isAvailable,
                                    onClick = {
                                        selectedProvider = provider
                                        showProviderMenu = false
                                    },
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 28.dp, bottom = 420.dp)
                        ) {
                            itemsIndexed(lyricsItems, key = { _, item -> item.key }) { index, item ->
                                val isItemActive = activeItemIndices.contains(index) || index == currentItemIndex
                                val isCurrent = index == currentItemIndex
                                val isFocused = index == focusedItemIndex || (isItemActive && !isBrowsing)
                                val focusDist = if (isItemActive) 0 else kotlin.math.abs(index - focusedItemIndex)
                                val isPast = index < currentItemIndex && !isItemActive
                                val isBetter = lyricsStyle == LyricsStyle.BETTER
                                val targetItemAlpha = if (blurInactiveLyrics || !isBrowsing) {
                                    when (focusDist) {
                                        0 -> 1.0f
                                        1 -> if (isBetter && isPast) 0.30f else 0.75f
                                        2 -> if (isBetter && isPast) 0.18f else 0.55f
                                        3 -> if (isBetter && isPast) 0.10f else 0.40f
                                        else -> if (isBetter && isPast) 0.05f else 0.28f
                                    }
                                } else {
                                    if (isCurrent || isFocused || isItemActive) 1.0f else if (isBetter && isPast) 0.25f else 0.78f
                                }
                                val itemAlpha by animateFloatAsState(
                                    targetValue = targetItemAlpha,
                                    animationSpec = tween(220),
                                    label = "item_alpha_$index"
                                )
                                val textColor = MaterialTheme.colorScheme.onBackground
                                val dimColor = textColor.copy(alpha = itemAlpha)

                                val targetBlurDp = if (blurInactiveLyrics && !isFocused && !isItemActive) {
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
                                    label = "item_blur_$index"
                                )
                                val itemBlurModifier = if (blurDp > 0.dp) {
                                    Modifier.blur(blurDp)
                                } else {
                                    Modifier
                                }

                                when (item) {
                                    is LyricsItem.InstrumentalGap -> {
                                        InstrumentalGapItem(
                                            gap = item,
                                            currentPositionMs = smoothPositionMs,
                                            isActive = isItemActive,
                                            accentColor = accentColor,
                                            textColor = textColor,
                                            dimColor = dimColor,
                                            onSeekTo = { pos ->
                                                userScrolledAway = false
                                                onSeekTo(pos)
                                            },
                                            lyricsStyle = lyricsStyle,
                                            modifier = itemBlurModifier
                                        )
                                    }
                                    is LyricsItem.Lyric -> {
                                        val line = item.line
                                        if (line.isWordSynced) {
                                            val lineParts = remember(line.words, line.endMs, lyricsStyle) {
                                                if (lyricsStyle == LyricsStyle.BETTER) {
                                                    parseWordSyncedLine(line.words, line.endMs)
                                                } else {
                                                    null
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = Color.Transparent,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(itemBlurModifier)
                                            ) {
                                                if (lineParts != null && lineParts.parentheticalWords.isNotEmpty()) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                userScrolledAway = false
                                                                onSeekTo(line.startMs)
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    ) {
                                                        FlowRow(
                                                            horizontalArrangement = Arrangement.Start,
                                                            verticalArrangement = Arrangement.Center,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            lineParts.mainWords.forEach { itemWord ->
                                                                KaraokeWord(
                                                                    word = itemWord.word,
                                                                    wordEnd = itemWord.wordEnd,
                                                                    currentPositionMs = smoothPositionMs,
                                                                    accentColor = accentColor,
                                                                    textColor = textColor,
                                                                    dimColor = dimColor,
                                                                    fontSize = 21.sp,
                                                                    isCurrentLine = isItemActive,
                                                                    lyricsStyle = lyricsStyle
                                                                )
                                                            }
                                                        }
                                                        FlowRow(
                                                            horizontalArrangement = Arrangement.Start,
                                                            verticalArrangement = Arrangement.Center,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(top = 2.dp)
                                                        ) {
                                                            lineParts.parentheticalWords.forEach { itemWord ->
                                                                KaraokeWord(
                                                                    word = itemWord.word,
                                                                    wordEnd = itemWord.wordEnd,
                                                                    currentPositionMs = smoothPositionMs,
                                                                    accentColor = accentColor,
                                                                    textColor = textColor,
                                                                    dimColor = dimColor,
                                                                    fontSize = 15.5.sp,
                                                                    isCurrentLine = isItemActive,
                                                                    lyricsStyle = lyricsStyle
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.Start,
                                                        verticalArrangement = Arrangement.Center,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                userScrolledAway = false
                                                                onSeekTo(line.startMs)
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 6.dp)
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
                                                                fontSize = 21.sp,
                                                                isCurrentLine = isItemActive,
                                                                lyricsStyle = lyricsStyle
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = Color.Transparent,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(itemBlurModifier)
                                            ) {
                                                LineSyncedLyricText(
                                                    text = line.text,
                                                    startMs = line.startMs,
                                                    currentPositionMs = smoothPositionMs,
                                                    isActive = isItemActive,
                                                    isPast = isPast,
                                                    accentColor = accentColor,
                                                    textColor = textColor,
                                                    dimColor = dimColor,
                                                    fontSize = 21.sp,
                                                    lineHeight = 30.sp,
                                                    lyricsStyle = lyricsStyle,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            userScrolledAway = false
                                                            onSeekTo(line.startMs)
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
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
                                .padding(bottom = 12.dp)
                        ) {
                            Surface(
                                onClick = {
                                    userScrolledAway = false
                                    coroutineScope.launch {
                                        listState.animateScrollToItem((currentItemIndex - 1).coerceAtLeast(0))
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                                shadowElevation = 6.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Tabler.Outline.RotateClockwise,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.lyrics_resume_scroll),
                                        style = MaterialTheme.typography.labelLarge,
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
                                .padding(vertical = 28.dp)
                        ) {
                            Text(
                                text = plainLyrics,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 34.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp)
                ) {
                    PlaybackSeekBar(
                        value = sliderValue,
                        onValueChange = {
                            isDraggingSlider = true
                            dragPositionMs = it
                        },
                        onValueChangeFinished = {
                            onSeekTo(dragPositionMs.toLong())
                            isDraggingSlider = false
                        },
                        valueRange = 0f..effectiveDurationMs.toFloat().coerceAtLeast(1f),
                        isPlaying = isPlaying,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
                        thumbColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(32.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Tabler.Filled.PlayerSkipBack,
                                contentDescription = stringResource(R.string.player_previous_cd),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Surface(
                            onClick = onTogglePlayPause,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Tabler.Filled.PlayerPause else Tabler.Filled.PlayerPlay,
                                    contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Tabler.Filled.PlayerSkipForward,
                                contentDescription = stringResource(R.string.player_next_cd),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun LyricsAmbientBackground(
    track: TrackItem? = null,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    val dynamicThemeManager = LocalDynamicThemeManager.current
    val dynamicDarkBgColor by (dynamicThemeManager?.dynamicDarkBgColor?.collectAsState() ?: remember { mutableStateOf(null) })
    val dynamicSeedColor by (dynamicThemeManager?.dynamicSeedColor?.collectAsState() ?: remember { mutableStateOf(null) })

    val isLightMode = (0.2126f * MaterialTheme.colorScheme.background.red +
            0.7152f * MaterialTheme.colorScheme.background.green +
            0.0722f * MaterialTheme.colorScheme.background.blue) > 0.5f

    val ambientBgColor = if (isLightMode) MaterialTheme.colorScheme.background else (dynamicDarkBgColor ?: Color(0xFF101014))
    val targetBlobColor = dynamicSeedColor ?: MaterialTheme.colorScheme.primary

    val blobColor by animateColorAsState(targetBlobColor, animationSpec = tween(700), label = "lyrics_ambient_blob")

    val ambientProgress = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val current = ambientProgress.value
                ambientProgress.animateTo(
                    targetValue = current + 1f,
                    animationSpec = tween(durationMillis = 44000, easing = LinearEasing)
                )
            }
        } else {
            ambientProgress.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ambientBgColor)
    ) {
        val t = ambientProgress.value
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(70.dp)
        ) {
            val w = size.width
            val h = size.height
            val twoPi = 6.2831855f

            val p1Alpha1 = if (isLightMode) 0.28f else 0.52f
            val p1Alpha2 = if (isLightMode) 0.09f else 0.18f
            val p2Alpha1 = if (isLightMode) 0.25f else 0.48f
            val p2Alpha2 = if (isLightMode) 0.08f else 0.16f
            val s3Alpha1 = if (isLightMode) 0.24f else 0.46f
            val s3Alpha2 = if (isLightMode) 0.07f else 0.14f
            val s4Alpha1 = if (isLightMode) 0.22f else 0.44f
            val s4Alpha2 = if (isLightMode) 0.07f else 0.14f
            val s5Alpha1 = if (isLightMode) 0.25f else 0.48f
            val s5Alpha2 = if (isLightMode) 0.08f else 0.16f

            val c1X = w * (0.50f + 0.36f * sin(t * twoPi * 0.45f) + 0.16f * cos(t * twoPi * 0.72f))
            val c1Y = h * (0.35f + 0.12f * cos(t * twoPi * 0.38f) + 0.05f * sin(t * twoPi * 0.65f))
            val r1X = w * 0.85f * (1f + 0.15f * sin(t * 2.8f))
            val r1Y = w * 0.75f * (1f - 0.15f * cos(t * 2.4f))
            val path1 = createOrganicBlobPath(
                centerX = c1X,
                centerY = c1Y,
                baseRadiusX = r1X,
                baseRadiusY = r1Y,
                pointCount = 8,
                time = t,
                speed = 3.6f,
                distortion = 0.38f,
                phase = 0.0f
            )
            drawPath(
                path = path1,
                brush = Brush.radialGradient(
                    colors = listOf(
                        blobColor.copy(alpha = p1Alpha1),
                        blobColor.copy(alpha = p1Alpha2),
                        Color.Transparent
                    ),
                    center = Offset(c1X, c1Y),
                    radius = (r1X.coerceAtLeast(r1Y) * 1.15f)
                )
            )

            val c2X = w * (0.50f - 0.34f * cos(t * twoPi * 0.40f) - 0.18f * sin(t * twoPi * 0.82f))
            val c2Y = h * (0.55f - 0.10f * sin(t * twoPi * 0.35f) + 0.05f * cos(t * twoPi * 0.58f))
            val r2X = w * 0.80f * (1f - 0.16f * cos(t * 2.5f))
            val r2Y = w * 0.85f * (1f + 0.16f * sin(t * 2.7f))
            val path2 = createOrganicBlobPath(
                centerX = c2X,
                centerY = c2Y,
                baseRadiusX = r2X,
                baseRadiusY = r2Y,
                pointCount = 8,
                time = t,
                speed = 3.2f,
                distortion = 0.40f,
                phase = 2.1f
            )
            drawPath(
                path = path2,
                brush = Brush.radialGradient(
                    colors = listOf(
                        blobColor.copy(alpha = p2Alpha1),
                        blobColor.copy(alpha = p2Alpha2),
                        Color.Transparent
                    ),
                    center = Offset(c2X, c2Y),
                    radius = (r2X.coerceAtLeast(r2Y) * 1.15f)
                )
            )

            val c3X = w * (0.50f + 0.60f * sin(t * twoPi * 0.80f + 1.2f) - 0.18f * cos(t * twoPi * 1.35f))
            val c3Y = h * (0.28f + 0.10f * sin(t * twoPi * 0.70f) - 0.05f * cos(t * twoPi * 1.10f))
            val r3X = w * 0.90f * (1f + 0.18f * sin(t * 4.8f))
            val r3Y = h * 0.22f * (1f - 0.18f * sin(t * 4.8f))
            val path3 = createOrganicBlobPath(
                centerX = c3X,
                centerY = c3Y,
                baseRadiusX = r3X,
                baseRadiusY = r3Y,
                pointCount = 10,
                time = t,
                speed = 5.8f,
                distortion = 0.42f,
                phase = 4.3f
            )
            drawPath(
                path = path3,
                brush = Brush.radialGradient(
                    colors = listOf(
                        blobColor.copy(alpha = s3Alpha1),
                        blobColor.copy(alpha = s3Alpha2),
                        Color.Transparent
                    ),
                    center = Offset(c3X, c3Y),
                    radius = (r3X * 1.1f)
                )
            )

            val c4X = w * (0.50f - 0.55f * cos(t * twoPi * 0.75f + 0.7f) + 0.20f * sin(t * twoPi * 1.45f))
            val c4Y = h * (0.72f + 0.08f * cos(t * twoPi * 0.65f) + 0.04f * sin(t * twoPi * 1.05f))
            val r4X = w * 0.85f * (1f + 0.16f * cos(t * 4.2f))
            val r4Y = h * 0.20f * (1f - 0.16f * sin(t * 4.2f))
            val tiltAngle = 30f * sin(t * 4.5f)
            val path4 = createOrganicBlobPath(
                centerX = c4X,
                centerY = c4Y,
                baseRadiusX = r4X,
                baseRadiusY = r4Y,
                pointCount = 8,
                time = t,
                speed = 5.2f,
                distortion = 0.40f,
                phase = 1.2f
            )
            withTransform({
                rotate(tiltAngle, Offset(c4X, c4Y))
            }) {
                drawPath(
                    path = path4,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            blobColor.copy(alpha = s4Alpha1),
                            blobColor.copy(alpha = s4Alpha2),
                            Color.Transparent
                        ),
                        center = Offset(c4X, c4Y),
                        radius = (r4X * 1.1f)
                    )
                )
            }

            val c5X = w * (0.25f + 0.50f * sin(t * twoPi * 0.90f + 2.5f) - 0.18f * cos(t * twoPi * 1.60f))
            val c5Y = h * (0.85f - 0.08f * sin(t * twoPi * 0.80f) + 0.04f * cos(t * twoPi * 1.25f))
            val r5X = w * 0.80f * (1f + 0.20f * sin(t * 5.5f))
            val r5Y = w * 0.70f * (1f - 0.20f * cos(t * 5.5f))
            val path5 = createOrganicBlobPath(
                centerX = c5X,
                centerY = c5Y,
                baseRadiusX = r5X,
                baseRadiusY = r5Y,
                pointCount = 7,
                time = t,
                speed = 6.8f,
                distortion = 0.45f,
                phase = 3.5f
            )
            drawPath(
                path = path5,
                brush = Brush.radialGradient(
                    colors = listOf(
                        blobColor.copy(alpha = s5Alpha1),
                        blobColor.copy(alpha = s5Alpha2),
                        Color.Transparent
                    ),
                    center = Offset(c5X, c5Y),
                    radius = (r5X * 1.15f)
                )
            )
        }
    }
}

private fun createOrganicBlobPath(
    centerX: Float,
    centerY: Float,
    baseRadiusX: Float,
    baseRadiusY: Float,
    pointCount: Int = 8,
    time: Float,
    speed: Float,
    distortion: Float,
    phase: Float
): Path {
    val path = Path()
    val points = ArrayList<Offset>(pointCount)
    val angleStep = (2.0 * Math.PI / pointCount).toFloat()

    for (i in 0 until pointCount) {
        val angle = i * angleStep
        val wave1 = sin(angle * 2f + time * speed + phase)
        val wave2 = cos(angle * 3f - time * speed * 1.3f + phase * 1.5f)
        val wave3 = sin(angle * 1f + time * speed * 0.7f)
        val factor = 1f + distortion * (0.55f * wave1 + 0.30f * wave2 + 0.15f * wave3)

        val rx = baseRadiusX * factor
        val ry = baseRadiusY * factor
        val px = centerX + rx * cos(angle)
        val py = centerY + ry * sin(angle)
        points.add(Offset(px, py))
    }

    val n = points.size
    val firstMid = Offset(
        (points[0].x + points[n - 1].x) / 2f,
        (points[0].y + points[n - 1].y) / 2f
    )
    path.moveTo(firstMid.x, firstMid.y)

    for (i in 0 until n) {
        val current = points[i]
        val next = points[(i + 1) % n]
        val midX = (current.x + next.x) / 2f
        val midY = (current.y + next.y) / 2f
        path.quadraticTo(current.x, current.y, midX, midY)
    }
    path.close()
    return path
}
