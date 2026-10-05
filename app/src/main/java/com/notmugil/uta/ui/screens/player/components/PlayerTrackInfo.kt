package com.notmugil.uta.ui.screens.player.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.*
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.notmugil.uta.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notmugil.uta.domain.model.TrackItem

@Composable
fun LeftAlignedMetadataRow(
    track: TrackItem?,
    isStarred: Boolean,
    onToggleFavorite: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onMoreOptions: (() -> Unit)? = null,
    enableMarquee: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = track?.title ?: stringResource(R.string.album_unknown_album),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = if (enableMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (enableMarquee) Modifier.basicMarquee() else Modifier
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (track != null) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = if (enableMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = (if (enableMarquee) Modifier.basicMarquee() else Modifier)
                        .clickable {
                            track.artistId?.let { onNavigateToArtist(it) }
                        }
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isStarred) Tabler.Filled.Heart else Tabler.Outline.Heart,
                    contentDescription = if (isStarred) stringResource(R.string.action_unstar) else stringResource(R.string.action_star),
                    tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }

            if (onMoreOptions != null) {
                IconButton(
                    onClick = onMoreOptions,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Tabler.Outline.DotsVertical,
                        contentDescription = stringResource(R.string.action_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
