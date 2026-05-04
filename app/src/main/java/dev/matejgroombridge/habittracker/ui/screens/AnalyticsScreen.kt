package dev.matejgroombridge.habittracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons
import dev.matejgroombridge.habittracker.ui.theme.contentColor
import java.time.DayOfWeek
import java.time.LocalDate

private const val DEFAULT_WEEKS = 16

/**
 * Per-habit analytics. Each row shows: the habit's icon + name + completions
 * + streak (with fire icon), then a 7×N contribution grid (rows = Mon..Sun,
 * cols = N weeks back, rightmost = current week). The grid sits directly on
 * the screen background — no card wrapping — so the cells read as the only
 * visual structure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues = PaddingValues(),
    weeks: Int = DEFAULT_WEEKS,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = state.todayEpochDay
    val todayDate = remember(today) { LocalDate.ofEpochDay(today) }

    // Sunday at the end of the current week — the rightmost column ends on this date.
    val endOfCurrentWeek = remember(todayDate) {
        val daysAfterMon = ((todayDate.dayOfWeek.value - DayOfWeek.MONDAY.value) + 7) % 7
        val mondayOfThisWeek = todayDate.minusDays(daysAfterMon.toLong())
        mondayOfThisWeek.plusDays(6)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
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
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            items(items = state.activeHabits, key = { it.id }) { habit ->
                AnalyticsRow(
                    habit = habit,
                    today = today,
                    weeks = weeks,
                    endOfCurrentWeek = endOfCurrentWeek,
                )
            }
        }
    }
}

@Composable
private fun AnalyticsRow(
    habit: Habit,
    today: Long,
    weeks: Int,
    endOfCurrentWeek: LocalDate,
) {
    val color = HabitColors.entry(habit.colorKey)
    val iconEntry = HabitIcons.entry(habit.iconKey)

    val totalCompletions = remember(habit.completedDays) { habit.completedDays.size }
    val streak = remember(habit.completedDays, today) { habit.currentStreak(today) }

    val contentColor = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$totalCompletions completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Outlined.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = color.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = streak.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ContributionGrid(
            completedDays = habit.completedDays,
            today = today,
            endOfCurrentWeek = endOfCurrentWeek,
            weeks = weeks,
            accent = color.accent,
            emptyTint = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun ContributionGrid(
    completedDays: Set<Long>,
    today: Long,
    endOfCurrentWeek: LocalDate,
    weeks: Int,
    accent: Color,
    emptyTint: Color,
) {
    val firstMonday = remember(endOfCurrentWeek, weeks) {
        endOfCurrentWeek.minusDays(6).minusWeeks((weeks - 1).toLong())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (week in 0 until weeks) {
            val weekMonday = firstMonday.plusWeeks(week.toLong())
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (offset in 0..6) {
                    val cellDate = weekMonday.plusDays(offset.toLong())
                    val cellEpoch = cellDate.toEpochDay()
                    val inFuture = cellEpoch > today
                    val completed = cellEpoch in completedDays
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    inFuture -> Color.Transparent
                                    completed -> accent
                                    else -> emptyTint
                                },
                            ),
                    )
                }
            }
        }
    }
}
