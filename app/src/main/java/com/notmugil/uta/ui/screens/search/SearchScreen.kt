package com.notmugil.uta.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.ArtistItem
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.shared.ArtistCard
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.GenreCard
import com.notmugil.uta.ui.shared.MediaCard
import com.notmugil.uta.ui.shared.actionsheet.LocalMediaActionHandler
import com.notmugil.uta.ui.shared.actionsheet.MediaTarget

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToGenre: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mediaActionHandler = LocalMediaActionHandler.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TextField(
            value = state.query,
            onValueChange = { viewModel.onQueryChange(it) },
            placeholder = { Text(androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.search_placeholder)) },
            leadingIcon = {
                if (state.isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Tabler.Outline.Search,
                        contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_search)
                    )
                }
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(
                            imageVector = Tabler.Outline.X,
                            contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_clear)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { focusManager.clearFocus() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (state.query.isBlank()) {
            if (state.genres.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.search_browse_genres),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 168.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.genres, key = { it.name }) { genre ->
                            val genreCovers = getCoversForGenre(genre.name, state.genreCovers)
                            GenreCard(
                                genre = genre,
                                covers = genreCovers,
                                isOffline = state.isOfflineModeActive,
                                onClick = { onNavigateToGenre(genre.name) }
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.search_empty_prompt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (!state.isSearching &&
            state.results.tracks.isEmpty() &&
            state.results.albums.isEmpty() &&
            state.results.artists.isEmpty()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.search_no_results_for, state.query),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SearchResultsList(
                state = state,
                onPlayTrack = { viewModel.playTrack(it) },
                onTrackMoreClick = { mediaActionHandler.show(MediaTarget.TrackTarget(it)) },
                onAlbumClick = onNavigateToAlbum,
                onAlbumLongClick = { mediaActionHandler.show(MediaTarget.AlbumTarget(it)) },
                onArtistClick = onNavigateToArtist,
                onArtistLongClick = { mediaActionHandler.show(MediaTarget.ArtistTarget(it)) }
            )
        }
    }
}

private fun getCoversForGenre(genreName: String, coversMap: Map<String, List<String>>): List<String> {
    val clean = genreName.trim().lowercase()
    val direct = coversMap[clean]
    if (!direct.isNullOrEmpty()) return direct

    val alphaNumeric = clean.replace(Regex("[^a-z0-9]"), "")
    if (alphaNumeric.isNotBlank()) {
        val alphaMatch = coversMap[alphaNumeric]
        if (!alphaMatch.isNullOrEmpty()) return alphaMatch
    }

    val tokens = clean.split(Regex("[,/;|&+•\\\\]")).map { it.trim() }.filter { it.isNotBlank() }
    for (token in tokens) {
        val match = coversMap[token]
        if (!match.isNullOrEmpty()) return match
        val tokenAlpha = token.replace(Regex("[^a-z0-9]"), "")
        if (tokenAlpha.isNotBlank()) {
            val tokenAlphaMatch = coversMap[tokenAlpha]
            if (!tokenAlphaMatch.isNullOrEmpty()) return tokenAlphaMatch
        }
    }

    for ((key, list) in coversMap) {
        if (list.isNotEmpty() && (key.contains(clean) || clean.contains(key))) {
            return list
        }
    }
    return emptyList()
}

@Composable
private fun SearchResultsList(
    state: SearchState,
    onPlayTrack: (TrackItem) -> Unit,
    onTrackMoreClick: (TrackItem) -> Unit,
    onAlbumClick: (String) -> Unit,
    onAlbumLongClick: (AlbumItem) -> Unit,
    onArtistClick: (String) -> Unit,
    onArtistLongClick: (ArtistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 168.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.results.artists.isNotEmpty()) {
            item {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.search_artists),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.results.artists, key = { it.id }) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = { onArtistClick(artist.id) },
                            onLongClick = { onArtistLongClick(artist) }
                        )
                    }
                }
            }
        }

        if (state.results.albums.isNotEmpty()) {
            item {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.search_albums),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.results.albums, key = { it.id }) { album ->
                        val isDownloaded = state.downloadedAlbumIds.contains(album.id)
                        val alpha = if (!state.isOfflineModeActive || isDownloaded) 1f else 0.38f
                        MediaCard(
                            title = album.title,
                            subtitle = album.artist,
                            coverArtId = album.coverArtId,
                            onClick = { onAlbumClick(album.id) },
                            onLongClick = { onAlbumLongClick(album) },
                            alpha = alpha
                        )
                    }
                }
            }
        }

        if (state.results.tracks.isNotEmpty()) {
            item {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.search_tracks),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(state.results.tracks, key = { it.id }) { track ->
                val isDownloaded = state.downloadedTrackIds.contains(track.id)
                SearchTrackRow(
                    track = track,
                    isDownloaded = isDownloaded,
                    isOffline = state.isOfflineModeActive,
                    onMoreClick = { onTrackMoreClick(track) },
                    onClick = { onPlayTrack(track) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchTrackRow(
    track: TrackItem,
    isDownloaded: Boolean,
    isOffline: Boolean,
    onMoreClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlayable = !isOffline || isDownloaded
    val alpha = if (isPlayable) 1f else 0.38f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                enabled = isPlayable,
                onClick = onClick,
                onLongClick = onMoreClick
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArtImage(
            coverArtId = if (isOffline && !isDownloaded) null else track.coverArtId,
            contentDescription = track.title,
            size = 48.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = listOfNotNull(track.artist.takeIf { it.isNotBlank() }, track.album.takeIf { !it.isNullOrBlank() })
                .joinToString(" • ")
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (track.durationSeconds > 0) {
            val min = track.durationSeconds / 60
            val sec = track.durationSeconds % 60
            Text(
                text = "%d:%02d".format(min, sec),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Tabler.Outline.DotsVertical,
                contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
