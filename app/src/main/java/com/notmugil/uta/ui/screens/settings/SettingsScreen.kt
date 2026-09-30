package com.notmugil.uta.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.notmugil.uta.R
import com.notmugil.uta.data.AuthState
import com.notmugil.uta.data.update.AppUpdateManager
import com.notmugil.uta.data.update.UpdateResult
import com.notmugil.uta.ui.screens.settings.components.SettingsItemRow
import com.notmugil.uta.ui.screens.settings.sections.AboutSubPage
import com.notmugil.uta.ui.screens.settings.sections.DownloadsStorageSubPage
import com.notmugil.uta.ui.screens.settings.sections.GeneralSubPage
import com.notmugil.uta.ui.screens.settings.sections.NetworkSubPage
import com.notmugil.uta.ui.screens.settings.sections.OnlineLyricsProvidersSubPage
import com.notmugil.uta.ui.screens.settings.sections.PlaybackAudioSubPage
import com.notmugil.uta.ui.shared.ActionConfirmDialog
import com.notmugil.uta.ui.shared.AppToastManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToDownloads: () -> Unit = {}
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val currentSubPage by viewModel.currentSubPage.collectAsStateWithLifecycle()

    val mainScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(initial = 0) }
    val generalScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(initial = 0) }
    val playbackScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(initial = 0) }
    val downloadsScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(initial = 0) }
    val networkScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(initial = 0) }
    val lyricsProvidersScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(initial = 0) }
    val aboutScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(initial = 0) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        ActionConfirmDialog(
            title = stringResource(R.string.settings_logout_confirm_title),
            message = stringResource(R.string.settings_logout_confirm_msg),
            confirmText = stringResource(R.string.action_logout),
            isDestructive = true,
            icon = Tabler.Outline.Logout,
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    BackHandler(enabled = currentSubPage != null) {
        viewModel.navigateBackFromSubPage()
    }

    val subPageScrollState = when (currentSubPage) {
        SettingsSubPage.GENERAL -> generalScrollState
        SettingsSubPage.PLAYBACK -> playbackScrollState
        SettingsSubPage.DOWNLOADS -> downloadsScrollState
        SettingsSubPage.NETWORK -> networkScrollState
        SettingsSubPage.LYRICS_PROVIDERS,
        SettingsSubPage.ONLINE_LYRICS_PROVIDERS -> lyricsProvidersScrollState
        SettingsSubPage.ABOUT -> aboutScrollState
        null -> mainScrollState
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (currentSubPage == null) {
            MainSettingsView(
                viewModel = viewModel,
                authState = authState,
                scrollState = mainScrollState,
                onNavigateToSubPage = { viewModel.navigateToSubPage(it) },
                onRequestLogout = { showLogoutDialog = true }
            )
        } else {
            SettingsSubPageView(
                subPage = currentSubPage!!,
                viewModel = viewModel,
                scrollState = subPageScrollState,
                onNavigateToSubPage = { viewModel.navigateToSubPage(it) },
                onBack = {
                    viewModel.navigateBackFromSubPage()
                },
                onRequestLogout = { showLogoutDialog = true }
            )
        }
    }
}

