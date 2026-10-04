package com.notmugil.uta.data

import dev.zt64.subsonic.client.SubsonicClient

object SubsonicSession {
    var client: SubsonicClient? = null
    var isOfflineModeActive: Boolean = false
    var isOnline: Boolean = true
    var isManualOffline: Boolean = false
    var isServerUnreachable: Boolean = false
}
