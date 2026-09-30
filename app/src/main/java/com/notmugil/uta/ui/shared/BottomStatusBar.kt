package com.notmugil.uta.ui.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notmugil.uta.data.OfflineReason

@Composable
fun BottomStatusBar(
    offlineReason: OfflineReason,
    modifier: Modifier = Modifier
) {
    val isVisible = offlineReason != OfflineReason.ONLINE

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        val (message, icon) = when (offlineReason) {
            OfflineReason.MANUAL -> androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.status_offline_mode) to Tabler.Outline.CloudOff
            OfflineReason.NO_NETWORK -> androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.status_no_internet) to Tabler.Outline.WifiOff
            OfflineReason.SERVER_UNREACHABLE -> androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.status_server_unreachable) to Tabler.Outline.CloudOff
            OfflineReason.ONLINE -> return@AnimatedVisibility
        }

        val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
