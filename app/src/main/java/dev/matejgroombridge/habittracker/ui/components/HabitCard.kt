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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.habittracker.data.model.Habit

/**
 * One habit tile, styled to match the Groom Hub `AppRow` aesthetic — a rectangular
 * `Surface` with `surfaceContainer` background, 20.dp rounded corners, an icon on
 * the left and the habit's name on the right. No button and no helper text.
 *
 * Tap toggles today's completion; long-press triggers [onLongClick] (used by the
 * caller to show a delete confirmation).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    habit: Habit,
    todayEpochDay: Long,
    showStreak: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val completed = habit.isCompletedOn(todayEpochDay)

    val containerColor by animateColorAsState(
        targetValue = if (completed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "containerColor",
    )
    val onContainerColor by animateColorAsState(
        targetValue = if (completed) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "onContainerColor",
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
            HabitIcon(completed = completed)
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainerColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showStreak) {
                    val streak = habit.currentStreak(todayEpochDay)
                    if (streak > 0) {
                        Text(
                            text = "🔥 $streak day${if (streak == 1L) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainerColor.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitIcon(completed: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(
                if (completed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (completed) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = if (completed) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(26.dp),
        )
    }
}
