package dev.matejgroombridge.habittracker.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
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
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Per-habit "all time" analytics. Each row shows: the habit's icon + name +
 * total completions + streak (with fire icon), then a 7-row × N-week
 * GitHub-style contribution grid.
 *
 * Grid layout details:
 *  - Uses a [LazyRow] with `reverseLayout = true` so the most recent week is
 *    pinned to the right edge of the screen and older weeks scroll off to
 *    the left.
 *  - The grid grows naturally as the habit ages: weeks earlier than the
 *    habit's creation date are simply not emitted.
 *  - Days that haven't happened yet in the current week render as a faded
 *    version of the empty cell so the column shape is preserved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = state.todayEpochDay
    val todayDate = remember(today) { LocalDate.ofEpochDay(today) }

    // Sunday at the end of the current week — the rightmost column ends here.
    val endOfCurrentWeek = remember(todayDate) {
        val daysAfterMon = ((todayDate.dayOfWeek.value - DayOfWeek.MONDAY.value) + 7) % 7
        val mondayOfThisWeek = todayDate.minusDays(daysAfterMon.toLong())
        mondayOfThisWeek.plusDays(6)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("All time") },
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
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(items = state.activeHabits, key = { it.id }) { habit ->
                AnalyticsRow(
                    habit = habit,
                    today = today,
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
    endOfCurrentWeek: LocalDate,
) {
    val color = HabitColors.entry(habit.colorKey)
    val iconEntry = HabitIcons.entry(habit.iconKey)

    val totalCompletions = remember(habit.completedDays) { habit.completedDays.size }
    val streak = remember(habit.completedDays, today) { habit.currentStreak(today) }

    val contentColor = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header sits inside the standard horizontal padding so it lines up
        // with the rest of the app.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
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
            createdAtEpochDay = habit.createdAtEpochDay,
            completedDays = habit.completedDays,
            today = today,
            endOfCurrentWeek = endOfCurrentWeek,
            accent = color.accent,
            emptyTint = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

private const val MIN_WEEKS = 16

@Composable
private fun ContributionGrid(
    createdAtEpochDay: Long,
    completedDays: Set<Long>,
    today: Long,
    endOfCurrentWeek: LocalDate,
    accent: Color,
    emptyTint: Color,
) {
    // How many weeks of history do we have between creation and the end of
    // this week? We always render at least MIN_WEEKS so the grid feels
    // populated even on a brand-new habit; the user can scroll left to see
    // any pre-creation columns (which all render as empty/future cells).
    val weeksAvailable = remember(createdAtEpochDay, endOfCurrentWeek) {
        val createdDate = LocalDate.ofEpochDay(createdAtEpochDay)
        val daysBetween = java.time.temporal.ChronoUnit.DAYS
            .between(createdDate, endOfCurrentWeek).toInt()
        // +1 because both endpoints inclusive when computing weeks of coverage.
        val computed = (daysBetween + 6) / 7 + 1
        maxOf(MIN_WEEKS, computed)
    }

    val scrollState = rememberScrollState()

    // Snap to the rightmost (most recent) week on first composition + every
    // time the available week count grows.
    androidx.compose.runtime.LaunchedEffect(weeksAvailable) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        // Push columns toward the right edge so the most recent week is
        // flush with the screen's right side and older weeks fill leftward.
        horizontalArrangement = Arrangement.End,
    ) {
        // Render oldest → newest so the rightmost (last) column is the
        // current week.
        for (weeksBack in (weeksAvailable - 1) downTo 0) {
            val weekMonday = endOfCurrentWeek.minusDays(6).minusWeeks(weeksBack.toLong())
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(end = if (weeksBack == 0) 0.dp else 3.dp),
            ) {
                for (offset in 0..6) {
                    val cellDate = weekMonday.plusDays(offset.toLong())
                    val cellEpoch = cellDate.toEpochDay()
                    val inFuture = cellEpoch > today
                    val completed = cellEpoch in completedDays
                    val cellColor = when {
                        completed -> accent
                        // Future cells: slightly faded version of empty so the
                        // grid keeps its 7-row shape without looking "missing".
                        inFuture -> emptyTint.copy(alpha = 0.35f)
                        else -> emptyTint
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(cellColor),
                    )
                }
            }
        }
    }
}
