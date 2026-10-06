package dev.matejgroombridge.habittracker.data.stats

import dev.matejgroombridge.habittracker.data.model.Habit
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** What an [Insight] is about; the UI picks its icon from this. */
enum class InsightKind {
    NeverMissTwice, NearBest, BestStreak, BlockingPerfect, Slipping,
    WeakWeekday, Keystone, TooMany, Graduated, Forming,
}

/**
 * One piece of feedback for the Stats tab. Every insight ends in something
 * the user can do about it — a number with no next step is just a number.
 *
 * @param habitId The habit it's about, if one — the card takes its colour.
 */
data class Insight(
    val kind: InsightKind,
    val title: String,
    val body: String,
    val habitId: String? = null,
)

/**
 * Rule-based insights, most urgent first. Each rule returns at most one
 * insight and has a minimum-data bar, so a thin history yields fewer cards
 * rather than confident-sounding noise.
 */
internal object Insights {

    fun build(
        active: List<Habit>,
        stats: List<HabitStatsSummary>,
        grid: StatusGrid,
        days: List<DaySummary>,
        consistency: Consistency,
        today: Long,
    ): List<Insight> = listOfNotNull(
        neverMissTwice(active, grid, today),
        streaks(stats),
        blockingPerfectDays(active, days, today),
        slipping(active, grid, today),
        weakWeekday(stats),
        keystone(active, grid, today),
        tooMany(stats, consistency),
        graduated(active, grid, today),
        forming(stats),
    )

    /** Missing once is noise; missing twice is the start of a new pattern. */
    private fun neverMissTwice(active: List<Habit>, grid: StatusGrid, today: Long): Insight? {
        val missed = active.filter {
            grid.status(it, today - 1) == DayStatus.Missed && grid.status(it, today) == DayStatus.Pending
        }
        if (missed.isEmpty()) return null
        val names = joinNames(missed.map { it.name })
        return Insight(
            kind = InsightKind.NeverMissTwice,
            title = "Don't miss twice",
            body = "You missed $names yesterday. One miss is nothing — getting back to it " +
                "today is what keeps it from becoming a new pattern.",
            habitId = missed.singleOrNull()?.id,
        )
    }

    private fun streaks(stats: List<HabitStatsSummary>): Insight? {
        stats.filter { it.currentStreak >= 5 && it.currentStreak == it.bestStreak }
            .maxByOrNull { it.currentStreak }
            ?.let {
                return Insight(
                    kind = InsightKind.BestStreak,
                    title = "${it.habit.name}: best run ever",
                    body = "${it.currentStreak} days and counting — the longest you've kept it " +
                        "going. Protect it today.",
                    habitId = it.habit.id,
                )
            }
        return stats
            .filter { it.currentStreak >= 3 && it.bestStreak - it.currentStreak in 1..3 }
            .minByOrNull { it.bestStreak - it.currentStreak }
            ?.let {
                val gap = it.bestStreak - it.currentStreak
                Insight(
                    kind = InsightKind.NearBest,
                    title = "${it.habit.name}: $gap ${days(gap)} from your best",
                    body = "You're on ${it.currentStreak} days; your record is ${it.bestStreak}. " +
                        "Keep it up and you'll set a new one.",
                    habitId = it.habit.id,
                )
            }
    }

    /** The habit that most often stood alone between you and a perfect day. */
    private fun blockingPerfectDays(active: List<Habit>, days: List<DaySummary>, today: Long): Insight? {
        val counts = days
            .filter { it.day > today - HabitStats.WINDOW_DAYS && it.isNearPerfect }
            .groupingBy { it.missedHabitIds.single() }
            .eachCount()
        val (id, count) = counts.maxByOrNull { it.value } ?: return null
        if (count < 2) return null
        val habit = active.firstOrNull { it.id == id } ?: return null
        return Insight(
            kind = InsightKind.BlockingPerfect,
            title = "${habit.name} is costing you perfect days",
            body = "It was the only habit missing on $count days in the last 30 — every one " +
                "of those would have been a perfect day. Try doing it first.",
            habitId = habit.id,
        )
    }

    /** A recent drop against the habit's own baseline, caught early. */
    private fun slipping(active: List<Habit>, grid: StatusGrid, today: Long): Insight? {
        return active.mapNotNull { h ->
            val recent = grid.rate(h, today - 13, today)
            val before = grid.rate(h, today - 73, today - 14)
            val r = recent.fraction ?: return@mapNotNull null
            val b = before.fraction ?: return@mapNotNull null
            if (recent.total < 10 || before.total < 30 || b < 0.6f || b - r < 0.2f) null
            else Triple(h, r, b)
        }.maxByOrNull { it.third - it.second }?.let { (h, r, b) ->
            Insight(
                kind = InsightKind.Slipping,
                title = "${h.name} is slipping",
                body = "${pct(r)} over the last 2 weeks, down from ${pct(b)} before. " +
                    "A smaller version or a fixed time of day can stop the slide.",
                habitId = h.id,
            )
        }
    }

