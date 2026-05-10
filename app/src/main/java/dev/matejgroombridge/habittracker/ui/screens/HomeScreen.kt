package dev.matejgroombridge.habittracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.SettingsViewModel
import dev.matejgroombridge.habittracker.ui.components.ConfettiOverlay
import dev.matejgroombridge.habittracker.ui.components.HabitCard
import dev.matejgroombridge.habittracker.ui.components.HabitEditorDialog
import dev.matejgroombridge.habittracker.ui.components.HabitEditorResult
import dev.matejgroombridge.habittracker.ui.components.HabitOverviewDialog

/** Which dialog (if any) the home screen is currently showing. */
private sealed interface HomeDialog {
    data object Create : HomeDialog
    /** Read-only snapshot of stats — opened by long-pressing a card. */
    data class Overview(val habit: Habit) : HomeDialog
    /** Full editor — reachable from [Overview]'s edit button or by other entry points. */
    data class Edit(val habit: Habit) : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit,
    /**
     * Navigate to the in-app NFC writer. Passed through to
     * [HabitEditorDialog]'s NFC card. Optional — defaults to a no-op so
     * existing tests/previews don't break.
     */
    onOpenWriteNfc: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    requestCreate: Boolean = false,
    onCreateDialogConsumed: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }

    LaunchedEffect(requestCreate) {
        if (requestCreate) {
            dialog = HomeDialog.Create
            onCreateDialogConsumed()
        }
    }

    // Track all-complete transitions so we can fire confetti when the user
    // completes the final outstanding habit. We only fire on the *transition*
    // (not the initial state), so opening the app on an already-complete day
    // doesn't trigger a redundant burst.
    val allComplete = state.activeHabits.isNotEmpty() &&
        state.activeHabits.all { habit ->
            val displayHabit = if (settings.allowInverseHabits) habit else habit.copy(inverse = false)
            displayHabit.isVisuallyCompletedOn(state.todayEpochDay, settings.weekStart.dayOfWeek)
        }
    var previousAllComplete by remember { mutableStateOf<Boolean?>(null) }
    var fireConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(allComplete) {
        val prev = previousAllComplete
        if (prev != null && !prev && allComplete) {
            // Toggle through to retrigger ConfettiOverlay's LaunchedEffect.
            fireConfetti = false
            fireConfetti = true
        }
        previousAllComplete = allComplete
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // We render the title + actions inline (instead of in `topBar`)
            // so the habit cards can sit directly underneath the title with
            // no extra padding leftover from the topbar's vertical centering.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                HabitsHeader(
                    onOpenArchive = onOpenArchive,
                    onOpenSettings = onOpenSettings,
                    // The archive icon disappears in Zen mode, but Settings
                    // stays so the user has a way to flip Zen back off.
                    showArchive = !settings.zenMode,
                )
                if (state.activeHabits.isEmpty()) {
                    EmptyState()
                } else {
                    HabitsGrid(
                        habits = state.activeHabits,
                        todayEpochDay = state.todayEpochDay,
                        bottomPadding = contentPadding.calculateBottomPadding() + 24.dp,
                        onToggle = viewModel::toggleToday,
                        // No long-press → overview dialog while in Zen.
                        onLongPress = if (settings.zenMode) ({ _ -> })
                        else ({ habit -> dialog = HomeDialog.Overview(habit) }),
                        weekStart = settings.weekStart,
                        allowInverseHabits = settings.allowInverseHabits,
                    )
                }
            }
            ConfettiOverlay(trigger = fireConfetti)
        }
    }

    // While Zen mode is on, no in-app dialog should ever open. Drop any
    // pending dialog and suppress the when-branch below as a hard guard.
    LaunchedEffect(settings.zenMode) { if (settings.zenMode) dialog = null }
    if (settings.zenMode) return

    when (val d = dialog) {
        is HomeDialog.Create -> HabitEditorDialog(
            existing = null,
            onDismiss = { dialog = null },
            dailyOnly = settings.dailyHabitsOnly,
            allowInverseHabits = settings.allowInverseHabits,
            onResult = { result ->
                if (result is HabitEditorResult.Save) {
                    viewModel.addHabit(
                        name = result.name,
                        description = result.description,
                        iconKey = result.iconKey,
                        colorKey = result.colorKey,
                        frequency = result.frequency,
                        inverse = result.inverse,
                    )
                }
                dialog = null
            },
        )
        is HomeDialog.Overview -> {
            // Source the latest version of the habit from state so stats
            // stay live if completion changes (e.g. via NFC) while the
            // dialog is open.
            val live = state.activeHabits.firstOrNull { it.id == d.habit.id } ?: d.habit
            val displayHabit = if (settings.allowInverseHabits) live else live.copy(inverse = false)
            HabitOverviewDialog(
                habit = displayHabit,
                todayEpochDay = state.todayEpochDay,
                onDismiss = { dialog = null },
                onEdit = { dialog = HomeDialog.Edit(live) },
                onToggleSkipToday = {
                    viewModel.setSkipped(
                        habitId = live.id,
                        epochDay = state.todayEpochDay,
                        skipped = !live.isSkippedOn(state.todayEpochDay),
                    )
                },
                onTogglePause = {
                    viewModel.setPaused(habitId = live.id, paused = !live.isPaused)
                },
                // Hide skip / pause icons when their global toggles are
                // off — there's no point exposing actions the user has
                // explicitly opted out of.
                showSkipAction = settings.allowSkips,
                showPauseAction = settings.allowPauses,
            )
        }
        is HomeDialog.Edit -> HabitEditorDialog(
            existing = d.habit,
            onDismiss = { dialog = null },
            dailyOnly = settings.dailyHabitsOnly,
            allowInverseHabits = settings.allowInverseHabits,
            onWriteNfc = {
                dialog = null
                onOpenWriteNfc()
            },
            onResult = { result ->
                when (result) {
                    is HabitEditorResult.Save -> viewModel.updateHabit(
                        habitId = d.habit.id,
                        name = result.name,
                        description = result.description,
                        iconKey = result.iconKey,
                        colorKey = result.colorKey,
                        frequency = result.frequency,
                        inverse = result.inverse,
                    )
                    is HabitEditorResult.Archive -> viewModel.setArchived(d.habit.id, result.archived)
                }
                dialog = null
            },
        )
        null -> Unit
    }
}

