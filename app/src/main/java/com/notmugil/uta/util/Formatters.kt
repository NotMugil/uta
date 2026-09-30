package com.notmugil.uta.util

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
}
