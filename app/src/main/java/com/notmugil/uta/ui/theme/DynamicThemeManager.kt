package com.notmugil.uta.ui.theme

import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.notmugil.uta.domain.model.TrackItem
import com.notmugil.uta.player.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

val LocalDynamicThemeManager = staticCompositionLocalOf<DynamicThemeManager?> {
    null
}

@Singleton
class DynamicThemeManager @Inject constructor(
    private val playbackController: PlaybackController,
    private val bitmapLoader: CoverArtBitmapLoader,
    private val colorExtractor: AlbumColorExtractor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = LruCache<String, AlbumColors>(32)

    private val _dynamicColors = MutableStateFlow<AlbumColors?>(null)
    val dynamicColors: StateFlow<AlbumColors?> = _dynamicColors.asStateFlow()

    private val _dynamicSeedColor = MutableStateFlow<Color?>(null)
    val dynamicSeedColor: StateFlow<Color?> = _dynamicSeedColor.asStateFlow()

    private val _dynamicSecondaryColor = MutableStateFlow<Color?>(null)
    val dynamicSecondaryColor: StateFlow<Color?> = _dynamicSecondaryColor.asStateFlow()

    private val _dynamicDarkBgColor = MutableStateFlow<Color?>(null)
    val dynamicDarkBgColor: StateFlow<Color?> = _dynamicDarkBgColor.asStateFlow()

    init {
        scope.launch {
            playbackController.currentTrack
                .map { track ->
                    val key = track?.coverArtId?.takeIf { it.isNotBlank() }
                        ?: track?.albumId?.takeIf { it.isNotBlank() }
                        ?: track?.id
                    key to track
                }
                .distinctUntilChangedBy { it.first }
                .collectLatest { (key, track) ->
                    if (track == null || key == null) {
                        updateColors(null)
                        return@collectLatest
                    }

                    val cached = cache.get(key)
                    if (cached != null) {
                        updateColors(cached)
                        return@collectLatest
                    }

                    val extracted = try {
                        bitmapLoader.loadCoverBitmap(track)?.use { bitmap ->
                            colorExtractor.extractColors(bitmap)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "[DynamicTheme] Extraction failed for ${track.title}")
                        null
                    }

                    if (extracted != null) {
                        cache.put(key, extracted)
                    }
                    updateColors(extracted)
                }
        }
    }

    private fun updateColors(colors: AlbumColors?) {
        _dynamicColors.value = colors
        _dynamicSeedColor.value = colors?.seed
        _dynamicSecondaryColor.value = colors?.accent ?: colors?.seed
        _dynamicDarkBgColor.value = colors?.darkBg
    }

    fun clearCache() {
        cache.evictAll()
    }
}
