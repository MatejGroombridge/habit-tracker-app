package dev.matejgroombridge.habittracker

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.repository.HabitRepository
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.SettingsViewModel
import dev.matejgroombridge.habittracker.ui.screens.AnalyticsScreen
import dev.matejgroombridge.habittracker.ui.screens.ArchivedHabitsScreen
import dev.matejgroombridge.habittracker.ui.screens.HomeScreen
import dev.matejgroombridge.habittracker.ui.screens.PastWeekScreen
import dev.matejgroombridge.habittracker.ui.screens.SettingsScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

private object Routes {
    const val HOME = "home"
    const val PAST_WEEK = "past_week"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val ARCHIVE = "archive"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val BOTTOM_TABS = listOf(
    BottomTab(Routes.HOME, "Today", Icons.Outlined.CheckCircle),
    BottomTab(Routes.PAST_WEEK, "Past Week", Icons.Outlined.CalendarViewWeek),
    BottomTab(Routes.ANALYTICS, "All Time", Icons.Outlined.BarChart),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // If we were launched from an NFC "open" deep link, complete the
        // referenced habit before any UI shows. NfcCompletionActivity already
        // does this for the background/overlay paths; we mirror the same
        // behaviour here for OpenApp so the result is identical regardless of
        // which entry point the system picked.
        completeHabitFromIntent(intent)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(application),
            )
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            dev.matejgroombridge.habittracker.ui.theme.AppTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppShell(settingsViewModel = settingsViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        completeHabitFromIntent(intent)
    }

    /**
     * If [intent] carries one of our deep links (`habittracker://habit/open/<id>`
     * or `.../complete/<id>`), mark today's completion for that habit. The
     * navigation graph itself doesn't depend on the deep link — opening the
     * app to the Home tab is good enough; the user will see the new tick.
     */
    private fun completeHabitFromIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != Habit.DEEP_LINK_SCHEME) return
        val segments = data.pathSegments
        if (segments.size < 2) return
        val habitId = segments[1].takeIf { it.isNotBlank() } ?: return
        val repo = HabitRepository(applicationContext)
        lifecycleScope.launch {
            repo.setCompleted(habitId, LocalDate.now().toEpochDay(), completed = true)
        }
    }
}

@Composable
private fun rememberApplication(): Application {
    val ctx = LocalContext.current.applicationContext
    return ctx as Application
}

@Composable
private fun AppShell(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val app = rememberApplication()

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(app),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTabRoute = currentRoute in BOTTOM_TABS.map { it.route }

    // Driving the FAB from the shell so it sits above the bottom bar correctly.
    var requestCreate by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onTabRoute) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    BOTTOM_TABS.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.HOME) {
                FloatingActionButton(onClick = { requestCreate = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add habit")
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = homeViewModel,
                    settingsViewModel = settingsViewModel,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
                    contentPadding = padding,
                    requestCreate = requestCreate,
                    onCreateDialogConsumed = { requestCreate = false },
                )
            }
            composable(Routes.PAST_WEEK) {
                PastWeekScreen(
                    viewModel = homeViewModel,
                    contentPadding = padding,
                )
            }
            composable(Routes.ANALYTICS) {
                AnalyticsScreen(
                    viewModel = homeViewModel,
                    contentPadding = padding,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ARCHIVE) {
                ArchivedHabitsScreen(
                    viewModel = homeViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
