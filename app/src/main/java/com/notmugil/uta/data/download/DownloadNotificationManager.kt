package com.notmugil.uta.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.notmugil.uta.MainActivity
import com.notmugil.uta.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_PROGRESS_ID = "uta_downloads_progress"
        private const val CHANNEL_PROGRESS_NAME = "Downloads in Progress"
        const val CHANNEL_COMPLETED_ID = "uta_downloads_complete"
        private const val CHANNEL_COMPLETED_NAME = "Download Completed"
        const val NOTIFICATION_ID_PROGRESS = 3001
        const val NOTIFICATION_ID_COMPLETED = 3002
    }

    private var isInitialized = false

    private fun ensureChannels() {
        if (isInitialized) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                CHANNEL_PROGRESS_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for active music downloads"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETED_ID,
                CHANNEL_COMPLETED_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows summary notification when music downloads complete"
                setShowBadge(true)
            }
            manager?.createNotificationChannel(progressChannel)
            manager?.createNotificationChannel(completedChannel)
        }
        isInitialized = true
    }

    fun showAggregateProgress(
        completedCount: Int,
        totalCount: Int,
        activeTitles: List<String>,
        progress: Float
    ) {
        ensureChannels()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (totalCount > 1) {
            val currentDisplayIndex = (completedCount + 1).coerceAtMost(totalCount)
            "Downloading ($currentDisplayIndex/$totalCount)"
        } else {
            "Downloading Music"
        }

        val text = when {
            activeTitles.isEmpty() -> "Preparing download..."
            activeTitles.size == 1 -> activeTitles.first()
            else -> "${activeTitles.size} tracks downloading (${activeTitles.joinToString(", ")})"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (totalCount > 1) {
            val maxProgress = totalCount * 100
            val currentProgress = (completedCount * 100 + (progress.coerceIn(0f, 1f) * 100).toInt()).coerceIn(0, maxProgress)
            builder.setProgress(maxProgress, currentProgress, false)
        } else if (progress > 0f) {
            builder.setProgress(100, (progress.coerceIn(0f, 1f) * 100).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }

        try {
            manager.notify(NOTIFICATION_ID_PROGRESS, builder.build())
        } catch (_: Exception) {}
    }

    fun showSummaryCompleted(completedCount: Int, failedCount: Int = 0) {
        ensureChannels()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        hideProgress()

        if (completedCount <= 0 && failedCount <= 0) return

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when {
            failedCount > 0 && completedCount > 0 -> "Downloads finished with issues"
            failedCount > 0 -> "Downloads failed"
            else -> "Downloads complete"
        }

        val text = when {
            completedCount > 0 && failedCount > 0 -> "$completedCount songs downloaded, $failedCount failed"
            completedCount == 1 -> "1 song downloaded for offline listening"
            completedCount > 1 -> "$completedCount songs downloaded for offline listening"
            failedCount == 1 -> "1 download failed"
            else -> "$failedCount downloads failed"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETED_ID)
            .setSmallIcon(if (failedCount > 0 && completedCount == 0) R.drawable.ic_download else R.drawable.ic_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOngoing(false)
            .setTimeoutAfter(6000L)

        try {
            manager.notify(NOTIFICATION_ID_COMPLETED, builder.build())
        } catch (_: Exception) {}
    }

    fun hideProgress() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            manager.cancel(NOTIFICATION_ID_PROGRESS)
        } catch (_: Exception) {}
    }

    fun hideAll() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            manager.cancel(NOTIFICATION_ID_PROGRESS)
            manager.cancel(NOTIFICATION_ID_COMPLETED)
        } catch (_: Exception) {}
    }
}