    private fun weakWeekday(stats: List<HabitStatsSummary>): Insight? {
        data class Weak(val s: HabitStatsSummary, val day: DayOfWeek, val rate: Float, val others: Float)
        return stats.mapNotNull { s ->
            val (day, rate) = s.weekdays.entries
                .filter { it.value.total >= 4 }
                .minByOrNull { it.value.fraction ?: 1f } ?: return@mapNotNull null
            val others = s.weekdays.filterKeys { it != day }.values
                .fold(Rate(0, 0)) { acc, r -> Rate(acc.done + r.done, acc.total + r.total) }
            val r = rate.fraction ?: return@mapNotNull null
            val o = others.fraction ?: return@mapNotNull null
            if (r > 0.5f || o - r < 0.3f) null else Weak(s, day, r, o)
        }.maxByOrNull { it.others - it.rate }?.let { w ->
            val dayName = w.day.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Insight(
                kind = InsightKind.WeakWeekday,
                title = "${w.s.habit.name} falls off on ${dayName}s",
                body = "${pct(w.rate)} on ${dayName}s vs ${pct(w.others)} on other days " +
                    "(last 12 weeks). Give ${dayName}s a set time, or plan a lighter version.",
                habitId = w.s.habit.id,
            )
        }
    }

    /** A habit whose completion lifts everything else — worth protecting. */
    private fun keystone(active: List<Habit>, grid: StatusGrid, today: Long): Insight? {
        if (active.size < 3) return null
        val from = today - 89
        return active.mapNotNull { k ->
            var doneDays = 0
            var missedDays = 0
            var whenDone = Rate(0, 0)
            var whenMissed = Rate(0, 0)
            for (day in from..today) {
                val others = active.filter { it.id != k.id }.map { grid.status(it, day) }
                val rate = Rate(
                    others.count { it == DayStatus.Done },
                    others.count { it == DayStatus.Done || it == DayStatus.Missed },
                )
                when (grid.status(k, day)) {
                    DayStatus.Done -> { doneDays++; whenDone = whenDone + rate }
                    DayStatus.Missed -> { missedDays++; whenMissed = whenMissed + rate }
                    else -> Unit
                }
            }
            val d = whenDone.fraction ?: return@mapNotNull null
            val m = whenMissed.fraction ?: return@mapNotNull null
            if (doneDays < 7 || missedDays < 7 || d - m < 0.2f) null else Triple(k, d, m)
        }.maxByOrNull { it.second - it.third }?.let { (k, d, m) ->
            Insight(
                kind = InsightKind.Keystone,
                title = "${k.name} carries the rest",
                body = "On days you do it you finish ${pct(d)} of your other habits, vs ${pct(m)} " +
                    "on days you don't. Make it the one you never skip.",
                habitId = k.id,
            )
        }
    }

    private fun tooMany(stats: List<HabitStatsSummary>, consistency: Consistency): Insight? {
        val tracked = stats.filterNot { it.habit.isPaused }
        val overall = consistency.last30.fraction ?: return null
        if (tracked.size < 6 || overall >= 0.6f) return null
        val weakest = tracked
            .filter { it.last30.total >= 14 }
            .minByOrNull { it.last30.fraction ?: 1f } ?: return null
        val w = weakest.last30.fraction ?: return null
        if (w >= 0.4f) return null
        return Insight(
            kind = InsightKind.TooMany,
            title = "A lot on your plate",
            body = "You're tracking ${tracked.size} daily habits and finishing ${pct(overall)}. " +
                "Pausing ${weakest.habit.name} (${pct(w)}) could free up focus for the rest.",
            habitId = weakest.habit.id,
        )
    }

    /** Consistently near-perfect for two months: it's automatic now. */
    private fun graduated(active: List<Habit>, grid: StatusGrid, today: Long): Insight? {
        return active.mapNotNull { h ->
            val r = grid.rate(h, today - 59, today)
            val f = r.fraction ?: return@mapNotNull null
            if (r.total < 50 || f < 0.95f) null else h to f
        }.maxByOrNull { it.second }?.let { (h, f) ->
            Insight(
                kind = InsightKind.Graduated,
                title = "${h.name} is automatic",
                body = "${pct(f)} for 60 days straight. Archive it to make room, or raise the " +
                    "bar so it keeps challenging you.",
                habitId = h.id,
            )
        }
    }

    private fun forming(stats: List<HabitStatsSummary>): Insight? {
        val s = stats.filter { (it.formationDay ?: 0) >= 3 }.minByOrNull { it.formationDay!! } ?: return null
        return Insight(
            kind = InsightKind.Forming,
            title = "${s.habit.name}: day ${s.formationDay} of ~${HabitStats.FORMATION_DAYS}",
            body = "New habits take about ${HabitStats.FORMATION_DAYS} days on average to become " +
                "automatic. The early days are the hardest — keep showing up.",
            habitId = s.habit.id,
        )
    }

    private operator fun Rate.plus(other: Rate) = Rate(done + other.done, total + other.total)

    private fun days(n: Int) = if (n == 1) "day" else "days"

    private fun joinNames(names: List<String>): String = when (names.size) {
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
}

/** "84%" */
fun pct(fraction: Float): String = "${(fraction * 100).roundToInt()}%"
