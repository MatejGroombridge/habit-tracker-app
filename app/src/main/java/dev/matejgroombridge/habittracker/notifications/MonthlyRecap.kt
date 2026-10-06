package dev.matejgroombridge.habittracker.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import dev.matejgroombridge.habittracker.data.repository.HabitRepository
import dev.matejgroombridge.habittracker.data.settings.SettingsRepository
import dev.matejgroombridge.habittracker.data.stats.HabitStats
import dev.matejgroombridge.habittracker.data.stats.MonthlyRecap
import dev.matejgroombridge.habittracker.data.stats.pct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Schedules the monthly recap: one alarm, for the 1st of next month at the
 * user's first reminder time. Like daily reminders it's a one-shot alarm
 * that [MonthlyRecapReceiver] re-arms after firing, and it's restored on
 * boot via [ReminderScheduler.rescheduleAll].
 */
object MonthlyRecapScheduler {

    private const val REQUEST_CODE = 7_800_000
    private const val ACTION = "ACTION_MONTHLY_RECAP"
    private val FALLBACK_TIME: LocalTime = LocalTime.of(9, 0)

    suspend fun reschedule(context: Context) {
        val mgr = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        pendingIntent(context, create = false)?.let { mgr.cancel(it); it.cancel() }

        val settings = SettingsRepository(context).settings.first()
        if (!settings.monthlyRecap) return

        val time = runCatching { LocalTime.parse(settings.reminders.firstTime) }
            .getOrDefault(FALLBACK_TIME)
        var fireAt = LocalDate.now().withDayOfMonth(1).atTime(time)
        if (!fireAt.isAfter(LocalDateTime.now())) fireAt = fireAt.plusMonths(1)
        val triggerAtMillis = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // A monthly summary doesn't need to land to the minute, so an
        // inexact Doze-friendly alarm is plenty.
        val pi = pendingIntent(context, create = true) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            mgr.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun pendingIntent(context: Context, create: Boolean): PendingIntent? {
        val intent = Intent(context, MonthlyRecapReceiver::class.java).apply { action = ACTION }
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}

/** Posts last month's recap when the alarm fires, then arms next month's. */
class MonthlyRecapReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                postRecap(appContext)
                MonthlyRecapScheduler.reschedule(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun postRecap(context: Context) {
        if (!SettingsRepository(context).settings.first().monthlyRecap) return
        val today = LocalDate.now()
        val habits = HabitRepository(context).habits.first()
        val recap = HabitStats.monthlyRecap(habits, YearMonth.from(today).minusMonths(1), today.toEpochDay())
            ?: return // Nothing tracked last month — nothing to say.
        val (title, body) = recapMessage(recap)
        Notifications.postRecap(context, title, body)
    }
}

/**
 * Title and body for [recap], e.g. "September recap" /
 * "12 perfect days · 84% consistency, up 6 pts on August. Best: Run at 97%."
 */
internal fun recapMessage(recap: MonthlyRecap): Pair<String, String> {
    val monthName = recap.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val prevName = recap.month.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val rate = recap.rate.fraction ?: 0f

    val trend = recap.previousRate?.fraction?.let { prev ->
        val delta = ((rate - prev) * 100).roundToInt()
        when {
            delta > 0 -> ", up $delta pts on $prevName"
            delta < 0 -> ", down ${-delta} pts on $prevName"
            else -> ", level with $prevName"
        }
    }.orEmpty()

    val body = buildString {
        append("${recap.perfectDays} perfect ${if (recap.perfectDays == 1) "day" else "days"}")
        append(" · ${pct(rate)} consistency$trend.")
        val best = recap.bestHabitRate?.fraction
        if (recap.bestHabitName != null && best != null) append(" Best: ${recap.bestHabitName} at ${pct(best)}.")
        if (recap.mostImprovedName != null) append(" Most improved: ${recap.mostImprovedName}.")
    }
    return "$monthName recap" to body
}
