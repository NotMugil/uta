package com.notmugil.uta.ui.screens.login

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.notmugil.uta.R
import com.notmugil.uta.data.NetworkPermissionHelper
import com.notmugil.uta.ui.shared.AppAmbientBackground

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    initialErrorMessage: String? = null,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var pendingLoginAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val action = pendingLoginAction
        pendingLoginAction = null
        if (action != null) {
            val ungranted = NetworkPermissionHelper.getUngrantedNearbyPermissions(context, state.serverUrl)
            if (ungranted.isEmpty()) {
                action()
            } else {
                viewModel.setErrorMessage(context.getString(R.string.login_nearby_permission_required))
            }
        }
    }

    val isFormValid = state.serverUrl.isNotBlank() && state.username.isNotBlank() && state.password.isNotBlank()

    fun performLogin() {
        if (!isFormValid || state.isLoading) return
        focusManager.clearFocus()

        fun execute() {
            viewModel.login(onSuccess = onLoginSuccess)
        }

        val ungranted = NetworkPermissionHelper.getUngrantedNearbyPermissions(context, state.serverUrl)
            .ifEmpty {
                state.fallbackServerUrl.takeIf { it.isNotBlank() }?.let {
                    NetworkPermissionHelper.getUngrantedNearbyPermissions(context, it)
                } ?: emptyList()
            }

        if (ungranted.isNotEmpty()) {
            pendingLoginAction = { execute() }
            permissionLauncher.launch(ungranted.toTypedArray())
        } else {
            execute()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AppAmbientBackground(forceDefaultColors = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.login_header),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            val fieldShape = RoundedCornerShape(14.dp)
            val fieldColors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )

            // Server URL
            TextField(
                value = state.serverUrl,
                onValueChange = { viewModel.onServerUrlChange(it) },
                placeholder = { Text(stringResource(R.string.login_server_url)) },
                leadingIcon = {
                    Icon(
                        imageVector = Tabler.Outline.Server,
                        contentDescription = stringResource(R.string.login_server_url),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (state.serverUrl.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearServerUrl() }) {
                            Icon(
                                imageVector = Tabler.Outline.X,
                                contentDescription = stringResource(R.string.login_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = fieldShape,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Fallback Server URL (Optional)
            TextField(
                value = state.fallbackServerUrl,
                onValueChange = { viewModel.onFallbackServerUrlChange(it) },
                placeholder = { Text(stringResource(R.string.login_fallback_server_url)) },
                leadingIcon = {
                    Icon(
                        imageVector = Tabler.Outline.ArrowsSplit,
                        contentDescription = stringResource(R.string.login_fallback_server_url),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (state.fallbackServerUrl.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearFallbackServerUrl() }) {
                            Icon(
                                imageVector = Tabler.Outline.X,
                                contentDescription = stringResource(R.string.login_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = fieldShape,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Username
            TextField(
                value = state.username,
                onValueChange = { viewModel.onUsernameChange(it) },
                placeholder = { Text(stringResource(R.string.login_username)) },
                leadingIcon = {
                    Icon(
                        imageVector = Tabler.Outline.User,
                        contentDescription = stringResource(R.string.login_username),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (state.username.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearUsername() }) {
                            Icon(
                                imageVector = Tabler.Outline.X,
                                contentDescription = stringResource(R.string.login_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = fieldShape,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password
            TextField(
                value = state.password,
                onValueChange = { viewModel.onPasswordChange(it) },
                placeholder = { Text(stringResource(R.string.login_password)) },
                leadingIcon = {
                    Icon(
                        imageVector = Tabler.Outline.Lock,
                        contentDescription = stringResource(R.string.login_password),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    val icon = if (state.passwordVisible) Tabler.Outline.EyeOff else Tabler.Outline.Eye
                    val desc = if (state.passwordVisible) stringResource(R.string.login_hide_password) else stringResource(R.string.login_show_password)
                    IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                        Icon(
                            imageVector = icon,
                            contentDescription = desc,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = fieldShape,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { performLogin() }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error Status Message
            val displayError = state.errorMessage ?: initialErrorMessage
            AnimatedVisibility(
                visible = !displayError.isNullOrBlank(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                displayError?.let { error ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val errorIcon = when {
                                error.contains("permission", ignoreCase = true) -> Tabler.Outline.WifiOff
                                error.contains("unreachable", ignoreCase = true) || error.contains("server", ignoreCase = true) -> Tabler.Outline.CloudOff
                                error.contains("network", ignoreCase = true) || error.contains("connection", ignoreCase = true) || error.contains("offline", ignoreCase = true) -> Tabler.Outline.WifiOff
                                else -> Tabler.Outline.AlertCircle
                            }
                            Icon(
                                imageVector = errorIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (error.contains("permission", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = { NetworkPermissionHelper.openAppSettings(context) }
                            ) {
                                Text(
                                    text = stringResource(R.string.login_open_settings),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Login Button
            Button(
                onClick = { performLogin() },
                enabled = isFormValid && !state.isLoading,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.login_connect),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}
