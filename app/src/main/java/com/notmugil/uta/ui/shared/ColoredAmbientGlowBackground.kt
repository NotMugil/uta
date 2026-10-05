package com.notmugil.uta.ui.shared

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.notmugil.uta.ui.theme.LocalDynamicThemeManager
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.isActive

internal fun createOrganicBlobPath(
    centerX: Float,
    centerY: Float,
    baseRadiusX: Float,
    baseRadiusY: Float,
    pointCount: Int = 8,
    time: Float,
    speed: Float,
    distortion: Float,
    phase: Float
): Path {
    val path = Path()
    val points = ArrayList<Offset>(pointCount)
    val angleStep = (2.0 * Math.PI / pointCount).toFloat()

    for (i in 0 until pointCount) {
        val angle = i * angleStep
        val wave1 = sin(angle * 2f + time * speed + phase)
        val wave2 = cos(angle * 3f - time * speed * 1.3f + phase * 1.5f)
        val wave3 = sin(angle * 1f + time * speed * 0.7f)
        val factor = 1f + distortion * (0.55f * wave1 + 0.30f * wave2 + 0.15f * wave3)

        val rx = baseRadiusX * factor
        val ry = baseRadiusY * factor
        val px = centerX + rx * cos(angle)
        val py = centerY + ry * sin(angle)
        points.add(Offset(px, py))
    }

    val n = points.size
    val firstMid = Offset(
        (points[0].x + points[n - 1].x) / 2f,
        (points[0].y + points[n - 1].y) / 2f
    )
    path.moveTo(firstMid.x, firstMid.y)

    for (i in 0 until n) {
        val current = points[i]
        val next = points[(i + 1) % n]
        val midX = (current.x + next.x) / 2f
        val midY = (current.y + next.y) / 2f
        path.quadraticTo(current.x, current.y, midX, midY)
    }
    path.close()
    return path
}

