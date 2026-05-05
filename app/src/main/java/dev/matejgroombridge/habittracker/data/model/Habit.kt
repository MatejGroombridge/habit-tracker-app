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
) {
    fun isCompletedOn(epochDay: Long): Boolean = epochDay in completedDays

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
        while (day in completedDays) {
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

    /**
     * Whether the habit should *visually* render as completed on [day].
     *
     * Daily habits are completed iff [day] is in [completedDays]. For weekly
     * and "every N days" habits, completing the habit early counts for the
     * remainder of its current window — so a habit you knock out on Monday
     * for a 3-day cadence will keep its checked styling on Tuesday and
     * Wednesday too.
     */
    fun isVisuallyCompletedOn(day: Long): Boolean {
        if (day in completedDays) return true
        val window = when (val f = frequency) {
            HabitFrequency.Daily -> return false
            HabitFrequency.Weekly -> 7
            is HabitFrequency.EveryNDays -> f.days
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
