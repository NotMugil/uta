package com.notmugil.uta.ui.screens.artist

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.notmugil.uta.ui.shared.actionsheet.MediaActionBottomSheet
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

    val heroHeight = 340.dp
    val buttonsHeight = 70.dp
    val topBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp
    val maxTravelPx = with(density) { (heroHeight - topBarHeight).toPx() }
    val maxHeroTravelPx = with(density) { (heroHeight - topBarHeight - 60.dp).toPx().coerceAtLeast(0f) }
    val heroHeightPx = with(density) { heroHeight.toPx() }
    val topBarHeightPx = with(density) { topBarHeight.toPx() }

    val heroOffsetPx by remember(maxHeroTravelPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                -maxHeroTravelPx
            } else {
                (-listState.firstVisibleItemScrollOffset.toFloat()).coerceIn(-maxHeroTravelPx, 0f)
            }
        }
    }

    val scrollFraction by remember(maxTravelPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else if (maxTravelPx <= 0f) {
                0f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / maxTravelPx).coerceIn(0f, 1f)
            }
        }
    }

    val buttonsOffsetPx by remember(heroHeightPx, topBarHeightPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                topBarHeightPx
            } else {
                (heroHeightPx - listState.firstVisibleItemScrollOffset.toFloat()).coerceAtLeast(topBarHeightPx)
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
            state.artist?.let { artist ->
                ArtistHeroHeader(
                    artist = artist,
                    height = heroHeight,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            translationY = heroOffsetPx
                        }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarHeight)
                    .clipToBounds(),
                contentPadding = PaddingValues(bottom = 210.dp)
            ) {
                state.artist?.let { artist ->
                    item(key = "hero_spacer") {
                        Spacer(modifier = Modifier.height(heroHeight - topBarHeight + buttonsHeight))
                    }

                    val bio = state.biography?.trim()
                    if (!bio.isNullOrBlank()) {
                        item(key = "artist_about") {
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
                        val albumListState = rememberLazyListState()
                        val albumFlingBehavior = rememberSnapFlingBehavior(lazyListState = albumListState, snapPosition = SnapPosition.Start)
                        LazyRow(
                            state = albumListState,
                            flingBehavior = albumFlingBehavior,
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

            state.artist?.let {
                ArtistPlayShuffleContainer(
                    onPlay = { viewModel.playArtist(shuffle = false) },
                    onShuffle = { viewModel.playArtist(shuffle = true) },
                    hasTracks = state.topTracks.isNotEmpty() || state.albums.isNotEmpty(),
                    gradientHeight = topBarHeight,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            translationY = (buttonsOffsetPx - topBarHeightPx).coerceAtLeast(0f)
                        }
                )
            }

            state.artist?.let { artist ->
                ArtistHeroName(
                    artist = artist,
                    scrollFraction = scrollFraction,
                    height = heroHeight,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            translationY = heroOffsetPx
                        }
                )
            }
        }

        state.artist?.let { artist ->
            ArtistTopBarOverlay(
                artistName = artist.name,
                scrollFraction = scrollFraction,
                onNavigateBack = onNavigateBack,
                onOptionsClick = {
                    mediaActionState.show(MediaTarget.ArtistTarget(artist))
                }
            )
        }

        val currentTarget = mediaActionState.currentTarget
        val isTargetDownloaded = when (currentTarget) {
            is MediaTarget.TrackTarget -> state.downloadedTrackIds.contains(currentTarget.track.id)
            is MediaTarget.AlbumTarget -> state.downloadedAlbumIds.contains(currentTarget.album.id)
            is MediaTarget.ArtistTarget -> state.albums.isNotEmpty() && state.albums.all { state.downloadedAlbumIds.contains(it.id) }
            else -> false
        }

        MediaActionBottomSheet(
            state = mediaActionState,
            playbackController = viewModel.playbackController,
            libraryRepository = viewModel.libraryRepository,
            offlineDownloadManager = viewModel.offlineDownloadManager,
            isDownloaded = isTargetDownloaded,
            isOffline = state.isOfflineModeActive,
            onNavigateToAlbum = onNavigateToAlbum
        )
    }
}

@Composable
private fun ArtistPlayShuffleContainer(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    hasTracks: Boolean,
    gradientHeight: Dp,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.background
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gradientHeight)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.50f to bgColor.copy(alpha = 0.35f),
                            0.80f to bgColor.copy(alpha = 0.75f),
                            1.0f to bgColor
                        )
                    )
                )
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onPlay,
                enabled = hasTracks,
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
                onClick = onShuffle,
                enabled = hasTracks,
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
}

@Composable
private fun ArtistHeroHeader(
    artist: ArtistItem,
    height: Dp = 340.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun ArtistHeroName(
    artist: ArtistItem,
    scrollFraction: Float,
    height: Dp = 340.dp,
    modifier: Modifier = Modifier
) {
    val nameAlpha = (1f - ((scrollFraction - 0.40f) / 0.35f)).coerceIn(0f, 1f)
    if (nameAlpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
        ) {
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
    scrollFraction: Float,
    onNavigateBack: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fadeColor = MaterialTheme.colorScheme.background
    val bgProgress = ((scrollFraction - 0.85f) / 0.15f).coerceIn(0f, 1f)
    val bgAlpha = FastOutSlowInEasing.transform(bgProgress)
    val titleAlpha = ((scrollFraction - 0.40f) / 0.35f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topPadding + 56.dp)
    ) {
        if (bgAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(bgAlpha)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                fadeColor.copy(alpha = 0.70f),
                                fadeColor.copy(alpha = 0.85f),
                                fadeColor.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
        }

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
                        Color.Black.copy(alpha = (0.35f * (1f - bgAlpha)).coerceIn(0f, 0.35f))
                    )
            ) {
                Icon(
                    imageVector = Tabler.Outline.ArrowLeft,
                    contentDescription = stringResource(R.string.nav_back),
                    tint = if (bgAlpha >= 0.85f) MaterialTheme.colorScheme.onSurface else Color.White
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
                onClick = onOptionsClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Color.Black.copy(alpha = (0.35f * (1f - bgAlpha)).coerceIn(0f, 0.35f))
                    )
            ) {
                Icon(
                    imageVector = Tabler.Outline.DotsVertical,
                    contentDescription = stringResource(R.string.artist_options_cd),
                    tint = if (bgAlpha >= 0.85f) MaterialTheme.colorScheme.onSurface else Color.White,
                    modifier = Modifier.size(20.dp)
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
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .basicMarquee()
            )
            track.album?.let { album ->
                Text(
                    text = album,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .basicMarquee()
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
