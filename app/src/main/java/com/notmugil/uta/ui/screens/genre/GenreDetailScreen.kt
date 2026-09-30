package com.notmugil.uta.ui.screens.genre

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.notmugil.uta.ui.shared.AppTopBar
import com.notmugil.uta.ui.shared.FullScreenLoader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.R
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.MediaCard
import com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler
import com.notmugil.uta.ui.shared.actionsheet.MediaActionBottomSheet
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget

@Composable
fun GenreDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GenreDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mediaActionState = LocalMediaActionHandler.current
    val listState = rememberLazyListState()

    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200
        }
    }

    val distinctCovers = remember(state.albums, state.songs) {
        val list = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (album in state.albums) {
            val cid = album.coverArtId
            if (!cid.isNullOrBlank() && seen.add(cid)) {
                list.add(cid)
                if (list.size == 3) break
            }
        }
        if (list.size < 3) {
            for (song in state.songs) {
                val cid = song.coverArtId
                if (!cid.isNullOrBlank() && seen.add(cid)) {
                    list.add(cid)
                    if (list.size == 3) break
                }
            }
        }
        list
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = statusBarTop + 56.dp

    Box(modifier = modifier.fillMaxSize()) {
        val firstCoverArtId = distinctCovers.firstOrNull()
        if (!firstCoverArtId.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize()) {
                CoverArtImage(
                    coverArtId = firstCoverArtId,
                    contentDescription = null,
                    size = 1000.dp,
                    shape = RoundedCornerShape(0.dp),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.35f)
                        .blur(50.dp)
                        .alpha(0.5f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }
        }

        when {
            state.isLoading && state.albums.isEmpty() && state.songs.isEmpty() -> {
                FullScreenLoader(
                    title = state.genreName,
                    onNavigateBack = onNavigateBack
                )
            }
            state.errorMessage != null && state.albums.isEmpty() && state.songs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AppTopBar(
                        title = state.genreName,
                        onNavigateBack = onNavigateBack,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.errorMessage ?: stringResource(R.string.failed_load_genre),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = topBarHeight, bottom = 168.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "genre_header") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            GenreDeckCoverArt(
                                genreName = state.genreName,
                                covers = distinctCovers
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = state.genreName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = stringResource(R.string.genre_albums_songs_format, state.albums.size, state.songs.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { viewModel.playAll() },
                                    enabled = state.songs.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Tabler.Filled.PlayerPlay, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_play))
                                }

                                OutlinedButton(
                                    onClick = { viewModel.shuffleAll() },
                                    enabled = state.songs.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Tabler.Outline.ArrowsShuffle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_shuffle))
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    if (state.albums.isNotEmpty()) {
                        item(key = "genre_albums_header") {
                            Text(
                                text = stringResource(R.string.genre_albums),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        item(key = "genre_albums_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.albums, key = { it.id }) { album ->
                                    MediaCard(
                                        title = album.title,
                                        subtitle = album.artist,
                                        coverArtId = album.coverArtId,
                                        onClick = { onNavigateToAlbum(album.id) },
                                        modifier = Modifier.width(140.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (state.songs.isNotEmpty()) {
                        item(key = "genre_songs_header") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.genre_tracks),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        itemsIndexed(state.songs, key = { index, song -> "${song.id}_$index" }) { index, song ->
                            val isDownloaded = state.downloadedTrackIds.contains(song.id)
                            GenreTrackRow(
                                track = song,
                                index = index + 1,
                                isDownloaded = isDownloaded,
                                isOffline = state.isOfflineModeActive,
                                onClick = { viewModel.playTrack(song) },
                                onOptionsClick = {
                                    mediaActionState.show(MediaTarget.TrackTarget(song))
                                }
                            )
                        }
                    }
                }
            }
        }

        GenreTopBar(
            title = state.genreName,
            showTitle = showTopBarTitle,
            onBack = onNavigateBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        val currentTarget = mediaActionState.currentTarget
        val isTargetDownloaded = when (currentTarget) {
            is MediaTarget.TrackTarget -> state.downloadedTrackIds.contains(currentTarget.track.id)
            else -> false
        }

        MediaActionBottomSheet(
            state = mediaActionState,
            playbackController = viewModel.playbackController,
            libraryRepository = viewModel.libraryRepository,
            offlineDownloadManager = viewModel.offlineDownloadManager,
            isDownloaded = isTargetDownloaded,
            isOffline = state.isOfflineModeActive,
            onNavigateToArtist = onNavigateToArtist
        )
    }
}

@Composable
private fun GenreTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fadeColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = showTitle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                fadeColor,
                                fadeColor.copy(alpha = 0.95f),
                                fadeColor.copy(alpha = 0.80f),
                                fadeColor.copy(alpha = 0.50f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (showTitle) {
                            Color.Transparent
                        } else {
                            Color.Black.copy(alpha = 0.35f)
                        }
                    )
            ) {
                Icon(
                    imageVector = Tabler.Outline.ArrowLeft,
                    contentDescription = stringResource(R.string.content_desc_back),
                    tint = if (showTitle) MaterialTheme.colorScheme.onSurface else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
            }

            if (!showTitle) {
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun GenreDeckCoverArt(
    genreName: String,
    covers: List<String>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            covers.size >= 3 -> {
                CoverArtImage(
                    coverArtId = covers[1],
                    contentDescription = null,
                    size = 145.dp,
                    fallbackIcon = Tabler.Outline.Shape,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .size(145.dp)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            rotationZ = -9f
                            translationX = -65f
                            translationY = 6f
                            alpha = 0.78f
                        }
                )

                CoverArtImage(
                    coverArtId = covers[2],
                    contentDescription = null,
                    size = 145.dp,
                    fallbackIcon = Tabler.Outline.Shape,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .size(145.dp)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            rotationZ = 9f
                            translationX = 65f
                            translationY = 6f
                            alpha = 0.78f
                        }
                )

                CoverArtImage(
                    coverArtId = covers[0],
                    contentDescription = genreName,
                    size = 155.dp,
                    fallbackIcon = Tabler.Outline.Shape,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .size(155.dp)
                        .aspectRatio(1f)
                        .shadow(14.dp, RoundedCornerShape(20.dp))
                )
            }
            covers.size == 2 -> {
                CoverArtImage(
                    coverArtId = covers[1],
                    contentDescription = null,
                    size = 142.dp,
                    fallbackIcon = Tabler.Outline.Shape,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .size(142.dp)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            rotationZ = -8f
                            translationX = -50f
                            scaleX = 0.90f
                            scaleY = 0.90f
                            alpha = 0.78f
                        }
                )

                CoverArtImage(
                    coverArtId = covers[0],
                    contentDescription = genreName,
                    size = 155.dp,
                    fallbackIcon = Tabler.Outline.Shape,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .size(155.dp)
                        .aspectRatio(1f)
                        .shadow(14.dp, RoundedCornerShape(20.dp))
                )
            }
            covers.size == 1 -> {
                CoverArtImage(
                    coverArtId = covers[0],
                    contentDescription = genreName,
                    size = 160.dp,
                    fallbackIcon = Tabler.Outline.Shape,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .size(160.dp)
                        .aspectRatio(1f)
                        .shadow(14.dp, RoundedCornerShape(20.dp))
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Tabler.Outline.Shape,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreTrackRow(
    track: TrackItem,
    index: Int,
    isDownloaded: Boolean,
    isOffline: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlayable = !isOffline || isDownloaded
    val rowAlpha = if (isPlayable) 1f else 0.38f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isPlayable, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha),
            modifier = Modifier.width(28.dp)
        )

        CoverArtImage(
            coverArtId = if (isOffline && !isDownloaded) null else track.coverArtId,
            contentDescription = track.title,
            size = 44.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = rowAlpha),
                modifier = Modifier.basicMarquee()
            )
            val sub = listOfNotNull(track.artist.takeIf { it.isNotBlank() }, track.album.takeIf { !it.isNullOrBlank() })
                .joinToString(" • ")
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha),
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha),
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        IconButton(
            onClick = onOptionsClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Tabler.Outline.DotsVertical,
                contentDescription = stringResource(R.string.more_actions_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
