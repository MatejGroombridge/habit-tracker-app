package dev.matejgroombridge.habittracker.data.settings

import java.time.DayOfWeek

/**
 * Day the user wants their week to start on. Affects:
 *  - The "current week" range used by `TimesPerWeek` frequencies.
 *  - The Past Week strip alignment.
 *  - The Last 7 Days mini-strip in the habit overview dialog.
 */
enum class WeekStart(val dayOfWeek: DayOfWeek) {
    Monday(DayOfWeek.MONDAY),
    Sunday(DayOfWeek.SUNDAY),
    ;

    companion object {
        val Default: WeekStart = Monday
    }
}
