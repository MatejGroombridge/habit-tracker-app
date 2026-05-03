package dev.matejgroombridge.habittracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.SettingsViewModel
import dev.matejgroombridge.habittracker.ui.components.AddHabitDialog
import dev.matejgroombridge.habittracker.ui.components.DeleteHabitDialog
import dev.matejgroombridge.habittracker.ui.components.HabitCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Habit?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Habits") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add habit")
            }
        },
    ) { padding ->
        if (state.habits.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            HabitsGrid(
                habits = state.habits,
                todayEpochDay = state.todayEpochDay,
                showStreaks = settings.showStreaks,
                contentPadding = padding,
                onToggle = viewModel::toggleToday,
                onLongPress = { habit -> pendingDelete = habit },
            )
        }
    }

    if (showAddDialog) {
        AddHabitDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addHabit(name)
                showAddDialog = false
            },
        )
    }

    pendingDelete?.let { habit ->
        DeleteHabitDialog(
            habitName = habit.name,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.deleteHabit(habit.id)
                pendingDelete = null
            },
        )
    }
}

@Composable
private fun HabitsGrid(
    habits: List<Habit>,
    todayEpochDay: Long,
    showStreaks: Boolean,
    contentPadding: PaddingValues,
    onToggle: (String) -> Unit,
    onLongPress: (Habit) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp, // room for the FAB
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = habits, key = { it.id }) { habit ->
            HabitCard(
                habit = habit,
                todayEpochDay = todayEpochDay,
                showStreak = showStreaks,
                onClick = { onToggle(habit.id) },
                onLongClick = { onLongPress(habit) },
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
