package dev.matejgroombridge.habittracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.model.HabitFrequency
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Read-only "habit overview" sheet shown when the user long-presses a habit
 * card. Designed to be a polished snapshot rather than a control surface — a
 * single Edit button (top-right) is the only interaction, and routes to the
 * existing [HabitEditorDialog]. Tapping outside or the close button dismisses.
 *
 * Surfaced metrics:
 *  - Large coloured icon badge using the habit's accent.
 *  - Habit name + description (if present) + frequency chip.
 *  - Three big stats: current streak, top streak, total completions.
 *  - "Last 7 days" mini-strip so trends are visible at a glance.
 *  - Footer line with creation date / completion rate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitOverviewDialog(
    habit: Habit,
    todayEpochDay: Long,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleSkipToday: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    /**
     * Whether to expose the per-habit "include in reminders" toggle.
     * Pass `false` (or just omit) when the global daily-reminders setting
     * is off, so the row doesn't appear stranded with no global behaviour
     * to gate.
     */
    showRemindersToggle: Boolean = false,
    onToggleIncludeInReminders: () -> Unit = {},
) {
    val color = HabitColors.entry(habit.colorKey)
    val iconEntry = HabitIcons.entry(habit.iconKey)

    val currentStreak = remember(habit.completedDays, todayEpochDay) {
        habit.currentStreak(todayEpochDay)
    }
    val topStreak = remember(habit.completedDays) { habit.longestStreak() }
    val totalCompletions = habit.completedDays.size
    val daysSinceCreated = (todayEpochDay - habit.createdAtEpochDay).coerceAtLeast(0L)
    val completionRate = remember(habit.completedDays, daysSinceCreated) {
        // Use createdAtEpochDay as the denominator so a brand-new habit
        // doesn't read 0% on day zero — `+1` makes day-of-creation count
        // as one full day.
        val days = (daysSinceCreated + 1).coerceAtLeast(1L)
        ((totalCompletions.toDouble() / days.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                // Header row: large icon, name + frequency chip on the left;
                // edit + close icons pinned to the right.
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LargeIconBadge(accent = color.accent, icon = iconEntry.icon)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = habit.name,
                            // Single-line + ellipsis so unusually long habit
                            // names don't push the icon row down. Tap-outside
                            // still dismisses the dialog so we don't need an
                            // explicit close button.
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                        Spacer(Modifier.height(4.dp))
                        FrequencyChip(text = HabitFrequency.describe(habit.frequency), accent = color.accent)
                    }
                    // Skip + Pause sit alongside the existing edit / close
                    // icons so the action surface stays in one row at the
                    // top — the body of the dialog now reads as pure stats.
                    // Active states tint with the habit's accent so the
                    // user can see at a glance what's currently set.
                    HeaderActionIcon(
                        icon = Icons.Outlined.SkipNext,
                        contentDescription = if (habit.isSkippedOn(todayEpochDay)) "Unskip today"
                        else "Skip today",
                        active = habit.isSkippedOn(todayEpochDay),
                        accent = color.accent,
                        onClick = onToggleSkipToday,
                    )
                    HeaderActionIcon(
                        icon = if (habit.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = if (habit.isPaused) "Resume habit" else "Pause habit",
                        active = habit.isPaused,
                        accent = color.accent,
                        onClick = onTogglePause,
                    )
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit habit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (habit.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = habit.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Three big stat tiles. Equal-weight Row so they stretch
                // to fill the dialog width regardless of value length.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        label = "Current",
                        value = currentStreak.toString(),
                        icon = Icons.Outlined.LocalFireDepartment,
                        accent = color.accent,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Best",
                        value = topStreak.toString(),
                        icon = Icons.Outlined.EmojiEvents,
                        accent = color.accent,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Total",
                        value = totalCompletions.toString(),
                        icon = Icons.Outlined.TaskAlt,
                        accent = color.accent,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Last 7 days",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LastSevenDaysStrip(
                    habit = habit,
                    todayEpochDay = todayEpochDay,
                    accent = color.accent,
                )

                if (showRemindersToggle) {
                    Spacer(Modifier.height(14.dp))
                    // Compact switch row matching the action-button corners
                    // and surface so the dialog still feels like one unit.
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = onToggleIncludeInReminders,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = if (habit.includeInReminders)
                                    "Included in daily reminders"
                                else "Hidden from daily reminders",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            androidx.compose.material3.Switch(
                                checked = habit.includeInReminders,
                                onCheckedChange = { onToggleIncludeInReminders() },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Compact footer with creation context + completion rate.
                FooterStats(
                    daysSinceCreated = daysSinceCreated,
                    completionRate = completionRate,
                )
            }
        }
    }
}

/**
 * Compact icon-only action used in the overview dialog header. When [active]
 * the icon sits inside a tinted circular badge so the user can see at a
 * glance whether the action is currently set (e.g. paused / skipped today).
 */
@Composable
private fun HeaderActionIcon(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val tint = if (active) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    val bg = if (active) accent.copy(alpha = 0.35f) else Color.Transparent
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(bg, shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

@Composable
private fun LargeIconBadge(accent: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun FrequencyChip(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.25f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 7-cell row showing whether the habit was completed each of the last 7
 * days. Today is the rightmost cell; uses [Habit.isCompletedOn] (not the
 * "visually completed" variant) so the strip reflects raw completion
 * history rather than interval cosmetics.
 */
@Composable
private fun LastSevenDaysStrip(
    habit: Habit,
    todayEpochDay: Long,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (offset in 6 downTo 0) {
            val day = todayEpochDay - offset
            val date = LocalDate.ofEpochDay(day)
            val completed = habit.isCompletedOn(day)
            val isToday = offset == 0
            DayCell(
                label = dayLabel(date.dayOfWeek),
                completed = completed,
                isToday = isToday,
                accent = accent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayCell(
    label: String,
    completed: Boolean,
    isToday: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(
                    if (completed) Modifier.background(accent)
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                .then(
                    if (isToday) Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                Icon(
                    imageVector = Icons.Outlined.TaskAlt,
                    contentDescription = "Completed",
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "M"
    DayOfWeek.TUESDAY -> "T"
    DayOfWeek.WEDNESDAY -> "W"
    DayOfWeek.THURSDAY -> "T"
    DayOfWeek.FRIDAY -> "F"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "S"
}

@Composable
private fun FooterStats(
    daysSinceCreated: Long,
    completionRate: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (daysSinceCreated == 0L) "Created today"
            else "Tracking for ${daysSinceCreated} day${if (daysSinceCreated == 1L) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$completionRate% completion",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
