package dev.matejgroombridge.habittracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.habittracker.data.stats.HabitStats
import dev.matejgroombridge.habittracker.data.stats.HabitStatsSummary
import dev.matejgroombridge.habittracker.data.stats.pct
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.components.RateBar
import dev.matejgroombridge.habittracker.ui.components.RateBars
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * One daily habit in depth: rates, streaks, when in the week it slips,
 * how fast it recovers from a miss, and which habits it tends to go with.
 * Opened from a habit row or a habit's insight on the Stats tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitStatsScreen(
    viewModel: HomeViewModel,
    habitId: String,
    onBack: () -> Unit,
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val summary = stats.habits.firstOrNull { it.habit.id == habitId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = summary?.habit?.name ?: "Habit",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (summary == null) {
            // Archived, made non-daily, or deleted while open.
            EmptyState(modifier = Modifier.padding(padding), message = "No stats for this habit.")
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeaderCard(summary)
            summary.formationDay?.let { FormationCard(it) }
            WeekdayCard(summary)
            MonthlyCard(summary)
            RecoveryCard(summary)
            if (summary.pairs.isNotEmpty()) PairsCard(summary)
        }
    }
}

private val SINCE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
private fun HeaderCard(s: HabitStatsSummary) {
    val color = HabitColors.entry(s.habit.colorKey)
    val icon = HabitIcons.entry(s.habit.iconKey)
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon.icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                HeroNumber(value = s.last30.fraction?.let(::pct) ?: "—", label = "last 30 days")
            }
        }
        TrendLine(current = s.last30, previous = s.previous30, comparedTo = "the 30 before")
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MiniStat("All time", s.allTime.fraction?.let(::pct) ?: "—", Modifier.weight(1f))
            MiniStat("Streak", s.currentStreak.toString(), Modifier.weight(1f))
            MiniStat("Best", s.bestStreak.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        val since = LocalDate.ofEpochDay(s.habit.realStartEpochDay).format(SINCE_FORMAT)
        Text(
            text = if (s.habit.trackedSinceEpochDay != null) "Tracked since $since (earlier history was backfilled)"
            else "Tracked since $since",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FormationCard(day: Int) {
    StatsCard {
        Text(
            text = "Day $day of ~${HabitStats.FORMATION_DAYS}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { day.toFloat() / HabitStats.FORMATION_DAYS },
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "A new habit takes about ${HabitStats.FORMATION_DAYS} days on average to become " +
                "automatic. Missing the odd day doesn't reset that.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun WeekdayCard(s: HabitStatsSummary) {
    val accent = HabitColors.entry(s.habit.colorKey).accent
    val order = DayOfWeek.entries
    val weakest = order.indices
        .filter { s.weekdays.getValue(order[it]).total > 0 }
        .minByOrNull { s.weekdays.getValue(order[it]).fraction ?: 1f } ?: 0
    StatsCard {
        CardTitle("By weekday · last 12 weeks")
        RateBars(
            bars = order.map { dow ->
                RateBar(
                    label = dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    detail = dow.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    rate = s.weekdays.getValue(dow),
                )
            },
            color = accent,
            // Lead with the weakest day: it's the one worth planning for.
            initialSelection = weakest,
        )
    }
}

@Composable
private fun MonthlyCard(s: HabitStatsSummary) {
    val accent = HabitColors.entry(s.habit.colorKey).accent
    StatsCard {
        CardTitle("By month")
        RateBars(
            bars = s.monthly.map { m ->
                RateBar(
                    label = m.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    detail = m.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${m.month.year}",
                    rate = m.rate,
                )
            },
            color = accent,
        )
    }
}

@Composable
private fun RecoveryCard(s: HabitStatsSummary) {
    val bounced = s.bouncedBackNextDay
    StatsCard {
        CardTitle("After a miss")
        val text = if (bounced == null || s.averageMissRun == null) {
            "No misses to recover from yet."
        } else {
            val avg = "%.1f".format(s.averageMissRun)
            "You're back the next day ${bounced.fraction?.let(::pct)} of the time " +
                "(${bounced.done} of ${bounced.total}). A gap lasts $avg days on average — " +
                "the shorter it stays, the less a miss costs."
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PairsCard(s: HabitStatsSummary) {
    StatsCard {
        CardTitle("Goes well with")
        s.pairs.forEachIndexed { i, pair ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Text(
                text = pair.otherName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${s.habit.name} gets done ${pair.whenOtherDone.fraction?.let(::pct)} of the time on " +
                    "days you do it, vs ${pair.whenOtherMissed.fraction?.let(::pct)} when you don't. " +
                    "Try doing them back to back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
