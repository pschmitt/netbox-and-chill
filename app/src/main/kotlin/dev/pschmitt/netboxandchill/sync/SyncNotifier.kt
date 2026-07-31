package dev.pschmitt.netboxandchill.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pschmitt.netboxandchill.MainActivity
import dev.pschmitt.netboxandchill.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a system [android.app.Notification] when the *background* periodic/scheduled sync
 * ([SyncWorker]) fails with no retries left (NBC-23) - the gap NBC-17 slice 2 flagged: a background
 * `Worker` has no foreground `Activity` to show a `Snackbar` in, unlike the manual "Sync now" path
 * (`SettingsViewModel`/`SettingsScreen`), which already surfaces failures via Snackbar and is
 * unaffected by this.
 */
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
                .apply { description = "Alerts when a scheduled background sync fails" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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

    companion object {
        const val CHANNEL_ID = "background_sync"
        private const val NOTIFICATION_ID = 1001
    }
}
