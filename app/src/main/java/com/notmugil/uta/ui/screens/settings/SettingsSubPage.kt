package com.notmugil.uta.ui.screens.settings

import androidx.annotation.StringRes
import com.notmugil.uta.R

enum class SettingsSubPage(@StringRes val titleResId: Int) {
    GENERAL(R.string.settings_general),
    PLAYBACK(R.string.settings_playback),
    DOWNLOADS(R.string.settings_downloads),
    NETWORK(R.string.settings_network),
    ABOUT(R.string.settings_about),

    // Nested Sub-Pages
    LYRICS_PROVIDERS(R.string.setting_lyrics_provider),
    ONLINE_LYRICS_PROVIDERS(R.string.setting_lyrics_provider);

    val title: String get() = name
}
