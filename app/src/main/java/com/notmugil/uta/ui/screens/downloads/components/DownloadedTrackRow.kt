package com.notmugil.uta.ui.screens.downloads.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.ui.screens.downloads.DownloadedTrackInfo
import com.notmugil.uta.ui.shared.CoverArtImage
import com.notmugil.uta.ui.shared.PlayingAnimatedEqualizer
import com.notmugil.uta.util.Formatters

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadedTrackRow(
    info: DownloadedTrackInfo,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = info.track

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            CoverArtImage(
                coverArtId = track.coverArtId,
                contentDescription = track.title,
                size = 48.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrentTrack) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayingAnimatedEqualizer(
                        color = Color.White,
                        isPlaying = isPlaying,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isCurrentTrack) Modifier.basicMarquee() else Modifier
            )
            val sizeText = if (info.fileSizeBytes > 0) Formatters.formatBytes(info.fileSizeBytes) else null
            val subtitleText = listOfNotNull(
                track.artist.takeIf { it.isNotBlank() },
                sizeText
            ).joinToString(" • ")

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Tabler.Outline.Trash,
                contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_remove_download),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Tabler.Outline.DotsVertical,
                contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
