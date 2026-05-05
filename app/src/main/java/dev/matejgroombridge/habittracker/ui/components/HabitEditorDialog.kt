package dev.matejgroombridge.habittracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

    /**
     * Delete is intentionally not exposed from this dialog — habits can only
     * be deleted from the Archive screen. Use [Archive] to archive first.
     */
    data class Archive(val archived: Boolean) : HabitEditorResult
}

/**
 * Single dialog for both creating and editing a habit. The icon badge in the
 * top-left opens a combined icon + colour picker. Colour is randomised on
 * creation (the user can change it from the picker if they want).
 *
 * For edit mode, archive is a small icon-only action at the top-right of the
 * dialog title row. Deletion is intentionally not available here — to delete
 * a habit the user must first archive it and then delete from the Archive
 * screen, which protects against accidental loss of completion history.
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
    var colorKey by remember {
        mutableStateOf(existing?.colorKey ?: HabitColors.palette.random().key)
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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isEdit) "Edit Habit" else "New Habit",
                    modifier = Modifier.weight(1f),
                )
                if (isEdit) {
                    val archived = existing?.archived == true
                    IconButton(onClick = {
                        onResult(HabitEditorResult.Archive(!archived))
                    }) {
                        Icon(
                            imageVector = if (archived) Icons.Outlined.Unarchive
                            else Icons.Outlined.Archive,
                            contentDescription = if (archived) "Restore" else "Archive",
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                // 14dp gives a comfortable rhythm between the title row,
                // description field, frequency picker, and NFC link without
                // the awkward "is this a new section or just whitespace?"
                // gaps a larger value created. Section headers contribute
                // no padding of their own — see SectionLabel{,WithHelp}.
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
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

                // Each section header is grouped with its content in a
                // tight Column so the gap between label and control is
                // visibly smaller (4dp) than the gap between sections
                // (the parent's 14dp spacedBy).
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Frequency")
                    FrequencyPicker(
                        kind = frequencyKind,
                        onKindChange = { frequencyKind = it },
                        intervalDays = intervalDays,
                        onIntervalChange = { intervalDays = it.coerceIn(1, 30) },
                    )
                }

                if (existing != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabelWithHelp(
                            text = "NFC Tag Link",
                            helpText = "Write this URL to an NFC tag with any tag-writer " +
                                "app. Scanning the tag will complete this habit. " +
                                "What happens on scan (background / overlay / open the " +
                                "app) is set in Settings → NFC.",
                        )
                        NfcLinkRow(url = existing.nfcUrl, habitName = existing.name)
                    }
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
        IconAndColorPickerDialog(
            selectedIconKey = iconKey,
            selectedColorKey = colorKey,
            onIconSelected = { iconKey = it },
            onColorSelected = { colorKey = it },
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
private fun IconAndColorPickerDialog(
    selectedIconKey: String,
    selectedColorKey: String,
    onIconSelected: (String) -> Unit,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an Icon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ColorRow(selectedKey = selectedColorKey, onSelected = onColorSelected)
                IconGrid(
                    selectedKey = selectedIconKey,
                    accent = HabitColors.entry(selectedColorKey).accent,
                    onSelected = onIconSelected,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun ColorRow(selectedKey: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HabitColors.palette.forEach { entry ->
            val selected = entry.key == selectedKey
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(entry.accent)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else Color.Black.copy(alpha = 0.10f),
                        shape = CircleShape,
                    )
                    .clickable { onSelected(entry.key) },
            )
        }
    }
}

@Composable
private fun IconGrid(selectedKey: String, accent: Color, onSelected: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
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
                        if (selected) accent
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { onSelected(entry.key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.label,
                    tint = if (selected) Color.Black.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
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
                label = { Text("Custom") },
            )
        }
        if (kind == FrequencyKind.EveryN) {
            // Compact pill-shaped stepper centred under the chips.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactStepper(
                    value = intervalDays,
                    onChange = onIntervalChange,
                    suffix = if (intervalDays == 1) "day" else "days",
                )
            }
        }
    }
}

@Composable
private fun CompactStepper(value: Int, onChange: (Int) -> Unit, suffix: String) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = Icons.Outlined.Remove,
            description = "Decrease",
            enabled = value > 1,
            onClick = { onChange(value - 1) },
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Every $value $suffix",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Spacer(Modifier.width(4.dp))
        StepperButton(
            icon = Icons.Outlined.Add,
            description = "Increase",
            enabled = value < 30,
            onClick = { onChange(value + 1) },
        )
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 1f else 0.4f,
            ),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Read-only display of the habit's NFC URL, with copy and share buttons.
 *
 * The user pastes this URL into any NFC-writer app and writes it to a tag.
 * Tapping the tag triggers `NfcCompletionActivity`, which marks the habit
 * complete and runs the action chosen in Settings.
 */
@Composable
private fun NfcLinkRow(url: String, habitName: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Nfc,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        IconButton(onClick = { copyToClipboard(context, url) }) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy NFC link",
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = { shareUrl(context, url, habitName) }) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share NFC link",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Section header that pairs a label with a small `?` icon. Tapping the icon
 * opens an in-place popover containing [helpText] — keeps the dialog from
 * being dominated by long explanatory paragraphs while still giving the
 * user a way to discover what the section does.
 */
@Composable
private fun SectionLabelWithHelp(text: String, helpText: String) {
    var showHelp by remember { mutableStateOf(false) }
    // No vertical padding here — vertical rhythm is owned by the parent
    // Column's `Arrangement.spacedBy`, so this header should be the same
    // visual height as a plain SectionLabel().
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(2.dp))
        Box {
            // Use a clickable Box rather than IconButton so the help icon
            // sits flush with the label — IconButton's 48dp min size adds
            // a noticeable amount of empty vertical space.
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { showHelp = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = "What's this?",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (showHelp) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopStart,
                    onDismissRequest = { showHelp = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                ) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(top = 28.dp)
                            .widthIn(max = 280.dp),
                    ) {
                        Text(
                            text = helpText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Habit NFC link", url))
    Toast.makeText(context, "NFC link copied", Toast.LENGTH_SHORT).show()
}

private fun shareUrl(context: Context, url: String, habitName: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "NFC link for $habitName")
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, "Share NFC link"))
}
