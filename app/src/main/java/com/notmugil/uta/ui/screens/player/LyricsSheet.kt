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
import com.notmugil.uta.data.repository.LyricsData
import com.notmugil.uta.data.repository.LyricsRepository
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.screens.player.components.LyricsAmbientBackground
import com.notmugil.uta.ui.screens.player.components.PlaybackSeekBar
import com.notmugil.uta.ui.screens.player.lyrics.KaraokeWord
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

    val currentView = LocalView.current
    DisposableEffect(keepScreenOnLyrics) {
        if (keepScreenOnLyrics) {
            currentView.keepScreenOn = true
        }
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    val availableProviderEntries = remember(onlineLyricsProviders, lyricsSourceMode) {
        val enabledOnline = onlineLyricsProviders.filter { it.enabled }.map { it.provider }
        when (lyricsSourceMode) {
            LyricsSourceMode.DISABLED -> emptyList()
            LyricsSourceMode.SERVER_ONLY -> listOf(LyricsProvider.AUTO, LyricsProvider.SUBSONIC)
            LyricsSourceMode.ONLINE_ONLY -> listOf(LyricsProvider.AUTO) + enabledOnline
            LyricsSourceMode.BOTH -> listOf(LyricsProvider.AUTO, LyricsProvider.SUBSONIC) + enabledOnline
        }
    }

    var lyricsData by remember { mutableStateOf<LyricsData?>(null) }
    var selectedProvider by remember { mutableStateOf(LyricsProvider.AUTO) }
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
    LaunchedEffect(track.id, selectedProvider) {
        userScrolledAway = false
    }

    // Check available providers
    LaunchedEffect(track.id) {
        try {
            availableProvidersMap = LyricsRepository.checkAvailableProviders(context, track)
        } catch (_: Exception) {}
    }

    LaunchedEffect(track.id, selectedProvider) {
        isLoading = true
        try {
            lyricsData = LyricsRepository.getLyrics(context, track, provider = selectedProvider)
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

    val currentLineIndex = if (lines.isNotEmpty()) {
        lines.indexOfLast { it.startMs <= smoothPositionMs }.coerceAtLeast(0)
    } else {
        0
    }

    LaunchedEffect(currentLineIndex, userScrolledAway, lyricsData) {
        if (!userScrolledAway && lines.isNotEmpty() && currentLineIndex in lines.indices) {
            listState.animateScrollToItem((currentLineIndex - 1).coerceAtLeast(0))
        }
    }

    val effectiveDurationMs = if (durationMs > 0) durationMs else (track.durationSeconds * 1000L).coerceAtLeast(1L)
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val sliderValue = if (isDraggingSlider) {
        dragPositionMs
    } else {
        smoothPositionMs.toFloat().coerceIn(0f, effectiveDurationMs.toFloat())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LyricsAmbientBackground(track = track, isPlaying = isPlaying)

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
                            text = listOfNotNull(track.artist, track.album)
                                .filter { it.isNotBlank() }
                                .joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showProviderMenu = true },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.RotateClockwise,
                                contentDescription = stringResource(R.string.lyrics_switch_provider_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
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
                                val isSelected = provider == selectedProvider
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
                            contentPadding = PaddingValues(top = 28.dp, bottom = 420.dp)
                        ) {
                            itemsIndexed(lines) { index, line ->
                                val isCurrent = index == currentLineIndex
                                val dist = kotlin.math.abs(index - currentLineIndex)
                                val targetLineAlpha = if (isBrowsing) {
                                    if (isCurrent) 1.0f else 0.78f
                                } else {
                                    when (dist) {
                                        0 -> 1.0f
                                        1 -> 0.75f
                                        2 -> 0.55f
                                        3 -> 0.40f
                                        else -> 0.28f
                                    }
                                }
                                val lineAlpha by animateFloatAsState(
                                    targetValue = targetLineAlpha,
                                    animationSpec = tween(220),
                                    label = "line_alpha_$index"
                                )
                                val textColor = MaterialTheme.colorScheme.onBackground
                                val dimColor = textColor.copy(alpha = lineAlpha)

                                val targetBlurDp = if (blurInactiveLyrics && !isCurrent && !isBrowsing) {
                                    when (dist) {
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
                                    label = "line_blur_$index"
                                )
                                val lineBlurModifier = if (blurDp > 0.dp) {
                                    Modifier.blur(blurDp)
                                } else {
                                    Modifier
                                }

                                if (line.isWordSynced) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
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
                                                    fontSize = if (isCurrent) 28.sp else 20.sp,
                                                    isCurrentLine = isCurrent
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    val lineColor = if (isCurrent) accentColor else dimColor
                                    val lineFontWeight = if (isCurrent) FontWeight.ExtraBold else (if (dist == 1) FontWeight.Medium else FontWeight.Normal)
                                    val lineFontSize = if (isCurrent) 28.sp else 20.sp
                                    val lineLineHeight = if (isCurrent) 38.sp else 28.sp

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
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
                                                .padding(horizontal = 8.dp, vertical = if (isCurrent) 8.dp else 6.dp)
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
                                .padding(bottom = 12.dp)
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
                    } else if (!plainLyrics.isNullOrBlank()) {
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
                            Text(
                                text = stringResource(R.string.lyrics_not_found),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
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
                                imageVector = Tabler.Filled.PlayerTrackPrev,
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
                                imageVector = Tabler.Filled.PlayerTrackNext,
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
