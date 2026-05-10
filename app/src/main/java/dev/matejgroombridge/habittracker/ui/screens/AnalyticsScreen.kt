package dev.matejgroombridge.habittracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Pause
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
    /** When false, render stored inverse habits as normal habits without deleting their flag. */
    allowInverseHabits: Boolean = true,
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
                title = { Text("All Time") },
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
                    habit = if (allowInverseHabits) habit else habit.copy(inverse = false),
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

    val totalCompletions = remember(habit.completedDays, habit.inverse, habit.createdAtEpochDay, today) {
        if (habit.inverse) (habit.createdAtEpochDay..today).count { habit.isSuccessfulOn(it) }
        else habit.completedDays.size
    }
    val streak = remember(habit.completedDays, habit.inverse, today) { habit.currentStreak(today) }
    val topStreak = remember(habit.completedDays, habit.inverse, habit.createdAtEpochDay) { habit.longestStreak() }

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
                        text = if (habit.inverse) "$totalCompletions successful" else "$totalCompletions completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                    )
                    Spacer(Modifier.width(10.dp))
                    // Current streak — fire icon, rendered in the habit's
                    // accent so each row gets a touch of personality.
                    Icon(
                        imageVector = Icons.Outlined.LocalFireDepartment,
                        contentDescription = "Current streak",
                        tint = color.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = streak.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                    )
                    Spacer(Modifier.width(10.dp))
                    // Top streak — trophy icon. Always shown, even when it
                    // equals the current streak, so the layout doesn't
                    // jump as a habit's record is matched and broken.
                    Icon(
                        imageVector = Icons.Outlined.EmojiEvents,
                        contentDescription = "Top streak",
                        tint = color.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = topStreak.toString(),
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
            skippedDays = habit.skippedDays,
            inverse = habit.inverse,
            pausedSinceEpochDay = habit.pausedSinceEpochDay,
            today = today,
            endOfCurrentWeek = endOfCurrentWeek,
            accent = color.accent,
            emptyTint = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

private val CELL_SIZE = 12.dp
private val CELL_GAP = 3.dp
private val GRID_LEFT_PADDING = 20.dp
private val GRID_RIGHT_PADDING = 12.dp

@Composable
private fun ContributionGrid(
    createdAtEpochDay: Long,
    completedDays: Set<Long>,
    skippedDays: Set<Long>,
    inverse: Boolean,
    pausedSinceEpochDay: Long?,
    today: Long,
    endOfCurrentWeek: LocalDate,
    accent: Color,
    emptyTint: Color,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Compute how many 7-day columns fit comfortably across the
        // available width given our cell size + inter-column gap and the
        // chosen left/right insets.
        val columnsThatFit = run {
            val available = maxWidth - GRID_LEFT_PADDING - GRID_RIGHT_PADDING
            val columnPitch = CELL_SIZE + CELL_GAP // includes the gap after each column
            // +CELL_GAP because the final column doesn't need a trailing gap,
            // so we get one "free" pitch's worth of room.
            ((available + CELL_GAP) / columnPitch).toInt().coerceAtLeast(1)
        }

        // Total weeks rendered = at least enough to fill the screen, but
        // grow over time so the user can scroll back through history.
        val weeksAvailable = remember(createdAtEpochDay, endOfCurrentWeek, columnsThatFit) {
            val createdDate = LocalDate.ofEpochDay(createdAtEpochDay)
            val daysBetween = java.time.temporal.ChronoUnit.DAYS
                .between(createdDate, endOfCurrentWeek).toInt()
            val historyWeeks = (daysBetween + 6) / 7 + 1
            maxOf(columnsThatFit, historyWeeks)
        }

        val scrollState = rememberScrollState()

        // Snap to the rightmost (most recent) week on first composition + every
        // time the available week count grows.
        androidx.compose.runtime.LaunchedEffect(weeksAvailable) {
            scrollState.scrollTo(scrollState.maxValue)
        }

        // Only attach horizontalScroll when the content actually overflows
        // the available width. Otherwise the modifier still consumes horizontal
        // drag gestures via nested-scroll dispatch, which on the All-Time
        // page makes it hard to swipe across to the Today page when the
        // grid hasn't accumulated enough history to need scrolling.
        val needsScroll = weeksAvailable > columnsThatFit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (needsScroll) Modifier.horizontalScroll(scrollState)
                    else Modifier,
                )
                .padding(start = GRID_LEFT_PADDING, end = GRID_RIGHT_PADDING),
            // The right padding above provides the small breathing space on
            // the right; columns themselves use `Arrangement.End` so the
            // current week sits at the trailing edge of the row.
            horizontalArrangement = Arrangement.End,
        ) {
            // Render oldest → newest so the rightmost (last) column is the
            // current week.
            for (weeksBack in (weeksAvailable - 1) downTo 0) {
                val weekMonday = endOfCurrentWeek.minusDays(6).minusWeeks(weeksBack.toLong())
                Column(
                    verticalArrangement = Arrangement.spacedBy(CELL_GAP),
                    modifier = Modifier.padding(end = if (weeksBack == 0) 0.dp else CELL_GAP),
                ) {
                    for (offset in 0..6) {
                        val cellDate = weekMonday.plusDays(offset.toLong())
                        val cellEpoch = cellDate.toEpochDay()
                        val inFuture = cellEpoch > today
                        val completed = if (inverse) {
                            cellEpoch >= createdAtEpochDay && cellEpoch !in completedDays
                        } else {
                            cellEpoch in completedDays
                        }
                        val skipped = cellEpoch in skippedDays
                        val paused = pausedSinceEpochDay != null && cellEpoch >= pausedSinceEpochDay
                        GridCell(
                            completed = completed,
                            skipped = skipped,
                            paused = paused,
                            inFuture = inFuture,
                            accent = accent,
                            emptyTint = emptyTint,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single cell in the analytics contribution grid. Visual rules:
 *  - **Completed**: filled square in the habit's accent.
 *  - **Skipped**: filled circle in a muted accent — clearly distinct from a
 *    full completion (different shape) but visible at a glance.
 *  - **Paused** (any day on or after the pause date): pause "‖" symbol that
 *    fills the whole cell (rounded square background tinted with the muted
 *    accent so a paused stretch reads as a single visual block).
 *  - **Future**: faded empty so the grid shape is preserved.
 *  - **Otherwise**: empty filled square.
 */
@Composable
private fun GridCell(
    completed: Boolean,
    skipped: Boolean,
    paused: Boolean,
    inFuture: Boolean,
    accent: Color,
    emptyTint: Color,
) {
    when {
        // Pause overrides skip & complete: the user explicitly froze tracking
        // for these days, so render a pause glyph rather than an apparent
        // achievement. Background takes the full cell and the icon scales
        // up to occupy ~80% of it so the pause state reads instantly.
        paused -> Box(
            modifier = Modifier
                .size(CELL_SIZE)
                .clip(RoundedCornerShape(3.dp))
                .background(accent.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Pause,
                contentDescription = "Paused",
                tint = accent,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Filled accent circle for skipped days. Slightly muted so the user
        // can still tell it apart from a completion at a glance.
        skipped -> Box(
            modifier = Modifier
                .size(CELL_SIZE)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.55f)),
        )
        completed -> Box(
            modifier = Modifier
                .size(CELL_SIZE)
                .clip(RoundedCornerShape(3.dp))
                .background(accent),
        )
        else -> Box(
            modifier = Modifier
                .size(CELL_SIZE)
                .clip(RoundedCornerShape(3.dp))
                .background(if (inFuture) emptyTint.copy(alpha = 0.35f) else emptyTint),
        )
    }
}
