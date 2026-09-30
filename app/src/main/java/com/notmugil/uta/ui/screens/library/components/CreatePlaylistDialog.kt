package com.notmugil.uta.ui.screens.library.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.notmugil.uta.ui.shared.ActionConfirmDialog

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onPlaylistCreated: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }

    ActionConfirmDialog(
        title = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.library_new_playlist),
        confirmText = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.library_create_playlist),
        dismissText = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.action_cancel),
        confirmEnabled = playlistName.trim().isNotBlank(),
        onConfirm = {
            if (playlistName.trim().isNotBlank()) {
                onPlaylistCreated(playlistName.trim())
                onDismiss()
            }
        },
        onDismiss = onDismiss,
        content = {
            TextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                placeholder = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.notmugil.uta.R.string.library_playlist_name),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
