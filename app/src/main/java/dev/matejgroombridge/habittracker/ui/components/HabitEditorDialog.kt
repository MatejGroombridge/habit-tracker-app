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
import androidx.compose.material3.Surface
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
    /**
     * Called when the user taps the "Write Tag" button on the NFC card.
     * The host is responsible for navigating to the NFC writer screen
     * (and optionally pre-selecting this habit). Pass null in create mode
     * — the NFC card is hidden until the habit has an id.
     */
    onWriteNfc: (() -> Unit)? = null,
    /**
     * When `true`, the frequency picker only exposes the Daily option.
     * Honoured when the global "Daily habits only" setting is on so the
     * user can't create a habit that would immediately violate it.
     * Existing non-daily habits opened in edit mode also lock to Daily
     * to keep state consistent.
     */
    dailyOnly: Boolean = false,
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
                is HabitFrequency.TimesPerWeek -> FrequencyKind.TimesPerWeek
            }
        )
    }
    var intervalDays by remember {
        mutableIntStateOf(
            (initialFrequency as? HabitFrequency.EveryNDays)?.days ?: 3
        )
    }
    var timesPerWeek by remember {
        mutableIntStateOf(
            (initialFrequency as? HabitFrequency.TimesPerWeek)?.times ?: 3
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
            FrequencyKind.TimesPerWeek -> HabitFrequency.TimesPerWeek(timesPerWeek.coerceIn(1, 7))
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
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                // Tighter rhythm — captions sit just above their cards
                // with 4dp inside CaptionedSection, and 12dp between
                // sections. Reads as a single tidy column rather than
                // separated islands.
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // --- Identity card: icon + title + description ---
                EditorSection(padding = 12.dp) {
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
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3,
                    )
                }

                // --- Frequency card ---
                CaptionedSection(caption = "Frequency") {
                    FrequencyPicker(
                        kind = frequencyKind,
                        onKindChange = { frequencyKind = it },
                        intervalDays = intervalDays,
                        onIntervalChange = { intervalDays = it.coerceIn(1, 30) },
                        timesPerWeek = timesPerWeek,
                        onTimesPerWeekChange = { timesPerWeek = it.coerceIn(1, 7) },
                        dailyOnly = dailyOnly,
                    )
                }

                // --- NFC actions card (edit mode only). Replaces the URL
                // display with three explicit verbs — copy / share / write
                // tag — so the dialog stays compact and the user never
                // sees a long bare-URL string. -----------------------
                if (existing != null) {
                    CaptionedSection(
                        caption = "NFC tag",
                        helpText = "Use this habit's link to fire a quick-complete from " +
                            "an NFC tag. Copy or share to write it with another app, " +
                            "or use Write Tag to scan and write directly. What " +
                            "happens on scan is set in Settings → NFC.",
                    ) {
                        NfcActionRow(
                            url = existing.nfcUrl,
                            habitName = existing.name,
                            onWriteTag = onWriteNfc,
                        )
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

private enum class FrequencyKind { Daily, Weekly, TimesPerWeek, EveryN }

/**
 * Compact help "?" icon. Tapping opens a small popover containing
 * [helpText]. Sized to sit flush with caption text without inflating
 * its row height.
 */
@Composable
private fun HelpIcon(helpText: String) {
    var showHelp by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(18.dp)
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
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(top = 24.dp)
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

/**
 * Rounded container that groups related controls — the editor equivalent
 * of [SettingsCard]. Keeps padding consistent across cards and makes it
 * easy to add new sections later.
 */
@Composable
private fun EditorSection(
    padding: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(padding)) { content() }
    }
}

/**
 * Caption (uppercased) + a help icon, then the contained card. Tight 4dp
 * spacing between caption and card so they read as a unit, with no padding
 * around the caption itself — the parent Column controls inter-section
 * spacing.
 */
@Composable
private fun CaptionedSection(
    caption: String,
    helpText: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp),
        ) {
            Text(
                text = caption.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (helpText != null) {
                Spacer(Modifier.width(4.dp))
                HelpIcon(helpText = helpText)
            }
        }
        EditorSection(padding = 12.dp) { content() }
    }
}

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

/**
 * 2×2 grid of equally-sized cards for the four frequency kinds, with a
 * stepper appearing below for the two kinds that take a number. The grid
 * layout means each card has predictable visual weight regardless of
 * label length, fixing the awkward "Daily is tiny, Custom is huge" look
 * the FlowRow chips had before.
 */
