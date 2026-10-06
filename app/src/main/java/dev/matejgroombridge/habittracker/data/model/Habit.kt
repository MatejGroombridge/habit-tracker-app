package dev.matejgroombridge.habittracker.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A single habit the user is tracking.
 *
 * Schema notes:
 *  - The repository decodes JSON with `ignoreUnknownKeys = true`, and all new
 *    fields below have defaults, so older stored habits will load cleanly and
 *    pick up the defaults (Daily / first color / default icon / not archived).
 *  - The set of completed days is the source of truth — frequency only affects
 *    presentation (see [isVisuallyCompletedOn]).
 *
 * @param id                Stable identifier. Generated once on creation.
 * @param name              User-supplied name (e.g. "Drink water").
 * @param description       Optional free-text description.
 * @param iconKey           Key into `HabitIcons.catalog`. Falls back to default if unknown.
 * @param colorKey          Key into `HabitColors.palette`. Falls back to first colour if unknown.
 * @param frequency         How often the habit is expected to be completed.
 * @param archived          When true, the habit is hidden from the main grid
 *                          and shown only on the Archived screen.
 * @param createdAtEpochDay The day the habit was created, as `LocalDate.toEpochDay()`.
 * @param completedDays     Set of days (epoch-day values) on which the habit was completed.
 */
