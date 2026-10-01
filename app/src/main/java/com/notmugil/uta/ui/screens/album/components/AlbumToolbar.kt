package com.notmugil.uta.ui.screens.album.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.R
import com.notmugil.uta.domain.model.SongSortOption
import com.notmugil.uta.domain.model.ViewDisplayMode

@Composable
fun AlbumToolbar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    searchFocusRequester: FocusRequester,
    processedSongsCount: Int,
    sortOption: SongSortOption,
    onSortOptionChange: (SongSortOption) -> Unit,
    isAscending: Boolean,
    onToggleAscending: () -> Unit,
    viewOption: ViewDisplayMode,
    onViewOptionChange: (ViewDisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showViewMenu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        if (isSearchActive) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.5.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.album_filter_placeholder),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                        fontSize = 13.5.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    onSearchQueryChange("")
                                } else {
                                    onCloseSearch()
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.X,
                                contentDescription = stringResource(if (searchQuery.isNotEmpty()) R.string.clear_search_cd else R.string.close_search_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .focusRequester(searchFocusRequester)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(20.dp)
                    )
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.album_tracks_count, processedSongsCount),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onOpenSearch,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Tabler.Outline.Search,
                    contentDescription = stringResource(R.string.album_search_tracks_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Box {
            IconButton(
                onClick = { showSortMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Tabler.Outline.ArrowsUpDown,
                    contentDescription = stringResource(R.string.sort_options_cd),
                    tint = if (sortOption != SongSortOption.ID) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                shape = RoundedCornerShape(12.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .widthIn(min = 160.dp, max = 240.dp)
                    .heightIn(max = 380.dp)
            ) {
                SongSortOption.entries.forEachIndexed { index, option ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp
                        )
                    }
                    val isSelected = sortOption == option
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(option.labelResId),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        },
                        onClick = {
                            onSortOptionChange(option)
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        IconButton(
            onClick = onToggleAscending,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isAscending) Tabler.Outline.ArrowUp else Tabler.Outline.ArrowDown,
                contentDescription = stringResource(if (isAscending) R.string.sort_ascending_cd else R.string.sort_descending_cd),
                tint = if (!isAscending || sortOption != SongSortOption.ID) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }

        Box {
            IconButton(
                onClick = { showViewMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = when (viewOption) {
                        ViewDisplayMode.LIST -> Tabler.Outline.List
                        ViewDisplayMode.TEXT_ONLY -> Tabler.Outline.Playlist
                    },
                    contentDescription = stringResource(R.string.view_options_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showViewMenu,
                onDismissRequest = { showViewMenu = false },
                shape = RoundedCornerShape(12.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .widthIn(min = 160.dp, max = 240.dp)
                    .heightIn(max = 320.dp)
            ) {
                ViewDisplayMode.entries.forEachIndexed { index, option ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp
                        )
                    }
                    val isSelected = viewOption == option
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(option.displayNameResId),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        },
                        onClick = {
                            onViewOptionChange(option)
                            showViewMenu = false
                        }
                    )
                }
            }
        }
    }
}
