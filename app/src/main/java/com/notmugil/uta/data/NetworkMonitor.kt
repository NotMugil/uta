package com.notmugil.uta.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.notmugil.uta.data.preferences.AppPreferences
import com.notmugil.uta.data.sync.MutationManager
import com.notmugil.uta.data.sync.ScrobbleManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.staticCompositionLocalOf
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

val LocalNetworkMonitor = staticCompositionLocalOf<NetworkMonitor?> { null }

enum class OfflineReason {
    ONLINE,
    MANUAL,
    NO_NETWORK,
    SERVER_UNREACHABLE
}

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val subsonicRepository: SubsonicRepository,
    private val mutationManagerProvider: Provider<MutationManager>,
    private val scrobbleManagerProvider: Provider<ScrobbleManager>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isServerUnreachable = MutableStateFlow(false)
    val isServerUnreachable: StateFlow<Boolean> = _isServerUnreachable.asStateFlow()

    val isOfflineModeActive: StateFlow<Boolean> = combine(
        appPreferences.isOfflineModeManual,
        _isOnline,
        _isServerUnreachable
    ) { isManual, isOnline, isServerUnreachable ->
        isManual || !isOnline || isServerUnreachable
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = appPreferences.isOfflineModeManual.value || !_isOnline.value || _isServerUnreachable.value
    )

    val offlineReason: StateFlow<OfflineReason> = combine(
        appPreferences.isOfflineModeManual,
        _isOnline,
        _isServerUnreachable
    ) { isManual, isOnline, isServerUnreachable ->
        when {
            isManual -> OfflineReason.MANUAL
            !isOnline -> OfflineReason.NO_NETWORK
            isServerUnreachable -> OfflineReason.SERVER_UNREACHABLE
            else -> OfflineReason.ONLINE
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = when {
            appPreferences.isOfflineModeManual.value -> OfflineReason.MANUAL
            !_isOnline.value -> OfflineReason.NO_NETWORK
            _isServerUnreachable.value -> OfflineReason.SERVER_UNREACHABLE
            else -> OfflineReason.ONLINE
        }
    )

    private var probeJob: Job? = null

    init {
        registerNetworkCallback()
        observeOfflineTransitions()
        observeServerUnreachableLoop()
    }

    private fun checkInitialConnectivity(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Timber.d("[NetworkMonitor] Network available")
                    _isOnline.value = true
                    if (!appPreferences.isOfflineModeManual.value && _isServerUnreachable.value) {
                        scope.launch { probeReachability() }
                    }
                }

                override fun onLost(network: Network) {
                    Timber.d("[NetworkMonitor] Network lost")
                    _isOnline.value = checkInitialConnectivity()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    _isOnline.value = hasInternet
                    if (hasInternet && !appPreferences.isOfflineModeManual.value && _isServerUnreachable.value) {
                        scope.launch { probeReachability() }
                    }
                }
            }
        )
    }

    private fun observeOfflineTransitions() {
        scope.launch {
            SubsonicSession.isOfflineModeActive = isOfflineModeActive.value
            SubsonicSession.isOnline = _isOnline.value
            SubsonicSession.isManualOffline = appPreferences.isOfflineModeManual.value
            SubsonicSession.isServerUnreachable = _isServerUnreachable.value

            var previousOffline = isOfflineModeActive.value
            combine(
                appPreferences.isOfflineModeManual,
                _isOnline,
                _isServerUnreachable,
                isOfflineModeActive
            ) { manual, online, unreachable, offline ->
                SubsonicSession.isOfflineModeActive = offline
                SubsonicSession.isOnline = online
                SubsonicSession.isManualOffline = manual
                SubsonicSession.isServerUnreachable = unreachable
                offline
            }.distinctUntilChanged().collect { offline ->
                if (previousOffline && !offline) {
                    Timber.i("[NetworkMonitor] Reconnected to online mode! Flushing outboxes...")
                    try {
                        mutationManagerProvider.get().flushOutbox()
                        scrobbleManagerProvider.get().flushOutbox()
                    } catch (e: Exception) {
                        Timber.w(e, "[NetworkMonitor] Error flushing outboxes on reconnect")
                    }
                }
                previousOffline = offline
            }
        }
    }

    private fun observeServerUnreachableLoop() {
        scope.launch {
            combine(_isServerUnreachable, appPreferences.isOfflineModeManual, _isOnline) { unreachable, manual, online ->
                unreachable && !manual && online
            }.distinctUntilChanged().collect { shouldProbe ->
                probeJob?.cancel()
                if (shouldProbe) {
                    probeJob = scope.launch {
                        while (isActive) {
                            delay(15_000L)
                            val ok = probeReachability()
                            if (ok) break
                        }
                    }
                }
            }
        }
    }

    fun markServerUnreachable() {
        if (!_isServerUnreachable.value) {
            Timber.w("[NetworkMonitor] Server marked unreachable -> switching to offline mode")
            _isServerUnreachable.value = true
        }
    }

    suspend fun probeReachability(): Boolean {
        if (appPreferences.isOfflineModeManual.value) {
            Timber.d("[NetworkMonitor] Skipping probeReachability: manual offline mode is enabled")
            return false
        }
        val credentials = subsonicRepository.currentServerUrl
        if (credentials.isNullOrBlank()) {
            _isServerUnreachable.value = false
            return false
        }

        return try {
            kotlinx.coroutines.withTimeout(4000L) {
                val client = subsonicRepository.currentClient
                if (client != null) {
                    client.ping()
                    Timber.d("[NetworkMonitor] Server reachability probe succeeded (client)")
                    _isServerUnreachable.value = false
                    true
                } else {
                    _isServerUnreachable.value = false
                    false
                }
            }
        } catch (e: Exception) {
            Timber.d("[NetworkMonitor] Server reachability probe failed: ${e.message}")
            _isServerUnreachable.value = true
            false
        }
    }
}
