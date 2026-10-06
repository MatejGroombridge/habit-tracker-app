package dev.matejgroombridge.habittracker.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.matejgroombridge.habittracker.EXTRA_OPEN_TAB
import dev.matejgroombridge.habittracker.MainActivity
import dev.matejgroombridge.habittracker.R
import dev.matejgroombridge.habittracker.TAB_STATS

/** Channel + notification helpers, isolated from the scheduler logic. */
object Notifications {

    const val CHANNEL_ID = "daily_reminders"
    const val CHANNEL_NAME = "Daily reminders"
    private const val NOTIFICATION_ID_BASE = 9_000_000

    /**
     * Creates the daily-reminder notification channel if it doesn't already
     * exist. Safe to call multiple times.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Reminders to complete your habits."
        }
        mgr.createNotificationChannel(channel)
    }

    /**
     * Posts a single reminder notification. [slot] (0..N-1) is used to give
     * each per-day reminder a unique notification ID so multiple notifications
     * can sit in the shade rather than overwriting each other.
     */
    fun postReminder(context: Context, title: String, body: String, slot: Int) {
        ensureChannel(context)
        val mgr = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, slot, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        mgr.notify(NOTIFICATION_ID_BASE + slot, notification)
    }

    const val RECAP_CHANNEL_ID = "monthly_recap"
    private const val RECAP_NOTIFICATION_ID = 9_100_000

    /** Own channel so the recap can be silenced without losing daily reminders. */
    private fun ensureRecapChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        if (mgr.getNotificationChannel(RECAP_CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                RECAP_CHANNEL_ID,
                "Monthly recap",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "A summary of last month's habits on the 1st." },
        )
    }

    /** Posts the monthly recap; tapping it opens the Stats tab. */
    fun postRecap(context: Context, title: String, body: String) {
        ensureRecapChannel(context)
        val mgr = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_TAB, TAB_STATS)
        }
        val pi = PendingIntent.getActivity(
            context, RECAP_NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, RECAP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        mgr.notify(RECAP_NOTIFICATION_ID, notification)
    }
}
