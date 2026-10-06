package dev.matejgroombridge.habittracker.data.stats

import dev.matejgroombridge.habittracker.data.model.Habit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything the Stats tab, the Today screen's perfect-day line and the
 * monthly recap need, computed in one pass from the habit list.
 *
 * Ground rules, applied everywhere below:
 *  - **Daily habits only.** Weekly / every-N / N-per-week habits have no
 *    per-day expectation, so they're left out entirely.
 *  - **Real history only.** Days before [Habit.realStartEpochDay] were
 *    backfilled, i.e. made up, and never count.
 *  - **Archived habits count up to the day they were archived**, so that
 *    archiving doesn't retroactively turn past days perfect.
 *  - **Paused and skipped days are neutral**: they neither count for nor
 *    against a habit — the same way they leave the Today card looking done.
 *  - **Today is in progress**: completing it counts, not completing it yet
 *    doesn't count against anything.
 */
object HabitStats {

    /** Rolling window for headline consistency and per-habit rates. */
    const val WINDOW_DAYS = 30

    /** Median days for a habit to become automatic (Lally et al., 2010). */
    const val FORMATION_DAYS = 66

    private const val WEEKS_SHOWN = 12
    private const val MONTHS_SHOWN = 6
    private const val PAIR_WINDOW_DAYS = 90
    private const val MAX_INSIGHTS = 5

    fun compute(habits: List<Habit>, today: Long): StatsSnapshot {
        val daily = habits.filter { it.isDaily && it.realStartEpochDay <= today }
        if (daily.isEmpty()) return StatsSnapshot.empty(today)

        val firstDay = daily.minOf { it.realStartEpochDay }
        val grid = StatusGrid(daily, firstDay, today)
        val days = (firstDay..today).map { grid.summarise(it) }
        val byDay = days.associateBy { it.day }

        val active = daily.filterNot { it.archived }
        val habitStats = active.map { habitStats(it, grid, active, today) }
        val perfect = perfectDayStats(days, today)
        val consistency = Consistency(
            last30 = poolRate(byDay, today - WINDOW_DAYS + 1, today),
            previous30 = poolRate(byDay, today - 2 * WINDOW_DAYS + 1, today - WINDOW_DAYS),
            weekly = (WEEKS_SHOWN - 1 downTo 0).map { weeksBack ->
                val end = today - 7L * weeksBack
                WeekRate(startEpochDay = end - 6, rate = poolRate(byDay, end - 6, end))
            },
        )

        return StatsSnapshot(
            today = today,
            firstEpochDay = firstDay,
            days = days,
            perfect = perfect,
            consistency = consistency,
            // Weakest first: the list is a to-do of where attention pays off.
            // Habits without enough history yet sink to the bottom.
            habits = habitStats.sortedWith(
                compareBy<HabitStatsSummary> { it.last30.fraction == null }
                    .thenBy { it.last30.fraction ?: 0f },
            ),
            insights = Insights.build(active, habitStats, grid, days, consistency, today)
                .take(MAX_INSIGHTS),
        )
    }

    /**
     * The previous calendar month's numbers for the recap notification, or
     * `null` if no daily habit was tracked during it.
     */
    fun monthlyRecap(habits: List<Habit>, month: YearMonth, today: Long): MonthlyRecap? {
        val start = month.atDay(1).toEpochDay()
        val end = minOf(month.atEndOfMonth().toEpochDay(), today)
        val daily = habits.filter { it.isDaily && it.realStartEpochDay <= end }
        if (daily.isEmpty() || start > today) return null

        val firstDay = minOf(start - 40, daily.minOf { it.realStartEpochDay })
        val grid = StatusGrid(daily, firstDay, today)
        val byDay = (firstDay..today).associateWith { grid.summarise(it) }
        val rate = poolRate(byDay, start, end)
        if (rate.total == 0) return null

        val prevMonth = month.minusMonths(1)
        val prevRate = poolRate(byDay, prevMonth.atDay(1).toEpochDay(), prevMonth.atEndOfMonth().toEpochDay())
        val perHabit = daily.map { h ->
            Triple(h, grid.rate(h, start, end), grid.rate(h, prevMonth.atDay(1).toEpochDay(), start - 1))
        }
        val best = perHabit
            .filter { it.second.total >= 10 }
            .maxByOrNull { it.second.fraction ?: 0f }
        // From the others, so the recap never names the same habit twice.
        val improved = perHabit
            .filter { it.first != best?.first && it.second.total >= 10 && it.third.total >= 10 }
            .maxByOrNull { (it.second.fraction ?: 0f) - (it.third.fraction ?: 0f) }
            ?.takeIf { (it.second.fraction ?: 0f) - (it.third.fraction ?: 0f) >= 0.1f }

        return MonthlyRecap(
            month = month,
            perfectDays = (start..end).count { byDay.getValue(it).isPerfect },
            daysInMonth = (end - start + 1).toInt(),
            rate = rate,
            previousRate = prevRate.takeIf { it.total > 0 },
            bestHabitName = best?.first?.name,
            bestHabitRate = best?.second,
            mostImprovedName = improved?.first?.name,
        )
    }

