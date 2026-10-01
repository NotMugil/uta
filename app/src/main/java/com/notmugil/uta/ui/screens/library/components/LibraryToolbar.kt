package com.notmugil.uta.ui.screens.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.ui.screens.library.SortCriteria

@Composable
fun LibraryToolbar(
    selectedCriteria: SortCriteria,
    availableSortOptions: List<SortCriteria>,
    onSortCriteriaSelected: (SortCriteria) -> Unit,
    isAscending: Boolean,
    onToggleAscending: () -> Unit,
    columnCount: Int,
    onColumnCountChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showViewMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box {
                Surface(
                    onClick = { showSortMenu = true },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.ArrowsUpDown,
                            contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.library_sort_by),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(selectedCriteria.labelResId),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Tabler.Outline.ChevronDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .widthIn(min = 160.dp, max = 240.dp)
                        .heightIn(max = 320.dp)
                ) {
                    availableSortOptions.forEachIndexed { index, criteria ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )
                        }
                        val isSelected = selectedCriteria == criteria
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(criteria.labelResId),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            onClick = {
                                onSortCriteriaSelected(criteria)
                                showSortMenu = false
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Tabler.Outline.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
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
                    contentDescription = if (isAscending) androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.sort_ascending) else androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.sort_descending),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box {
            IconButton(
                onClick = { showViewMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (columnCount == 1) Tabler.Outline.List else Tabler.Outline.LayoutGrid,
                    contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.view_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                val isListSelected = columnCount == 1
                DropdownMenuItem(
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Tabler.Outline.List,
                            contentDescription = null,
                            tint = if (isListSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    text = {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.view_list),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = if (isListSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isListSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    trailingIcon = {
                        if (isListSelected) {
                            Icon(
                                imageVector = Tabler.Outline.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    },
                    onClick = {
                        onColumnCountChanged(1)
                        showViewMenu = false
                    }
                )
                for (cols in 2..4) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )
                    val isColSelected = columnCount == cols
                    DropdownMenuItem(
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Tabler.Outline.LayoutGrid,
                                contentDescription = null,
                                tint = if (isColSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.view_columns_format, cols),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = if (isColSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isColSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        trailingIcon = {
                            if (isColSelected) {
                                Icon(
                                    imageVector = Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        },
                        onClick = {
                            onColumnCountChanged(cols)
                            showViewMenu = false
                        }
                    )
                }
            }
        }
    }
}
