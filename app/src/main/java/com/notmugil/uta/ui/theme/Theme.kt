package com.notmugil.uta.ui.theme

import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.data.preferences.AppThemeMode
import com.notmugil.uta.data.preferences.DynamicColorSource
import com.notmugil.uta.data.preferences.LocalAppPreferences

private object NoRippleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = PurpleGrey80,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Pink80,
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = NeutralDarkBackground,
    onBackground = NeutralDarkOnBackground,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkOnSurface,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = NeutralDarkOnSurfaceVariant,
    surfaceContainerLowest = NeutralDarkSurfaceContainerLowest,
    surfaceContainerLow = NeutralDarkSurfaceContainerLow,
    surfaceContainer = NeutralDarkSurfaceContainer,
    surfaceContainerHigh = NeutralDarkSurfaceContainerHigh,
    surfaceContainerHighest = NeutralDarkSurfaceContainerHighest,
    surfaceBright = NeutralDarkSurfaceBright,
    surfaceDim = NeutralDarkSurfaceDim,
    outline = NeutralDarkOutline,
    outlineVariant = NeutralDarkOutlineVariant
)

val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = PurpleGrey40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Pink40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = NeutralLightBackground,
    onBackground = NeutralLightOnBackground,
    surface = NeutralLightSurface,
    onSurface = NeutralLightOnSurface,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralLightOnSurfaceVariant,
    surfaceContainerLowest = NeutralLightSurfaceContainerLowest,
    surfaceContainerLow = NeutralLightSurfaceContainerLow,
    surfaceContainer = NeutralLightSurfaceContainer,
    surfaceContainerHigh = NeutralLightSurfaceContainerHigh,
    surfaceContainerHighest = NeutralLightSurfaceContainerHighest,
    surfaceBright = NeutralLightSurfaceBright,
    surfaceDim = NeutralLightSurfaceDim,
    outline = NeutralLightOutline,
    outlineVariant = NeutralLightOutlineVariant
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtaTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val appPreferences = LocalAppPreferences.current
    val dynamicThemeManager = LocalDynamicThemeManager.current

    val themeMode by (appPreferences?.themeMode?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(AppThemeMode.SYSTEM) })
    val isSystemDark = isSystemInDarkTheme()
    val isDark = darkTheme ?: when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemDark
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val customAccentColor by (appPreferences?.customAccentColor?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(null) })
    val dynamicColorSource by (appPreferences?.dynamicColorSource?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(DynamicColorSource.BOTH) })
    val dynamicSeedColor by (dynamicThemeManager?.dynamicSeedColor?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(null) })

    val context = LocalContext.current

    val defaultScheme = remember(customAccentColor, isDark) {
        if (customAccentColor != null) {
            val seed = Color(customAccentColor!!)
            if (isDark) dynamicDarkColorSchemeFromSeed(seed) else dynamicLightColorSchemeFromSeed(seed)
        } else {
            if (isDark) DarkColorScheme else LightColorScheme
        }
    }

    val colorScheme = when (dynamicColorSource) {
        DynamicColorSource.COVER_ONLY -> {
            if (dynamicSeedColor != null) {
                val seed = dynamicSeedColor!!
                if (isDark) dynamicDarkColorSchemeFromSeed(seed) else dynamicLightColorSchemeFromSeed(seed)
            } else {
                defaultScheme
            }
        }
        DynamicColorSource.WALLPAPER_ONLY -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) {
                    accentOnlyDarkColorScheme(dynamicDarkColorScheme(context))
                } else {
                    accentOnlyLightColorScheme(dynamicLightColorScheme(context))
                }
            } else {
                defaultScheme
            }
        }
        DynamicColorSource.BOTH -> {
            if (dynamicSeedColor != null) {
                val seed = dynamicSeedColor!!
                if (isDark) dynamicDarkColorSchemeFromSeed(seed) else dynamicLightColorSchemeFromSeed(seed)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) {
                    accentOnlyDarkColorScheme(dynamicDarkColorScheme(context))
                } else {
                    accentOnlyLightColorScheme(dynamicLightColorScheme(context))
                }
            } else {
                defaultScheme
            }
        }
    }

    val fontPreference by (appPreferences?.fontPreference?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(com.notmugil.uta.data.preferences.AppFont.SYSTEM_DEFAULT) })
    val typography = remember(fontPreference) {
        createAppTypography(fontPreference.fontFamily)
    }

    CompositionLocalProvider(
        LocalRippleConfiguration provides null,
        LocalIndication provides NoRippleIndication
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colorScheme.onBackground,
                content = content
            )
        }
    }
}