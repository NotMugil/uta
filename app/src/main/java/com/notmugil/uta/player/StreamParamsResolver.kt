package com.notmugil.uta.player

import com.notmugil.uta.data.preferences.StreamingBitrate
import com.notmugil.uta.data.preferences.TranscodingFormat

data class StreamParams(
    val format: String?,
    val maxBitRate: Int?
)

object StreamParamsResolver {

    private val LOSSLESS_EXTENSIONS = setOf(
        "flac", "wav", "wave", "aif", "aiff", "aifc", "alac", "ape", "wv", "dsf", "dff"
    )

    fun isLossless(sourceSuffix: String?, sourceBitRate: Int? = null): Boolean {
        val ext = sourceSuffix?.lowercase()?.trimStart('.') ?: return (sourceBitRate ?: 0) > 500
        if (LOSSLESS_EXTENSIONS.contains(ext)) return true
        return (sourceBitRate ?: 0) > 500
    }

    fun resolve(
        bitrateSetting: StreamingBitrate,
        formatSetting: TranscodingFormat,
        sourceSuffix: String? = null,
        sourceBitRate: Int? = null
    ): StreamParams {
        if (formatSetting == TranscodingFormat.RAW && bitrateSetting == StreamingBitrate.UNLIMITED) {
            return StreamParams(format = "raw", maxBitRate = null)
        }

        val requestedFormat = when (formatSetting) {
            TranscodingFormat.RAW -> null
            TranscodingFormat.OPUS -> "opus"
            TranscodingFormat.MP3 -> "mp3"
            TranscodingFormat.AAC -> "aac"
            TranscodingFormat.FLAC -> "flac"
        }

        if (formatSetting == TranscodingFormat.FLAC) {
            return StreamParams(format = requestedFormat, maxBitRate = null)
        }

        val normalizedSourceSuffix = sourceSuffix?.lowercase()?.trimStart('.')
        val isSameFormat = when {
            requestedFormat == null -> false
            requestedFormat == "mp3" && normalizedSourceSuffix == "mp3" -> true
            requestedFormat == "opus" && (normalizedSourceSuffix == "opus" || normalizedSourceSuffix == "ogg") -> true
            requestedFormat == "aac" && (normalizedSourceSuffix == "aac" || normalizedSourceSuffix == "m4a" || normalizedSourceSuffix == "mp4") -> true
            requestedFormat == "flac" && normalizedSourceSuffix == "flac" -> true
            else -> false
        }

        val requestedKbps = bitrateSetting.kbps

        val effectiveMaxBitRate: Int? = when {
            requestedKbps > 0 -> {
                if (sourceBitRate != null && sourceBitRate > 0 && sourceBitRate < requestedKbps) {
                    sourceBitRate
                } else {
                    requestedKbps
                }
            }
            requestedFormat != null -> {
                when (requestedFormat) {
                    "mp3" -> if (sourceBitRate != null && sourceBitRate in 1..320) sourceBitRate else 320
                    "aac" -> if (sourceBitRate != null && sourceBitRate in 1..256) sourceBitRate else 256
                    "opus" -> if (sourceBitRate != null && sourceBitRate in 1..192) sourceBitRate else 192
                    else -> null
                }
            }
            else -> null
        }

        if (isSameFormat && sourceBitRate != null && effectiveMaxBitRate != null && sourceBitRate <= effectiveMaxBitRate) {
            return StreamParams(format = null, maxBitRate = null)
        }

        return StreamParams(format = requestedFormat, maxBitRate = effectiveMaxBitRate)
    }
}