@Serializable
data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val iconKey: String = DEFAULT_ICON_KEY,
    val colorKey: String = DEFAULT_COLOR_KEY,
    val frequency: HabitFrequency = HabitFrequency.Daily,
    val archived: Boolean = false,
    val createdAtEpochDay: Long,
    val completedDays: Set<Long> = emptySet(),
    /**
     * How many "skips" the user is allowed per week before a missed day
     * counts against their streak. 0 = no skips allowed (legacy behaviour).
     * Stored on the habit so different habits can have different
     * tolerances ("workout: 1 skip", "meditate: 0 skips", etc.).
     */
    val skipsPerWeek: Int = 0,
    /** Days the user has explicitly marked as a "skip" (not missed, not done). */
    val skippedDays: Set<Long> = emptySet(),
    /**
     * When non-null, the habit is paused and shouldn't count for streaks
     * or the analytics grid. Value is the epoch-day on which the pause
     * began; `null` = active. Pauses that have ended live in [pausedRanges].
     */
    val pausedSinceEpochDay: Long? = null,
    /**
     * Pauses that have since been resumed, so the paused stretch keeps
     * reading as paused (rather than as missed days) after the habit
     * restarts. Also records the time a restored habit spent archived.
     */
    val pausedRanges: List<DayRange> = emptyList(),
    /**
     * Whether this habit appears in the daily reminder notification.
     * Defaults to true so legacy habits keep their existing behaviour.
     * Toggle off for habits the user already remembers without prompting,
     * or doesn't want to be nagged about.
     */
    val includeInReminders: Boolean = true,
    /**
     * The day the habit was archived, or `null` if it isn't — or if it was
     * archived before this was recorded. Stats stop counting the habit from
     * this day on, so archiving doesn't rewrite past days.
     */
    val archivedAtEpochDay: Long? = null,
    /**
     * The first day of real tracking when the history before it was
     * backfilled (see [HabitBackfill]), or `null` when nothing was. Stats
     * start here so they're never computed over synthetic days.
     */
    val trackedSinceEpochDay: Long? = null,
) {
    fun isCompletedOn(epochDay: Long): Boolean = epochDay in completedDays
    fun isSkippedOn(epochDay: Long): Boolean = epochDay in skippedDays
    val isPaused: Boolean get() = pausedSinceEpochDay != null

    /** Whether [epochDay] falls in the current pause or any earlier one. */
    fun isPausedOn(epochDay: Long): Boolean {
        val since = pausedSinceEpochDay
        if (since != null && epochDay >= since) return true
        return pausedRanges.any { epochDay in it }
    }

    /** First day of real (non-backfilled) history. */
    val realStartEpochDay: Long get() = trackedSinceEpochDay ?: createdAtEpochDay

    /** Due every day — the only habits stats and perfect days consider. */
    val isDaily: Boolean
        get() = when (val f = frequency) {
            HabitFrequency.Daily -> true
            is HabitFrequency.EveryNDays -> f.days == 1
            else -> false
        }

    /**
     * Returns a copy paused from [todayEpochDay], or resumed — in which case
     * the finished pause is kept in [pausedRanges]. Today itself is active
     * again on resume, so a pause started and ended on the same day leaves
     * no trace.
     */
    fun withPaused(paused: Boolean, todayEpochDay: Long): Habit {
        val since = pausedSinceEpochDay
        return when {
            paused -> if (since != null) this else copy(pausedSinceEpochDay = todayEpochDay)
            since == null -> this
            else -> copy(
                pausedSinceEpochDay = null,
                pausedRanges = pausedRanges.plusRange(since, todayEpochDay - 1),
            )
        }
    }

    /**
     * Returns a copy archived on [todayEpochDay], or restored. A restored
     * habit's archived stretch is recorded as a pause so it doesn't come
     * back as a run of missed days.
     */
    fun withArchived(archived: Boolean, todayEpochDay: Long): Habit {
        if (archived) {
            return if (this.archived) this
            else copy(archived = true, archivedAtEpochDay = todayEpochDay)
        }
        if (!this.archived) return this
        val since = archivedAtEpochDay
        return copy(
            archived = false,
            archivedAtEpochDay = null,
            pausedRanges = if (since == null) pausedRanges
            else pausedRanges.plusRange(since, todayEpochDay - 1),
        )
    }

    /**
     * Whether [epochDay] neither extends nor breaks a streak: paused or
     * skipped days are the user explicitly setting the habit aside.
     */
    private fun isNeutralOn(epochDay: Long): Boolean =
        epochDay in skippedDays || isPausedOn(epochDay)

    /** Earliest day any streak could include. */
    private val streakFloor: Long
        get() = minOf(createdAtEpochDay, completedDays.minOrNull() ?: createdAtEpochDay)

    /** Returns a copy with [epochDay] toggled in [completedDays]. */
    fun toggleCompletion(epochDay: Long): Habit {
        val next = completedDays.toMutableSet().apply {
            if (!add(epochDay)) remove(epochDay)
        }
        return copy(completedDays = next)
    }

    /** Returns a copy that is completed on [epochDay] (idempotent). */
    fun markCompleted(epochDay: Long): Habit =
        if (epochDay in completedDays) this else copy(completedDays = completedDays + epochDay)

    /** Returns a copy that is not completed on [epochDay] (idempotent). */
    fun markNotCompleted(epochDay: Long): Habit =
        if (epochDay !in completedDays) this else copy(completedDays = completedDays - epochDay)

    /**
     * Completed days in the run ending on [today], or 0. Paused and skipped
     * days are stepped over without counting, so a pause freezes the streak
     * rather than resetting it.
     */
    fun currentStreak(today: Long): Long {
        var streak = 0L
        var day = today
        val floor = streakFloor
        while (day >= floor) {
            when {
                day in completedDays -> streak++
                // Today is still in progress: not yet completing it hasn't
                // broken the streak — the day isn't missed until it's over.
                day == today || isNeutralOn(day) -> Unit
                else -> break
            }
            day--
        }
        return streak
    }

    /**
     * The most completed days in any single run, with paused and skipped
     * days bridging rather than breaking it (see [currentStreak]). Used as
     * the "personal best" alongside the current streak.
     */
    fun longestStreak(today: Long): Long {
        var best = 0L
        var run = 0L
        for (day in streakFloor..today) {
            when {
                day in completedDays -> {
                    run++
                    if (run > best) best = run
                }
                day == today || isNeutralOn(day) -> Unit
                else -> run = 0L
            }
        }
        return best
    }

    /**
     * Whether the habit should *visually* render as completed on [day].
     *
     * Daily habits are completed iff [day] is in [completedDays]. For weekly
     * and "every N days" habits, completing the habit early counts for the
     * remainder of its current window — so a habit you knock out on Monday
     * for a 3-day cadence will keep its checked styling on Tuesday and
     * Wednesday too.
     */
    fun isVisuallyCompletedOn(
        day: Long,
        weekStart: java.time.DayOfWeek = java.time.DayOfWeek.MONDAY,
    ): Boolean {
        // Skipped or paused days behave the same as completed in the
        // "should this card look done?" sense — the user has explicitly
        // resolved the habit for the day, so it shouldn't sit there as
        // an outstanding TODO. The All-Time grid still shows them as
        // skip-circles / pause-cells (those use isCompletedOn directly).
        if (day in skippedDays) return true
        if (isPausedOn(day)) return true
        if (day in completedDays) return true
        val window = when (val f = frequency) {
            HabitFrequency.Daily -> return false
            HabitFrequency.Weekly -> 7
            is HabitFrequency.EveryNDays -> f.days
            is HabitFrequency.TimesPerWeek -> {
                // "Done for the week" once we've hit the target — check the
                // user-week (start day comes from settings) containing `day`.
                val date = java.time.LocalDate.ofEpochDay(day)
                val daysSinceStart = ((date.dayOfWeek.value - weekStart.value) % 7 + 7) % 7
                val startEpoch = day - daysSinceStart
                val endEpoch = startEpoch + 6
                val countThisWeek = completedDays.count { it in startEpoch..endEpoch }
                return countThisWeek >= f.times
            }
        }
        if (window <= 1) return false
        // Find the most recent completion strictly before `day` and check
        // whether it falls inside the same window.
        val mostRecent = completedDays.filter { it < day }.maxOrNull() ?: return false
        return (day - mostRecent) < window
    }

    /**
     * URL that, when scanned from an NFC tag, will complete this habit. Behaviour
     * (background / overlay / open app) is controlled by [dev.matejgroombridge.habittracker.data.settings.NfcAction].
     *
     * Built using [DEEP_LINK_SCHEME] + [DEEP_LINK_HOST] + this habit's [id].
     * Suitable for writing to an NFC tag with any tag-writer app.
     */
    val nfcUrl: String get() = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/complete/$id"

    companion object {
        const val DEFAULT_ICON_KEY = "check_circle"
        const val DEFAULT_COLOR_KEY = "blush"

        // App-private deep-link scheme; matches the intent-filter declared in
        // AndroidManifest.xml. Avoids using `https` so writing/scanning stays
        // entirely within this app and never opens a browser.
        const val DEEP_LINK_SCHEME = "habittracker"
        const val DEEP_LINK_HOST = "habit"
    }
}

/** An inclusive run of epoch days, e.g. a finished pause. */
@Serializable
data class DayRange(val start: Long, val end: Long) {
    operator fun contains(epochDay: Long): Boolean = epochDay in start..end
}

/** Appends `start..end`, ignoring it when empty (e.g. paused and resumed the same day). */
private fun List<DayRange>.plusRange(start: Long, end: Long): List<DayRange> =
    if (end < start) this else this + DayRange(start, end)
