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

    companion object {
        const val DEFAULT_ICON_KEY = "check_circle"
        const val DEFAULT_COLOR_KEY = "blush"
    }
}
