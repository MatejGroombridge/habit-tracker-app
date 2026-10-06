package dev.matejgroombridge.habittracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A paused day drawn as the pause glyph itself: the whole cell filled in
 * [color] with its middle third left clear. Unlike an icon inside the cell
 * this stays legible at the All Time grid's 12dp, and a paused stretch
 * reads as one run of ‖ bars.
 *
 * Size it with [modifier]; [shape] should match the neighbouring cells so
 * the outer corners line up.
 */
@Composable
fun PausedCell(color: Color, shape: Shape, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(shape)
            .semantics { contentDescription = "Paused" },
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().background(color))
        Spacer(Modifier.weight(1f))
        Box(Modifier.weight(1f).fillMaxHeight().background(color))
    }
}
