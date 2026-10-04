package com.notmugil.uta.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.R
import com.notmugil.uta.data.preferences.StreamingBitrate
import com.notmugil.uta.data.preferences.TranscodingFormat
import com.notmugil.uta.player.SleepTimerMode
import com.notmugil.uta.ui.screens.settings.SettingsViewModel
import com.notmugil.uta.ui.screens.settings.components.SettingsDropdownRow
import com.notmugil.uta.ui.screens.settings.components.SettingsItemRow
import com.notmugil.uta.ui.screens.settings.components.SettingsSectionHeader
import com.notmugil.uta.ui.screens.settings.components.SettingsSliderRow
import com.notmugil.uta.ui.screens.settings.components.UtaSwitch
import com.notmugil.uta.ui.shared.SleepTimerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackAudioSubPage(
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current

    val wifiBitrate by viewModel.wifiStreamingBitrate.collectAsStateWithLifecycle()
    val cellularBitrate by viewModel.cellularStreamingBitrate.collectAsStateWithLifecycle()
    val transcodingFormat by viewModel.transcodingFormat.collectAsStateWithLifecycle()
    val pauseOnDisconnect by viewModel.pauseOnDisconnect.collectAsStateWithLifecycle()
    val autoResume by viewModel.autoResume.collectAsStateWithLifecycle()
    val autoNextEnabled by viewModel.autoNextEnabled.collectAsStateWithLifecycle()
    val isScrobblingEnabled by viewModel.isScrobblingEnabled.collectAsStateWithLifecycle()
    val scrobbleThresholdPercent by viewModel.scrobbleThresholdPercent.collectAsStateWithLifecycle()
    val sleepTimerMode by viewModel.sleepTimerMode.collectAsStateWithLifecycle()
    val sleepTimerSeconds by viewModel.sleepTimerSeconds.collectAsStateWithLifecycle()
    val prefetchUpcoming by viewModel.prefetchUpcomingEnabled.collectAsStateWithLifecycle()
    val prefetchTrackCount by viewModel.prefetchTrackCount.collectAsStateWithLifecycle()
    val prefetchOnlyOnWifi by viewModel.prefetchOnlyOnWifi.collectAsStateWithLifecycle()

    var showSleepTimerSheet by remember { mutableStateOf(false) }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            sleepTimerManager = viewModel.sleepTimerManager,
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(stringResource(R.string.setting_streaming_quality_header))

        val isFlac = transcodingFormat == TranscodingFormat.FLAC

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_wifi_quality),
            subtitle = if (isFlac) {
                "Lossless audio (FLAC ignores bitrate caps)"
            } else if (transcodingFormat != TranscodingFormat.RAW && wifiBitrate == StreamingBitrate.UNLIMITED) {
                "Maximum quality ${transcodingFormat.displayName}"
            } else if (wifiBitrate == StreamingBitrate.UNLIMITED) {
                "Direct stream without transcoding"
            } else if (wifiBitrate == StreamingBitrate.AUTO) {
                "Automatic quality based on format"
            } else {
                "${wifiBitrate.displayName} streaming quality"
            },
            selectedValue = wifiBitrate,
            options = StreamingBitrate.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setWifiStreamingBitrate(it) }
        )

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_cellular_quality),
            subtitle = if (isFlac) {
                "Lossless audio (FLAC ignores bitrate caps)"
            } else if (transcodingFormat != TranscodingFormat.RAW && cellularBitrate == StreamingBitrate.UNLIMITED) {
                "Maximum quality ${transcodingFormat.displayName}"
            } else if (cellularBitrate == StreamingBitrate.UNLIMITED) {
                "Direct stream without transcoding"
            } else if (cellularBitrate == StreamingBitrate.AUTO) {
                "Automatic quality based on format"
            } else {
                "${cellularBitrate.displayName} streaming quality"
            },
            selectedValue = cellularBitrate,
            options = StreamingBitrate.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setCellularStreamingBitrate(it) }
        )

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_transcoding_format),
            subtitle = if (transcodingFormat == TranscodingFormat.RAW) {
                "Stream original server file without transcoding"
            } else {
                "Transcode streamed audio into ${transcodingFormat.displayName}"
            },
            selectedValue = transcodingFormat,
            options = TranscodingFormat.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setTranscodingFormat(it) }
        )

        SettingsSectionHeader(stringResource(R.string.settings_playback))

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_auto_next),
            subtitle = stringResource(R.string.setting_auto_next_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = autoNextEnabled,
                    onCheckedChange = { viewModel.setAutoNextEnabled(it) }
                )
            }
        )

        val timerStatusText = when (sleepTimerMode) {
            SleepTimerMode.Disabled -> stringResource(R.string.sleep_timer_off)
            is SleepTimerMode.Duration -> {
                val s = sleepTimerSeconds ?: 0L
                val mins = s / 60
                val secs = s % 60
                "%02d:%02d".format(mins, secs)
            }
        }

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.sleep_timer),
            subtitle = stringResource(R.string.settings_sleep_timer_subtitle),
            value = timerStatusText,
            verticalPadding = 16.dp,
            onClick = { showSleepTimerSheet = true }
        )

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_pause_on_disconnect),
            subtitle = stringResource(R.string.setting_pause_on_disconnect_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = pauseOnDisconnect,
                    onCheckedChange = { viewModel.setPauseOnDisconnect(it) }
                )
            }
        )

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_auto_resume),
            subtitle = stringResource(R.string.setting_auto_resume_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = autoResume,
                    onCheckedChange = { viewModel.setAutoResume(it) }
                )
            }
        )

        SettingsSectionHeader(stringResource(R.string.setting_prefetch_header))

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_prefetch_upcoming),
            subtitle = stringResource(R.string.setting_prefetch_upcoming_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = prefetchUpcoming,
                    onCheckedChange = { viewModel.setPrefetchUpcomingEnabled(it) }
                )
            },
            onClick = {
                viewModel.setPrefetchUpcomingEnabled(!prefetchUpcoming)
            }
        )

        if (prefetchUpcoming) {
            SettingsDropdownRow(
                icon = null,
                title = stringResource(R.string.setting_prefetch_track_count),
                subtitle = stringResource(R.string.setting_prefetch_track_count_subtitle),
                selectedValue = prefetchTrackCount,
                options = listOf(1, 2, 3, 4, 5, 8, 10),
                getDisplayName = { count ->
                    if (count == 1) {
                        context.getString(R.string.prefetch_count_single)
                    } else {
                        context.getString(R.string.prefetch_count_format, count)
                    }
                },
                onValueChange = { viewModel.setPrefetchTrackCount(it) }
            )

            SettingsItemRow(
                icon = null,
                title = stringResource(R.string.setting_prefetch_only_on_wifi),
                subtitle = stringResource(R.string.setting_prefetch_only_on_wifi_subtitle),
                showChevron = false,
                verticalPadding = 16.dp,
                trailing = {
                    UtaSwitch(
                        checked = prefetchOnlyOnWifi,
                        onCheckedChange = { viewModel.setPrefetchOnlyOnWifi(it) }
                    )
                },
                onClick = {
                    viewModel.setPrefetchOnlyOnWifi(!prefetchOnlyOnWifi)
                }
            )
        }

        SettingsSectionHeader(title = stringResource(R.string.setting_scrobbling_header))

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_subsonic_scrobble),
            subtitle = stringResource(R.string.settings_scrobbling_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = isScrobblingEnabled,
                    onCheckedChange = { viewModel.setScrobblingEnabled(it) }
                )
            },
            onClick = {
                viewModel.setScrobblingEnabled(!isScrobblingEnabled)
            }
        )

        if (isScrobblingEnabled) {
            SettingsSliderRow(
                title = stringResource(R.string.setting_scrobble_threshold),
                subtitle = stringResource(R.string.setting_scrobble_threshold_subtitle),
                value = scrobbleThresholdPercent.toFloat(),
                valueRange = 10f..100f,
                steps = 0,
                valueLabel = "$scrobbleThresholdPercent%",
                onValueChange = { viewModel.setScrobbleThresholdPercent(it.toInt()) }
            )
        }
    }
}
