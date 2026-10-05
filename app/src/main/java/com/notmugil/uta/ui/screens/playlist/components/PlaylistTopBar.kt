package com.notmugil.uta.ui.screens.playlist.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.R

@Composable
fun PlaylistTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fadeColor = MaterialTheme.colorScheme.background
    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        label = "titleAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
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
                    contentDescription = stringResource(R.string.nav_back),
                    tint = if (showTitle) MaterialTheme.colorScheme.onSurface else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (titleAlpha > 0f) {
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
                        if (showTitle) {
                            Color.Transparent
                        } else {
                            Color.Black.copy(alpha = 0.35f)
                        }
                    )
            ) {
                Icon(
                    imageVector = Tabler.Outline.DotsVertical,
                    contentDescription = stringResource(R.string.playlist_options_cd),
                    tint = if (showTitle) MaterialTheme.colorScheme.onSurface else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
