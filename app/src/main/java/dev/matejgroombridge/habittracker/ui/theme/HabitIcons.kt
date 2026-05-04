package dev.matejgroombridge.habittracker.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The curated set of habit icons the user can pick from. Stored on a [Habit]
 * by string key so adding/removing icons in the future doesn't break the
 * persisted JSON — unknown keys gracefully fall back to [defaultEntry].
 */
data class HabitIconEntry(val key: String, val label: String, val icon: ImageVector)

object HabitIcons {

    val catalog: List<HabitIconEntry> = listOf(
        HabitIconEntry("check_circle", "Check", Icons.Outlined.CheckCircle),
        HabitIconEntry("water_drop", "Water", Icons.Outlined.WaterDrop),
        HabitIconEntry("fitness", "Workout", Icons.Outlined.FitnessCenter),
        HabitIconEntry("run", "Run", Icons.AutoMirrored.Outlined.DirectionsRun),
        HabitIconEntry("yoga", "Yoga", Icons.Outlined.SelfImprovement),
        HabitIconEntry("spa", "Spa", Icons.Outlined.Spa),
        HabitIconEntry("book", "Book", Icons.Outlined.Book),
        HabitIconEntry("menu_book", "Read", Icons.AutoMirrored.Outlined.MenuBook),
        HabitIconEntry("brush", "Art", Icons.Outlined.Brush),
        HabitIconEntry("code", "Code", Icons.Outlined.Code),
        HabitIconEntry("music", "Music", Icons.Outlined.MusicNote),
        HabitIconEntry("headphones", "Listen", Icons.Outlined.Headphones),
        HabitIconEntry("game", "Game", Icons.Outlined.SportsEsports),
        HabitIconEntry("coffee", "Coffee", Icons.Outlined.LocalCafe),
        HabitIconEntry("restaurant", "Eat", Icons.Outlined.Restaurant),
        HabitIconEntry("flower", "Flower", Icons.Outlined.LocalFlorist),
        HabitIconEntry("pet", "Pet", Icons.Outlined.Pets),
        HabitIconEntry("heart", "Love", Icons.Outlined.FavoriteBorder),
        HabitIconEntry("sun", "Morning", Icons.Outlined.WbSunny),
        HabitIconEntry("moon", "Night", Icons.Outlined.NightsStay),
        HabitIconEntry("bed", "Sleep", Icons.Outlined.Bedtime),
    )

    private val byKey: Map<String, HabitIconEntry> = catalog.associateBy { it.key }

    val defaultEntry: HabitIconEntry get() = catalog.first()

    fun entry(key: String): HabitIconEntry = byKey[key] ?: defaultEntry

    fun icon(key: String): ImageVector = entry(key).icon
}