/**
 * Inline title + actions row. Larger headline, bottom-aligned so it sits
 * "down the page" (similar to a M3 LargeTopAppBar in its expanded state),
 * but with no baseline padding below the title so habit cards can sit
 * directly underneath.
 */
@Composable
private fun HabitsHeader(
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
    showArchive: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
    ) {
        // Action icons pinned to the top-right.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showArchive) {
                IconButton(onClick = onOpenArchive) {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = "Archived Habits",
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                )
            }
        }
        // Headline aligned to the bottom-start so cards immediately below
        // sit flush with the underline of the text.
        Text(
            text = "Habits",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 18.dp),
        )
    }
}

@Composable
private fun HabitsGrid(
    habits: List<Habit>,
    todayEpochDay: Long,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onToggle: (String) -> Unit,
    onLongPress: (Habit) -> Unit,
    weekStart: dev.matejgroombridge.habittracker.data.settings.WeekStart,
    allowInverseHabits: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 0.dp,
            bottom = bottomPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = habits, key = { it.id }) { habit ->
            HabitCard(
                habit = habit,
                todayEpochDay = todayEpochDay,
                onClick = { onToggle(habit.id) },
                onLongClick = { onLongPress(habit) },
                weekStart = weekStart,
                allowInverseHabits = allowInverseHabits,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No habits yet.\nTap + to add your first one.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Sums two PaddingValues so the inner content respects both Scaffold padding and the bottom bar. */
internal fun mergePadding(a: PaddingValues, b: PaddingValues): PaddingValues = PaddingValues(
    start = a.calculateLeftPadding(LayoutDirection.Ltr) +
        b.calculateLeftPadding(LayoutDirection.Ltr),
    end = a.calculateRightPadding(LayoutDirection.Ltr) +
        b.calculateRightPadding(LayoutDirection.Ltr),
    top = a.calculateTopPadding() + b.calculateTopPadding(),
    bottom = a.calculateBottomPadding() + b.calculateBottomPadding(),
)
