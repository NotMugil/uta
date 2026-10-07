package com.notmugil.uta.ui.theme

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

private object ColorTuning {
    const val MAX_PALETTE_COLORS = 24
    const val MIN_CHROMA_FOR_CHROMATIC = 14.0
    const val CHROMATIC_COVERAGE_THRESHOLD = 0.035
    const val TARGET_MID_TONE = 50.0
    const val TONE_SIGMA = 25.0
    const val CHROMA_NORMALIZER = 48.0
    const val MAX_CHROMA_TERM = 1.5
    const val MIN_SCORE_RATIO_FOR_ACCENT = 0.35
    const val MIN_HUE_DIFFERENCE_FOR_ACCENT = 32.0
    const val DEFAULT_DARK_BG = 0xFF101014.toInt()
    const val FALLBACK_SEED = 0xFF3F51B5.toInt()
}

private data class ScoredCandidate(
    val rgb: Int,
    val population: Int,
    val l: Double,
    val chroma: Double,
    val hue: Double,
    val score: Double
)

@Singleton
class AlbumColorExtractor @Inject constructor() {
    fun extractColors(bitmap: Bitmap): AlbumColors? {
        if (bitmap.isRecycled) return null

        val palette = try {
            Palette.from(bitmap)
                .maximumColorCount(ColorTuning.MAX_PALETTE_COLORS)
                .clearFilters()
                .generate()
        } catch (_: Exception) {
            return null
        }

        val swatches = palette.swatches
        if (swatches.isEmpty()) return null

        val totalPopulation = swatches.sumOf { it.population }.coerceAtLeast(1)

        val candidates = swatches.map { swatch ->
            val lab = DoubleArray(3)
            ColorUtils.colorToLAB(swatch.rgb, lab)
            val l = lab[0]
            val a = lab[1]
            val b = lab[2]
            val chroma = sqrt(a * a + b * b)
            val hue = (atan2(b, a) * 180.0 / Math.PI + 360.0) % 360.0

            val popRatio = swatch.population.toDouble() / totalPopulation
            val popTerm = sqrt(popRatio)
            val chromaTerm = (chroma / ColorTuning.CHROMA_NORMALIZER).coerceIn(0.0, ColorTuning.MAX_CHROMA_TERM)
            val toneDiff = (l - ColorTuning.TARGET_MID_TONE) / ColorTuning.TONE_SIGMA
            val toneTerm = exp(-0.5 * toneDiff * toneDiff)

            val score = popTerm * chromaTerm * toneTerm

            ScoredCandidate(
                rgb = swatch.rgb,
                population = swatch.population,
                l = l,
                chroma = chroma,
                hue = hue,
                score = score
            )
        }

        val chromaticPop = candidates.filter {
            it.chroma >= ColorTuning.MIN_CHROMA_FOR_CHROMATIC && it.l in 12.0..88.0
        }.sumOf { it.population }

        val chromaticCoverage = chromaticPop.toDouble() / totalPopulation
        val isNeutral = chromaticCoverage < ColorTuning.CHROMATIC_COVERAGE_THRESHOLD

        if (isNeutral) {
            val bestNeutral = candidates.maxByOrNull {
                val whitePenalty = if (it.l > 72.0) (1.0 - (it.l - 72.0) / 28.0).coerceIn(0.1, 1.0) else 1.0
                val midToneBoost = if (it.l in 25.0..65.0) 1.25 else 1.0
                it.population.toDouble() * whitePenalty * midToneBoost
            }

            val seedRgb = bestNeutral?.rgb ?: ColorTuning.FALLBACK_SEED
            val seedHsl = FloatArray(3)
            ColorUtils.colorToHSL(seedRgb, seedHsl)
            if (seedHsl[2] > 0.72f) seedHsl[2] = 0.58f
            if (seedHsl[2] < 0.18f) seedHsl[2] = 0.24f
            seedHsl[1] = 0.0f
            val neutralRgb = ColorUtils.HSLToColor(seedHsl)

            return AlbumColors(
                seed = Color(neutralRgb),
                accent = null,
                darkBg = Color(ColorTuning.DEFAULT_DARK_BG),
                isNeutral = true
            )
        }

        val ranked = candidates.filter {
            it.chroma >= ColorTuning.MIN_CHROMA_FOR_CHROMATIC && it.l in 14.0..85.0
        }.sortedByDescending { it.score }

        val best = ranked.firstOrNull() ?: candidates.maxByOrNull { it.score }
        val seedRgb = best?.rgb ?: ColorTuning.FALLBACK_SEED

        val seedHsl = FloatArray(3)
        ColorUtils.colorToHSL(seedRgb, seedHsl)
        if (seedHsl[2] > 0.70f) seedHsl[2] = 0.62f
        if (seedHsl[2] < 0.20f) seedHsl[2] = 0.30f
        seedHsl[1] = seedHsl[1].coerceIn(0.20f, 0.95f)
        val finalSeedRgb = ColorUtils.HSLToColor(seedHsl)

        val seedLab = DoubleArray(3)
        ColorUtils.colorToLAB(finalSeedRgb, seedLab)
        val seedHue = (atan2(seedLab[2], seedLab[1]) * 180.0 / Math.PI + 360.0) % 360.0

        val bestScore = best?.score ?: 1.0
        val accentCandidate = ranked.firstOrNull { candidate ->
            val hueDiff = abs(candidate.hue - seedHue).let { if (it > 180.0) 360.0 - it else it }
            hueDiff >= ColorTuning.MIN_HUE_DIFFERENCE_FOR_ACCENT && candidate.score >= bestScore * ColorTuning.MIN_SCORE_RATIO_FOR_ACCENT
        }

        val darkBg = deriveDarkBackground(finalSeedRgb)

        return AlbumColors(
            seed = Color(finalSeedRgb),
            accent = accentCandidate?.let { Color(it.rgb) },
            darkBg = Color(darkBg),
            isNeutral = false
        )
    }

    private fun deriveDarkBackground(seedRgb: Int): Int {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(seedRgb, hsv)
        hsv[1] = (hsv[1] * 0.60f).coerceIn(0.18f, 0.45f)
        hsv[2] = 0.08f
        return AndroidColor.HSVToColor(hsv)
    }
}
