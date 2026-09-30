package com.notmugil.uta.ui.screens.player.components

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.notmugil.uta.data.preferences.DynamicColorSource
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import kotlinx.coroutines.isActive

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.ui.shared.CoverArtImage

@Composable
fun LyricsAmbientBackground(
    track: TrackItem? = null,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    val appPreferences = LocalAppPreferences.current
    val dynamicColorSource by (appPreferences?.dynamicColorSource?.collectAsState() ?: remember { mutableStateOf(DynamicColorSource.BOTH) })
    val dynamicThemeManager = LocalDynamicThemeManager.current
    val dynamicSeedColor by (dynamicThemeManager?.dynamicSeedColor?.collectAsState() ?: remember { mutableStateOf(null) })
    val dynamicSecondaryColor by (dynamicThemeManager?.dynamicSecondaryColor?.collectAsState() ?: remember { mutableStateOf(null) })

    val isCoverActive = dynamicColorSource == DynamicColorSource.COVER_ONLY || dynamicColorSource == DynamicColorSource.BOTH
    val isWallpaperActive = dynamicColorSource == DynamicColorSource.WALLPAPER_ONLY

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
        deriveHarmonicColors(targetPrimary, targetSecondary)
    }

    val color1 by animateColorAsState(rawC1, animationSpec = tween(700), label = "lyrics_ambient_c1")
    val color2 by animateColorAsState(rawC2, animationSpec = tween(700), label = "lyrics_ambient_c2")
    val color3 by animateColorAsState(rawC3, animationSpec = tween(700), label = "lyrics_ambient_c3")
    val bgColor = MaterialTheme.colorScheme.background

    val ambientRotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val current = ambientRotation.value
                ambientRotation.animateTo(
                    targetValue = current + 360f,
                    animationSpec = tween(durationMillis = 60000, easing = LinearEasing)
                )
            }
        } else {
            ambientRotation.stop()
        }
    }

    val coverArtId = track?.coverArtId ?: track?.albumId

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (!coverArtId.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = ambientRotation.value
                        scaleX = 3.2f
                        scaleY = 3.2f
                    }
            ) {
                CoverArtImage(
                    coverArtId = coverArtId,
                    contentDescription = null,
                    size = 1000.dp,
                    shape = RoundedCornerShape(0.dp),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(65.dp)
                        .alpha(0.60f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                bgColor.copy(alpha = 0.35f),
                                bgColor.copy(alpha = 0.60f),
                                bgColor.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        } else {
            val rad = (ambientRotation.value * (Math.PI / 180.0)).toFloat()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(45.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val center1 = Offset(
                        x = w * (0.28f + 0.14f * kotlin.math.cos(rad)),
                        y = h * (0.22f + 0.10f * kotlin.math.sin(rad))
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to color1.copy(alpha = 0.48f),
                                0.40f to color1.copy(alpha = 0.24f),
                                0.70f to color1.copy(alpha = 0.06f),
                                1.0f to Color.Transparent
                            ),
                            center = center1,
                            radius = (w * 1.15f).coerceAtLeast(1f)
                        )
                    )

                    val center2 = Offset(
                        x = w * (0.74f - 0.16f * kotlin.math.cos(rad * 0.85f)),
                        y = h * (0.36f - 0.12f * kotlin.math.sin(rad * 0.85f))
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to color2.copy(alpha = 0.38f),
                                0.45f to color2.copy(alpha = 0.16f),
                                0.75f to color2.copy(alpha = 0.03f),
                                1.0f to Color.Transparent
                            ),
                            center = center2,
                            radius = (w * 1.05f).coerceAtLeast(1f)
                        )
                    )

                    val center3 = Offset(
                        x = w * (0.34f + 0.12f * kotlin.math.sin(rad * 1.15f)),
                        y = h * (0.64f + 0.10f * kotlin.math.cos(rad * 1.15f))
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to color3.copy(alpha = 0.28f),
                                0.50f to color3.copy(alpha = 0.10f),
                                0.80f to color3.copy(alpha = 0.02f),
                                1.0f to Color.Transparent
                            ),
                            center = center3,
                            radius = (w * 0.95f).coerceAtLeast(1f)
                        )
                    )

                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                bgColor.copy(alpha = 0.18f),
                                bgColor.copy(alpha = 0.50f)
                            )
                        )
                    )
                }
            }
        }
    }
}

private fun deriveHarmonicColors(primary: Color, secondary: Color): Triple<Color, Color, Color> {
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