@Composable
private fun MainSettingsView(
    viewModel: SettingsViewModel,
    authState: AuthState,
    scrollState: ScrollState,
    onNavigateToSubPage: (SettingsSubPage) -> Unit,
    onRequestLogout: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary
    val isOfflineModeActive by viewModel.isOfflineModeActive.collectAsStateWithLifecycle()
    val isOfflineModeManual by viewModel.isOfflineModeManual.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdates by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .padding(bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (authState is AuthState.Authenticated) {
            val creds = authState.credentials

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = creds.username,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isOfflineModeActive) stringResource(R.string.settings_offline_summary) else creds.serverUrl.removePrefix("http://").removePrefix("https://"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.setOfflineModeManual(!isOfflineModeManual) }
                ) {
                    Icon(
                        imageVector = if (isOfflineModeActive) Tabler.Outline.CloudOff else Tabler.Outline.Cloud,
                        contentDescription = if (isOfflineModeActive) stringResource(R.string.settings_switch_online_cd) else stringResource(R.string.settings_switch_offline_cd),
                        tint = if (isOfflineModeActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRequestLogout
                ) {
                    Icon(
                        imageVector = Tabler.Outline.Logout,
                        contentDescription = stringResource(R.string.cd_logout),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SettingsItemRow(
                icon = Tabler.Outline.Settings,
                title = stringResource(R.string.settings_general),
                verticalPadding = 16.dp,
                onClick = { onNavigateToSubPage(SettingsSubPage.GENERAL) }
            )

            SettingsItemRow(
                icon = Tabler.Outline.AdjustmentsHorizontal,
                title = stringResource(R.string.settings_playback),
                verticalPadding = 16.dp,
                onClick = { onNavigateToSubPage(SettingsSubPage.PLAYBACK) }
            )

            SettingsItemRow(
                icon = Tabler.Outline.Download,
                title = stringResource(R.string.settings_downloads),
                verticalPadding = 16.dp,
                onClick = { onNavigateToSubPage(SettingsSubPage.DOWNLOADS) }
            )

            SettingsItemRow(
                icon = Tabler.Outline.Server,
                title = stringResource(R.string.settings_network),
                verticalPadding = 16.dp,
                onClick = { onNavigateToSubPage(SettingsSubPage.NETWORK) }
            )

            SettingsItemRow(
                icon = Tabler.Outline.CircleArrowDown,
                title = stringResource(R.string.settings_check_updates),
                subtitle = if (isCheckingUpdates) stringResource(R.string.settings_checking_releases) else null,
                verticalPadding = 16.dp,
                onClick = {
                    if (isCheckingUpdates) return@SettingsItemRow
                    isCheckingUpdates = true
                    AppToastManager.showInfo(context.getString(R.string.settings_checking_updates))
                    coroutineScope.launch {
                        try {
                            when (val result = AppUpdateManager.checkForUpdates(context)) {
                                is UpdateResult.UpdateAvailable -> {
                                    AppToastManager.showSuccess(
                                        message = context.getString(R.string.update_available_toast, result.latestVersion),
                                        actionLabel = context.getString(R.string.update_action),
                                        durationMs = 5000L,
                                        onAction = {
                                            AppUpdateManager.openReleasePage(context, result.releaseUrl)
                                        }
                                    )
                                    AppUpdateManager.openReleasePage(context, result.releaseUrl)
                                }
                                is UpdateResult.UpToDate -> {
                                    AppToastManager.showSuccess(
                                        context.getString(R.string.update_up_to_date, result.currentVersion)
                                    )
                                }
                                is UpdateResult.Error -> {
                                    AppToastManager.showError(
                                        context.getString(R.string.update_error, result.message)
                                    )
                                }
                            }
                        } finally {
                            isCheckingUpdates = false
                        }
                    }
                }
            )

            SettingsItemRow(
                icon = Tabler.Outline.InfoCircle,
                title = stringResource(R.string.settings_about),
                verticalPadding = 16.dp,
                onClick = { onNavigateToSubPage(SettingsSubPage.ABOUT) }
            )
        }

        Spacer(modifier = Modifier.height(168.dp))
    }
}


@Composable
private fun SettingsSubPageView(
    subPage: SettingsSubPage,
    viewModel: SettingsViewModel,
    scrollState: ScrollState,
    onNavigateToSubPage: (SettingsSubPage) -> Unit,
    onBack: () -> Unit,
    onRequestLogout: () -> Unit
) {
    val isScrolled by remember {
        derivedStateOf { scrollState.value > 10 }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(top = 60.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) {
            when (subPage) {
                SettingsSubPage.GENERAL -> {
                    GeneralSubPage(
                        viewModel = viewModel,
                        onNavigate = onNavigateToSubPage
                    )
                }
                SettingsSubPage.LYRICS_PROVIDERS,
                SettingsSubPage.ONLINE_LYRICS_PROVIDERS -> {
                    OnlineLyricsProvidersSubPage(viewModel = viewModel)
                }
                SettingsSubPage.PLAYBACK -> {
                    PlaybackAudioSubPage(viewModel = viewModel)
                }
                SettingsSubPage.DOWNLOADS -> {
                    DownloadsStorageSubPage(viewModel = viewModel)
                }
                SettingsSubPage.NETWORK -> {
                    NetworkSubPage(
                        viewModel = viewModel,
                        onRequestLogout = onRequestLogout
                    )
                }
                SettingsSubPage.ABOUT -> {
                    AboutSubPage()
                }
            }

            Spacer(modifier = Modifier.height(168.dp))
        }

        val fadeColor = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            AnimatedVisibility(
                visible = isScrolled,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.matchParentSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    fadeColor,
                                    fadeColor.copy(alpha = 0.90f),
                                    fadeColor.copy(alpha = 0.60f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isScrolled) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.60f)
                            }
                        )
                ) {
                    Icon(
                        imageVector = Tabler.Outline.ArrowLeft,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = stringResource(subPage.titleResId),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.size(40.dp))
            }
        }
    }
}
