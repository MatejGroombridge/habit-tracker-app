package dev.matejgroombridge.habittracker.nfc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.matejgroombridge.habittracker.MainActivity
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.repository.HabitRepository
import dev.matejgroombridge.habittracker.data.settings.NfcAction
import dev.matejgroombridge.habittracker.data.settings.SettingsRepository
import dev.matejgroombridge.habittracker.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Entry point for NFC tag scans. The user writes a `habittracker://habit/complete/<id>`
 * URL onto a tag using any NFC-writer app; tapping the tag launches this
 * activity, which:
 *
 *   1. Resolves the habit id from the intent URI.
 *   2. Marks today as completed for that habit.
 *   3. Decides what to do next based on the persisted [NfcAction] setting:
 *      - [NfcAction.Background]: finish silently.
 *      - [NfcAction.Overlay]: show a fullscreen celebration overlay for ~1.6s.
 *      - [NfcAction.OpenApp]: launch [MainActivity] and finish.
 *
 * The activity uses a transparent theme so "background" and the brief gap
 * before the overlay paints don't flash anything over whichever app the user
 * was just in.
 */
class NfcCompletionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val habitId = intent?.data?.let(::extractHabitId)
        if (habitId == null) {
            // Malformed deep link — there's nothing useful to do. Fall back
            // to opening the app so the user isn't left staring at a blank
            // transparent screen.
            launchAppAndFinish()
            return
        }

        val habitRepo = HabitRepository(applicationContext)
        val settingsRepo = SettingsRepository(applicationContext)

        lifecycleScope.launch {
            val today = LocalDate.now().toEpochDay()
            habitRepo.setCompleted(habitId, today, completed = true)
            val action = settingsRepo.settings.first().nfcAction

            // Look up the freshly-completed habit so the overlay can show
            // its name / icon / colour.
            val habit = habitRepo.habits.first().firstOrNull { it.id == habitId }

            when (action) {
                NfcAction.Background -> finish()
                NfcAction.OpenApp -> launchAppAndFinish(habitId)
                NfcAction.Overlay -> showOverlay(habit)
            }
        }
    }

    private fun showOverlay(habit: Habit?) {
        setContent {
            AppTheme {
                var visible by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    // Auto-dismiss after the celebration finishes playing.
                    kotlinx.coroutines.delay(1800)
                    visible = false
                    // Brief delay for the exit animation to play.
                    kotlinx.coroutines.delay(250)
                    finishWithNoAnimation()
                }
                NfcCompletionOverlay(habit = habit, visible = visible)
            }
        }
    }

    private fun launchAppAndFinish(habitId: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (habitId != null) {
                data = Uri.parse(
                    "${Habit.DEEP_LINK_SCHEME}://${Habit.DEEP_LINK_HOST}/open/$habitId"
                )
            }
        }
        startActivity(intent)
        finishWithNoAnimation()
    }

    /**
     * Closes this activity without any open/close animation. Uses the modern
     * `overrideActivityTransition` on Android 14+ and the deprecated
     * `overridePendingTransition` as a fallback on older releases.
     */
    @Suppress("DEPRECATION")
    private fun finishWithNoAnimation() {
        finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    /**
     * Pulls the habit id off the deep-link path. Accepts either of:
     *   - habittracker://habit/complete/<id>
     *   - habittracker://habit/open/<id>
     * and returns null for anything else.
     */
    private fun extractHabitId(uri: Uri): String? {
        val segments = uri.pathSegments
        if (segments.size < 2) return null
        val first = segments[0]
        if (first != "complete" && first != "open") return null
        val id = segments[1]
        return id.takeIf { it.isNotBlank() }
    }
}
