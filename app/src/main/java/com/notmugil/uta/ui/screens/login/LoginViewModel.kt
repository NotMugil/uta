package com.notmugil.uta.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notmugil.uta.data.SubsonicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String = "",
    val fallbackServerUrl: String = "",
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val subsonicRepository: SubsonicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            subsonicRepository.authState.collect { authState ->
                if (authState is com.notmugil.uta.data.AuthState.Unauthenticated) {
                    _uiState.value = LoginUiState(errorMessage = authState.message)
                }
            }
        }
    }

    fun onServerUrlChange(url: String) {
        _uiState.update { it.copy(serverUrl = url, errorMessage = null) }
    }

    fun onFallbackServerUrlChange(url: String) {
        _uiState.update { it.copy(fallbackServerUrl = url, errorMessage = null) }
    }

    fun onUsernameChange(name: String) {
        _uiState.update { it.copy(username = name, errorMessage = null) }
    }

    fun onPasswordChange(pwd: String) {
        _uiState.update { it.copy(password = pwd, errorMessage = null) }
    }

    fun clearServerUrl() {
        _uiState.update { it.copy(serverUrl = "", errorMessage = null) }
    }

    fun clearFallbackServerUrl() {
        _uiState.update { it.copy(fallbackServerUrl = "", errorMessage = null) }
    }

    fun clearUsername() {
        _uiState.update { it.copy(username = "", errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun setErrorMessage(message: String?) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun login(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in all fields") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                Timber.i("[Login] Attempting login for ${state.serverUrl}")
                subsonicRepository.login(
                    serverUrl = state.serverUrl.trim(),
                    username = state.username.trim(),
                    password = state.password,
                    fallbackServerUrl = state.fallbackServerUrl.trim().takeIf { it.isNotBlank() }
                )
                _uiState.value = LoginUiState()
                onSuccess()
            } catch (e: Exception) {
                Timber.w(e, "[Login] Login failed for ${state.serverUrl}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = formatLoginError(e)
                    )
                }
            }
        }
    }

    private fun formatLoginError(e: Throwable): String {
        val raw = e.localizedMessage ?: e.message ?: ""
        return when {
            raw.contains("Wrong username", ignoreCase = true) ||
                raw.contains("401", ignoreCase = true) ||
                raw.contains("auth", ignoreCase = true) ||
                raw.contains("credential", ignoreCase = true) ||
                (raw.contains("user", ignoreCase = true) && raw.contains("pass", ignoreCase = true)) -> "Invalid username or password"
            raw.contains("timeout", ignoreCase = true) || raw.contains("timed out", ignoreCase = true) || raw.contains("after ") -> "Connection timed out"
            raw.contains("refused", ignoreCase = true) -> "Connection refused (port unreachable)"
            raw.contains("ssl", ignoreCase = true) || raw.contains("cert", ignoreCase = true) -> "SSL certificate error"
            raw.contains("offline", ignoreCase = true) || raw.contains("network", ignoreCase = true) || raw.contains("internet", ignoreCase = true) -> "Network error"
            raw.contains("unreachable", ignoreCase = true) || raw.contains("failed to connect", ignoreCase = true) -> "Server unreachable"
            raw.isNotBlank() && raw.length <= 50 -> raw
            else -> "Server unreachable"
        }
    }
}
