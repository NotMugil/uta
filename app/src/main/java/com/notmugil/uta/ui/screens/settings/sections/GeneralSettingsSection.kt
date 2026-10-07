package com.notmugil.uta.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.R
import com.notmugil.uta.data.preferences.AppFont
import com.notmugil.uta.data.preferences.AppLanguage
import com.notmugil.uta.data.preferences.AppThemeMode
import com.notmugil.uta.data.preferences.CoverArtQuality
import com.notmugil.uta.data.preferences.DynamicColorSource
import com.notmugil.uta.data.preferences.LyricsSourceMode
import com.notmugil.uta.data.preferences.LyricsStyle
import com.notmugil.uta.data.preferences.MiniPlayerPlacement
import com.notmugil.uta.data.preferences.MiniPlayerStyle
import com.notmugil.uta.data.preferences.PlayerStyle
import com.notmugil.uta.data.preferences.SeekBarStyle
import com.notmugil.uta.ui.screens.settings.SettingsSubPage
import com.notmugil.uta.ui.screens.settings.SettingsViewModel
import com.notmugil.uta.ui.screens.settings.components.SettingsDropdownRow
import com.notmugil.uta.ui.screens.settings.components.SettingsItemRow
import com.notmugil.uta.ui.screens.settings.components.SettingsSectionHeader
import com.notmugil.uta.ui.screens.settings.components.UtaSwitch