@Composable
private fun FrequencyPicker(
    kind: FrequencyKind,
    onKindChange: (FrequencyKind) -> Unit,
    intervalDays: Int,
    onIntervalChange: (Int) -> Unit,
    timesPerWeek: Int,
    onTimesPerWeekChange: (Int) -> Unit,
    dailyOnly: Boolean = false,
) {
    // When the dailyOnly setting is on, the host may have opened an existing
    // non-daily habit. Snap the local UI back to Daily as a defensive measure
    // so the picker reflects what will actually be saved.
    androidx.compose.runtime.LaunchedEffect(dailyOnly) {
        if (dailyOnly && kind != FrequencyKind.Daily) onKindChange(FrequencyKind.Daily)
    }
    if (dailyOnly) {
        // Single-card display so the picker still has visual presence; no
        // tap target needed since Daily is the only option.
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Daily (only option)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Row 1 — Daily, Weekly
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrequencyOption(
                label = "Daily",
                selected = kind == FrequencyKind.Daily,
                onClick = { onKindChange(FrequencyKind.Daily) },
                modifier = Modifier.weight(1f),
            )
            FrequencyOption(
                label = "Weekly",
                selected = kind == FrequencyKind.Weekly,
                onClick = { onKindChange(FrequencyKind.Weekly) },
                modifier = Modifier.weight(1f),
            )
        }
        // Row 2 — Times per week, Every N days
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrequencyOption(
                label = "Times per week",
                selected = kind == FrequencyKind.TimesPerWeek,
                onClick = { onKindChange(FrequencyKind.TimesPerWeek) },
                modifier = Modifier.weight(1f),
            )
            FrequencyOption(
                label = "Every N days",
                selected = kind == FrequencyKind.EveryN,
                onClick = { onKindChange(FrequencyKind.EveryN) },
                modifier = Modifier.weight(1f),
            )
        }
        when (kind) {
            FrequencyKind.EveryN -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactStepper(
                    value = intervalDays,
                    onChange = onIntervalChange,
                    label = { v -> "Every $v ${if (v == 1) "day" else "days"}" },
                )
            }
            FrequencyKind.TimesPerWeek -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactStepper(
                    value = timesPerWeek,
                    onChange = { onTimesPerWeekChange(it.coerceIn(1, 7)) },
                    label = { v -> "$v ${if (v == 1) "time" else "times"} per week" },
                )
            }
            else -> Unit
        }
    }
}

/**
 * One cell in the 2×2 frequency grid. Tinted accent when selected; plain
 * surface when not. Sized via the parent Row's `Modifier.weight(1f)` so
 * all four cells are visually identical regardless of label length.
 */
@Composable
private fun FrequencyOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CompactStepper(
    value: Int,
    onChange: (Int) -> Unit,
    label: (Int) -> String,
) {
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
            text = label(value),
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
 * Three pill-shaped actions for the habit's NFC URL — Copy, Share, Write.
 *
 * Replaces the old "show the URL + two icon buttons" row. Most users only
 * ever need the verbs (the URL itself is implementation detail), so we
 * omit it from the visible UI to keep the dialog short and uncluttered.
 *
 *  * **Copy** — copies the URL to the system clipboard.
 *  * **Share** — opens the system share sheet so the user can paste it
 *    into a tag-writer app of their choosing.
 *  * **Write Tag** — navigates to the in-app NFC writer (only available
 *    when [onWriteTag] is non-null).
 */
@Composable
private fun NfcActionRow(
    url: String,
    habitName: String,
    onWriteTag: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NfcAction(
            label = "Copy",
            icon = Icons.Outlined.ContentCopy,
            modifier = Modifier.weight(1f),
            onClick = { copyToClipboard(context, url) },
        )
        NfcAction(
            label = "Share",
            icon = Icons.Outlined.Share,
            modifier = Modifier.weight(1f),
            onClick = { shareUrl(context, url, habitName) },
        )
        if (onWriteTag != null) {
            NfcAction(
                label = "Write Tag",
                icon = Icons.Outlined.Nfc,
                modifier = Modifier.weight(1f),
                onClick = onWriteTag,
            )
        }
    }
}

/**
 * One of the three NFC verbs. Pill-style with stacked icon + label so all
 * three line up at the same height regardless of label length.
 */
@Composable
private fun NfcAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
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
