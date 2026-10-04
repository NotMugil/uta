package com.notmugil.uta.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.R
import com.notmugil.uta.data.preferences.LyricsSourceMode
import com.notmugil.uta.ui.screens.settings.SettingsViewModel
import com.notmugil.uta.ui.screens.settings.components.SettingsItemRow
import com.notmugil.uta.ui.screens.settings.components.SettingsSectionHeader
import com.notmugil.uta.ui.screens.settings.components.UtaSwitch

@Composable
fun LyricsProvidersSubPage(
    viewModel: SettingsViewModel
) {
    val lyricsSourceMode by viewModel.lyricsSourceMode.collectAsStateWithLifecycle()
    val onlineProviders by viewModel.onlineLyricsProviders.collectAsStateWithLifecycle()
    val isOnlineSourceActive = lyricsSourceMode == LyricsSourceMode.BOTH

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SettingsSectionHeader(stringResource(R.string.setting_lyrics_source_header))

        LyricsSourceMode.entries.forEach { mode ->
            val isSelected = lyricsSourceMode == mode
            val titleRes = when (mode) {
                LyricsSourceMode.BOTH -> R.string.setting_lyrics_source_both
                LyricsSourceMode.SERVER_ONLY -> R.string.setting_lyrics_source_server
                LyricsSourceMode.DISABLED -> R.string.setting_lyrics_source_disabled
            }

            SettingsItemRow(
                icon = null,
                title = stringResource(titleRes),
                subtitle = null,
                showChevron = false,
                verticalPadding = 4.dp,
                onClick = { viewModel.setLyricsSourceMode(mode) },
                trailing = {
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.setLyricsSourceMode(mode) }
                    )
                }
            )
        }

        if (onlineProviders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            SettingsSectionHeader(stringResource(R.string.settings_online_lyrics))

            onlineProviders.forEach { config ->
                SettingsItemRow(
                    icon = null,
                    title = config.provider.displayName,
                    subtitle = if (!isOnlineSourceActive) stringResource(R.string.setting_online_lyrics_inactive_notice) else null,
                    showChevron = false,
                    enabled = isOnlineSourceActive,
                    verticalPadding = 16.dp,
                    trailing = {
                        UtaSwitch(
                            checked = config.enabled && isOnlineSourceActive,
                            onCheckedChange = { isChecked ->
                                viewModel.setOnlineLyricsProviderEnabled(config.provider, isChecked)
                            },
                            enabled = isOnlineSourceActive
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun OnlineLyricsProvidersSubPage(
    viewModel: SettingsViewModel
) {
    LyricsProvidersSubPage(viewModel = viewModel)
}
