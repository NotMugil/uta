package com.notmugil.uta.player

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.notmugil.uta.ui.screens.player.components.SongWaveformGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

object AudioWaveformExtractor {
    private const val TIMEOUT_US = 5000L
    private const val MAX_DECODE_TIME_MS = 5000L

    suspend fun extractFromLocalFile(
        file: File,
        sampleCount: Int = 256
    ): FloatArray? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 1024) return@withContext null

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || trackFormat == null) return@withContext null

            extractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val trackDurationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                trackFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            if (trackDurationUs <= 0L) return@withContext null

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val bucketDurationUs = (trackDurationUs / sampleCount).coerceAtLeast(1L)
            val bucketPeaks = FloatArray(sampleCount)
            val bucketSumSquares = DoubleArray(sampleCount)
            val bucketSampleCounts = LongArray(sampleCount)

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            val startTime = System.currentTimeMillis()

            while (!isOutputEOS && (System.currentTimeMillis() - startTime < MAX_DECODE_TIME_MS)) {
                if (!isInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }

                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuffer = outputBuffer.asShortBuffer()

                        val currentPresentationTimeUs = bufferInfo.presentationTimeUs
                        val bucketIdx = (currentPresentationTimeUs / bucketDurationUs).toInt().coerceIn(0, sampleCount - 1)

                        var sumSq = 0.0
                        var maxPeak = bucketPeaks[bucketIdx]
                        var count = 0
                        val step = 4
                        val numSamples = shortBuffer.remaining()
                        var p = 0
                        while (p < numSamples) {
                            val sampleVal = abs(shortBuffer.get(p) / 32768f)
                            if (sampleVal > maxPeak) {
                                maxPeak = sampleVal
                            }
                            sumSq += (sampleVal * sampleVal).toDouble()
                            count++
                            p += step
                        }

                        if (count > 0) {
                            bucketPeaks[bucketIdx] = maxPeak
                            bucketSumSquares[bucketIdx] += sumSq
                            bucketSampleCounts[bucketIdx] += count
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }

            val rawPeaks = FloatArray(sampleCount)
            val rawRms = FloatArray(sampleCount)
            for (i in 0 until sampleCount) {
                val count = bucketSampleCounts[i]
                rawPeaks[i] = bucketPeaks[i]
                rawRms[i] = if (count > 0) sqrt(bucketSumSquares[i] / count).toFloat() else bucketPeaks[i]
            }

            SongWaveformGenerator.processEnergyProfile(rawPeaks, rawRms, sampleCount)
        } catch (e: Exception) {
            Timber.w(e, "[AudioWaveformExtractor] Error extracting waveform from ${file.name}")
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
