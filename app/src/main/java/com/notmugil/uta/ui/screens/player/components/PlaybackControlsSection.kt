package com.notmugil.uta.ui.screens.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.notmugil.uta.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Composable
fun PlaybackControlsSection(
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    isBuffering: Boolean = false,
    compact: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playPauseSize = if (compact) 48.dp else 64.dp
    val playPauseIconSize = if (compact) 26.dp else 36.dp
    val skipIconSize = if (compact) 26.dp else 36.dp
    val auxIconSize = if (compact) 20.dp else 24.dp
    val buttonModifier = if (compact) Modifier.size(36.dp) else Modifier

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                com.notmugil.uta.util.HapticFeedbackHelper.perform(context, com.notmugil.uta.util.HapticFeedbackHelper.HapticType.LIGHT)
                onToggleShuffle()
            },
            modifier = buttonModifier
        ) {
            Icon(
                imageVector = Tabler.Outline.ArrowsShuffle,
                contentDescription = stringResource(R.string.action_shuffle),
                modifier = Modifier.size(auxIconSize),
                tint = if (isShuffleEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        IconButton(
            onClick = {
                com.notmugil.uta.util.HapticFeedbackHelper.perform(context, com.notmugil.uta.util.HapticFeedbackHelper.HapticType.CLICK)
                onSkipPrevious()
            },
            modifier = buttonModifier
        ) {
            Icon(
                imageVector = Tabler.Filled.PlayerTrackPrev,
                contentDescription = stringResource(R.string.player_previous_cd),
                modifier = Modifier.size(skipIconSize),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        FilledIconButton(
            onClick = {
                com.notmugil.uta.util.HapticFeedbackHelper.perform(context, com.notmugil.uta.util.HapticFeedbackHelper.HapticType.CLICK)
                onTogglePlayPause()
            },
            modifier = Modifier.size(playPauseSize),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(playPauseIconSize * 0.8f),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Tabler.Filled.PlayerPause else Tabler.Filled.PlayerPlay,
                    contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                    modifier = Modifier.size(playPauseIconSize)
                )
            }
        }

        IconButton(
            onClick = {
                com.notmugil.uta.util.HapticFeedbackHelper.perform(context, com.notmugil.uta.util.HapticFeedbackHelper.HapticType.CLICK)
                onSkipNext()
            },
            modifier = buttonModifier
        ) {
            Icon(
                imageVector = Tabler.Filled.PlayerTrackNext,
                contentDescription = stringResource(R.string.player_next_cd),
                modifier = Modifier.size(skipIconSize),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        IconButton(
            onClick = {
                com.notmugil.uta.util.HapticFeedbackHelper.perform(context, com.notmugil.uta.util.HapticFeedbackHelper.HapticType.LIGHT)
                onToggleRepeat()
            },
            modifier = buttonModifier
        ) {
            val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Tabler.Outline.RepeatOnce else Tabler.Outline.Repeat
            val tint = if (repeatMode != Player.REPEAT_MODE_OFF) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.action_repeat),
                modifier = Modifier.size(auxIconSize),
                tint = tint
            )
        }
    }
}
