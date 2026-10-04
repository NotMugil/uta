package com.notmugil.uta.ui.screens.settings.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.notmugil.uta.R
import com.notmugil.uta.data.preferences.NavBarItem
import com.notmugil.uta.ui.screens.settings.SettingsViewModel
import com.notmugil.uta.ui.screens.settings.components.UtaSwitch
import com.notmugil.uta.util.HapticFeedbackHelper
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun NavBarSectionsSubPage(
    viewModel: SettingsViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
    val navBarConfigs by viewModel.navBarItemConfigs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var currentConfigs by remember(navBarConfigs) { mutableStateOf(navBarConfigs) }
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        scrollThreshold = 48.dp
    ) { from, to ->
        val fromIdx = from.index
        val toIdx = to.index
        if (fromIdx in currentConfigs.indices && toIdx in currentConfigs.indices && fromIdx != toIdx) {
            val updated = currentConfigs.toMutableList().apply {
                add(toIdx, removeAt(fromIdx))
            }
            currentConfigs = updated
            viewModel.setNavBarItemConfigs(updated)
            HapticFeedbackHelper.perform(context, HapticFeedbackHelper.HapticType.LIGHT)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = currentConfigs,
                key = { it.item.name }
            ) { config ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = config.item.name
                ) { isDragging ->
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 0.dp,
                        label = "navbar_section_drag_elevation"
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDragging) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            Color.Transparent
                        },
                        shadowElevation = elevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier
                                        .draggableHandle(
                                            onDragStarted = {
                                                HapticFeedbackHelper.perform(context, HapticFeedbackHelper.HapticType.HEAVY)
                                            }
                                        )
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Tabler.Outline.GripVertical,
                                        contentDescription = stringResource(R.string.edit_playlist_swipe_drag_reorder),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Icon(
                                    imageVector = config.item.icon,
                                    contentDescription = null,
                                    tint = if (config.enabled) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = stringResource(config.item.titleRes),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.Medium,
                                    color = if (config.enabled) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    }
                                )
                            }

                            val isHomeItem = config.item == NavBarItem.HOME
                            UtaSwitch(
                                checked = if (isHomeItem) true else config.enabled,
                                enabled = !isHomeItem,
                                onCheckedChange = { isChecked ->
                                    if (isHomeItem) return@UtaSwitch
                                    val updated = currentConfigs.map {
                                        if (it.item == config.item) it.copy(enabled = isChecked) else it
                                    }
                                    currentConfigs = updated
                                    viewModel.setNavBarItemConfigs(updated)
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = {
                            viewModel.resetNavBarItems()
                            HapticFeedbackHelper.perform(context, HapticFeedbackHelper.HapticType.CLICK)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.RotateClockwise2,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.setting_navbar_sections_reset),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
