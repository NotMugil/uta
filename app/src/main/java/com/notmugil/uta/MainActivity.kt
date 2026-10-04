package com.notmugil.uta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.notmugil.uta.data.AuthState
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.ui.UtaApp
import com.notmugil.uta.ui.screens.login.LoginScreen
import com.notmugil.uta.ui.shared.FullScreenLoader
import com.notmugil.uta.ui.theme.UtaTheme
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories
import dagger.hilt.android.internal.managers.ActivityComponentManager
import dagger.hilt.android.internal.managers.SavedStateHandleHolder
import dagger.hilt.components.SingletonComponent
import dagger.hilt.internal.GeneratedComponentManagerHolder

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MainActivityEntryPoint {
    fun subsonicRepository(): SubsonicRepository
    fun appPreferences(): com.notmugil.uta.data.preferences.AppPreferences
    fun dynamicThemeManager(): com.notmugil.uta.ui.theme.DynamicThemeManager
    fun lyricsRepository(): com.notmugil.uta.data.repository.LyricsRepository
    fun networkMonitor(): com.notmugil.uta.data.NetworkMonitor
}

@AndroidEntryPoint
class MainActivity : ComponentActivity(), GeneratedComponentManagerHolder {

    private val activityComponentManager by lazy { ActivityComponentManager(this) }
    private var savedStateHandleHolder: SavedStateHandleHolder? = null

    override fun componentManager(): ActivityComponentManager = activityComponentManager

    override fun generatedComponent(): Any = activityComponentManager.generatedComponent()

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = DefaultViewModelFactories.getActivityFactory(this, super.defaultViewModelProviderFactory)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedStateHandleHolder = activityComponentManager.savedStateHandleHolder.apply {
            if (isInvalid) {
                setExtras(defaultViewModelCreationExtras)
            }
        }
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            MainActivityEntryPoint::class.java
        )
        val subsonicRepository = entryPoint.subsonicRepository()
        val appPreferences = entryPoint.appPreferences()
        val dynamicThemeManager = entryPoint.dynamicThemeManager()
        val lyricsRepository = entryPoint.lyricsRepository()
        val networkMonitor = entryPoint.networkMonitor()
        com.notmugil.uta.data.repository.LyricsRepository.init(lyricsRepository)

        setContent {
            val appLanguage by appPreferences.appLanguage.collectAsStateWithLifecycle()
            val locale = androidx.compose.runtime.remember(appLanguage) { java.util.Locale.forLanguageTag(appLanguage.code) }
            val currentConfig = androidx.compose.ui.platform.LocalConfiguration.current
            val localizedConfig = androidx.compose.runtime.remember(currentConfig, locale) {
                android.content.res.Configuration(currentConfig).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
            }
            val currentContext = androidx.compose.ui.platform.LocalContext.current
            val localizedContext = androidx.compose.runtime.remember(currentContext, locale, localizedConfig) {
                LocalizedActivityContext(currentContext, localizedConfig)
            }

            androidx.compose.runtime.CompositionLocalProvider(
                com.notmugil.uta.data.preferences.LocalAppPreferences provides appPreferences,
                com.notmugil.uta.data.LocalNetworkMonitor provides networkMonitor,
                com.notmugil.uta.ui.theme.LocalDynamicThemeManager provides dynamicThemeManager,
                androidx.compose.ui.platform.LocalConfiguration provides localizedConfig,
                androidx.compose.ui.platform.LocalContext provides localizedContext
            ) {
                UtaTheme {
                    val authState by subsonicRepository.authState.collectAsStateWithLifecycle()

                    when (val state = authState) {
                        is AuthState.Loading -> {
                            FullScreenLoader()
                        }
                        is AuthState.Unauthenticated -> {
                            LoginScreen(
                                initialErrorMessage = state.message
                            )
                        }
                        is AuthState.Authenticated -> {
                            UtaApp()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        savedStateHandleHolder?.clear()
    }
}

private class LocalizedActivityContext(
    base: android.content.Context,
    private val localizedConfig: android.content.res.Configuration
) : android.content.ContextWrapper(base) {
    private val localizedResources: android.content.res.Resources by lazy {
        base.createConfigurationContext(localizedConfig).resources
    }
    override fun getResources(): android.content.res.Resources = localizedResources
}