package com.notmugil.uta.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.bitmapConfig
import coil3.toBitmap
import com.notmugil.uta.data.SubsonicSession
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.PlaybackController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

val LocalDynamicThemeManager = staticCompositionLocalOf<DynamicThemeManager?> {
    null
}

@Singleton
class DynamicThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackController: PlaybackController,
    private val subsonicRepository: com.notmugil.uta.data.SubsonicRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var extractionJob: Job? = null

    // In-memory cache for extracted seed colors: coverArtId/trackId -> RGB Int
    private val colorCache = ConcurrentHashMap<String, Int>()
    private val secondaryColorCache = ConcurrentHashMap<String, Int>()
    private val darkColorCache = ConcurrentHashMap<String, Int>()

    private val _dynamicSeedColor = MutableStateFlow<Color?>(null)
    val dynamicSeedColor: StateFlow<Color?> = _dynamicSeedColor.asStateFlow()

    private val _dynamicSecondaryColor = MutableStateFlow<Color?>(null)
    val dynamicSecondaryColor: StateFlow<Color?> = _dynamicSecondaryColor.asStateFlow()

    private val _dynamicDarkBgColor = MutableStateFlow<Color?>(null)
    val dynamicDarkBgColor: StateFlow<Color?> = _dynamicDarkBgColor.asStateFlow()

    init {
        scope.launch {
            playbackController.currentTrack.collectLatest { track ->
                handleTrackChanged(track)
            }
        }
    }

    private suspend fun handleTrackChanged(track: TrackItem?) {
        if (track == null) {
            _dynamicSeedColor.value = null
            _dynamicSecondaryColor.value = null
            _dynamicDarkBgColor.value = null
            return
        }

        val coverId = track.coverArtId?.takeIf { it.isNotBlank() }
            ?: track.albumId?.takeIf { it.isNotBlank() }
            ?: track.id

        // Check memory cache first for 0ms instant theme switch
        val cachedSeed = colorCache[coverId]
        val cachedSecondary = secondaryColorCache[coverId]
        val cachedDark = darkColorCache[coverId]
        if (cachedSeed != null && cachedDark != null) {
            _dynamicSeedColor.value = Color(cachedSeed)
            _dynamicSecondaryColor.value = cachedSecondary?.let { Color(it) } ?: Color(cachedSeed)
            _dynamicDarkBgColor.value = Color(cachedDark)
            return
        }

        // Extract in background
        extractionJob?.cancel()
        extractionJob = scope.launch {
            extractColorForTrack(track, coverId)
        }
    }

    private suspend fun extractColorForTrack(track: TrackItem, coverId: String) {
        try {
            val localFile = OfflineDownloadManager.getLocalCoverArtFile(context, track.coverArtId)
                ?: OfflineDownloadManager.getLocalCoverArtFile(context, track.albumId)

            var bitmap: Bitmap? = null
            if (localFile != null && localFile.exists()) {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                bitmap = BitmapFactory.decodeFile(localFile.absolutePath, opts)
            }

            if (bitmap == null && !SubsonicSession.isOfflineModeActive) {
                val targetCoverId = track.coverArtId ?: track.albumId
                if (!targetCoverId.isNullOrBlank()) {
                    val url = subsonicRepository.getCoverArtUrl(targetCoverId, size = 800)
                        ?: SubsonicSession.client?.getCoverArtUrl(targetCoverId, size = "800")
                    if (url != null) {
                        val loader = SingletonImageLoader.get(context)
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .size(800)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build()
                        val result = loader.execute(request)
                        if (result is SuccessResult) {
                            try {
                                bitmap = result.image.toBitmap()
                            } catch (_: Exception) {
                                val drawable = result.image as? BitmapDrawable
                                bitmap = drawable?.bitmap
                            }
                        }
                    }
                }
            }

            if (bitmap != null && !bitmap.isRecycled) {
                val softwareBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                    bitmap.config == Bitmap.Config.HARDWARE
                ) {
                    try {
                        bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    bitmap
                }

                if (softwareBitmap != null && !softwareBitmap.isRecycled) {
                    val palette = withContext(Dispatchers.Default) {
                        try {
                            Palette.from(softwareBitmap)
                                .maximumColorCount(24)
                                .clearFilters()
                                .generate()
                        } catch (_: Exception) {
                            null
                        }
                    }

                    if (palette != null) {
                        val swatches = palette.swatches

                        val chromaticSwatches = swatches.filter { s ->
                            val hsl = s.hsl
                            val sat = hsl[1]
                            val light = hsl[2]
                            sat >= 0.15f && light in 0.18f..0.75f && !(light > 0.68f && sat < 0.30f)
                        }

                        val bestSwatch = if (chromaticSwatches.isNotEmpty()) {
                            chromaticSwatches.maxByOrNull { s ->
                                val hsl = s.hsl
                                val sat = hsl[1]
                                val light = hsl[2]
                                val popSqrt = kotlin.math.sqrt(s.population.toFloat()).coerceAtLeast(1f)
                                val lightDist = kotlin.math.abs(light - 0.48f)
                                val lightWeight = (1.0f - lightDist * 1.6f).coerceIn(0.20f, 1.0f)
                                val satWeight = (sat * sat * 3.0f + 0.4f).coerceIn(0.4f, 3.5f)
                                popSqrt * lightWeight * satWeight
                            }
                        } else {
                            swatches.filter { s ->
                                val light = s.hsl[2]
                                light in 0.20f..0.60f
                            }.maxByOrNull { s ->
                                val lightDist = kotlin.math.abs(s.hsl[2] - 0.40f)
                                s.population.toFloat() * (1.0f - lightDist)
                            } ?: palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.mutedSwatch
                        }

                        val finalSeedRgb = if (bestSwatch != null) {
                            val hsl = FloatArray(3)
                            androidx.core.graphics.ColorUtils.colorToHSL(bestSwatch.rgb, hsl)
                            if (hsl[2] > 0.70f) hsl[2] = 0.62f
                            if (hsl[1] < 0.15f && hsl[2] > 0.50f) {
                                hsl[2] = 0.42f
                                hsl[1] = 0.25f
                                hsl[0] = 215f
                            }
                            androidx.core.graphics.ColorUtils.HSLToColor(hsl)
                        } else {
                            android.graphics.Color.rgb(63, 81, 181)
                        }

                        val seedHsl = FloatArray(3)
                        androidx.core.graphics.ColorUtils.colorToHSL(finalSeedRgb, seedHsl)
                        val bestHue = seedHsl[0]
                        val seedLightness = seedHsl[2]

                        val lighterCandidate = chromaticSwatches.filter { s ->
                            val hsl = s.hsl
                            val hueDiff = kotlin.math.abs(hsl[0] - bestHue).let { if (it > 180f) 360f - it else it }
                            hueDiff <= 32f && hsl[2] in (seedLightness + 0.06f)..0.68f && hsl[1] >= 0.20f
                        }.maxByOrNull { s ->
                            s.population * (1.0f + s.hsl[1]) * s.hsl[2]
                        }

                        val secondaryRgb = if (lighterCandidate != null) {
                            lighterCandidate.rgb
                        } else {
                            val hsl = FloatArray(3)
                            androidx.core.graphics.ColorUtils.colorToHSL(finalSeedRgb, hsl)
                            hsl[2] = (hsl[2] * 1.25f + 0.12f).coerceIn(0.48f, 0.68f)
                            hsl[1] = (hsl[1] * 0.95f).coerceIn(0.35f, 0.85f)
                            androidx.core.graphics.ColorUtils.HSLToColor(hsl)
                        }

                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(finalSeedRgb, hsv)
                        hsv[1] = (hsv[1] * 0.65f).coerceIn(0.25f, 0.55f)
                        hsv[2] = 0.08f
                        val darkRgb = android.graphics.Color.HSVToColor(hsv)

                        colorCache[coverId] = finalSeedRgb
                        secondaryColorCache[coverId] = secondaryRgb
                        darkColorCache[coverId] = darkRgb

                        if (playbackController.currentTrack.value?.id == track.id) {
                            _dynamicSeedColor.value = Color(finalSeedRgb)
                            _dynamicSecondaryColor.value = Color(secondaryRgb)
                            _dynamicDarkBgColor.value = Color(darkRgb)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[DynamicTheme] Failed to extract palette for ${track.title}")
        }
    }

    fun clearCache() {
        colorCache.clear()
        secondaryColorCache.clear()
        darkColorCache.clear()
    }
}
