package dev.matejgroombridge.habittracker.data.model

import kotlinx.serialization.Serializable

/**
 * How often a habit is expected to be completed.
 *
 * The repository / model layer doesn't actually *gate* completions on the
 * frequency — the user can mark any habit complete on any day. The frequency
 * only drives:
 *
 *  - The "is this habit due today?" hint on the home screen.
 *  - The "completing an interval habit early keeps it shown as completed
 *    until the interval ends" behaviour: see [Habit.isVisuallyCompletedOn].
 *
 * Sealed + serializable so it round-trips through the JSON blob.
 */
@Serializable
sealed interface HabitFrequency {

    /** Should be done every day. */
    @Serializable
    data object Daily : HabitFrequency

    /** Should be done at least once per ISO week (Mon-Sun). */
    @Serializable
    data object Weekly : HabitFrequency

    /**
     * Should be done once every [days] days. `days >= 1`. A value of 1 is
     * functionally equivalent to [Daily] but is kept as its own case so the
     * UI can show the user's chosen N exactly.
     */
    @Serializable
    data class EveryNDays(val days: Int) : HabitFrequency {
        init { require(days >= 1) { "EveryNDays requires days >= 1, got $days" } }
    }

    companion object {
        /** Human-readable summary, e.g. "Daily", "Weekly", "Every 3 days". */
        fun describe(frequency: HabitFrequency): String = when (frequency) {
            Daily -> "Daily"
            Weekly -> "Weekly"
            is EveryNDays -> if (frequency.days == 1) "Daily" else "Every ${frequency.days} days"
        }
    }
}
