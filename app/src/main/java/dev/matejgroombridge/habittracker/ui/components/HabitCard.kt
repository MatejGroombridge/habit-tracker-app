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
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons
import dev.matejgroombridge.habittracker.ui.theme.containerColor
import dev.matejgroombridge.habittracker.ui.theme.contentColor

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
) {
    val color = HabitColors.entry(habit.colorKey)
    val iconEntry = HabitIcons.entry(habit.iconKey)

    val markedToday = habit.isCompletedOn(todayEpochDay)
    val visuallyCompleted = habit.isVisuallyCompletedOn(todayEpochDay)

    val baseContainer = color.containerColor()
    val baseContent = color.contentColor()

    val containerColor by animateColorAsState(
        targetValue = if (visuallyCompleted) blend(baseContainer, color.accent, 0.35f) else baseContainer,
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
                onClick = onClick,
                onLongClick = onLongClick,
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
                val subtitle = subtitleFor(habit, todayEpochDay, markedToday, visuallyCompleted)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = baseContent.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

private fun subtitleFor(
    habit: Habit,
    today: Long,
    markedToday: Boolean,
    visuallyCompleted: Boolean,
): String? = when (val f = habit.frequency) {
    HabitFrequency.Daily -> null
    HabitFrequency.Weekly ->
        if (visuallyCompleted && !markedToday) "Done this week" else "Weekly"
    is HabitFrequency.EveryNDays -> when {
        visuallyCompleted && !markedToday -> {
            val mostRecent = habit.completedDays.filter { it < today }.maxOrNull()
            if (mostRecent != null) {
                val daysLeft = (f.days - (today - mostRecent)).toInt().coerceAtLeast(0)
                if (daysLeft > 0) "Next due in $daysLeft day${if (daysLeft == 1) "" else "s"}"
                else "Due today"
            } else "Every ${f.days} days"
        }
        else -> if (f.days == 1) null else "Every ${f.days} days"
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