    private fun perfectDayStats(days: List<DaySummary>, today: Long): PerfectDayStats {
        val monthStart = LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay()
        var best = 0
        var run = 0
        for (d in days) {
            when {
                d.isPerfect -> { run++; best = maxOf(best, run) }
                // In-progress today and days nothing was due don't break a run.
                d.isNeutral || d.day == today -> Unit
                else -> run = 0
            }
        }
        val total = days.count { it.isPerfect }
        val todayPerfect = days.lastOrNull()?.let { it.day == today && it.isPerfect } == true
        return PerfectDayStats(
            total = total,
            thisMonth = days.count { it.day >= monthStart && it.isPerfect },
            currentRun = run,
            bestRun = best,
            nearPerfectLast30 = days.count { it.day > today - WINDOW_DAYS && it.isNearPerfect },
            todayNumber = if (todayPerfect) total else null,
        )
    }

    private fun habitStats(
        habit: Habit,
        grid: StatusGrid,
        active: List<Habit>,
        today: Long,
    ): HabitStatsSummary {
        val start = habit.realStartEpochDay
        var best = 0
        var run = 0
        // Miss runs: consecutive missed days with neutral days stepped over.
        // Only runs a completion closed count — an open one is still going.
        val missRuns = mutableListOf<Int>()
        var missRun = 0
        for (day in start..today) {
            when (grid.status(habit, day)) {
                DayStatus.Done -> {
                    run++
                    best = maxOf(best, run)
                    if (missRun > 0) missRuns += missRun
                    missRun = 0
                }
                DayStatus.Missed -> { run = 0; missRun++ }
                else -> Unit
            }
        }
        // Pending today and neutral days don't reset `run`, so it's the live streak.
        val current = run

        val weekdayWindowStart = maxOf(start, today - 7L * WEEKS_SHOWN + 1)
        val weekdays = DayOfWeek.entries.associateWith { dow ->
            grid.rate(habit, weekdayWindowStart, today) { LocalDate.ofEpochDay(it).dayOfWeek == dow }
        }
        val thisMonth = YearMonth.from(LocalDate.ofEpochDay(today))
        val monthly = (MONTHS_SHOWN - 1 downTo 0).map { back ->
            val m = thisMonth.minusMonths(back.toLong())
            MonthRate(m, grid.rate(habit, m.atDay(1).toEpochDay(), m.atEndOfMonth().toEpochDay()))
        }

        // "Goes well with": how much more often this habit gets done on days
        // another one does, vs days it doesn't.
        val pairStart = maxOf(start, today - PAIR_WINDOW_DAYS + 1)
        val pairs = active.filter { it.id != habit.id }.mapNotNull { other ->
            val whenDone = grid.rate(habit, pairStart, today) { grid.status(other, it) == DayStatus.Done }
            val whenMissed = grid.rate(habit, pairStart, today) { grid.status(other, it) == DayStatus.Missed }
            if (whenDone.total < 7 || whenMissed.total < 7) return@mapNotNull null
            val lift = (whenDone.fraction ?: 0f) - (whenMissed.fraction ?: 0f)
            if (lift < 0.15f) null else HabitPair(other.id, other.name, whenDone, whenMissed)
        }.sortedByDescending { it.lift }.take(2)

        // Formation progress is only meaningful for habits genuinely started
        // in-app; a backfilled one was established before tracking began.
        val age = (today - habit.createdAtEpochDay).toInt() + 1
        return HabitStatsSummary(
            habit = habit,
            last30 = grid.rate(habit, today - WINDOW_DAYS + 1, today),
            previous30 = grid.rate(habit, today - 2 * WINDOW_DAYS + 1, today - WINDOW_DAYS),
            allTime = grid.rate(habit, start, today),
            currentStreak = current,
            bestStreak = best,
            weekdays = weekdays,
            monthly = monthly,
            averageMissRun = missRuns.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            bouncedBackNextDay = missRuns.takeIf { it.isNotEmpty() }
                ?.let { runs -> Rate(runs.count { it == 1 }, runs.size) },
            pairs = pairs,
            formationDay = age.takeIf { habit.trackedSinceEpochDay == null && it < FORMATION_DAYS },
        )
    }

