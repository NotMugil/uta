package com.notmugil.uta.ui.screens.player.layouts

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.notmugil.uta.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.notmugil.uta.data.repository.LyricsData
import com.notmugil.uta.data.repository.LyricsRepository
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.SleepTimerMode
import com.notmugil.uta.ui.screens.player.components.PlayerSeekBarSection
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import kotlin.math.abs

@Composable
fun CoverPlayerLayout(
    track: TrackItem?,
    horizontalArtworkOffset: Float,
    isPlaying: Boolean,
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
    scrollState: ScrollState,
    onOpenSleepTimer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    showLyrics: Boolean = true,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float? = null,
    onToggleDownload: () -> Unit = {},
    trackStats: String? = null
) {
    val dynamicThemeManager = LocalDynamicThemeManager.current
    val dynamicDarkBgColor by (dynamicThemeManager?.dynamicDarkBgColor?.collectAsState() ?: remember { mutableStateOf(null) })
    val accentColor = MaterialTheme.colorScheme.primary
    val bottomFadeColor = dynamicDarkBgColor ?: Color(0xFF0F1218)

    val context = LocalContext.current
    var showLyricsOverlay by remember { mutableStateOf(false) }
    var lyricsData by remember { mutableStateOf<LyricsData?>(null) }
    var isLoadingLyrics by remember { mutableStateOf(false) }

    LaunchedEffect(track?.id, showLyricsOverlay) {
        if (showLyricsOverlay && track != null) {
            isLoadingLyrics = true
            try {
                lyricsData = LyricsRepository.getLyrics(context, track)
            } catch (_: Exception) {
                lyricsData = null
            } finally {
                isLoadingLyrics = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = horizontalArtworkOffset
                    val progress = (abs(horizontalArtworkOffset) / 900f).coerceIn(0f, 0.45f)
                    alpha = 1f - progress
                    scaleX = 1f + progress * 0.1f
                    scaleY = 1f + progress * 0.1f
                }
        ) {
            com.notmugil.uta.ui.shared.AnimatedAlbumArtView(
                track = track,
                isPlaying = isPlaying,
                shape = RectangleShape,
                highRes = true,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.45f),
                                0.18f to Color.Black.copy(alpha = 0.15f),
                                0.35f to Color.Transparent,
                                0.50f to bottomFadeColor.copy(alpha = 0.35f),
                                0.68f to bottomFadeColor.copy(alpha = 0.76f),
                                0.85f to bottomFadeColor.copy(alpha = 0.94f),
                                1.0f to bottomFadeColor
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (showLyricsOverlay && track != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 10.dp)
                ) {
                    val lines = lyricsData?.syncedLines.orEmpty()
                    val plainLyrics = lyricsData?.plainLyrics
                    val listState = rememberLazyListState()
                    val currentLineIndex = if (lines.isNotEmpty()) {
                        lines.indexOfLast { it.startMs <= currentPositionMs }.coerceAtLeast(0)
                    } else {
                        0
                    }

                    LaunchedEffect(currentLineIndex, lines) {
                        if (lines.isNotEmpty() && currentLineIndex in lines.indices) {
                            listState.animateScrollToItem((currentLineIndex - 1).coerceAtLeast(0))
                        }
                    }

                    if (isLoadingLyrics) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    } else if (lines.isNotEmpty()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                        ) {
                            itemsIndexed(lines) { index, line ->
                                val isCurrent = index == currentLineIndex
                                Text(
                                    text = line.text,
                                    fontSize = if (isCurrent) 22.sp else 16.sp,
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (isCurrent) accentColor else Color.White.copy(alpha = 0.35f),
                                    lineHeight = if (isCurrent) 30.sp else 24.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSeekTo(line.startMs) }
                                        .padding(vertical = 6.dp)
                                )
                            }
                        }
                    } else if (!plainLyrics.isNullOrBlank() && !plainLyrics.trim().equals("null", ignoreCase = true)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = plainLyrics,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 26.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.Music,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = stringResource(R.string.lyrics_not_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (track != null) {
                        IconButton(
                            onClick = onMoreOptions,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.DotsVertical,
                                contentDescription = stringResource(R.string.action_more_options),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = track?.title ?: stringResource(R.string.album_unknown_album),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (track != null) {
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                fontWeight = FontWeight.Medium,
                                color = accentColor.copy(alpha = 0.95f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .basicMarquee()
                                    .clickable {
                                        track.artistId?.let { onNavigateToArtist(it) }
                                    }
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isStarred) Tabler.Filled.Heart else Tabler.Outline.Heart,
                            contentDescription = if (isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
                            tint = if (isStarred) accentColor else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                    trackStats = trackStats
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onSkipPrevious,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Tabler.Filled.PlayerSkipBack,
                                contentDescription = stringResource(R.string.player_previous_cd),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onTogglePlayPause,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Tabler.Filled.PlayerPause else Tabler.Filled.PlayerPlay,
                                contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onSkipNext,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Tabler.Filled.PlayerSkipForward,
                                contentDescription = stringResource(R.string.player_next_cd),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            imageVector = Tabler.Outline.ArrowsShuffle,
                            contentDescription = stringResource(R.string.action_shuffle),
                            tint = if (isShuffleEnabled) accentColor else Color.White.copy(alpha = 0.65f)
                        )
                    }

                    IconButton(onClick = onToggleRepeat) {
                        val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Tabler.Outline.RepeatOnce else Tabler.Outline.Repeat
                        val tint = if (repeatMode != Player.REPEAT_MODE_OFF) accentColor else Color.White.copy(alpha = 0.65f)
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(R.string.action_repeat),
                            tint = tint
                        )
                    }

                    IconButton(onClick = onToggleDownload) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                progress = { downloadProgress ?: 0.1f },
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.2.dp,
                                color = accentColor
                            )
                        } else {
                            Icon(
                                imageVector = if (isDownloaded) Tabler.Outline.CircleCheck else Tabler.Outline.Download,
                                contentDescription = if (isDownloaded) stringResource(R.string.download_status_completed) else stringResource(R.string.action_download),
                                tint = if (isDownloaded) accentColor else Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    if (showLyrics) {
                        IconButton(
                            onClick = { showLyricsOverlay = !showLyricsOverlay },
                            enabled = track != null
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.Microphone2,
                                contentDescription = stringResource(R.string.lyrics),
                                tint = if (showLyricsOverlay) accentColor else Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    IconButton(onClick = onOpenSleepTimer) {
                        val isTimerRunning = sleepTimerMode !is SleepTimerMode.Disabled
                        Icon(
                            imageVector = Tabler.Outline.Moon,
                            contentDescription = stringResource(R.string.sleep_timer),
                            tint = if (isTimerRunning) accentColor else Color.White.copy(alpha = 0.65f)
                        )
                    }

                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            imageVector = Tabler.Outline.Playlist,
                            contentDescription = stringResource(R.string.queue_title),
                            tint = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
