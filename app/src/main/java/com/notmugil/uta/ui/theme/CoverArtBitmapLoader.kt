package com.notmugil.uta.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.toBitmap
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.SubsonicSession
import com.notmugil.uta.data.download.OfflineDownloadManager
import com.notmugil.uta.domain.model.TrackItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

class LoadedBitmap(
    val bitmap: Bitmap,
    private val shouldRecycle: Boolean
) : AutoCloseable {
    override fun close() {
        if (shouldRecycle && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    inline fun <R> use(block: (Bitmap) -> R): R = try {
        block(bitmap)
    } finally {
        close()
    }
}

@Singleton
class CoverArtBitmapLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subsonicRepository: SubsonicRepository
) {
    suspend fun loadCoverBitmap(track: TrackItem, targetSize: Int = 128): LoadedBitmap? {
        return try {
            val localFile = OfflineDownloadManager.getLocalCoverArtFile(context, track.coverArtId)
                ?: OfflineDownloadManager.getLocalCoverArtFile(context, track.albumId)

            if (localFile != null && localFile.exists()) {
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(localFile.absolutePath, boundsOptions)
                val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, targetSize, targetSize)
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeFile(localFile.absolutePath, decodeOptions)
                if (decoded != null && !decoded.isRecycled) {
                    return LoadedBitmap(decoded, shouldRecycle = true)
                }
            }

            if (!SubsonicSession.isOfflineModeActive) {
                val targetCoverId = track.coverArtId ?: track.albumId
                if (!targetCoverId.isNullOrBlank()) {
                    val url = subsonicRepository.getCoverArtUrl(targetCoverId, size = targetSize)
                        ?: SubsonicSession.client?.getCoverArtUrl(targetCoverId, size = targetSize.toString())
                    if (url != null) {
                        val loader = SingletonImageLoader.get(context)
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .size(targetSize)
                            .allowHardware(false)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build()
                        val result = loader.execute(request)
                        if (result is SuccessResult) {
                            val bitmap = try {
                                result.image.toBitmap()
                            } catch (_: Exception) {
                                (result.image as? BitmapDrawable)?.bitmap
                            }
                            if (bitmap != null && !bitmap.isRecycled) {
                                return LoadedBitmap(bitmap, shouldRecycle = false)
                            }
                        }
                    }
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "[CoverArtBitmapLoader] Failed to load cover bitmap for ${track.title}")
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
