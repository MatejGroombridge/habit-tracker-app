package dev.matejgroombridge.habittracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import dev.matejgroombridge.habittracker.data.repository.HabitRepository
import java.time.LocalDate

/**
 * Tapping a habit cell in the widget calls this to toggle today's completion.
 * Updates the repository, then re-renders all widgets so every instance
 * showing this habit reflects the new state.
 */
class ToggleHabitAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val habitId = parameters[KEY_HABIT_ID] ?: return
        HabitRepository(context).toggleCompletion(habitId, LocalDate.now().toEpochDay())
        // Re-render every active widget — repository.update already
        // broadcasts a refresh, but we explicitly force a Glance update
        // here too in case the broadcast races the next provideGlance.
        val widget = HabitWidget()
        val ids = GlanceAppWidgetManager(context).getGlanceIds(HabitWidget::class.java)
        for (gid in ids) widget.update(context, gid)
    }

    companion object {
        val KEY_HABIT_ID = ActionParameters.Key<String>("habit_id")
        fun params(habitId: String): ActionParameters =
            actionParametersOf(KEY_HABIT_ID to habitId)
    }
}