    private fun poolRate(byDay: Map<Long, DaySummary>, from: Long, to: Long): Rate {
        var done = 0
        var total = 0
        for (day in from..to) {
            val d = byDay[day] ?: continue
            done += d.done
            total += d.done + d.missed
        }
        return Rate(done, total)
    }
}

/** How a daily habit's day reads for stats. */
enum class DayStatus { Untracked, Done, Missed, Pending, Skipped, Paused }

/**
 * The status of every daily habit on every day from [firstDay] to [today],
 * worked out once and then looked up.
 */
internal class StatusGrid(
    private val habits: List<Habit>,
    private val firstDay: Long,
    private val today: Long,
) {
    private val rows: Map<String, Array<DayStatus>> = habits.associate { h ->
        h.id to Array((today - firstDay + 1).toInt()) { i -> statusOf(h, firstDay + i) }
    }

    fun status(habit: Habit, day: Long): DayStatus {
        if (day < firstDay || day > today) return DayStatus.Untracked
        return rows[habit.id]?.get((day - firstDay).toInt()) ?: DayStatus.Untracked
    }

    /** Completion rate over `from..to`, optionally only on days matching [filter]. */
    fun rate(habit: Habit, from: Long, to: Long, filter: (Long) -> Boolean = { true }): Rate {
        var done = 0
        var total = 0
        for (day in maxOf(from, firstDay)..minOf(to, today)) {
            if (!filter(day)) continue
            when (status(habit, day)) {
                DayStatus.Done -> { done++; total++ }
                DayStatus.Missed -> total++
                else -> Unit
            }
        }
        return Rate(done, total)
    }

    fun summarise(day: Long): DaySummary {
        var done = 0
        var missed = 0
        var pending = 0
        val missedIds = mutableListOf<String>()
        for (h in habits) {
            when (status(h, day)) {
                DayStatus.Done -> done++
                DayStatus.Missed -> { missed++; missedIds += h.id }
                DayStatus.Pending -> pending++
                else -> Unit
            }
        }
        return DaySummary(day, done, missed, pending, missedIds)
    }

    private fun statusOf(h: Habit, day: Long): DayStatus = when {
        day < h.realStartEpochDay || day > today -> DayStatus.Untracked
        day >= archivedFrom(h) -> DayStatus.Untracked
        h.isPausedOn(day) -> DayStatus.Paused
        day in h.completedDays -> DayStatus.Done
        day in h.skippedDays -> DayStatus.Skipped
        day == today -> DayStatus.Pending
        else -> DayStatus.Missed
    }

    /**
     * First day an archived habit stops counting. Habits archived before the
     * date was recorded are assumed to have been archived the day after
     * their last completion — the best signal there is of when they stopped.
     */
    private fun archivedFrom(h: Habit): Long = when {
        !h.archived -> Long.MAX_VALUE
        h.archivedAtEpochDay != null -> h.archivedAtEpochDay
        else -> (h.completedDays.maxOrNull() ?: h.realStartEpochDay) + 1
    }
}

