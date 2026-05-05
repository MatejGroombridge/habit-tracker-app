package dev.matejgroombridge.habittracker.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Toys
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Whatshot
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
        HabitIconEntry("bike", "Bike", Icons.AutoMirrored.Outlined.DirectionsBike),
        HabitIconEntry("hike", "Hike", Icons.Outlined.Hiking),
        HabitIconEntry("swim", "Swim", Icons.Outlined.Pool),
        HabitIconEntry("basketball", "Hoops", Icons.Outlined.SportsBasketball),
        HabitIconEntry("soccer", "Soccer", Icons.Outlined.SportsSoccer),
        HabitIconEntry("yoga", "Yoga", Icons.Outlined.SelfImprovement),
        HabitIconEntry("spa", "Spa", Icons.Outlined.Spa),
        HabitIconEntry("mind", "Mindful", Icons.Outlined.Psychology),
        HabitIconEntry("book", "Book", Icons.Outlined.Book),
        HabitIconEntry("menu_book", "Read", Icons.AutoMirrored.Outlined.MenuBook),
        HabitIconEntry("school", "Study", Icons.Outlined.School),
        HabitIconEntry("write", "Write", Icons.Outlined.Create),
        HabitIconEntry("brush", "Art", Icons.Outlined.Brush),
        HabitIconEntry("camera", "Photo", Icons.Outlined.Camera),
        HabitIconEntry("code", "Code", Icons.Outlined.Code),
        HabitIconEntry("music", "Music", Icons.Outlined.MusicNote),
        HabitIconEntry("headphones", "Listen", Icons.Outlined.Headphones),
        HabitIconEntry("game", "Game", Icons.Outlined.SportsEsports),
        HabitIconEntry("toys", "Play", Icons.Outlined.Toys),
        HabitIconEntry("coffee", "Coffee", Icons.Outlined.LocalCafe),
        HabitIconEntry("restaurant", "Eat", Icons.Outlined.Restaurant),
        HabitIconEntry("pizza", "Pizza", Icons.Outlined.LocalPizza),
        HabitIconEntry("cake", "Cake", Icons.Outlined.Cake),
        HabitIconEntry("cookie", "Snack", Icons.Outlined.Cookie),
        HabitIconEntry("flower", "Flower", Icons.Outlined.LocalFlorist),
        HabitIconEntry("park", "Outdoors", Icons.Outlined.Park),
        HabitIconEntry("pet", "Pet", Icons.Outlined.Pets),
        HabitIconEntry("heart", "Love", Icons.Outlined.FavoriteBorder),
        HabitIconEntry("smile", "Mood", Icons.Outlined.Mood),
        HabitIconEntry("star", "Star", Icons.Outlined.Star),
        HabitIconEntry("trophy", "Win", Icons.Outlined.EmojiEvents),
        HabitIconEntry("rocket", "Boost", Icons.Outlined.RocketLaunch),
        HabitIconEntry("fire", "Streak", Icons.Outlined.Whatshot),
        HabitIconEntry("phone", "Call", Icons.Outlined.Phone),
        HabitIconEntry("sun", "Morning", Icons.Outlined.WbSunny),
        HabitIconEntry("moon", "Night", Icons.Outlined.NightsStay),
        HabitIconEntry("bed", "Sleep", Icons.Outlined.Bedtime),
        HabitIconEntry("clean", "Clean", Icons.Outlined.CleaningServices),
        HabitIconEntry("savings", "Save", Icons.Outlined.Savings),
        HabitIconEntry("medication", "Meds", Icons.Outlined.Medication),
        HabitIconEntry("drink", "Drink", Icons.Outlined.LocalDrink),
    )

    private val byKey: Map<String, HabitIconEntry> = catalog.associateBy { it.key }

    val defaultEntry: HabitIconEntry get() = catalog.first()

    fun entry(key: String): HabitIconEntry = byKey[key] ?: defaultEntry

    fun icon(key: String): ImageVector = entry(key).icon
}
