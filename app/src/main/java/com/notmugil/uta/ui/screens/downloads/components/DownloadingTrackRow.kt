package com.notmugil.uta.ui.screens.downloads.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.data.download.DownloadStatus
import com.notmugil.uta.data.download.DownloadTask
import com.notmugil.uta.ui.shared.CoverArtImage

@Composable
fun DownloadingTrackRow(
    task: DownloadTask,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = task.track

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        CoverArtImage(
            coverArtId = track.coverArtId,
            contentDescription = track.title,
            size = 48.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            val statusText = when (val status = task.status) {
                is DownloadStatus.Downloading -> androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.download_status_downloading, (status.progress * 100).toInt())
                is DownloadStatus.Queued -> androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.download_status_queued)
                is DownloadStatus.Failed -> status.error.ifBlank { androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.download_status_failed) }
                is DownloadStatus.Completed -> androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.download_status_completed)
                is DownloadStatus.Idle -> androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.download_status_waiting)
            }
            val isError = task.status is DownloadStatus.Failed

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        when (val status = task.status) {
            is DownloadStatus.Downloading -> {
                CircularProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            is DownloadStatus.Queued, is DownloadStatus.Idle -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            else -> {}
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Tabler.Outline.X,
                contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
