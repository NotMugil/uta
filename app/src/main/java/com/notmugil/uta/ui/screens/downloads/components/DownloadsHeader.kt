package com.notmugil.uta.ui.screens.downloads.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.notmugil.uta.ui.shared.ActionConfirmDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notmugil.uta.data.download.StorageStats
import com.notmugil.uta.util.Formatters

@Composable
fun DownloadsHeader(
    hasDownloadedItems: Boolean,
    storageStats: StorageStats,
    onBack: (() -> Unit)?,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        ActionConfirmDialog(
            title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_clear_confirm_title),
            message = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_clear_confirm_message),
            confirmText = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_clear_all),
            dismissText = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                onClearAll()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Tabler.Outline.ArrowLeft,
                        contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.nav_back)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Column {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (storageStats.downloadedAudioBytes > 0) {
                    Text(
                        text = Formatters.formatBytes(storageStats.downloadedAudioBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (hasDownloadedItems) {
            IconButton(onClick = { showClearDialog = true }) {
                Icon(
                    imageVector = Tabler.Outline.Trash,
                    contentDescription = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.downloads_clear_all),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
