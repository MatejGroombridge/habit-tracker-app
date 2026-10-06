package dev.matejgroombridge.habittracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.habittracker.data.stats.Rate
import dev.matejgroombridge.habittracker.data.stats.pct

/** One bar: [label] under it, [detail] in the readout when it's selected. */
data class RateBar(val label: String, val detail: String, val rate: Rate)

/**
 * Single-series bar chart of completion rates on a fixed 0–100% scale.
 *
 * Tapping a bar selects it and shows its value in the readout line above —
 * the touch stand-in for a hover tooltip — so values are always one tap
 * away without printing a number on every bar. [initialSelection] picks
 * what's read out first (e.g. the weakest weekday).
 *
 * Bars with nothing counted (no data) render as a short neutral stub so the
 * gap reads as "no data", not "0%".
 */
@Composable
fun RateBars(
    bars: List<RateBar>,
    color: Color,
    modifier: Modifier = Modifier,
    initialSelection: Int = bars.lastIndex,
    chartHeight: Dp = 72.dp,
) {
    var selected by remember(bars) { mutableIntStateOf(initialSelection.coerceIn(0, bars.lastIndex)) }
    val stub = MaterialTheme.colorScheme.surfaceContainerHighest
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        bars.getOrNull(selected)?.let { bar ->
            Text(
                text = bar.rate.fraction?.let { "${bar.detail} · ${pct(it)} (${bar.rate.done} of ${bar.rate.total})" }
                    ?: "${bar.detail} · no data",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { i, bar ->
                val fraction = bar.rate.fraction
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // Whole column is the hit target, not just the bar.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { selected = i }
                        .semantics {
                            contentDescription = "${bar.detail}: " + (fraction?.let(::pct) ?: "no data")
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction?.coerceAtLeast(0.03f) ?: 0.03f)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                when {
                                    fraction == null -> stub
                                    i == selected -> color
                                    else -> color.copy(alpha = 0.5f)
                                },
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            bars.forEachIndexed { i, bar ->
                // A label may be wider than its bar (e.g. "29 Sep" over a
                // week): let it spill, centred, into its neighbours rather
                // than wrap — callers sparse-label dense charts for this.
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface else muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                    )
                }
            }
        }
    }
}
