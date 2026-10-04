package com.notmugil.uta.util

import com.notmugil.uta.domain.model.TrackItem
import androidx.media3.common.Format
import java.util.Locale

object Formatters {

    fun formatDurationSeconds(seconds: Long): String {
        if (seconds <= 0L) return "0:00"
        val totalSecs = seconds
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return if (mins >= 60) {
            val hours = mins / 60
            val remainingMins = mins % 60
            String.format(Locale.US, "%d:%02d:%02d", hours, remainingMins, secs)
        } else {
            String.format(Locale.US, "%d:%02d", mins, secs)
        }
    }

    fun formatDurationMs(ms: Long): String {
        if (ms <= 0L) return "0:00"
        return formatDurationSeconds(ms / 1000L)
    }

    fun formatHumanDuration(seconds: Long): String {
        if (seconds <= 0L) return "0 min"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "$hours hr $minutes min"
            hours > 0 -> "$hours hr"
            minutes > 0 -> "$minutes min"
            else -> "${seconds}s"
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatTrackAudioStats(
        track: TrackItem?,
        audioFormat: Format? = null,
        requestedBitrate: Int? = null
    ): String? {
        if (track == null && audioFormat == null) return null

        val rawSuffix = track?.suffix?.trim()?.trimStart('.')?.uppercase(Locale.US)
        val mimeType = audioFormat?.sampleMimeType ?: audioFormat?.containerMimeType

        val activeCodec = when {
            mimeType != null -> when {
                mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "MP3"
                mimeType.contains("flac", ignoreCase = true) -> "FLAC"
                mimeType.contains("opus", ignoreCase = true) -> "OPUS"
                mimeType.contains("mp4a", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> "AAC"
                mimeType.contains("vorbis", ignoreCase = true) || mimeType.contains("ogg", ignoreCase = true) -> "OGG"
                mimeType.contains("wav", ignoreCase = true) || mimeType.contains("raw", ignoreCase = true) -> "WAV"
                mimeType.contains("alac", ignoreCase = true) -> "ALAC"
                mimeType.contains("ac3", ignoreCase = true) -> "AC3"
                mimeType.contains("eac3", ignoreCase = true) -> "E-AC3"
                else -> mimeType.substringAfterLast("/").removePrefix("x-").uppercase(Locale.US)
            }
            !rawSuffix.isNullOrBlank() -> rawSuffix
            else -> null
        }

        val isDifferentCodec = rawSuffix != null && activeCodec != null &&
            !rawSuffix.equals(activeCodec, ignoreCase = true) &&
            !(rawSuffix.equals("OGG", ignoreCase = true) && activeCodec == "OPUS") &&
            !(rawSuffix.equals("M4A", ignoreCase = true) && activeCodec == "AAC")

        val codecLabel = if (isDifferentCodec) {
            "$rawSuffix → $activeCodec"
        } else {
            activeCodec ?: rawSuffix
        }

        val rawBitrate = when {
            audioFormat != null && audioFormat.bitrate > 0 -> (audioFormat.bitrate + 500) / 1000
            requestedBitrate != null && requestedBitrate > 0 -> requestedBitrate
            track?.bitRate != null && track.bitRate > 0 -> track.bitRate
            else -> null
        }
        val bitrate = if (rawBitrate != null && rawBitrate > 0) "${rawBitrate}kbps" else null

        val sampleRate = audioFormat?.sampleRate
        val sampleRateStr = if (sampleRate != null && sampleRate > 0) {
            val khz = sampleRate / 1000.0
            when {
                sampleRate % 1000 == 0 -> "${sampleRate / 1000}kHz"
                sampleRate % 100 == 0 -> String.format(Locale.US, "%.1fkHz", khz)
                else -> String.format(Locale.US, "%.2fkHz", khz)
            }
        } else null

        val parts = listOfNotNull(codecLabel, bitrate, sampleRateStr).filter { it.isNotBlank() }
        return if (parts.isEmpty()) null else parts.joinToString(" • ")
    }
}
