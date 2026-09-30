package com.notmugil.uta.ui.screens.history.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySwipeItem(
    onAddToQueue: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onAddToQueue()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                onDelete()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    val isSwiping = dismissState.dismissDirection != SwipeToDismissBoxValue.Settled ||
        dismissState.targetValue != SwipeToDismissBoxValue.Settled

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                label = "swipe_bg_color"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSwiping) color else Color.Transparent)
                    .padding(horizontal = 16.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.Settled -> Alignment.Center
                }
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.Playlist,
                                contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_add_to_queue),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_add_to_queue),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_delete),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Tabler.Outline.Trash,
                                contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_delete),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    SwipeToDismissBoxValue.Settled -> {}
                }
            }
        }
    ) {
        val contentBgColor by animateColorAsState(
            targetValue = if (isSwiping) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f) else Color.Transparent,
            label = "content_swipe_bg"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isSwiping) 8.dp else 0.dp, vertical = if (isSwiping) 2.dp else 0.dp)
                .clip(RoundedCornerShape(if (isSwiping) 12.dp else 0.dp))
                .background(contentBgColor)
        ) {
            content()
        }
    }
}
