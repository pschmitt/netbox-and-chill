package dev.pschmitt.netboxandchill.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pschmitt.netboxandchill.MainActivity
import dev.pschmitt.netboxandchill.R
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the progress and failure notifications for the background WorkManager sync. */
@Singleton
class SyncNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Creates the notification channel once, ahead of time - required on API 26+, cheap/no-op if it
     * already exists. Call from [dev.pschmitt.netboxandchill.NetBoxAndChillApp.onCreate].
     */
    fun createChannel() {
        val channel =
            NotificationChannel(
                    CHANNEL_ID,
                    "Background sync",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                .apply { description = "Shows background NetBox sync progress and failures" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Builds the foreground notification required for long-running WorkManager syncs. */
    fun foregroundInfo(message: String = "Syncing NetBox data…"): ForegroundInfo {
        val notification = progressNotification(message, step = 0, totalSteps = 1)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Best-effort: silently does nothing if POST_NOTIFICATIONS (API 33+) hasn't been granted - this
     * is a nice-to-have surface, not something worth crashing the worker over.
     */
    fun notifySyncFailed(message: String?) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val contentIntent =
            PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_sync_problem)
                .setContentTitle("Background sync failed")
                .setContentText(message ?: "Couldn't sync with NetBox - showing cached data")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message ?: "Couldn't sync with NetBox - showing cached data")
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** Shows an ongoing system-level progress notification while any sync attempt is running. */
    fun notifySyncStarted() {
        notifySyncProgress(SyncProgress("Refreshing cached data…", step = 0, totalSteps = 1))
    }

    fun notifySyncProgress(progress: SyncProgress) {
        if (!notificationsAllowed()) return
        NotificationManagerCompat.from(context)
            .notify(
                NOTIFICATION_ID,
                progressNotification(progress.message, progress.step, progress.totalSteps),
            )
    }

    /** Keeps the ongoing notification visible while WorkManager waits before retrying. */
    fun notifySyncRetry(attempt: Int) {
        if (!notificationsAllowed()) return
        NotificationManagerCompat.from(context)
            .notify(
                NOTIFICATION_ID,
                progressNotification("Retrying sync (attempt $attempt)…"),
            )
    }

    /** Removes the progress notification after a successful sync. */
    fun notifySyncSucceeded() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun progressNotification(message: String, step: Int? = null, totalSteps: Int? = null) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync_problem)
            // Keep the current stage in the title: Android may omit contentText in the collapsed
            // foreground notification, which otherwise leaves only the unhelpful generic label.
            .setContentTitle(message)
            .setContentText("Syncing NetBox data…")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message)
                    .setSummaryText("Syncing NetBox data…")
            )
            .apply {
                if (step != null && totalSteps != null) {
                    setProgress(totalSteps, step.coerceIn(0, totalSteps), false)
                    setSubText("Step ${step.coerceIn(0, totalSteps)} of $totalSteps")
                } else {
                    setProgress(0, 0, true)
                }
            }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        const val CHANNEL_ID = "background_sync"
        private const val NOTIFICATION_ID = 1001
    }
}
