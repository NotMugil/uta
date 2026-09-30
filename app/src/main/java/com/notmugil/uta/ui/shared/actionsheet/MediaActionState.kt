package com.notmugil.uta.ui.shared.actionsheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class MediaActionState {
    var currentTarget by mutableStateOf<MediaTarget?>(null)
        private set

    fun show(target: MediaTarget) {
        currentTarget = target
    }

    fun dismiss() {
        currentTarget = null
    }
}

val LocalMediaActionHandler = staticCompositionLocalOf { MediaActionState() }
