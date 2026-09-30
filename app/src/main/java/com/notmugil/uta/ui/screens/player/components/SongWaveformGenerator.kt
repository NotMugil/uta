package com.notmugil.uta.ui.screens.player.components

import android.content.Context
import android.util.LruCache
import com.notmugil.uta.player.AudioWaveformExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

object SongWaveformGenerator {
    private const val DEFAULT_SAMPLE_COUNT = 256
    private val memoryCache = LruCache<String, FloatArray>(200)

    fun computeSeed(songKey: String): Long {
        var h = -3750763034362895579L
        for (c in songKey) {
            h = (h xor c.code.toLong()) * 1099511628211L
        }
        return h
    }

    fun processEnergyProfile(
        rawPeaks: FloatArray,
        rawRms: FloatArray? = null,
        sampleCount: Int = DEFAULT_SAMPLE_COUNT
    ): FloatArray {
        val count = rawPeaks.size
        if (count == 0) return FloatArray(sampleCount) { 0.08f }

        val combined = FloatArray(count)
        for (i in 0 until count) {
            val peak = rawPeaks[i].coerceAtLeast(0f)
            val rms = rawRms?.getOrNull(i)?.coerceAtLeast(0f) ?: peak
            // Prioritize peaks over RMS for strong transients and exaggerated contrast
            combined[i] = 0.75f * peak + 0.25f * rms
        }

        val dbValues = FloatArray(count)
        val minDb = -48f
        for (i in 0 until count) {
            val amp = combined[i].coerceIn(0.0001f, 1.0f)
            val db = 20f * log10(amp)
            dbValues[i] = ((db - minDb) / (-minDb)).coerceIn(0f, 1f)
        }

        // Contrast expansion: power > 1.0 exaggerates peaks and plunges valleys low
        val expanded = FloatArray(count)
        for (i in 0 until count) {
            expanded[i] = dbValues[i].toDouble().pow(1.55).toFloat()
        }

        val smoothed = FloatArray(count)
        for (i in 0 until count) {
            val prev = if (i > 0) expanded[i - 1] else expanded[i]
            val curr = expanded[i]
            val next = if (i < count - 1) expanded[i + 1] else expanded[i]
            val avg = 0.15f * prev + 0.70f * curr + 0.15f * next
            // Peak preservation keeps sharp hits distinct without flattening them
            smoothed[i] = max(avg, curr * 0.95f)
        }

        var minVal = Float.MAX_VALUE
        var maxVal = 0.0001f
        for (v in smoothed) {
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }
        if (minVal == Float.MAX_VALUE) minVal = 0f
        val range = (maxVal - minVal).coerceAtLeast(0.001f)

        val result = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val srcIdx = ((i.toFloat() / (sampleCount - 1).coerceAtLeast(1).toFloat()) * (count - 1)).toInt().coerceIn(0, count - 1)
            val norm = ((smoothed[srcIdx] - minVal) / range).coerceIn(0f, 1f)
            // Baseline sits low at 0.05f with full headroom up to 1.0f
            result[i] = (0.05f + norm * 0.95f).coerceIn(0.05f, 1.0f)
        }