@Composable
fun GeneralSubPage(
    viewModel: SettingsViewModel,
    onNavigate: (SettingsSubPage) -> Unit
) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isDynamicThemeEnabled by viewModel.isDynamicThemeEnabled.collectAsStateWithLifecycle()
    val dynamicColorSource by viewModel.dynamicColorSource.collectAsStateWithLifecycle()
    val enableAppAmbientGradient by viewModel.enableAppAmbientGradient.collectAsStateWithLifecycle()
    val fontPreference by viewModel.fontPreference.collectAsStateWithLifecycle()
    val hapticFeedback by viewModel.hapticFeedback.collectAsStateWithLifecycle()

    val playerStyle by viewModel.playerStyle.collectAsStateWithLifecycle()
    val seekBarStyle by viewModel.seekBarStyle.collectAsStateWithLifecycle()
    val miniPlayerStyle by viewModel.miniPlayerStyle.collectAsStateWithLifecycle()
    val miniPlayerPlacement by viewModel.miniPlayerPlacement.collectAsStateWithLifecycle()
    val tintMiniPlayerAccent by viewModel.tintMiniPlayerAccent.collectAsStateWithLifecycle()
    val animatedArtworkEnabled by viewModel.animatedArtworkEnabled.collectAsStateWithLifecycle()
    val coverArtQuality by viewModel.coverArtQuality.collectAsStateWithLifecycle()
    val lyricsSourceMode by viewModel.lyricsSourceMode.collectAsStateWithLifecycle()
    val onlineLyricsProviders by viewModel.onlineLyricsProviders.collectAsStateWithLifecycle()
    val keepScreenOnLyrics by viewModel.keepScreenOnLyrics.collectAsStateWithLifecycle()
    val blurInactiveLyrics by viewModel.blurInactiveLyrics.collectAsStateWithLifecycle()
    val lyricsStyle by viewModel.lyricsStyle.collectAsStateWithLifecycle()

    val showExternalLinks by viewModel.showExternalLinks.collectAsStateWithLifecycle()
    val showLastFmLinks by viewModel.showLastFmLinks.collectAsStateWithLifecycle()
    val showMusicBrainzLinks by viewModel.showMusicBrainzLinks.collectAsStateWithLifecycle()

    val isLyricsCompletelyDisabled = lyricsSourceMode == LyricsSourceMode.DISABLED

    val availablePlayerStyles = if (isLyricsCompletelyDisabled) {
        PlayerStyle.entries.filter { it != PlayerStyle.LYRICS }
    } else {
        PlayerStyle.entries
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsSectionHeader(stringResource(R.string.setting_display_header))

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_language),
            subtitle = stringResource(R.string.setting_language_subtitle),
            selectedValue = appLanguage,
            options = AppLanguage.entries,
            getDisplayName = { it.label },
            onValueChange = { viewModel.setAppLanguage(it) }
        )

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_font),
            subtitle = fontPreference.displayName,
            selectedValue = fontPreference,
            options = AppFont.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setFontPreference(it) }
        )

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_theme),
            subtitle = stringResource(R.string.setting_theme_subtitle),
            selectedValue = themeMode,
            options = AppThemeMode.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setThemeMode(it) }
        )

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_dynamic_theme),
            subtitle = stringResource(R.string.setting_dynamic_theme_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = isDynamicThemeEnabled,
                    onCheckedChange = { viewModel.setDynamicThemeEnabled(it) }
                )
            }
        )

        if (isDynamicThemeEnabled) {
            SettingsDropdownRow(
                icon = null,
                title = stringResource(R.string.setting_dynamic_color_source),
                subtitle = stringResource(R.string.setting_dynamic_color_source_subtitle),
                selectedValue = dynamicColorSource,
                options = DynamicColorSource.entries,
                getDisplayName = { it.displayName },
                onValueChange = { viewModel.setDynamicColorSource(it) }
            )
        }

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_app_ambient_gradient),
            subtitle = stringResource(R.string.setting_app_ambient_gradient_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = enableAppAmbientGradient,
                    onCheckedChange = { viewModel.setEnableAppAmbientGradient(it) }
                )
            }
        )

        SettingsSectionHeader(stringResource(R.string.setting_layout_style_header))

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_home_sections_title),
            subtitle = stringResource(R.string.setting_home_sections_subtitle),
            verticalPadding = 14.dp,
            onClick = { onNavigate(SettingsSubPage.HOME_SECTIONS) }
        )

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_navbar_sections_title),
            subtitle = stringResource(R.string.setting_navbar_sections_subtitle),
            verticalPadding = 14.dp,
            onClick = { onNavigate(SettingsSubPage.NAVBAR_SECTIONS) }
        )

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_player_style),
            subtitle = if (isLyricsCompletelyDisabled && playerStyle == PlayerStyle.LYRICS) {
                stringResource(R.string.setting_player_style_lyrics_disabled)
            } else {
                stringResource(R.string.setting_player_style_subtitle)
            },
            selectedValue = if (isLyricsCompletelyDisabled && playerStyle == PlayerStyle.LYRICS) PlayerStyle.DEFAULT else playerStyle,
            options = availablePlayerStyles,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setPlayerStyle(it) }
        )

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_seekbar_style),
            subtitle = stringResource(R.string.setting_seekbar_style_subtitle),
            selectedValue = seekBarStyle,
            options = SeekBarStyle.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setSeekBarStyle(it) }
        )

        SettingsSectionHeader(stringResource(R.string.setting_miniplayer_header))

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_miniplayer_buttons_title),
            subtitle = stringResource(R.string.setting_miniplayer_buttons_subtitle),
            verticalPadding = 14.dp,
            onClick = { onNavigate(SettingsSubPage.MINIPLAYER_BUTTONS) }
        )

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_miniplayer_style),
            subtitle = stringResource(R.string.setting_miniplayer_style_subtitle),
            selectedValue = miniPlayerStyle,
            options = MiniPlayerStyle.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setMiniPlayerStyle(it) }
        )

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_miniplayer_placement),
            subtitle = stringResource(R.string.setting_miniplayer_placement_subtitle),
            selectedValue = miniPlayerPlacement,
            options = MiniPlayerPlacement.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setMiniPlayerPlacement(it) }
        )

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_tint_miniplayer),
            subtitle = stringResource(R.string.setting_tint_miniplayer_enabled_desc),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = tintMiniPlayerAccent,
                    onCheckedChange = { viewModel.setTintMiniPlayerAccent(it) }
                )
            }
        )

        SettingsSectionHeader(stringResource(R.string.setting_cover_art_header))

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_animated_covers),
            subtitle = stringResource(R.string.setting_animated_covers_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = animatedArtworkEnabled,
                    onCheckedChange = { viewModel.setAnimatedArtworkEnabled(it) }
                )
            }
        )

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_cover_art_resolution),
            subtitle = stringResource(R.string.setting_cover_art_resolution_subtitle),
            selectedValue = coverArtQuality,
            options = CoverArtQuality.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setCoverArtQuality(it) }
        )

        SettingsSectionHeader(stringResource(R.string.setting_lyrics_header))

        SettingsDropdownRow(
            icon = null,
            title = stringResource(R.string.setting_lyrics_style),
            subtitle = stringResource(R.string.setting_lyrics_style_subtitle),
            selectedValue = lyricsStyle,
            options = LyricsStyle.entries,
            getDisplayName = { it.displayName },
            onValueChange = { viewModel.setLyricsStyle(it) }
        )

        val lyricsSourceSummary = when (lyricsSourceMode) {
            LyricsSourceMode.BOTH -> {
                val enabledList = onlineLyricsProviders.filter { it.enabled }.map { it.provider.displayName }
                if (enabledList.isNotEmpty()) {
                    "${stringResource(R.string.setting_lyrics_source_both)} (${enabledList.joinToString()})"
                } else {
                    stringResource(R.string.setting_lyrics_source_both)
                }
            }
            LyricsSourceMode.SERVER_ONLY -> stringResource(R.string.setting_lyrics_source_server)
            LyricsSourceMode.DISABLED -> stringResource(R.string.setting_lyrics_source_disabled)
        }

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_lyrics_provider),
            subtitle = lyricsSourceSummary,
            showChevron = true,
            verticalPadding = 16.dp,
            onClick = {
                onNavigate(SettingsSubPage.LYRICS_PROVIDERS)
            }
        )

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_blur_inactive_lyrics),
            subtitle = stringResource(R.string.setting_blur_inactive_lyrics_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = blurInactiveLyrics,
                    onCheckedChange = { viewModel.setBlurInactiveLyrics(it) }
                )
            }
        )

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_keep_screen_on_lyrics),
            subtitle = stringResource(R.string.setting_keep_screen_on_lyrics_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = keepScreenOnLyrics,
                    onCheckedChange = { viewModel.setKeepScreenOnLyrics(it) }
                )
            }
        )

        SettingsSectionHeader(stringResource(R.string.setting_external_links_header))

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_show_external_links),
            subtitle = stringResource(R.string.setting_show_external_links_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = showExternalLinks,
                    onCheckedChange = { viewModel.setShowExternalLinks(it) }
                )
            }
        )

        if (showExternalLinks) {
            SettingsItemRow(
                icon = null,
                title = stringResource(R.string.setting_show_lastfm_links),
                subtitle = stringResource(R.string.setting_show_lastfm_links_subtitle),
                showChevron = false,
                verticalPadding = 16.dp,
                trailing = {
                    UtaSwitch(
                        checked = showLastFmLinks,
                        onCheckedChange = { viewModel.setShowLastFmLinks(it) }
                    )
                }
            )

            SettingsItemRow(
                icon = null,
                title = stringResource(R.string.setting_show_musicbrainz_links),
                subtitle = stringResource(R.string.setting_show_musicbrainz_links_subtitle),
                showChevron = false,
                verticalPadding = 16.dp,
                trailing = {
                    UtaSwitch(
                        checked = showMusicBrainzLinks,
                        onCheckedChange = { viewModel.setShowMusicBrainzLinks(it) }
                    )
                }
            )
        }

        SettingsSectionHeader(stringResource(R.string.setting_feedback_header))

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_haptic_feedback),
            subtitle = stringResource(R.string.setting_haptic_feedback_subtitle),
            showChevron = false,
            verticalPadding = 16.dp,
            trailing = {
                UtaSwitch(
                    checked = hapticFeedback,
                    onCheckedChange = { viewModel.setHapticFeedback(it) }
                )
            }
        )
    }
}
