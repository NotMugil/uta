package com.notmugil.uta.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.notmugil.uta.data.AuthState
import com.notmugil.uta.data.sync.SyncState
import com.notmugil.uta.ui.screens.settings.SettingsViewModel
import com.notmugil.uta.ui.screens.settings.components.SettingsItemRow
import com.notmugil.uta.ui.screens.settings.components.SettingsSectionHeader
import com.notmugil.uta.ui.shared.ActionConfirmDialog
import com.notmugil.uta.ui.shared.AppToastManager
import java.text.DateFormat
import java.util.Date

@Composable
fun NetworkSubPage(
    viewModel: SettingsViewModel,
    onRequestLogout: () -> Unit
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isOfflineModeActive by viewModel.isOfflineModeActive.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val fallbackServerUrl by viewModel.fallbackServerUrl.collectAsStateWithLifecycle()
    val lastFullSyncTime by viewModel.lastFullSyncTime.collectAsStateWithLifecycle()

    val formattedLastSync = remember(lastFullSyncTime) {
        val stamp = lastFullSyncTime
        if (stamp == null || stamp <= 0L) {
            null
        } else {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(stamp))
        }
    }

    var showFallbackUrlDialog by remember { mutableStateOf(false) }
    var fallbackUrlInput by remember { mutableStateOf(fallbackServerUrl.orEmpty()) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        ActionConfirmDialog(
            title = stringResource(R.string.setting_disconnect_server),
            message = stringResource(R.string.setting_disconnect_server_confirm_msg),
            confirmText = stringResource(R.string.action_logout),
            isDestructive = true,
            icon = Tabler.Outline.Logout,
            onConfirm = {
                showLogoutDialog = false
                onRequestLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    if (showFallbackUrlDialog) {
        ActionConfirmDialog(
            title = stringResource(R.string.settings_fallback_url_title),
            confirmText = stringResource(R.string.playlist_save),
            onConfirm = {
                viewModel.setFallbackServerUrl(fallbackUrlInput.trim().takeIf { it.isNotBlank() })
                showFallbackUrlDialog = false
                AppToastManager.showSuccess(context.getString(R.string.toast_fallback_url_updated))
            },
            onDismiss = { showFallbackUrlDialog = false },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_fallback_url_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = fallbackUrlInput,
                        onValueChange = { fallbackUrlInput = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.settings_fallback_url_hint),
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
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(title = stringResource(R.string.setting_connected_server_header))

        if (authState is AuthState.Authenticated) {
            val creds = (authState as AuthState.Authenticated).credentials

            SettingsItemRow(
                icon = null,
                title = creds.serverUrl.removePrefix("http://").removePrefix("https://"),
                subtitle = stringResource(
                    R.string.settings_server_status_format,
                    creds.username,
                    if (isOfflineModeActive) stringResource(R.string.status_offline) else stringResource(R.string.status_connected)
                ),
                showChevron = false,
                verticalPadding = 16.dp,
                trailing = {
                    IconButton(
                        onClick = { showLogoutDialog = true }
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.Logout,
                            contentDescription = stringResource(R.string.cd_logout),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            val fallback = fallbackServerUrl

            SettingsItemRow(
                icon = null,
                title = stringResource(R.string.setting_fallback_url),
                subtitle = if (fallback.isNullOrBlank()) stringResource(R.string.settings_fallback_url_not_configured) else fallback,
                verticalPadding = 16.dp,
                onClick = {
                    fallbackUrlInput = fallback.orEmpty()
                    showFallbackUrlDialog = true
                }
            )

            SettingsSectionHeader(title = stringResource(R.string.setting_synchronization_header))

            SettingsItemRow(
                icon = null,
                title = stringResource(R.string.setting_last_full_sync),
                subtitle = formattedLastSync ?: stringResource(R.string.setting_sync_never),
                showChevron = false,
                verticalPadding = 16.dp
            )

            val isSyncing = syncState is SyncState.Syncing
            val syncSubtitle = when (val s = syncState) {
                is SyncState.Syncing -> s.stage
                is SyncState.Error -> stringResource(R.string.sync_error_format, s.message)
                is SyncState.Idle -> stringResource(R.string.setting_sync_library_subtitle)
            }

            SettingsItemRow(
                icon = null,
                title = stringResource(R.string.setting_sync_library),
                subtitle = syncSubtitle,
                showChevron = !isSyncing,
                verticalPadding = 16.dp,
                trailing = if (isSyncing) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else null,
                onClick = {
                    if (!isSyncing) {
                        viewModel.triggerSync()
                    }
                }
            )
        } else {
            SettingsItemRow(
                icon = null,
                title = stringResource(R.string.settings_no_server_connected),
                subtitle = stringResource(R.string.settings_no_server_connected_desc),
                showChevron = false,
                verticalPadding = 16.dp
            )
        }
    }
}
