package dev.matejgroombridge.habittracker.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import dev.matejgroombridge.habittracker.data.settings.ReminderSettings
import dev.matejgroombridge.habittracker.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules per-day reminder alarms. Reminders are evenly spaced between
 * [ReminderSettings.firstTime] and [ReminderSettings.lastTime] when
 * `timesPerDay > 1`; otherwise only the first time fires.
 *
 * Implementation notes:
 *  - Uses `AlarmManager.setAndAllowWhileIdle` so reminders still fire when
 *    the device is in Doze. Exact alarms aren't required for our cadence.
 *  - Each "slot" (0..timesPerDay-1) gets its own PendingIntent + request
 *    code so they can be cancelled and re-scheduled independently.
 *  - The receiver re-schedules itself for "tomorrow" after firing, so the
 *    pattern repeats forever without us needing to use the deprecated
 *    inexact-repeating alarms.
 */
object ReminderScheduler {

    private const val MAX_SLOTS = 6
    private const val REQUEST_CODE_BASE = 7_700_000

    /**
     * Cancel any existing alarms and re-schedule based on the user's current
     * settings. Call this:
     *  - From [dev.matejgroombridge.habittracker.HabitTrackerApp.onCreate].
     *  - When the user changes any reminder setting (the SettingsViewModel
     *    helper [scheduleAfterSettingsChange] does this).
     *  - From [ReminderReceiver] when [Intent.ACTION_BOOT_COMPLETED] fires.
     */
    suspend fun rescheduleAll(context: Context) {
        // Always cancel everything first so disabling reminders fully clears.
        for (slot in 0 until MAX_SLOTS) cancelSlot(context, slot)

        val settings = SettingsRepository(context).settings.first().reminders
        if (!settings.enabled) return

        for (slot in 0 until settings.timesPerDay.coerceIn(1, MAX_SLOTS)) {
            scheduleSlot(context, slot, daysAhead = 0, settings = settings)
        }
    }

    /**
     * Schedule a single slot. [daysAhead] is added to today; pass 0 from
     * [rescheduleAll] (we'll skip slots already in the past), or 1 from the
     * receiver's daily re-arm path.
     */
    fun scheduleSlot(
        context: Context,
        slot: Int,
        daysAhead: Long,
        settings: ReminderSettings? = null,
    ) {
        // Resolve settings synchronously — caller already provides them on
        // the rescheduleAll path; the receiver path needs to re-read them.
        val resolved = settings ?: kotlinx.coroutines.runBlocking {
            SettingsRepository(context).settings.first().reminders
        }
        if (!resolved.enabled) return

        val time = computeTimeForSlot(resolved, slot) ?: return
        val now = LocalDateTime.now()
        var fireAt = LocalDate.now().plusDays(daysAhead).atTime(time)
        if (fireAt.isBefore(now)) {
            // Slot already passed today — push to tomorrow.
            fireAt = fireAt.plusDays(1)
        }

        val triggerAtMillis = fireAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val pi = pendingIntentForSchedule(context, slot)
        val mgr = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        // Try the most accurate scheduling API the OS will let us use:
        //   * setExactAndAllowWhileIdle (≥ M, gated by canScheduleExactAlarms on ≥ S),
        //   * setAndAllowWhileIdle (≥ M, no gating, ±~9 min on Doze),
        //   * set (legacy fallback).
        // Wrapped in runCatching so a SecurityException (revoked at runtime)
        // gracefully degrades to inexact instead of crashing the receiver.
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canUseExactAlarms(mgr) -> {
                    mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
                else -> mgr.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }.onFailure {
            // Fallback: inexact alarm — better late than never.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                mgr.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }
    }

    private fun cancelSlot(context: Context, slot: Int) {
        val pi = pendingIntent(context, slot, create = false) ?: return
        val mgr = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        mgr.cancel(pi)
        pi.cancel()
    }

    /**
     * Returns a PendingIntent for [slot]. When [create] is false this returns
     * `null` if no PendingIntent currently exists (used for cancellation —
     * cancelling a never-created intent is a no-op).
     */
    private fun pendingIntent(context: Context, slot: Int, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_SLOT, slot)
            // Action ensures different slots produce distinct PendingIntents
            // even though Android also disambiguates by request code.
            action = "ACTION_REMIND_SLOT_$slot"
        }
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_NO_CREATE) or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE_BASE + slot, intent, flags)
    }

    /** Like [pendingIntent] but never returns null — used in scheduleSlot. */
    private fun pendingIntentForSchedule(context: Context, slot: Int): PendingIntent {
        return pendingIntent(context, slot, create = true)
            ?: error("PendingIntent.getBroadcast returned null with FLAG_UPDATE_CURRENT")
    }

    /**
     * Whether the OS will currently let us schedule exact alarms.
     *  * < S: Always allowed (no permission needed).
     *  * S/T: Requires SCHEDULE_EXACT_ALARM (user-managed) or USE_EXACT_ALARM.
     *  * U+: USE_EXACT_ALARM is fine; we declare it in the manifest.
     */
    private fun canUseExactAlarms(mgr: AlarmManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return mgr.canScheduleExactAlarms()
    }

    private fun computeTimeForSlot(settings: ReminderSettings, slot: Int): LocalTime? {
        val first = parseTime(settings.firstTime) ?: return null
        if (settings.timesPerDay <= 1) return first
        val last = parseTime(settings.lastTime) ?: return first
        if (last <= first) return first
        // Linear spacing: slot 0 → first, slot N-1 → last.
        val firstMin = first.hour * 60 + first.minute
        val lastMin = last.hour * 60 + last.minute
        val step = (lastMin - firstMin) / (settings.timesPerDay - 1)
        val totalMin = firstMin + step * slot
        return LocalTime.of((totalMin / 60).coerceIn(0, 23), (totalMin % 60).coerceIn(0, 59))
    }

    private fun parseTime(raw: String): LocalTime? = runCatching { LocalTime.parse(raw) }.getOrNull()
}
