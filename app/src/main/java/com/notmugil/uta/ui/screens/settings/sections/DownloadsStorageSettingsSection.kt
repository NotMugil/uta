package com.notmugil.uta.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.notmugil.uta.R
import com.notmugil.uta.data.preferences.DownloadFormatPreference
import com.notmugil.uta.data.preferences.DownloadQualityPreference
import com.notmugil.uta.ui.screens.settings.SettingsViewModel
import com.notmugil.uta.ui.screens.settings.components.SettingsDropdownRow
import com.notmugil.uta.ui.screens.settings.components.SettingsItemRow
import com.notmugil.uta.ui.screens.settings.components.SettingsSectionHeader
import com.notmugil.uta.ui.screens.settings.components.StorageLineItem
import com.notmugil.uta.ui.screens.settings.components.UtaSwitch
import com.notmugil.uta.ui.shared.ActionConfirmDialog
import com.notmugil.uta.util.Formatters

@Composable
fun DownloadsStorageSubPage(
    viewModel: SettingsViewModel
) {
    val isOfflineModeManual by viewModel.isOfflineModeManual.collectAsStateWithLifecycle()
    val isOfflineModeActive by viewModel.isOfflineModeActive.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val downloadFormat by viewModel.downloadFormat.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()

    var showClearDownloadsDialog by remember { mutableStateOf(false) }

    if (showClearDownloadsDialog) {
        ActionConfirmDialog(
            title = stringResource(R.string.setting_clear_downloads_dialog_title),
            message = stringResource(R.string.setting_clear_downloads_dialog_msg),
            confirmText = stringResource(R.string.setting_clear_downloads_confirm),
            isDestructive = true,
            icon = Tabler.Outline.Trash,
            onConfirm = {
                showClearDownloadsDialog = false
                viewModel.clearDownloads()
            },
            onDismiss = { showClearDownloadsDialog = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(stringResource(R.string.setting_downloads_prefs_header))

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_offline_mode),
            subtitle = if (isOfflineModeActive) {
                stringResource(R.string.setting_offline_mode_active_desc)
            } else {
                stringResource(R.string.setting_offline_mode_inactive_desc)
            },
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = isOfflineModeManual,
                    onCheckedChange = { viewModel.setOfflineModeManual(it) }
                )
            }
        )

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_download_quality),
            subtitle = stringResource(R.string.setting_download_quality_subtitle),
            selectedValue = downloadQuality,
            options = DownloadQualityPreference.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setDownloadQuality(it) }
        )

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_download_codec),
            subtitle = stringResource(R.string.setting_download_codec_subtitle),
            selectedValue = downloadFormat,
            options = DownloadFormatPreference.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setDownloadFormat(it) }
        )

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_wifi_only_downloads),
            subtitle = stringResource(R.string.setting_wifi_only_downloads_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = wifiOnly,
                    onCheckedChange = { viewModel.setWifiOnlyDownloads(it) }
                )
            }
        )

        SettingsSectionHeader(stringResource(R.string.setting_storage_maintenance_header))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.storage_used_by_uta),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatters.formatBytes(storageStats.downloadedAudioBytes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val totalDisk = storageStats.totalDiskSpaceBytes.toFloat().coerceAtLeast(1f)
            val dlBytes = storageStats.downloadedAudioBytes
            val freeBytes = storageStats.availableDiskSpaceBytes
            val otherBytes = (storageStats.totalDiskSpaceBytes - freeBytes - dlBytes).coerceAtLeast(0L)

            val dlRatio = (dlBytes.toFloat() / totalDisk).coerceIn(0f, 1f)
            val otherRatio = (otherBytes.toFloat() / totalDisk).coerceIn(0f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                if (dlRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(dlRatio.coerceAtLeast(0.005f))
                            .background(Color(0xFFFF85A1))
                    )
                }
                if (otherRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(otherRatio.coerceAtLeast(0.005f))
                            .background(Color(0xFF64748B))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StorageLineItem(
                    color = Color(0xFFFF85A1),
                    label = stringResource(R.string.storage_downloaded_music),
                    hint = stringResource(R.string.storage_tracks_offline_format, storageStats.downloadedTrackCount),
                    size = Formatters.formatBytes(dlBytes)
                )
                StorageLineItem(
                    color = Color(0xFF64748B),
                    label = stringResource(R.string.storage_other),
                    hint = stringResource(R.string.storage_other_hint),
                    size = Formatters.formatBytes(otherBytes)
                )
                StorageLineItem(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    label = stringResource(R.string.storage_free_space),
                    hint = stringResource(R.string.storage_free_space_hint),
                    size = Formatters.formatBytes(freeBytes)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItemRow(
            icon = Tabler.Outline.Trash,
            iconTint = MaterialTheme.colorScheme.error,
            title = stringResource(R.string.setting_clear_downloads),
            subtitle = stringResource(R.string.setting_clear_downloads_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            onClick = { showClearDownloadsDialog = true }
        )
    }
}
