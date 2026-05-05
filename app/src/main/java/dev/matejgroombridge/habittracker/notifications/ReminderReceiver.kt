package dev.matejgroombridge.habittracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.matejgroombridge.habittracker.data.repository.HabitRepository
import dev.matejgroombridge.habittracker.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Fires when an alarm set by [ReminderScheduler] elapses. Posts a notification
 * summarising the habits the user still needs to complete today, and then
 * re-arms itself for the next day so the schedule keeps repeating.
 *
 * On boot we receive [Intent.ACTION_BOOT_COMPLETED] (registered separately
 * in the manifest) and call [ReminderScheduler.rescheduleAll] to restore
 * alarms that were lost when the device powered off.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ReminderScheduler.rescheduleAll(appContext)
                } finally {
                    pending.finish()
                }
            }
            return
        }

        val slot = intent.getIntExtra(EXTRA_SLOT, 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fireReminder(appContext, slot)
                // Re-arm for tomorrow so alarms keep firing daily even
                // without the app being opened. setAndAllowWhileIdle is
                // one-shot, so the receiver does the daily re-scheduling.
                ReminderScheduler.scheduleSlot(appContext, slot, daysAhead = 1)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fireReminder(context: Context, slot: Int) {
        val habits = HabitRepository(context).habits.first()
        val settings = SettingsRepository(context).settings.first()
        val today = LocalDate.now().toEpochDay()
        val weekStart = settings.weekStart.dayOfWeek

        // A habit is "outstanding" iff:
        //   * it isn't archived or paused,
        //   * it isn't visually completed on today (which honours
        //     Daily/Weekly/EveryNDays/TimesPerWeek frequencies),
        //   * the user hasn't explicitly skipped it for today,
        //   * the user hasn't opted it out of reminders.
        // The visually-completed check covers the "3 of 3 this week" case
        // so a fully-met TimesPerWeek habit doesn't trigger a notification
        // for the rest of the week.
        val outstanding = habits
            .filter { it.includeInReminders }
            .filterNot { it.archived || it.isPaused }
            .filterNot { it.isSkippedOn(today) }
            .filterNot { it.isVisuallyCompletedOn(today, weekStart) }
        if (outstanding.isEmpty()) return  // Don't nag if there's nothing left.

        val title = "Habits"
        val body = if (outstanding.size == 1) {
            "Don't forget: ${outstanding.first().name}"
        } else {
            "${outstanding.size} habits still to do today"
        }
        Notifications.postReminder(context, title, body, slot)
    }

    companion object {
        const val EXTRA_SLOT = "slot"
    }
}
