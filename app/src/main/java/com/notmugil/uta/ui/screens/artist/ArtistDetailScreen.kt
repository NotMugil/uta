package com.notmugil.uta.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.notmugil.uta.ui.shared.AppTopBar
import com.notmugil.uta.ui.shared.FullScreenLoader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.R
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.MediaCard
import com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget

@Composable
fun ArtistDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mediaActionState = LocalMediaActionHandler.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var isTopSongsExpanded by rememberSaveable { mutableStateOf(false) }

    val scrollFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val travelPx = with(density) { 240.dp.toPx() }
                if (travelPx <= 0f) 0f else (listState.firstVisibleItemScrollOffset.toFloat() / travelPx).coerceIn(0f, 1f)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isLoading && state.artist == null) {
            FullScreenLoader(onNavigateBack = onNavigateBack)
        } else if (state.errorMessage != null && state.artist == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                AppTopBar(
                    title = "",
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.errorMessage ?: stringResource(R.string.failed_load_artist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadArtist() }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 168.dp)
            ) {
                state.artist?.let { artist ->
                    item {
                        ArtistHeroHeader(
                            artist = artist,
                            scrollFraction = scrollFraction
                        )
                    }

                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.playTopTracks(shuffle = false) },
                                enabled = state.topTracks.isNotEmpty(),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Tabler.Filled.PlayerPlay,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_play),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            FilledTonalButton(
                                onClick = { viewModel.playTopTracks(shuffle = true) },
                                enabled = state.topTracks.isNotEmpty(),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.ArrowsShuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_shuffle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    val bio = state.biography?.trim()
                    if (!bio.isNullOrBlank()) {
                        item {
                            var isBioExpanded by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { isBioExpanded = !isBioExpanded }
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = stringResource(R.string.artist_about),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = stringResource(if (isBioExpanded) R.string.action_show_less else R.string.action_read_more),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = bio,
                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (isBioExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.topTracks.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.artist_top_songs),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (state.topTracks.size > 5) {
                                Text(
                                    text = stringResource(if (isTopSongsExpanded) R.string.action_show_less else R.string.action_see_more),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { isTopSongsExpanded = !isTopSongsExpanded }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    val displayedTopTracks = if (isTopSongsExpanded) state.topTracks.take(20) else state.topTracks.take(5)
                    itemsIndexed(displayedTopTracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                        val isDownloaded = state.downloadedTrackIds.contains(track.id)
                        ArtistTrackRow(
                            index = index + 1,
                            track = track,
                            isDownloaded = isDownloaded,
                            isOffline = state.isOfflineModeActive,
                            onClick = { viewModel.playTrack(track, state.topTracks) },
                            onMoreClick = { mediaActionState.show(MediaTarget.TrackTarget(track)) }
                        )
                    }
                }

                if (state.albums.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.artist_albums),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }

                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(state.albums, key = { it.id }) { album ->
                                val isDownloaded = state.downloadedAlbumIds.contains(album.id)
                                val alpha = if (!state.isOfflineModeActive || isDownloaded) 1f else 0.38f
                                MediaCard(
                                    title = album.title,
                                    subtitle = album.year?.toString() ?: album.artist,
                                    coverArtId = album.coverArtId,
                                    alpha = alpha,
                                    onClick = { onNavigateToAlbum(album.id) },
                                    onLongClick = { mediaActionState.show(MediaTarget.AlbumTarget(album)) }
                                )
                            }
                        }
                    }
                }
            }
        }

        val fallbackArtistName = stringResource(R.string.artist_fallback)
        ArtistTopBarOverlay(
            artistName = state.artist?.name ?: fallbackArtistName,
            isStarred = state.artist?.isStarred == true,
            scrollFraction = scrollFraction,
            onNavigateBack = onNavigateBack,
            onToggleFavorite = { viewModel.toggleArtistFavorite() }
        )
    }
}

@Composable
private fun ArtistHeroHeader(
    artist: ArtistItem,
    scrollFraction: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
    ) {
        CoverArtImage(
            coverArtId = artist.coverArtId,
            contentDescription = artist.name,
            size = 500.dp,
            shape = RoundedCornerShape(0.dp),
            fallbackIcon = Tabler.Outline.User,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        val bgColor = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.45f),
                            0.3f to Color.Transparent,
                            0.65f to bgColor.copy(alpha = 0.35f),
                            0.85f to bgColor.copy(alpha = 0.80f),
                            1.0f to bgColor
                        )
                    )
                )
        )

        val nameAlpha = (1f - (scrollFraction * 2.0f)).coerceIn(0f, 1f)
        if (nameAlpha > 0f) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .alpha(nameAlpha)
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistTopBarOverlay(
    artistName: String,
    isStarred: Boolean,
    scrollFraction: Float,
    onNavigateBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fadeColor = MaterialTheme.colorScheme.background
    val titleAlpha = ((scrollFraction - 0.25f) / 0.5f).coerceIn(0f, 1f)
    val bgAlpha = scrollFraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topPadding + 56.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(bgAlpha)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            fadeColor.copy(alpha = 0.9f),
                            fadeColor.copy(alpha = 0.7f),
                            fadeColor.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Color.Black.copy(alpha = (0.35f * (1f - scrollFraction)).coerceIn(0f, 0.35f))
                    )
            ) {
                Icon(
                    imageVector = Tabler.Outline.ArrowLeft,
                    contentDescription = stringResource(R.string.nav_back),
                    tint = if (scrollFraction >= 0.85f) MaterialTheme.colorScheme.onSurface else Color.White
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (titleAlpha > 0f) {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .alpha(titleAlpha)
                            .basicMarquee()
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Color.Black.copy(alpha = (0.35f * (1f - scrollFraction)).coerceIn(0f, 0.35f))
                    )
            ) {
                Icon(
                    imageVector = Tabler.Outline.Heart,
                    contentDescription = stringResource(if (isStarred) R.string.action_unstar_artist else R.string.action_star_artist),
                    tint = if (isStarred) {
                        MaterialTheme.colorScheme.primary
                    } else if (scrollFraction >= 0.85f) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color.White
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistTrackRow(
    index: Int,
    track: TrackItem,
    isDownloaded: Boolean,
    isOffline: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreClick
            )
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(24.dp)
        )

        CoverArtImage(
            coverArtId = if (isOffline && !isDownloaded) null else track.coverArtId,
            contentDescription = track.title,
            size = 42.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            track.album?.let { album ->
                Text(
                    text = album,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
        }

        if (track.durationSeconds > 0) {
            val min = track.durationSeconds / 60
            val sec = track.durationSeconds % 60
            Text(
                text = "%d:%02d".format(min, sec),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Tabler.Outline.DotsVertical,
                contentDescription = stringResource(R.string.more_actions_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
