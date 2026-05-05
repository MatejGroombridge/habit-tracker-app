package dev.matejgroombridge.habittracker.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons
import dev.matejgroombridge.habittracker.ui.theme.containerColor
import dev.matejgroombridge.habittracker.ui.theme.contentColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Lets the user retroactively toggle the last 7 days of any habit. Each row
 * shows the habit's icon + name on the first line and a row of 7 day chips
 * (oldest left → today right, with the weekday letter inside each chip) on
 * the second line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastWeekScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = state.todayEpochDay

    val days = remember(today) { (0L..6L).map { today - (6L - it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Past Week") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.activeHabits.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                message = "No habits yet.\nAdd one on the Today tab.",
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = state.activeHabits, key = { it.id }) { habit ->
                PastWeekRow(
                    habit = habit,
                    days = days,
                    today = today,
                    onToggle = { day ->
                        viewModel.setCompleted(habit.id, day, !habit.isCompletedOn(day))
                    },
                )
            }
        }
    }
}

@Composable
private fun PastWeekRow(
    habit: Habit,
    days: List<Long>,
    today: Long,
    onToggle: (Long) -> Unit,
) {
    val color = HabitColors.entry(habit.colorKey)
    val iconEntry = HabitIcons.entry(habit.iconKey)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.containerColor(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconEntry.icon,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color.contentColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                days.forEach { epochDay ->
                    DayChip(
                        date = LocalDate.ofEpochDay(epochDay),
                        completed = habit.isCompletedOn(epochDay),
                        isToday = epochDay == today,
                        accent = color.accent,
                        onColor = color.contentColor(),
                        onClick = { onToggle(epochDay) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayChip(
    date: LocalDate,
    completed: Boolean,
    isToday: Boolean,
    accent: Color,
    onColor: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = date.dayOfWeek.shortLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = onColor.copy(alpha = if (isToday) 1f else 0.65f),
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (completed) accent else Color.Black.copy(alpha = 0.06f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Completed",
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = onColor.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
internal fun EmptyState(modifier: Modifier = Modifier, message: String) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun DayOfWeek.shortLabel(): String =
    getDisplayName(TextStyle.NARROW, Locale.getDefault())
