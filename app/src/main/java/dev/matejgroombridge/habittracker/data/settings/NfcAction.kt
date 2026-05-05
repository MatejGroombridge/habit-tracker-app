package dev.matejgroombridge.habittracker.data.settings

/**
 * What should happen when the user scans an NFC tag whose URL points at one
 * of their habits.
 *
 * The deep link is the same in all cases — the receiving activity uses this
 * setting to decide what to do once it has resolved the target habit.
 */
enum class NfcAction {
    /**
     * Mark the habit complete and immediately finish, with no UI shown over
     * whichever app the user happened to be in when they tapped the tag.
     */
    Background,

    /**
     * Mark the habit complete and show a transparent fullscreen "habit
     * completed" celebration overlay that floats on top of the current app
     * for a couple of seconds.
     */
    Overlay,

    /**
     * Mark the habit complete and bring the Habits app to the foreground.
     */
    OpenApp,
    ;

    companion object {
        val Default: NfcAction = Overlay
    }
}
