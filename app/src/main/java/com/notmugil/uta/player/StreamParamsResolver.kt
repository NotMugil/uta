package com.notmugil.uta.player

import com.notmugil.uta.data.preferences.StreamingBitrate
import com.notmugil.uta.data.preferences.TranscodingFormat

data class StreamParams(
    val format: String?,
    val maxBitRate: Int?
)

object StreamParamsResolver {

    /**
     * Resolves the query parameters (`format` and `maxBitRate`) to send to Subsonic `/rest/stream`.
     *
     * @param bitrateSetting User's streaming bitrate preference (AUTO, 128, 192, 256, 320, UNLIMITED/No Transcoding)
     * @param formatSetting User's transcoding format preference (RAW/Server Default, OPUS, MP3, AAC, FLAC)
     * @param sourceSuffix Source track's file extension/suffix if known (e.g. "opus", "flac", "mp3", "m4a")
     * @param sourceBitRate Source track's original bitrate in kbps if known
     */
    fun resolve(
        bitrateSetting: StreamingBitrate,
        formatSetting: TranscodingFormat,
        sourceSuffix: String? = null,
        sourceBitRate: Int? = null
    ): StreamParams {
        // 1. "No transcoding" explicitly requested -> format=raw and no bitrate cap
        if (bitrateSetting == StreamingBitrate.UNLIMITED) {
            return StreamParams(format = "raw", maxBitRate = null)
        }

        // 2. Format parameter resolution
        val requestedFormat = when (formatSetting) {
            TranscodingFormat.RAW -> null // Server default (omitted)
            TranscodingFormat.OPUS -> "opus"
            TranscodingFormat.MP3 -> "mp3"
            TranscodingFormat.AAC -> "aac"
            TranscodingFormat.FLAC -> "flac"
        }

        // 3. FLAC is lossless -> no bitrate cap
        if (formatSetting == TranscodingFormat.FLAC) {
            return StreamParams(format = requestedFormat, maxBitRate = null)
        }

        // 4. Source format comparison
        val normalizedSourceSuffix = sourceSuffix?.lowercase()?.trimStart('.')
        val isSameFormat = when {
            requestedFormat == null -> false
            requestedFormat == "mp3" && normalizedSourceSuffix == "mp3" -> true
            requestedFormat == "opus" && (normalizedSourceSuffix == "opus" || normalizedSourceSuffix == "ogg") -> true
            requestedFormat == "aac" && (normalizedSourceSuffix == "aac" || normalizedSourceSuffix == "m4a" || normalizedSourceSuffix == "mp4") -> true
            requestedFormat == "flac" && normalizedSourceSuffix == "flac" -> true
            else -> false
        }

        // 5. Bitrate cap resolution
        val requestedKbps = bitrateSetting.kbps // 0 for AUTO, > 0 for 128/192/256/320

        val effectiveMaxBitRate: Int? = when {
            // Explicit bitrate cap selected (128, 192, 256, 320)
            requestedKbps > 0 -> {
                if (sourceBitRate != null && sourceBitRate > 0 && sourceBitRate < requestedKbps) {
                    // Do not ask server to transcode to a higher bitrate than source
                    sourceBitRate
                } else {
                    requestedKbps
                }
            }
            // Auto bitrate with explicit lossy transcoding format -> provide sensible high-quality cap so server triggers transcoding
            requestedFormat != null -> {
                when (requestedFormat) {
                    "mp3" -> if (sourceBitRate != null && sourceBitRate in 1..320) sourceBitRate else 320
                    "aac" -> if (sourceBitRate != null && sourceBitRate in 1..256) sourceBitRate else 256
                    "opus" -> if (sourceBitRate != null && sourceBitRate in 1..192) sourceBitRate else 192
                    else -> null
                }
            }
            // Auto bitrate and Server Default format -> direct play / server decides
            else -> null
        }

        // If the source already matches the requested format and source bitrate <= effective cap, direct play
        if (isSameFormat && sourceBitRate != null && effectiveMaxBitRate != null && sourceBitRate <= effectiveMaxBitRate) {
            return StreamParams(format = null, maxBitRate = null)
        }

        return StreamParams(format = requestedFormat, maxBitRate = effectiveMaxBitRate)
    }
}
