package dev.matejgroombridge.habittracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.model.HabitFrequency
import dev.matejgroombridge.habittracker.data.settings.WeekStart
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons
import dev.matejgroombridge.habittracker.ui.theme.containerColor
import dev.matejgroombridge.habittracker.ui.theme.contentColor
import dev.matejgroombridge.habittracker.ui.util.rememberHaptics

/**
 * One habit tile. Background uses the habit's chosen pastel colour; the icon
 * tile uses the matching accent. Tap toggles today's completion; long-press
 * triggers [onLongClick] (used by the caller to open the edit dialog).
 *
 * For interval / weekly habits, the card stays visually completed until the
 * window ends — see [Habit.isVisuallyCompletedOn].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    habit: Habit,
    todayEpochDay: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    weekStart: WeekStart = WeekStart.Default,
    allowInverseHabits: Boolean = true,
) {
    val color = HabitColors.entry(habit.colorKey)
    val iconEntry = HabitIcons.entry(habit.iconKey)
    val haptics = rememberHaptics()

    val displayHabit = if (allowInverseHabits) habit else habit.copy(inverse = false)
    val markedToday = displayHabit.isCompletedOn(todayEpochDay)
    val visuallyCompleted = displayHabit.isVisuallyCompletedOn(todayEpochDay, weekStart.dayOfWeek)

    val baseContainer = color.containerColor()
    val baseContent = color.contentColor()
    val screenBg = MaterialTheme.colorScheme.background

    // Uncompleted cards are pulled slightly toward the screen background so
    // completed cards visually pop a bit more.
    val mutedContainer = blend(baseContainer, screenBg, 0.35f)

    val containerColor by animateColorAsState(
        targetValue = if (visuallyCompleted) blend(baseContainer, color.accent, 0.35f) else mutedContainer,
        label = "containerColor",
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    haptics.completion()
                    onClick()
                },
                onLongClick = {
                    haptics.longPress()
                    onLongClick()
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HabitIconTile(
                accent = color.accent,
                tinted = visuallyCompleted,
                icon = iconEntry.icon,
                contentDescription = iconEntry.label,
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = baseContent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                HabitSubtitle(
                    habit = displayHabit,
                    today = todayEpochDay,
                    markedToday = markedToday,
                    visuallyCompleted = visuallyCompleted,
                    color = baseContent.copy(alpha = 0.75f),
                    weekStart = weekStart,
                )
            }
        }
    }
}

@Composable
private fun HabitIconTile(
    accent: Color,
    tinted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(if (tinted) accent else accent.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.Black.copy(alpha = if (tinted) 0.85f else 0.55f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Renders the small text under the habit name. For interval habits that are
 * "still ticked because they were completed earlier in the window", we
 * previously showed `Resets in N day(s)` — but the narrow card width meant
 * the longer strings were cut off. Now we render a compact "🔄 N" pill
 * instead, which fits in any card width.
 */
@Composable
private fun HabitSubtitle(
    habit: Habit,
    today: Long,
    markedToday: Boolean,
    visuallyCompleted: Boolean,
    color: Color,
    weekStart: WeekStart,
) {
    when (val f = habit.frequency) {
        HabitFrequency.Daily -> Unit
        HabitFrequency.Weekly -> {
            val text = if (visuallyCompleted && !markedToday) "Done this week" else "Weekly"
            SubtitleText(text, color)
        }
        is HabitFrequency.EveryNDays -> {
            val isResting = visuallyCompleted && !markedToday
            if (isResting) {
                val mostRecent = habit.completedDays.filter { it < today }.maxOrNull()
                if (mostRecent != null) {
                    val daysLeft = (f.days - (today - mostRecent)).toInt().coerceAtLeast(0)
                    if (daysLeft > 0) {
                        ResetCountdown(daysLeft = daysLeft, color = color)
                    } else {
                        SubtitleText("Due today", color)
                    }
                } else {
                    SubtitleText("Every ${f.days} days", color)
                }
            } else if (f.days != 1) {
                SubtitleText("Every ${f.days} days", color)
            }
        }
        is HabitFrequency.TimesPerWeek -> {
            // Count completions in the current week (start day comes from
            // user settings via [weekStart] — see WeekMath.weekRange).
            // Renders as a calendar icon followed by the live count, so
            // the card subtitle stays compact and visually distinct from
            // the "in N days" reset countdown.
            val (startEpoch, endEpoch) = WeekMath.weekRange(today, weekStart)
            val countThisWeek = habit.completedDays.count { it in startEpoch..endEpoch }
            if (countThisWeek >= f.times) {
                SubtitleText("Done for the week", color)
            } else {
                WeeklyProgress(count = countThisWeek, target = f.times, color = color)
            }
        }
    }
}

@Composable
private fun WeeklyProgress(count: Int, target: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = "$count of $target this week",
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "$count of $target",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubtitleText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Compact "reset in N days" indicator: a small refresh glyph followed by the
 * number of remaining days. Replaces the older text-only "Resets in N day(s)"
 * label which got truncated on narrow cards.
 */
@Composable
private fun ResetCountdown(daysLeft: Int, color: Color) {
    val unit = if (daysLeft == 1) "day" else "days"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = "Resets in $daysLeft $unit",
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        // Reads as "[reset icon] in 3 days" — the prefix "in" makes the
        // countdown unambiguous; bare "[icon] 3" was easy to misread as a
        // count of resets rather than a duration.
        Text(
            text = "in $daysLeft $unit",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
        )
    }
}

/** Linear blend between [a] and [b] in straight RGB (good enough for pastels). */
private fun blend(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = a.red * (1 - u) + b.red * u,
        green = a.green * (1 - u) + b.green * u,
        blue = a.blue * (1 - u) + b.blue * u,
        alpha = a.alpha * (1 - u) + b.alpha * u,
    )
}