internal fun deriveLighterTone(color: Color): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(
        android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        ),
        hsl
    )
    hsl[2] = (hsl[2] * 1.25f + 0.12f).coerceIn(0.48f, 0.68f)
    hsl[1] = (hsl[1] * 0.95f).coerceIn(0.35f, 0.85f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

@Composable
fun ColoredAmbientGlowBackground(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    fullScreenSpread: Boolean = true
) {
    val dynamicThemeManager = LocalDynamicThemeManager.current
    val dynamicDarkBgColor by (dynamicThemeManager?.dynamicDarkBgColor?.collectAsState() ?: remember { mutableStateOf(null) })
    val dynamicSeedColor by (dynamicThemeManager?.dynamicSeedColor?.collectAsState() ?: remember { mutableStateOf(null) })
    val dynamicSecondaryColor by (dynamicThemeManager?.dynamicSecondaryColor?.collectAsState() ?: remember { mutableStateOf(null) })
    val ambientBgColor = dynamicDarkBgColor ?: Color(0xFF101014)

    val targetPrimary = dynamicSeedColor ?: MaterialTheme.colorScheme.primary
    val targetSecondary = dynamicSecondaryColor ?: deriveLighterTone(targetPrimary)

    val primaryBlobColor by animateColorAsState(targetPrimary, animationSpec = tween(700), label = "ambient_primary_blob")
    val secondaryBlobColor by animateColorAsState(targetSecondary, animationSpec = tween(700), label = "ambient_secondary_blob")

    val ambientProgress = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val current = ambientProgress.value
                ambientProgress.animateTo(
                    targetValue = current + 1f,
                    animationSpec = tween(durationMillis = 44000, easing = LinearEasing)
                )
            }
        } else {
            ambientProgress.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ambientBgColor)
    ) {
        val t = ambientProgress.value
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(75.dp)
        ) {
            val w = size.width
            val h = size.height
            val twoPi = 6.2831855f

            if (fullScreenSpread) {
                val c1X = w * (0.50f + 0.36f * sin(t * twoPi * 0.45f) + 0.16f * cos(t * twoPi * 0.72f))
                val c1Y = h * (0.35f + 0.12f * cos(t * twoPi * 0.38f) + 0.05f * sin(t * twoPi * 0.65f))
                val r1X = w * 0.78f * (1f + 0.16f * sin(t * 2.8f))
                val r1Y = w * 0.70f * (1f - 0.16f * cos(t * 2.4f))
                val path1 = createOrganicBlobPath(
                    centerX = c1X,
                    centerY = c1Y,
                    baseRadiusX = r1X,
                    baseRadiusY = r1Y,
                    pointCount = 8,
                    time = t,
                    speed = 3.6f,
                    distortion = 0.38f,
                    phase = 0.0f
                )
                drawPath(
                    path = path1,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryBlobColor.copy(alpha = 0.52f),
                            primaryBlobColor.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(c1X, c1Y),
                        radius = (r1X.coerceAtLeast(r1Y) * 1.15f)
                    )
                )

                val c2X = w * (0.50f - 0.35f * cos(t * twoPi * 0.40f) - 0.18f * sin(t * twoPi * 0.82f))
                val c2Y = h * (0.75f - 0.12f * sin(t * twoPi * 0.35f) + 0.05f * cos(t * twoPi * 0.58f))
                val r2X = w * 0.72f * (1f - 0.18f * cos(t * 2.5f))
                val r2Y = w * 0.76f * (1f + 0.18f * sin(t * 2.7f))
                val path2 = createOrganicBlobPath(
                    centerX = c2X,
                    centerY = c2Y,
                    baseRadiusX = r2X,
                    baseRadiusY = r2Y,
                    pointCount = 8,
                    time = t,
                    speed = 3.2f,
                    distortion = 0.40f,
                    phase = 2.1f
                )
                drawPath(
                    path = path2,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryBlobColor.copy(alpha = 0.48f),
                            primaryBlobColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(c2X, c2Y),
                        radius = (r2X.coerceAtLeast(r2Y) * 1.15f)
                    )
                )

                val c3X = w * (0.50f + 0.58f * sin(t * twoPi * 0.75f + 1.2f))
                val c3Y = h * (0.18f + 0.10f * sin(t * twoPi * 0.65f))
                val r3X = w * 0.65f * (1f + 0.18f * sin(t * 4.2f))
                val r3Y = h * 0.18f * (1f - 0.18f * sin(t * 4.2f))
                val path3 = createOrganicBlobPath(
                    centerX = c3X,
                    centerY = c3Y,
                    baseRadiusX = r3X,
                    baseRadiusY = r3Y,
                    pointCount = 9,
                    time = t,
                    speed = 5.2f,
                    distortion = 0.40f,
                    phase = 4.3f
                )
                drawPath(
                    path = path3,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryBlobColor.copy(alpha = 0.46f),
                            secondaryBlobColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(c3X, c3Y),
                        radius = (r3X.coerceAtLeast(r3Y) * 1.15f)
                    )
                )

                val c4X = w * (0.50f - 0.58f * cos(t * twoPi * 0.85f - 0.8f))
                val c4Y = h * (0.52f + 0.10f * cos(t * twoPi * 0.55f))
                val r4X = w * 0.60f * (1f - 0.18f * cos(t * 3.8f))
                val r4Y = h * 0.18f * (1f + 0.18f * cos(t * 3.8f))
                val path4 = createOrganicBlobPath(
                    centerX = c4X,
                    centerY = c4Y,
                    baseRadiusX = r4X,
                    baseRadiusY = r4Y,
                    pointCount = 9,
                    time = t,
                    speed = 4.8f,
                    distortion = 0.40f,
                    phase = 1.6f
                )
                drawPath(
                    path = path4,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryBlobColor.copy(alpha = 0.44f),
                            secondaryBlobColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(c4X, c4Y),
                        radius = (r4X.coerceAtLeast(r4Y) * 1.15f)
                    )
                )

                val c5X = w * (0.50f + 0.52f * sin(t * twoPi * 0.90f + 2.5f))
                val c5Y = h * (0.88f - 0.08f * sin(t * twoPi * 0.70f))
                val r5X = w * 0.68f * (1f + 0.20f * sin(t * 4.5f))
                val r5Y = h * 0.20f * (1f - 0.20f * sin(t * 4.5f))
                val path5 = createOrganicBlobPath(
                    centerX = c5X,
                    centerY = c5Y,
                    baseRadiusX = r5X,
                    baseRadiusY = r5Y,
                    pointCount = 9,
                    time = t,
                    speed = 5.4f,
                    distortion = 0.42f,
                    phase = 3.2f
                )
                drawPath(
                    path = path5,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryBlobColor.copy(alpha = 0.48f),
                            secondaryBlobColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(c5X, c5Y),
                        radius = (r5X.coerceAtLeast(r5Y) * 1.15f)
                    )
                )
            }
        }
    }
}
