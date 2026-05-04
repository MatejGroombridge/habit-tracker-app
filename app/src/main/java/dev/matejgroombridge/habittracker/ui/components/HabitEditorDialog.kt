package dev.matejgroombridge.habittracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.model.HabitFrequency
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons

/** Result emitted by [HabitEditorDialog] when the user takes an action. */
sealed interface HabitEditorResult {
    data class Save(
        val name: String,
        val description: String,
        val iconKey: String,
        val colorKey: String,
        val frequency: HabitFrequency,
    ) : HabitEditorResult

    data object Delete : HabitEditorResult
    data class Archive(val archived: Boolean) : HabitEditorResult
}

/**
 * Single dialog used both for creating and editing a habit. Colour is
 * assigned randomly on creation (no user picker); icon is selected by tapping
 * the current icon to open a picker dialog.
 */
@Composable
fun HabitEditorDialog(
    existing: Habit?,
    onDismiss: () -> Unit,
    onResult: (HabitEditorResult) -> Unit,
) {
    val isEdit = existing != null

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: Habit.DEFAULT_ICON_KEY) }
    // Colour is randomised on creation and preserved on edit.
    val colorKey = remember(existing?.id) {
        existing?.colorKey ?: HabitColors.palette.random().key
    }

    val initialFrequency = existing?.frequency ?: HabitFrequency.Daily
    var frequencyKind by remember {
        mutableStateOf(
            when (initialFrequency) {
                HabitFrequency.Daily -> FrequencyKind.Daily
                HabitFrequency.Weekly -> FrequencyKind.Weekly
                is HabitFrequency.EveryNDays -> FrequencyKind.EveryN
            }
        )
    }
    var intervalDays by remember {
        mutableIntStateOf(
            (initialFrequency as? HabitFrequency.EveryNDays)?.days ?: 3
        )
    }

    var showIconPicker by remember { mutableStateOf(false) }

    val canSave = name.trim().isNotEmpty()

    fun submit() {
        if (!canSave) return
        val frequency = when (frequencyKind) {
            FrequencyKind.Daily -> HabitFrequency.Daily
            FrequencyKind.Weekly -> HabitFrequency.Weekly
            FrequencyKind.EveryN -> HabitFrequency.EveryNDays(intervalDays.coerceAtLeast(1))
        }
        onResult(
            HabitEditorResult.Save(
                name = name,
                description = description,
                iconKey = iconKey,
                colorKey = colorKey,
                frequency = frequency,
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit habit" else "New habit") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Icon + title share a row: tap icon to change it.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBadge(
                        iconKey = iconKey,
                        colorKey = colorKey,
                        onClick = { showIconPicker = true },
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Title") },
                        placeholder = { Text("e.g. Drink water") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    minLines = 1,
                    maxLines = 3,
                )

                SectionLabel("Frequency")
                FrequencyPicker(
                    kind = frequencyKind,
                    onKindChange = { frequencyKind = it },
                    intervalDays = intervalDays,
                    onIntervalChange = { intervalDays = it.coerceIn(1, 30) },
                )

                if (isEdit) {
                    Spacer(Modifier.height(2.dp))
                    EditorActionRow(
                        archived = existing?.archived == true,
                        onArchiveToggle = {
                            onResult(HabitEditorResult.Archive(!(existing?.archived ?: false)))
                        },
                        onDelete = { onResult(HabitEditorResult.Delete) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = canSave) {
                Text(if (isEdit) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showIconPicker) {
        IconPickerDialog(
            selectedKey = iconKey,
            onSelected = {
                iconKey = it
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false },
        )
    }
}

private enum class FrequencyKind { Daily, Weekly, EveryN }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun IconBadge(iconKey: String, colorKey: String, onClick: () -> Unit) {
    val color = HabitColors.entry(colorKey)
    val icon = HabitIcons.entry(iconKey)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon.icon,
            contentDescription = "Change icon",
            tint = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun IconPickerDialog(
    selectedKey: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an icon") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    count = HabitIcons.catalog.size,
                    key = { HabitIcons.catalog[it].key },
                ) { index ->
                    val entry = HabitIcons.catalog[index]
                    val selected = entry.key == selectedKey
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .clickable { onSelected(entry.key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = entry.label,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun FrequencyPicker(
    kind: FrequencyKind,
    onKindChange: (FrequencyKind) -> Unit,
    intervalDays: Int,
    onIntervalChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = kind == FrequencyKind.Daily,
                onClick = { onKindChange(FrequencyKind.Daily) },
                label = { Text("Daily") },
            )
            FilterChip(
                selected = kind == FrequencyKind.Weekly,
                onClick = { onKindChange(FrequencyKind.Weekly) },
                label = { Text("Weekly") },
            )
            FilterChip(
                selected = kind == FrequencyKind.EveryN,
                onClick = { onKindChange(FrequencyKind.EveryN) },
                label = { Text("Every X days") },
            )
        }
        if (kind == FrequencyKind.EveryN) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Every", style = MaterialTheme.typography.bodyMedium)
                IntervalStepper(value = intervalDays, onChange = onIntervalChange)
                Text(
                    text = if (intervalDays == 1) "day" else "days",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun IntervalStepper(value: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AssistChip(onClick = { onChange(value - 1) }, label = { Text("−") })
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        }
        AssistChip(onClick = { onChange(value + 1) }, label = { Text("+") })
    }
}

@Composable
private fun EditorActionRow(
    archived: Boolean,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onArchiveToggle) {
            Icon(
                imageVector = if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (archived) "Restore" else "Archive")
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }
}
