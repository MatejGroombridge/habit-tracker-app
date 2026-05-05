package dev.matejgroombridge.habittracker.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.repository.HabitRepository
import dev.matejgroombridge.habittracker.ui.theme.AppTheme
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shown when the user adds the widget to their home screen. Lets them pick
 * up to [WidgetPrefs.MAX_HABITS_PER_WIDGET] habits to display, then writes
 * the selection to DataStore and tells AppWidgetManager to render the widget.
 *
 * If the user backs out without saving, [setResult(RESULT_CANCELED)] tells
 * AppWidgetManager to discard the widget — standard Android contract.
 */
class HabitWidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Default = cancelled until the user explicitly confirms.
        setResult(Activity.RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        setContent {
            AppTheme {
                ConfigScreen(
                    onCancel = { finish() },
                    onConfirm = { selection -> confirm(selection) },
                )
            }
        }
    }

    private fun confirm(selectedIds: List<String>) {
        // Persist + render synchronously so the widget appears with the
        // user's chosen habits the first time, not blank. runBlocking is
        // intentional: we want the AppWidgetManager result delivered after
        // the widget has been updated so users never see an empty cell.
        kotlinx.coroutines.runBlocking {
            WidgetPrefs.setSelection(applicationContext, widgetId, selectedIds)
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(widgetId)
            HabitWidget().update(applicationContext, glanceId)
        }

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    onCancel: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val all = HabitRepository(context).habits.first()
        habits = all.filterNot { it.archived }
        loaded = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Choose Habits") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                },
                actions = {
                    Button(
                        onClick = { onConfirm(selected.toList().take(WidgetPrefs.MAX_HABITS_PER_WIDGET)) },
                        enabled = selected.isNotEmpty(),
                    ) { Text("Add Widget") }
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        if (!loaded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Loading…") }
            return@Scaffold
        }
        if (habits.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Add some habits in the app first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(Modifier.padding(padding)) {
            Text(
                text = "Pick up to ${WidgetPrefs.MAX_HABITS_PER_WIDGET} habits to show on the widget.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(habits, key = { it.id }) { h ->
                    val isSelected = h.id in selected
                    val canAddMore = selected.size < WidgetPrefs.MAX_HABITS_PER_WIDGET
                    HabitChoiceRow(
                        habit = h,
                        selected = isSelected,
                        enabled = isSelected || canAddMore,
                        onToggle = {
                            selected = if (isSelected) selected - h.id
                            else if (canAddMore) selected + h.id
                            else selected
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitChoiceRow(
    habit: Habit,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val color = HabitColors.entry(habit.colorKey)
    val iconEntry = HabitIcons.entry(habit.iconKey)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) color.accent.copy(alpha = 0.20f)
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onToggle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.accent, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconEntry.icon,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = habit.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = enabled)
        }
    }
}
