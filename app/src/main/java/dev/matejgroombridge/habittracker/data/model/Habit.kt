package dev.matejgroombridge.habittracker.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A single habit the user is tracking.
 *
 * @param id            Stable identifier for the habit. Generated once on creation.
 * @param name          User-supplied name (e.g. "Drink water").
 * @param createdAtEpochDay The day the habit was created, expressed as an
 *                      `LocalDate.toEpochDay()` value.
 * @param completedDays Set of days (epoch-day values) on which the habit was
 *                      completed. Membership of "today" determines whether
 *                      the card renders as completed.
 */
@Serializable
data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
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
}
