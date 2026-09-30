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
                                .maximumColorCount(16)
                                .clearFilters()
                                .generate()
                        } catch (_: Exception) {
                            null
                        }
                    }

                    if (palette != null) {
                        val swatch = palette.vibrantSwatch
                            ?: palette.dominantSwatch
                            ?: palette.lightVibrantSwatch
                            ?: palette.darkVibrantSwatch
                            ?: palette.mutedSwatch
                            ?: palette.swatches.maxByOrNull { it.population }

                        val darkSwatch = palette.darkMutedSwatch
                            ?: palette.darkVibrantSwatch
                            ?: palette.mutedSwatch
                            ?: palette.dominantSwatch

                        val secondarySwatch = palette.lightVibrantSwatch?.takeIf { it != swatch }
                            ?: palette.mutedSwatch?.takeIf { it != swatch }
                            ?: palette.dominantSwatch?.takeIf { it != swatch }
                            ?: palette.darkVibrantSwatch?.takeIf { it != swatch }
                            ?: palette.lightMutedSwatch?.takeIf { it != swatch }
                            ?: palette.swatches.firstOrNull { it != swatch }
                            ?: swatch

                        if (swatch != null) {
                            val rgb = swatch.rgb
                            colorCache[coverId] = rgb
                            if (playbackController.currentTrack.value?.id == track.id) {
                                _dynamicSeedColor.value = Color(rgb)
                            }
                        }

                        if (secondarySwatch != null) {
                            val rgbSec = secondarySwatch.rgb
                            secondaryColorCache[coverId] = rgbSec
                            if (playbackController.currentTrack.value?.id == track.id) {
                                _dynamicSecondaryColor.value = Color(rgbSec)
                            }
                        }

                        if (darkSwatch != null) {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(darkSwatch.rgb, hsv)
                            hsv[1] = (hsv[1] * 0.70f).coerceIn(0.20f, 0.60f)
                            hsv[2] = 0.09f
                            val darkRgb = android.graphics.Color.HSVToColor(hsv)
                            darkColorCache[coverId] = darkRgb
                            if (playbackController.currentTrack.value?.id == track.id) {
                                _dynamicDarkBgColor.value = Color(darkRgb)
                            }
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