/** `done` out of `total` counted days; [fraction] is null when nothing counted. */
data class Rate(val done: Int, val total: Int) {
    val fraction: Float? get() = if (total == 0) null else done.toFloat() / total
}

/** One day across all daily habits. */
data class DaySummary(
    val day: Long,
    val done: Int,
    val missed: Int,
    /** Today's habits not ticked off yet. Always 0 for past days. */
    val pending: Int,
    val missedHabitIds: List<String>,
) {
    /**
     * The confetti definition: every habit was done, skipped or paused —
     * plus at least one actually done, so a day with everything paused
     * isn't a "perfect" one.
     */
    val isPerfect: Boolean get() = missed == 0 && pending == 0 && done > 0

    /** All but one habit done: the days a single habit cost a perfect day. */
    val isNearPerfect: Boolean get() = missed == 1 && pending == 0 && done > 0

    /** Nothing was due (all paused / skipped / untracked). */
    val isNeutral: Boolean get() = done == 0 && missed == 0 && pending == 0

    /** Share of counted habits done; null when nothing counted. */
    val fraction: Float? get() = if (done + missed == 0) null else done.toFloat() / (done + missed)
}

data class PerfectDayStats(
    val total: Int,
    val thisMonth: Int,
    val currentRun: Int,
    val bestRun: Int,
    val nearPerfectLast30: Int,
    /** "Perfect day #N" when today is one, else null. */
    val todayNumber: Int?,
)

data class Consistency(
    val last30: Rate,
    val previous30: Rate,
    /** Rolling 7-day buckets, oldest first; the last one ends today. */
    val weekly: List<WeekRate>,
)

data class WeekRate(val startEpochDay: Long, val rate: Rate)

data class MonthRate(val month: YearMonth, val rate: Rate)

data class HabitPair(
    val otherId: String,
    val otherName: String,
    /** This habit's rate on days the other was done. */
    val whenOtherDone: Rate,
    /** This habit's rate on days the other was missed. */
    val whenOtherMissed: Rate,
) {
    val lift: Float get() = (whenOtherDone.fraction ?: 0f) - (whenOtherMissed.fraction ?: 0f)
}

data class HabitStatsSummary(
    val habit: Habit,
    val last30: Rate,
    val previous30: Rate,
    val allTime: Rate,
    /** Completed days in the current run (real history; pauses/skips bridge). */
    val currentStreak: Int,
    val bestStreak: Int,
    /** Last 12 weeks, by weekday. */
    val weekdays: Map<DayOfWeek, Rate>,
    /** Last 6 calendar months, oldest first. */
    val monthly: List<MonthRate>,
    /** Average length of a run of misses before the next completion. */
    val averageMissRun: Float?,
    /** Of the miss runs, how many were a single day ("never missed twice"). */
    val bouncedBackNextDay: Rate?,
    val pairs: List<HabitPair>,
    /** Day N of [HabitStats.FORMATION_DAYS] while still forming, else null. */
    val formationDay: Int?,
)

data class StatsSnapshot(
    val today: Long,
    val firstEpochDay: Long,
    /** One entry per day from [firstEpochDay] to [today]. */
    val days: List<DaySummary>,
    val perfect: PerfectDayStats,
    val consistency: Consistency,
    /** Active daily habits, weakest 30-day rate first. */
    val habits: List<HabitStatsSummary>,
    val insights: List<Insight>,
) {
    val hasData: Boolean get() = days.isNotEmpty()

    companion object {
        fun empty(today: Long) = StatsSnapshot(
            today = today,
            firstEpochDay = today,
            days = emptyList(),
            perfect = PerfectDayStats(0, 0, 0, 0, 0, null),
            consistency = Consistency(Rate(0, 0), Rate(0, 0), emptyList()),
            habits = emptyList(),
            insights = emptyList(),
        )
    }
}

data class MonthlyRecap(
    val month: YearMonth,
    val perfectDays: Int,
    val daysInMonth: Int,
    val rate: Rate,
    val previousRate: Rate?,
    val bestHabitName: String?,
    val bestHabitRate: Rate?,
    val mostImprovedName: String?,
)
