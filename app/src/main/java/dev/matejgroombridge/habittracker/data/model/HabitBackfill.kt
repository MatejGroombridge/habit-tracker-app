package dev.matejgroombridge.habittracker.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Synthesises plausible completion history for a habit that started later
 * than the rest of them.
 *
 * The problem this solves: a habit added months after the others shows an
 * empty All Time grid next to rows that stretch back to the beginning, which
 * makes an established routine look brand new. Rather than ask the user to
 * tap hundreds of cells, the editor lets them state roughly how well they'd
 * been doing ("about 80%") and we fill the grid in for them, back to the day
 * their oldest habit was created.
 *
 * The generated days are a random sample rather than an even spread — real
 * history is streaky, and a perfectly regular pattern reads as fake at a
 * glance in the contribution grid.
 */
object HabitBackfill {

    /** Lowest percentage the editor offers; 0% would leave the grid empty. */
    const val MIN_PERCENT = 5

    /** Step between offered percentages, so the stepper walks 5/10/…/100. */
    const val PERCENT_STEP = 5

    /** Percentage pre-selected when the user first flips backfill on. */
    const val DEFAULT_PERCENT = 80

    /**
     * The day the user's oldest habit was created — the point history is
     * backfilled to. `null` when [habits] is empty, i.e. this is their first
     * habit and there's nothing to line up with.
     */
    fun earliestCreation(habits: List<Habit>): Long? =
        habits.minOfOrNull { it.createdAtEpochDay }

    /**
     * Builds a set of completed days covering `[startEpochDay,
     * endExclusiveEpochDay)` at roughly [percent] success.
     *
     * The end is exclusive because neither caller wants it filled: creating
     * a habit passes today, so the new habit still sits on the Today screen
     * waiting to be ticked off, and extending an existing habit's history
     * passes its creation day, so the real history it already has is never
     * overwritten.
     *
     * Which days are eligible depends on [frequency]: a weekly habit gets one
     * candidate day per ISO week rather than seven, so "100%" means "never
     * missed a week" instead of "completed every single day".
     *
     * [random] is injectable for tests; production calls take the default.
     */
    fun generate(
        startEpochDay: Long,
        endExclusiveEpochDay: Long,
        percent: Int,
        frequency: HabitFrequency = HabitFrequency.Daily,
        random: Random = Random.Default,
    ): Set<Long> {
        val lastDay = endExclusiveEpochDay - 1
        if (startEpochDay > lastDay) return emptySet()
        val rate = percent.coerceIn(0, 100)
        val candidates = candidateDays(startEpochDay, lastDay, frequency, random)
        return candidates.shuffled(random).take(proportionOf(candidates.size, rate)).toSet()
    }

    /**
     * Days on which the habit was *expected* to be done, given its frequency.
     * These form the denominator for the requested percentage.
     */
    private fun candidateDays(
        start: Long,
        end: Long,
        frequency: HabitFrequency,
        random: Random,
    ): List<Long> = when (frequency) {
        HabitFrequency.Daily -> (start..end).toList()

        HabitFrequency.Weekly -> isoWeekBlocks(start, end).map { block ->
            block.random(random)
        }

        is HabitFrequency.EveryNDays ->
            if (frequency.days <= 1) (start..end).toList()
            else generateSequence(start) { it + frequency.days }
                .takeWhile { it <= end }
                .toList()

        is HabitFrequency.TimesPerWeek -> isoWeekBlocks(start, end).flatMap { block ->
            // A partial week at either end of the range can't hold the full
            // quota, so take whatever fits.
            val days = block.toList()
            days.shuffled(random).take(minOf(frequency.times, days.size))
        }
    }

    /**
     * Splits `start..end` into Monday-anchored week blocks, clipped to the
     * range at both ends. Monday anchoring matches [HabitFrequency.Weekly]'s
     * documented ISO-week semantics and the All Time grid's columns.
     */
    private fun isoWeekBlocks(start: Long, end: Long): List<LongRange> {
        val mondayOffset =
            ((LocalDate.ofEpochDay(start).dayOfWeek.value - DayOfWeek.MONDAY.value) + 7) % 7
        val blocks = mutableListOf<LongRange>()
        var blockStart = start - mondayOffset
        while (blockStart <= end) {
            blocks += maxOf(blockStart, start)..minOf(blockStart + 6, end)
            blockStart += 7
        }
        return blocks
    }

    private fun proportionOf(total: Int, percent: Int): Int =
        (total * percent / 100.0).roundToInt().coerceIn(0, total)
}