        return result
    }

    fun generateProceduralProfile(songKey: String, sampleCount: Int = DEFAULT_SAMPLE_COUNT): FloatArray {
        val seed = computeSeed(songKey)
        val rawPeaks = FloatArray(sampleCount)
        val rawRms = FloatArray(sampleCount)

        var state = seed
        fun nextFloat(): Float {
            state += -7046029254386353131L
            var z = state
            z = (z xor (z ushr 30)) * -4658895280553007687L
            z = (z xor (z ushr 27)) * -7723592144701657875L
            z = z xor (z ushr 31)
            val u = (z ushr 40).toInt() and 0xFFFFFF
            return u / 16777216f
        }

        // 6 Distinct Song Archetypes
        val archetype = (abs(seed % 6L)).toInt()

        val f1 = 4f + nextFloat() * 8f
        val f2 = 14f + nextFloat() * 16f
        val f3 = 32f + nextFloat() * 28f
        val f4 = 64f + nextFloat() * 48f

        val p1 = nextFloat() * (2 * PI.toFloat())
        val p2 = nextFloat() * (2 * PI.toFloat())
        val p3 = nextFloat() * (2 * PI.toFloat())
        val p4 = nextFloat() * (2 * PI.toFloat())

        val beatCadence = when (archetype) {
            1 -> 16f + (nextFloat() * 16f).toInt() // Fast EDM pulse
            3 -> 8f + (nextFloat() * 12f).toInt()  // Heavy hip-hop trap beat
            else -> 10f + (nextFloat() * 20f).toInt()
        }

        for (i in 0 until sampleCount) {
            val normX = i.toFloat() / (sampleCount - 1).toFloat()

            // Calculate macro musical structure based on archetype
            val macroEnergy = when (archetype) {
                0 -> {
                    // Verse-Chorus Structure: Low verses (0.15), Explosive Choruses (0.95), Deep bridge valley (0.05)
                    when {
                        normX < 0.10f -> 0.12f + normX * 1.5f
                        normX < 0.28f -> 0.22f + 0.12f * sin(normX * 24f) // Verse 1
                        normX < 0.44f -> 0.88f + 0.12f * sin(normX * 16f) // Chorus 1 (Peak)
                        normX < 0.60f -> 0.25f + 0.10f * cos(normX * 20f) // Verse 2
                        normX < 0.72f -> 0.94f + 0.06f * sin(normX * 18f) // Chorus 2 (Peak)
                        normX < 0.82f -> 0.08f + 0.08f * sin(normX * 12f) // Bridge breakdown (Deep Valley)
                        normX < 0.94f -> 1.00f                             // Grand Final Climax
                        else -> ((1f - normX) / 0.06f).coerceIn(0f, 1f) * 0.4f // Outro
                    }
                }
                1 -> {
                    // EDM Build & Heavy Drops: Deep valleys, accelerated risers, massive drops
                    when {
                        normX < 0.15f -> 0.08f + 0.05f * sin(normX * 10f) // Ambient intro
                        normX < 0.25f -> 0.15f + ((normX - 0.15f) / 0.10f).pow(2.0f) * 0.75f // Build 1
                        normX < 0.46f -> 0.98f + 0.02f * sin(normX * 32f) // DROP 1 (Peak)
                        normX < 0.62f -> 0.06f + 0.06f * cos(normX * 14f) // Deep Breakdown Valley
                        normX < 0.74f -> 0.10f + ((normX - 0.62f) / 0.12f).pow(2.0f) * 0.85f // Build 2
                        normX < 0.92f -> 1.00f                             // MEGA DROP 2 (Peak)
                        else -> ((1f - normX) / 0.08f).coerceIn(0f, 1f) * 0.2f
                    }
                }
                2 -> {
                    // Progressive Crescendo: Whispering start, undulating rising climb to mountain peak at 80%
                    when {
                        normX < 0.82f -> {
                            val baseProgress = (normX / 0.82f).pow(1.8f)
                            (0.06f + baseProgress * 0.94f) + 0.08f * sin(normX * 28f + p1)
                        }
                        normX < 0.88f -> 0.95f + 0.05f * sin(normX * 30f) // Climax Summit
                        else -> ((1f - normX) / 0.12f).coerceIn(0f, 1f) * 0.15f // Sudden fade
                    }
                }
                3 -> {
                    // Staccato / Trap / Rhythmic: Punchy transient beat spikes, sharp valleys, mid breakdown
                    when {
                        normX in 0.48f..0.58f -> 0.08f + 0.06f * sin(normX * 16f) // Halftime breakdown valley
                        else -> 0.35f + 0.25f * sin(normX * 12f + p2)
                    }
                }
                4 -> {
                    // Dual Peak Ballad: Emotional acoustic first half, deep interlude valley, soaring orchestral climax
                    when {
                        normX < 0.12f -> 0.08f + normX * 0.8f
                        normX < 0.38f -> 0.75f + 0.15f * sin(normX * 14f) // First peak
                        normX < 0.60f -> 0.12f + 0.08f * cos(normX * 16f) // Soft interlude valley
                        normX < 0.88f -> 0.96f + 0.04f * sin(normX * 20f) // Full band climax
                        else -> ((1f - normX) / 0.12f).coerceIn(0f, 1f) * 0.12f
                    }
                }
                else -> {
                    // Driving Rock: High energy baseline, punchy riffs, sharp verse valleys
                    when {
                        normX < 0.12f -> 0.65f + 0.25f * sin(normX * 20f) // Intro riff
                        normX < 0.32f -> 0.28f + 0.12f * cos(normX * 24f) // Verse 1
                        normX < 0.52f -> 0.92f + 0.08f * sin(normX * 22f) // Chorus 1
                        normX < 0.68f -> 0.30f + 0.10f * cos(normX * 24f) // Verse 2
                        normX < 0.88f -> 0.98f + 0.02f * sin(normX * 26f) // Guitar Solo & Chorus (Peak)
                        else -> ((1f - normX) / 0.12f).coerceIn(0f, 1f) * 0.35f
                    }
                }
            }

            // Harmonic textures
            val h1 = sin(normX * f1 * PI.toFloat() + p1)
            val h2 = sin(normX * f2 * PI.toFloat() + p2)
            val h3 = sin(normX * f3 * PI.toFloat() + p3)
            val h4 = sin(normX * f4 * PI.toFloat() + p4)
            val combinedHarmonics = (h1 * 0.4f + h2 * 0.3f + h3 * 0.2f + h4 * 0.1f) * 0.15f

            // Rhythmic pulse
            val beat = abs(sin(normX * beatCadence * PI.toFloat())).pow(3.0f) * 0.30f

            // Micro transients (kick / snare spikes)
            val isBeatSpike = (i % (sampleCount / 32).coerceAtLeast(2) == 0)
            val transientSpike = if (isBeatSpike && nextFloat() > 0.35f) 0.35f + nextFloat() * 0.30f else 0f

            val baseEnergy = (macroEnergy + combinedHarmonics + beat * macroEnergy).coerceIn(0.01f, 1.0f)
            rawPeaks[i] = (baseEnergy + transientSpike).coerceIn(0.01f, 1.0f)
            rawRms[i] = baseEnergy.coerceIn(0.01f, 1.0f)
        }

        return processEnergyProfile(rawPeaks, rawRms, sampleCount)
    }

    fun getCachedOrGenerate(context: Context, songKey: String, sampleCount: Int = DEFAULT_SAMPLE_COUNT): FloatArray {
        synchronized(memoryCache) {
            val cached = memoryCache.get(songKey)
            if (cached != null && cached.size == sampleCount) return cached
        }

        val diskFile = getDiskCacheFile(context, songKey)
        val fromDisk = loadFromDisk(diskFile, sampleCount)
        if (fromDisk != null) {
            synchronized(memoryCache) {
                memoryCache.put(songKey, fromDisk)
            }
            return fromDisk
        }

        val generated = generateProceduralProfile(songKey, sampleCount)
        synchronized(memoryCache) {
            memoryCache.put(songKey, generated)
        }
        saveToDisk(diskFile, generated)
        return generated
    }

    suspend fun loadWaveform(
        context: Context,
        songKey: String,
        sampleCount: Int = DEFAULT_SAMPLE_COUNT,
        localAudioFile: File? = null
    ): FloatArray = withContext(Dispatchers.IO) {
        synchronized(memoryCache) {
            val cached = memoryCache.get(songKey)
            if (cached != null && cached.size == sampleCount) return@withContext cached
        }

        val diskFile = getDiskCacheFile(context, songKey)
        val fromDisk = loadFromDisk(diskFile, sampleCount)
        if (fromDisk != null) {
            synchronized(memoryCache) {
                memoryCache.put(songKey, fromDisk)
            }
            return@withContext fromDisk
        }

        if (localAudioFile != null && localAudioFile.exists() && localAudioFile.length() > 1024) {
            val extracted = AudioWaveformExtractor.extractFromLocalFile(localAudioFile, sampleCount)
            if (extracted != null) {
                synchronized(memoryCache) {
                    memoryCache.put(songKey, extracted)
                }
                saveToDisk(diskFile, extracted)
                return@withContext extracted
            }
        }

        val generated = generateProceduralProfile(songKey, sampleCount)
        synchronized(memoryCache) {
            memoryCache.put(songKey, generated)
        }
        saveToDisk(diskFile, generated)
        generated
    }

    private fun getDiskCacheFile(context: Context, songKey: String): File {
        val safeKey = songKey.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val dir = File(context.cacheDir, "waveforms_v2").apply { if (!exists()) mkdirs() }
        return File(dir, "$safeKey.bin")
    }

    private fun saveToDisk(file: File, data: FloatArray) {
        try {
            file.parentFile?.mkdirs()
            val buffer = ByteBuffer.allocate(data.size * 4)
            for (v in data) {
                buffer.putFloat(v)
            }
            FileOutputStream(file).use { out ->
                out.write(buffer.array())
                out.flush()
            }
        } catch (e: Exception) {
            Timber.w(e, "[SongWaveformGenerator] Failed to save waveform to disk")
        }
    }

    private fun loadFromDisk(file: File, expectedCount: Int): FloatArray? {
        if (!file.exists() || file.length() < expectedCount * 4L) return null
        return try {
            val bytes = file.readBytes()
            if (bytes.size < expectedCount * 4) return null
            val buffer = ByteBuffer.wrap(bytes)
            val result = FloatArray(expectedCount)
            for (i in 0 until expectedCount) {
                result[i] = buffer.float
            }
            result
        } catch (e: Exception) {
            Timber.w(e, "[SongWaveformGenerator] Failed to load waveform from disk")
            null
        }
    }
}
