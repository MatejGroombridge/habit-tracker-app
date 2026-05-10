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
     * began; `null` = active.
     */
    val pausedSinceEpochDay: Long? = null,
    /**
     * Whether this habit appears in the daily reminder notification.
     * Defaults to true so legacy habits keep their existing behaviour.
     * Toggle off for habits the user already remembers without prompting,
     * or doesn't want to be nagged about.
     */
    val includeInReminders: Boolean = true,
    /**
     * Inverse habits are for breaking bad habits. They start each day in a
     * visually-completed / successful state. If the user does the thing
     * they're trying to avoid, tapping the card records that day in
     * [completedDays] as an occurrence/failure, which makes the habit look
     * incomplete for that day.
     *
     * We intentionally reuse [completedDays] rather than adding another set:
     * for normal habits it means "completed", for inverse habits it means
     * "bad habit occurred". This keeps migration tiny and preserves the
     * existing JSON shape with a single new boolean defaulting to false.
     */
    val inverse: Boolean = false,
) {
    fun isCompletedOn(epochDay: Long): Boolean = epochDay in completedDays

    /** Whether this day has been resolved successfully for streak/stat purposes. */
    fun isSuccessfulOn(epochDay: Long): Boolean {
        if (epochDay < createdAtEpochDay) return false
        return if (inverse) epochDay !in completedDays else epochDay in completedDays
    }
    fun isSkippedOn(epochDay: Long): Boolean = epochDay in skippedDays
    val isPaused: Boolean get() = pausedSinceEpochDay != null

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

    /** Length of the longest streak of consecutive days ending on [today], or 0. */
    fun currentStreak(today: Long): Long {
        var streak = 0L
        var day = today
        while (day >= createdAtEpochDay && isSuccessfulOn(day)) {
            streak++
            day--
        }
        return streak
    }

    /**
     * The longest run of consecutive completed days this habit has ever had.
     * Used in the All Time analytics row as a "personal best" alongside the
     * current streak.
     *
     * Walks the sorted completion set once, so this is O(n log n) due to the
     * sort and O(n) thereafter. Empty set → 0.
     */
    fun longestStreak(): Long {
        if (!inverse) {
            if (completedDays.isEmpty()) return 0L
            val sorted = completedDays.sorted()
            var best = 1L
            var run = 1L
            for (i in 1 until sorted.size) {
                run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1L
                if (run > best) best = run
            }
            return best
        }

        // For inverse habits, success is the absence of an occurrence. Bound
        // the scan to the tracking window so "infinite success before creation"
        // doesn't exist.
        val today = java.time.LocalDate.now().toEpochDay()
        var best = 0L
        var run = 0L
        for (day in createdAtEpochDay..today) {
            if (isSuccessfulOn(day)) {
                run++
                if (run > best) best = run
            } else {
                run = 0L
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
        if (pausedSinceEpochDay != null && day >= pausedSinceEpochDay) return true
        if (inverse) return day !in completedDays
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
