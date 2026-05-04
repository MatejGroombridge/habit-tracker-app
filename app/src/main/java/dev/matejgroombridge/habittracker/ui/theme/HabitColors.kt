package dev.matejgroombridge.habittracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * A pastel-tone palette used to colour habit cards. Each entry has a light
 * variant (used as the card background in light mode) and a dark variant
 * (used in dark mode), plus a stronger accent for the icon tile.
 *
 * Stored on a habit by [key] so the palette can be re-ordered or extended
 * without breaking persisted data — unknown keys fall back to [defaultEntry].
 */
data class HabitColorEntry(
    val key: String,
    val label: String,
    val light: Color,
    val dark: Color,
    val accent: Color,
    val onColor: Color,
)

object HabitColors {

    val palette: List<HabitColorEntry> = listOf(
        HabitColorEntry(
            key = "blush",
            label = "Blush",
            light = Color(0xFFFFE0E6),
            dark = Color(0xFF5A3A42),
            accent = Color(0xFFF7A6B5),
            onColor = Color(0xFF3A1F25),
        ),
        HabitColorEntry(
            key = "peach",
            label = "Peach",
            light = Color(0xFFFFE3D1),
            dark = Color(0xFF5A3F30),
            accent = Color(0xFFFFB48A),
            onColor = Color(0xFF3A2418),
        ),
        HabitColorEntry(
            key = "butter",
            label = "Butter",
            light = Color(0xFFFFF4C2),
            dark = Color(0xFF55502B),
            accent = Color(0xFFFFE066),
            onColor = Color(0xFF3A330A),
        ),
        HabitColorEntry(
            key = "mint",
            label = "Mint",
            light = Color(0xFFD1F0DA),
            dark = Color(0xFF2E4D3A),
            accent = Color(0xFF8DD6A4),
            onColor = Color(0xFF143222),
        ),
        HabitColorEntry(
            key = "sage",
            label = "Sage",
            light = Color(0xFFDDE8D2),
            dark = Color(0xFF3D4A33),
            accent = Color(0xFFA8C690),
            onColor = Color(0xFF1F2A14),
        ),
        HabitColorEntry(
            key = "sky",
            label = "Sky",
            light = Color(0xFFD3E8F5),
            dark = Color(0xFF2F4756),
            accent = Color(0xFF8FC4E0),
            onColor = Color(0xFF12303F),
        ),
        HabitColorEntry(
            key = "lavender",
            label = "Lavender",
            light = Color(0xFFE3DAF5),
            dark = Color(0xFF3F354F),
            accent = Color(0xFFB7A5DD),
            onColor = Color(0xFF231A38),
        ),
        HabitColorEntry(
            key = "lilac",
            label = "Lilac",
            light = Color(0xFFF1D8F0),
            dark = Color(0xFF4F3450),
            accent = Color(0xFFD7A1D5),
            onColor = Color(0xFF381A38),
        ),
        HabitColorEntry(
            key = "rose",
            label = "Rose",
            light = Color(0xFFF6D5DD),
            dark = Color(0xFF55343C),
            accent = Color(0xFFE39CAA),
            onColor = Color(0xFF3A1620),
        ),
        HabitColorEntry(
            key = "sand",
            label = "Sand",
            light = Color(0xFFEDE2CD),
            dark = Color(0xFF4D4231),
            accent = Color(0xFFCBB68A),
            onColor = Color(0xFF2C2516),
        ),
        HabitColorEntry(
            key = "coral",
            label = "Coral",
            light = Color(0xFFFFD9CC),
            dark = Color(0xFF5A3A30),
            accent = Color(0xFFFFA688),
            onColor = Color(0xFF3A1B0F),
        ),
        HabitColorEntry(
            key = "teal",
            label = "Teal",
            light = Color(0xFFCFE8E4),
            dark = Color(0xFF2F4D49),
            accent = Color(0xFF8DCDC4),
            onColor = Color(0xFF143230),
        ),
        HabitColorEntry(
            key = "periwinkle",
            label = "Periwinkle",
            light = Color(0xFFD7DDF7),
            dark = Color(0xFF373E5E),
            accent = Color(0xFFA1ACE0),
            onColor = Color(0xFF1A2148),
        ),
        HabitColorEntry(
            key = "fog",
            label = "Fog",
            light = Color(0xFFE2E5EA),
            dark = Color(0xFF40454D),
            accent = Color(0xFFB6BCC6),
            onColor = Color(0xFF22262D),
        ),
    )

    private val byKey: Map<String, HabitColorEntry> = palette.associateBy { it.key }

    val defaultEntry: HabitColorEntry get() = palette.first()

    fun entry(key: String): HabitColorEntry = byKey[key] ?: defaultEntry
}

/**
 * Resolves the appropriate background colour for the current theme.
 */
@Composable
@ReadOnlyComposable
fun HabitColorEntry.containerColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.let {
        // Cheap proxy: dark theme → background luminance is low.
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.5f
    }
    return if (isDark) dark else light
}

/**
 * Foreground colour suitable for text on top of [containerColor]. In light
 * mode we use the entry's [HabitColorEntry.onColor]; in dark mode we use the
 * theme's onSurface so contrast stays comfortable.
 */
@Composable
@ReadOnlyComposable
fun HabitColorEntry.contentColor(): Color {
    val bg = MaterialTheme.colorScheme.background
    val isDark = (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) < 0.5f
    return if (isDark) MaterialTheme.colorScheme.onSurface else onColor
}
