package dev.matejgroombridge.habittracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.habittracker.data.stats.DaySummary
import dev.matejgroombridge.habittracker.data.stats.HabitStatsSummary
import dev.matejgroombridge.habittracker.data.stats.Insight
import dev.matejgroombridge.habittracker.data.stats.InsightKind
import dev.matejgroombridge.habittracker.data.stats.Rate
import dev.matejgroombridge.habittracker.data.stats.StatsSnapshot
import dev.matejgroombridge.habittracker.data.stats.pct
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.components.RateBar
import dev.matejgroombridge.habittracker.ui.components.RateBars
import dev.matejgroombridge.habittracker.ui.theme.HabitColors
import dev.matejgroombridge.habittracker.ui.theme.HabitIcons
import dev.matejgroombridge.habittracker.ui.theme.containerColor
import dev.matejgroombridge.habittracker.ui.theme.contentColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The Stats tab: feedback meant to change behaviour, not just record it.
 *
 * Reading order is deliberate — headline numbers (perfect days, 30-day
 * consistency), then one combined calendar of every day, then insights
 * that each suggest an action, then habits weakest-first so the list reads
 * as "where attention pays off". Consistency leads over streaks: a rate
 * dips gently after a miss where a streak drops to zero.
 *
 * Covers daily habits only and never counts backfilled history; see
 * [dev.matejgroombridge.habittracker.data.stats.HabitStats].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenHabit: (String) -> Unit,
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (!stats.hasData) {
            EmptyState(
                modifier = Modifier.padding(padding),
                message = "No daily habits to analyse yet.\nStats cover daily habits from the day you start tracking them.",
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "perfect") { PerfectDaysCard(stats) }
            item(key = "consistency") { ConsistencyCard(stats) }
            item(key = "calendar") { CalendarCard(stats) }

            if (stats.insights.isNotEmpty()) {
                item(key = "insights-caption") { StatsCaption("Insights") }
                items(items = stats.insights, key = { "insight-${it.kind}" }) { insight ->
                    val habit = stats.habits.firstOrNull { it.habit.id == insight.habitId }?.habit
                    InsightCard(
                        insight = insight,
                        colorKey = habit?.colorKey,
                        onClick = habit?.let { h -> { onOpenHabit(h.id) } },
                    )
                }
            }

            if (stats.habits.isNotEmpty()) {
                item(key = "habits-caption") { StatsCaption("Habits · weakest first") }
                items(items = stats.habits, key = { "habit-${it.habit.id}" }) { summary ->
                    HabitStatsRow(summary = summary, onClick = { onOpenHabit(summary.habit.id) })
                }
            }

            item(key = "footnote") {
                Text(
                    text = "Daily habits only. Backfilled history isn't counted, and paused " +
                        "or skipped days count neither for nor against you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun StatsCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
internal fun StatsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/** Big number + what it counts, the hero of a card. */
@Composable
internal fun HeroNumber(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/** Small labelled figure for a row of secondary stats. */
@Composable
internal fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PerfectDaysCard(stats: StatsSnapshot) {
    val p = stats.perfect
    StatsCard {
        HeroNumber(value = p.total.toString(), label = if (p.total == 1) "perfect day" else "perfect days")
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MiniStat("This month", p.thisMonth.toString(), Modifier.weight(1f))
            MiniStat("Current run", p.currentRun.toString(), Modifier.weight(1f))
            MiniStat("Best run", p.bestRun.toString(), Modifier.weight(1f))
        }
        if (p.nearPerfectLast30 > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${p.nearPerfectLast30} more ${if (p.nearPerfectLast30 == 1) "day was" else "days were"} " +
                    "one habit short in the last 30.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConsistencyCard(stats: StatsSnapshot) {
    val c = stats.consistency
    val now = c.last30.fraction
    StatsCard {
        HeroNumber(value = now?.let(::pct) ?: "—", label = "consistency, last 30 days")
        TrendLine(current = c.last30, previous = c.previous30, comparedTo = "the 30 before")
        Spacer(Modifier.height(16.dp))
        val fmt = remember { DateTimeFormatter.ofPattern("d MMM") }
        RateBars(
            bars = c.weekly.mapIndexed { i, w ->
                val start = LocalDate.ofEpochDay(w.startEpochDay)
                RateBar(
                    // Label every 4th week so the axis stays legible.
                    label = if ((c.weekly.lastIndex - i) % 4 == 0) start.format(fmt) else "",
                    detail = "Week from ${start.format(fmt)}",
                    rate = w.rate,
                )
            },
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** "▲ 6 pts vs …" in text colours; the arrow carries direction, not colour. */
@Composable
internal fun TrendLine(current: Rate, previous: Rate, comparedTo: String) {
    val now = current.fraction ?: return
    val before = previous.fraction ?: return
    val delta = ((now - before) * 100).roundToInt()
    val (icon, text) = when {
        delta > 0 -> Icons.AutoMirrored.Outlined.TrendingUp to "Up $delta pts on $comparedTo"
        delta < 0 -> Icons.AutoMirrored.Outlined.TrendingDown to "Down ${-delta} pts on $comparedTo"
        else -> Icons.AutoMirrored.Outlined.TrendingFlat to "Level with $comparedTo"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val CAL_CELL = 12.dp
private val CAL_GAP = 3.dp

/**
 * Every tracked day in one grid (weeks as columns, newest on the right),
 * shaded on a single-hue ramp by the share of habits done, with perfect
 * days at full strength. Tap a day to read it out.
 */
@Composable
private fun CalendarCard(stats: StatsSnapshot) {
    val byDay = remember(stats.days) { stats.days.associateBy { it.day } }
    val today = stats.today
    // Columns run Monday → Sunday, matching the All Time grid.
    val firstMonday = remember(stats.firstEpochDay) {
        val d = LocalDate.ofEpochDay(stats.firstEpochDay)
        stats.firstEpochDay - (d.dayOfWeek.value - DayOfWeek.MONDAY.value)
    }
    val lastSunday = remember(today) {
        val d = LocalDate.ofEpochDay(today)
        today + (DayOfWeek.SUNDAY.value - d.dayOfWeek.value)
    }
    val weeks = ((lastSunday - firstMonday + 1) / 7).toInt()
    var selected by remember(stats.days) { mutableStateOf(stats.days.last()) }

    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest

    StatsCard {
        Text(
            text = "Every day",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = describeDay(selected, today),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fits = ((maxWidth + CAL_GAP) / (CAL_CELL + CAL_GAP)).toInt()
            val scroll = rememberScrollState()
            LaunchedEffect(weeks) { scroll.scrollTo(scroll.maxValue) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (weeks > fits) Modifier.horizontalScroll(scroll) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(CAL_GAP, Alignment.End),
            ) {
                for (w in 0 until weeks) {
                    Column(verticalArrangement = Arrangement.spacedBy(CAL_GAP)) {
                        for (offset in 0..6) {
                            val day = firstMonday + w * 7L + offset
                            val summary = byDay[day]
                            val fill = when {
                                summary == null || day > today -> empty.copy(alpha = 0.4f)
                                summary.isPerfect -> primary
                                else -> summary.fraction?.let { primary.copy(alpha = rampAlpha(it)) } ?: empty
                            }
                            Box(
                                modifier = Modifier
                                    .size(CAL_CELL)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(fill)
                                    .then(
                                        if (summary == null) Modifier
                                        else Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { selected = summary },
                                    ),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Legend: the ramp from fewest done to perfect.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fewer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            listOf(empty, primary.copy(alpha = rampAlpha(0.2f)), primary.copy(alpha = rampAlpha(0.5f)),
                primary.copy(alpha = rampAlpha(0.9f)), primary).forEach { c ->
                Box(
                    Modifier
                        .padding(end = CAL_GAP)
                        .size(CAL_CELL)
                        .clip(RoundedCornerShape(3.dp))
                        .background(c),
                )
            }
            Spacer(Modifier.width(3.dp))
            Text("Perfect", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Three steps below full strength, so "perfect" is unmistakable. */
private fun rampAlpha(fraction: Float): Float = when {
    fraction < 1f / 3 -> 0.22f
    fraction < 2f / 3 -> 0.42f
    else -> 0.64f
}

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

private fun describeDay(d: DaySummary, today: Long): String {
    val date = if (d.day == today) "Today" else LocalDate.ofEpochDay(d.day).format(DAY_FORMAT)
    return when {
        d.isPerfect -> "$date · perfect day"
        d.isNeutral -> "$date · nothing due"
        d.day == today -> "$date · ${d.done} of ${d.done + d.pending} done so far"
        else -> "$date · ${d.done} of ${d.done + d.missed} done"
    }
}

@Composable
private fun InsightCard(insight: Insight, colorKey: String?, onClick: (() -> Unit)?) {
    val entry = colorKey?.let { HabitColors.entry(it) }
    val container = entry?.containerColor() ?: MaterialTheme.colorScheme.surfaceContainer
    val content = entry?.contentColor() ?: MaterialTheme.colorScheme.onSurface
    val tile = entry?.accent ?: MaterialTheme.colorScheme.primaryContainer
    val tileContent = if (entry != null) Color.Black.copy(alpha = 0.85f)
    else MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(insight.kind.icon(), contentDescription = null, tint = tileContent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = insight.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private fun InsightKind.icon(): ImageVector = when (this) {
    InsightKind.NeverMissTwice -> Icons.Outlined.Replay
    InsightKind.NearBest -> Icons.Outlined.EmojiEvents
    InsightKind.BestStreak -> Icons.Outlined.LocalFireDepartment
    InsightKind.BlockingPerfect -> Icons.Outlined.Block
    InsightKind.Slipping -> Icons.AutoMirrored.Outlined.TrendingDown
    InsightKind.WeakWeekday -> Icons.Outlined.EventBusy
    InsightKind.Keystone -> Icons.Outlined.Hub
    InsightKind.TooMany -> Icons.Outlined.Layers
    InsightKind.Graduated -> Icons.Outlined.School
    InsightKind.Forming -> Icons.Outlined.Spa
}

@Composable
private fun HabitStatsRow(summary: HabitStatsSummary, onClick: () -> Unit) {
    val habit = summary.habit
    val color = HabitColors.entry(habit.colorKey)
    val icon = HabitIcons.entry(habit.iconKey)
    val rate = summary.last30.fraction
    val prev = summary.previous30.fraction

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon.icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("${summary.currentStreak}-day streak · best ${summary.bestStreak}")
                        if (habit.isPaused) append(" · paused")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = rate?.let(::pct) ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (rate != null && prev != null) {
                    val delta = ((rate - prev) * 100).roundToInt()
                    Text(
                        text = when {
                            delta > 0 -> "▲ $delta"
                            delta < 0 -> "▼ ${-delta}"
                            else -> "level"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics {
                            contentDescription = when {
                                delta > 0 -> "Up $delta points on the previous 30 days"
                                delta < 0 -> "Down ${-delta} points on the previous 30 days"
                                else -> "Level with the previous 30 days"
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
