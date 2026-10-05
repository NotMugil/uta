package com.notmugil.uta.ui.shared

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notmugil.uta.data.preferences.DynamicColorSource
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.player.PlaybackController
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import kotlinx.coroutines.isActive

@Composable
fun AppAmbientBackground(
    playbackController: PlaybackController? = null,
    forceDefaultColors: Boolean = false,
    modifier: Modifier = Modifier
) {
    val appPreferences = LocalAppPreferences.current
    val dynamicThemeManager = LocalDynamicThemeManager.current

    val isDynamicThemeEnabled by (appPreferences?.isDynamicThemeEnabled?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(true) })

    val dynamicColorSource by (appPreferences?.dynamicColorSource?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(DynamicColorSource.BOTH) })

    val dynamicSeedColor by (dynamicThemeManager?.dynamicSeedColor?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(null) })

    val dynamicSecondaryColor by (dynamicThemeManager?.dynamicSecondaryColor?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(null) })

    val currentTrack by (playbackController?.currentTrack?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(null) })

    val isPlaying by (playbackController?.isPlaying?.collectAsStateWithLifecycle()
        ?: remember { androidx.compose.runtime.mutableStateOf(false) })

    val isCoverActive = !forceDefaultColors && isDynamicThemeEnabled &&
        (dynamicColorSource == DynamicColorSource.COVER_ONLY || dynamicColorSource == DynamicColorSource.BOTH) &&
        (currentTrack != null)

    val isWallpaperActive = !forceDefaultColors && isDynamicThemeEnabled && (
        (dynamicColorSource == DynamicColorSource.WALLPAPER_ONLY) ||
            (dynamicColorSource == DynamicColorSource.BOTH && currentTrack == null)
    )

    val themePrimary = MaterialTheme.colorScheme.primary
    val themeSecondary = MaterialTheme.colorScheme.secondary.takeIf { it != themePrimary }
        ?: MaterialTheme.colorScheme.tertiary

    val targetPrimary = when {
        isCoverActive -> dynamicSeedColor ?: themePrimary
        isWallpaperActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> themePrimary
        else -> themePrimary
    }

    val targetSecondary = when {
        isCoverActive -> dynamicSecondaryColor?.takeIf { it != targetPrimary } ?: themeSecondary
        isWallpaperActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> themeSecondary
        else -> themeSecondary
    }

    val (rawC1, rawC2, rawC3) = remember(targetPrimary, targetSecondary) {
        deriveThreeColors(targetPrimary, targetSecondary)
    }

    val color1 by animateColorAsState(rawC1, animationSpec = tween(700), label = "ambient_c1")
    val color2 by animateColorAsState(rawC2, animationSpec = tween(700), label = "ambient_c2")
    val color3 by animateColorAsState(rawC3, animationSpec = tween(700), label = "ambient_c3")
    val bgColor = MaterialTheme.colorScheme.background

    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val current = rotation.value
                rotation.animateTo(
                    targetValue = current + 360f,
                    animationSpec = tween(durationMillis = 45000, easing = LinearEasing)
                )
            }
        } else {
            rotation.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(55.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotation.value
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                val circleRadius = (size.width * 1.10f).coerceIn(340.dp.toPx(), 540.dp.toPx())
                val centerOffset = Offset(0f, 0f)

                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            color1.copy(alpha = 0.70f),
                            color2.copy(alpha = 0.65f),
                            color3.copy(alpha = 0.60f),
                            color1.copy(alpha = 0.70f)
                        ),
                        center = centerOffset
                    ),
                    radius = circleRadius,
                    center = centerOffset
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val circleRadius = (size.width * 1.10f).coerceIn(340.dp.toPx(), 540.dp.toPx())
                val centerOffset = Offset(0f, 0f)
                val screenCenter = Offset(size.width * 0.50f, size.height * 0.45f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.20f to Color.Transparent,
                            0.50f to bgColor.copy(alpha = 0.45f),
                            0.75f to bgColor.copy(alpha = 0.85f),
                            1.0f to bgColor
                        ),
                        center = centerOffset,
                        radius = circleRadius
                    ),
                    radius = circleRadius,
                    center = centerOffset
                )

                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to bgColor,
                            0.35f to bgColor.copy(alpha = 0.90f),
                            0.65f to bgColor.copy(alpha = 0.45f),
                            1.0f to Color.Transparent
                        ),
                        center = screenCenter,
                        radius = size.width * 0.95f
                    )
                )
            }
        }
    }
}

private fun deriveThreeColors(primary: Color, secondary: Color): Triple<Color, Color, Color> {
    val hsv1 = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (primary.red * 255).toInt(),
        (primary.green * 255).toInt(),
        (primary.blue * 255).toInt(),
        hsv1
    )

    val hsv2 = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (secondary.red * 255).toInt(),
        (secondary.green * 255).toInt(),
        (secondary.blue * 255).toInt(),
        hsv2
    )

    val isSecondaryDistinct = kotlin.math.abs(hsv1[0] - hsv2[0]) > 15f ||
        kotlin.math.abs(hsv1[2] - hsv2[2]) > 0.15f

    if (isSecondaryDistinct) {
        val hsv3 = hsv1.clone().apply {
            this[0] = (this[0] + 30f) % 360f
            this[2] = (this[2] * 1.25f).coerceIn(0.2f, 1f)
            this[1] = (this[1] * 0.85f).coerceIn(0.3f, 1f)
        }
        val c3 = Color(android.graphics.Color.HSVToColor(hsv3))
        return Triple(primary, secondary, c3)
    } else {
        val lighterHsv = hsv1.clone().apply {
            this[0] = (this[0] + 20f) % 360f
            this[2] = (this[2] * 1.3f).coerceIn(0.3f, 1f)
            this[1] = (this[1] * 0.75f).coerceIn(0.25f, 1f)
        }
        val darkerHsv = hsv1.clone().apply {
            this[0] = (this[0] - 25f + 360f) % 360f
            this[2] = (this[2] * 0.7f).coerceIn(0.15f, 0.9f)
            this[1] = (this[1] * 1.15f).coerceIn(0.3f, 1f)
        }
        val lighterColor = Color(android.graphics.Color.HSVToColor(lighterHsv))
        val darkerColor = Color(android.graphics.Color.HSVToColor(darkerHsv))
        return Triple(primary, lighterColor, darkerColor)
    }
}
