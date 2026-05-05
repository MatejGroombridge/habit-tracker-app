package dev.matejgroombridge.habittracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.matejgroombridge.habittracker.MainActivity
import dev.matejgroombridge.habittracker.R
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.repository.HabitRepository
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * A 2-column × 3-row Today widget. Tap a cell to toggle completion;
 * tap the "info" badge on a cell to open the app on the corresponding
 * habit. The set of habits to display is chosen at widget-add time
 * (see [HabitWidgetConfigActivity]) and stored per-widget-instance in
 * [WidgetPrefs].
 */
class HabitWidget : GlanceAppWidget() {

    // Keep using preferences-backed state so Glance can wire its
    // restoration/update plumbing — we don't store anything here ourselves.
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Resolve which habits to render. Glance gives us the AppWidget id
        // through GlanceAppWidgetManager; we look it up via reflection-free
        // helper in WidgetPrefs after fetching the integer id.
        val widgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            .getAppWidgetId(id)
        val selection = WidgetPrefs.getSelection(context, widgetId)
        val allHabits = HabitRepository(context).habits.first()
        val today = LocalDate.now().toEpochDay()

        // Resolve in the user's chosen order, dropping any IDs that no
        // longer exist (habit was deleted, etc).
        val habits = selection.mapNotNull { hid -> allHabits.firstOrNull { it.id == hid } }

        provideContent {
            GlanceTheme {
                WidgetBody(habits = habits, today = today)
            }
        }
    }
}

@Composable
private fun WidgetBody(habits: List<Habit>, today: Long) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(8.dp),
    ) {
        if (habits.isEmpty()) {
            // First-launch / config-deleted state.
            Column(
                modifier = GlanceModifier.fillMaxSize().clickable(
                    onClick = actionStartActivity<MainActivity>(),
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = context.getString(R.string.app_name),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "Re-add the widget to choose habits",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                )
            }
            return@Box
        }

        // Render up to 6 habits in a 2-col × 3-row grid. Empty cells stay
        // blank rather than centring the partial last row, which keeps tap
        // targets in stable positions across updates.
        Column(modifier = GlanceModifier.fillMaxSize()) {
            for (row in 0..2) {
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    val left = habits.getOrNull(row * 2)
                    val right = habits.getOrNull(row * 2 + 1)
                    HabitCell(habit = left, today = today, modifier = GlanceModifier.defaultWeight().fillMaxSize())
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    HabitCell(habit = right, today = today, modifier = GlanceModifier.defaultWeight().fillMaxSize())
                }
                if (row < 2) Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun HabitCell(habit: Habit?, today: Long, modifier: GlanceModifier) {
    if (habit == null) {
        // Blank placeholder so the grid stays aligned when fewer than 6
        // habits are picked.
        Box(modifier = modifier) {}
        return
    }

    val color = HabitColors.entry(habit.colorKey)
    val done = habit.isVisuallyCompletedOn(today)
    val cellColor: ColorProvider = ColorProvider(
        if (done) ComposeColor(color.accent.value)
        else ComposeColor(color.light.value),
    )
    val textColor: ColorProvider = ColorProvider(
        if (done) ComposeColor.White else ComposeColor(color.onColor.value),
    )

    Box(
        modifier = modifier
            .background(cellColor)
            .cornerRadius(16.dp)
            .clickable(
                onClick = actionRunCallback<ToggleHabitAction>(
                    parameters = ToggleHabitAction.params(habit.id),
                ),
            )
            .padding(8.dp),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = habit.name,
                style = TextStyle(
                    color = textColor,
                    fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 2,
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = if (done) "✓ Done" else "Tap to complete",
                style = TextStyle(
                    color = textColor,
                    fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                ),
                maxLines = 1,
            )
        }
    }
}
