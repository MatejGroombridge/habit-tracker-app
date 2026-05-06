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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons
import dev.matejgroombridge.habittracker.ui.util.rememberHaptics

/**
 * Settings → General → Reminders for habits.
 *
 * Lists every active habit with a switch controlling whether the habit is
 * included in the global daily reminder notification. Replaces the old
 * per-habit toggle that lived inside the habit overview dialog.
 *
 * Visual style mirrors a Settings card: rounded surface-container, a row
 * per habit, dividers between, fixed row min-height so a long list reads
 * cleanly. Each row also shows the habit's accent + icon as a small
 * leading badge so the user can recognise habits at a glance — these
 * aren't decorative icons (which we removed from Settings rows), they're
 * the habit's identity, so they belong here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitRemindersScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Reminders for Habits") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.light()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.activeHabits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No habits yet — create one and you can choose " +
                        "whether to include it in daily reminders here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Toggle which habits show up in your daily reminder " +
                    "notification. Off = the habit is silently skipped when " +
                    "the reminder fires.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyColumn {
                    itemsIndexed(state.activeHabits) { index, habit ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        HabitReminderRow(
                            habitName = habit.name,
                            colorKey = habit.colorKey,
                            iconKey = habit.iconKey,
                            included = habit.includeInReminders,
                            onToggle = {
                                haptics.light()
                                viewModel.setIncludeInReminders(
                                    habitId = habit.id,
                                    include = !habit.includeInReminders,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitReminderRow(
    habitName: String,
    colorKey: String,
    iconKey: String,
    included: Boolean,
    onToggle: () -> Unit,
) {
    val color = HabitColors.entry(colorKey)
    val iconEntry = HabitIcons.entry(iconKey)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Small habit-coloured badge so the user can recognise the habit
        // from its existing accent + icon. Not a settings "decoration"
        // icon — it's the habit's identity.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconEntry.icon,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = habitName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Switch(checked = included, onCheckedChange = { onToggle() })
    }
}

/**
 * LazyColumn doesn't have a built-in `itemsIndexed` for arbitrary lists in
 * older artifacts of `androidx.compose.foundation.lazy`. Wrap the call so
 * we don't depend on the exact import path. Equivalent to:
 *   items.forEachIndexed { i, item -> ... } inside a scope that adds them.
 */
private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<T>,
    crossinline content: @Composable (index: Int, item: T) -> Unit,
) {
    items.forEachIndexed { index, item ->
        item(key = "reminder_$index") { content(index, item) }
    }
}
