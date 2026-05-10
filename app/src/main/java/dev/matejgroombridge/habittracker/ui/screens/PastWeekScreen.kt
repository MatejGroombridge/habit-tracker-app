package dev.matejgroombridge.habittracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.matejgroombridge.habittracker.ui.util.rememberHaptics
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastWeekScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    /** Hide the Skip option in the long-press menu when global allowSkips is off. */
    allowSkips: Boolean = true,
    /** Hide the Pause option in the long-press menu when global allowPauses is off. */
    allowPauses: Boolean = true,
    /** When false, render stored inverse habits as normal habits without deleting their flag. */
    allowInverseHabits: Boolean = true,
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
                val displayHabit = if (allowInverseHabits) habit else habit.copy(inverse = false)
                PastWeekRow(
                    habit = displayHabit,
                    days = days,
                    today = today,
                    allowSkips = allowSkips,
                    allowPauses = allowPauses,
                    onTapDay = { day ->
                        // Tap = toggle completion (existing behaviour). If
                        // the day is currently skipped or paused, clear
                        // those first so the toggle has a sensible target.
                        if (displayHabit.isSkippedOn(day)) {
                            viewModel.setSkipped(displayHabit.id, day, false)
                        } else {
                            // For inverse habits, completedDays means "the bad
                            // habit happened". Tapping a successful/default day
                            // records an occurrence; tapping that occurrence
                            // clears it back to success.
                            viewModel.setCompleted(
                                displayHabit.id,
                                day,
                                !displayHabit.isCompletedOn(day),
                            )
                        }
                    },
                    onPickStateForDay = { day, state ->
                        viewModel.applyDayState(displayHabit, day, state)
                    },
                )
            }
        }
    }
}

/**
 * Helper on the viewmodel: collapse a day's "what state should this be?"
 * into the right combination of repository calls. Implemented here as an
 * extension so the screen file owns the UI-specific mapping rather than
 * polluting the viewmodel API surface.
 */
private fun HomeViewModel.applyDayState(habit: Habit, day: Long, state: DayState) {
    when (state) {
        DayState.Completed -> {
            // Mark complete; clear skip if any. Pause is global (not per-day)
            // so we don't touch it here.
            if (habit.isSkippedOn(day)) setSkipped(habit.id, day, false)
            setCompleted(habit.id, day, true)
        }
        DayState.Skipped -> {
            // Setting skipped already clears completion in the repo.
            setSkipped(habit.id, day, true)
        }
        DayState.Cleared -> {
            if (habit.isSkippedOn(day)) setSkipped(habit.id, day, false)
            setCompleted(habit.id, day, false)
        }
        DayState.Pause -> {
            // Pause is a habit-level toggle. Long-press flips it; the menu
            // shows whichever direction matters right now.
            setPaused(habit.id, !habit.isPaused)
        }
    }
}

/** What the long-press menu can apply to a day. */
private enum class DayState { Completed, Skipped, Cleared, Pause }

@Composable
private fun PastWeekRow(
    habit: Habit,
    days: List<Long>,
    today: Long,
    allowSkips: Boolean,
    allowPauses: Boolean,
    onTapDay: (Long) -> Unit,
    onPickStateForDay: (Long, DayState) -> Unit,
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
                        completed = habit.isSuccessfulOn(epochDay),
                        skipped = habit.isSkippedOn(epochDay),
                        paused = habit.pausedSinceEpochDay != null && epochDay >= habit.pausedSinceEpochDay,
                        isPaused = habit.isPaused,
                        isToday = epochDay == today,
                        accent = color.accent,
                        onColor = color.contentColor(),
                        allowSkips = allowSkips,
                        allowPauses = allowPauses,
                        onClick = { onTapDay(epochDay) },
                        onPick = { state -> onPickStateForDay(epochDay, state) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayChip(
    date: LocalDate,
    completed: Boolean,
    skipped: Boolean,
    paused: Boolean,
    isPaused: Boolean,
    isToday: Boolean,
    accent: Color,
    onColor: Color,
    allowSkips: Boolean,
    allowPauses: Boolean,
    onClick: () -> Unit,
    onPick: (DayState) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
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
        Box {
            DayCellShape(
                completed = completed,
                skipped = skipped,
                paused = paused,
                accent = accent,
                onColor = onColor,
                dayOfMonth = date.dayOfMonth,
                modifier = Modifier
                    .size(34.dp)
                    .combinedClickable(
                        onClick = {
                            haptics.completion()
                            onClick()
                        },
                        onLongClick = {
                            haptics.longPress()
                            menuOpen = true
                        },
                    ),
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                MenuChoice(
                    label = "Mark Completed",
                    icon = Icons.Outlined.Check,
                    onClick = { menuOpen = false; onPick(DayState.Completed) },
                )
                if (allowSkips) {
                    MenuChoice(
                        label = "Mark Skipped",
                        icon = Icons.Outlined.SkipNext,
                        onClick = { menuOpen = false; onPick(DayState.Skipped) },
                    )
                }
                if (allowPauses) {
                    MenuChoice(
                        label = if (isPaused) "Resume Habit" else "Pause Habit",
                        icon = Icons.Outlined.Pause,
                        onClick = { menuOpen = false; onPick(DayState.Pause) },
                    )
                }
                MenuChoice(
                    label = "Clear",
                    icon = Icons.Outlined.Close,
                    onClick = { menuOpen = false; onPick(DayState.Cleared) },
                )
            }
        }
    }
}

/**
 * Visual representation of a single day. Mirrors the All Time grid:
 *  * **Paused** → full-cell pause icon on a tinted background.
 *  * **Skipped** → filled circle in the muted accent.
 *  * **Completed** → filled rounded square + check mark.
 *  * **Otherwise** → faded square with day-of-month number.
 */
@Composable
private fun DayCellShape(
    completed: Boolean,
    skipped: Boolean,
    paused: Boolean,
    accent: Color,
    onColor: Color,
    dayOfMonth: Int,
    modifier: Modifier = Modifier,
) {
    when {
        paused -> Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Pause,
                contentDescription = "Paused",
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
        skipped -> Box(
            modifier = modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
            content = { /* no inner glyph — the filled circle is the indicator */ },
        )
        completed -> Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Completed",
                tint = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp),
            )
        }
        else -> Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = onColor.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun MenuChoice(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null)
        },
        onClick = onClick,
    )
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
