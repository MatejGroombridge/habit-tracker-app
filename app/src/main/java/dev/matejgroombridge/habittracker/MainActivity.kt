package dev.matejgroombridge.habittracker

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.matejgroombridge.habittracker.data.model.Habit
import dev.matejgroombridge.habittracker.data.repository.HabitRepository
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.SettingsViewModel
import dev.matejgroombridge.habittracker.ui.util.rememberHaptics
import dev.matejgroombridge.habittracker.ui.screens.AnalyticsScreen
import dev.matejgroombridge.habittracker.ui.screens.ArchivedHabitsScreen
import dev.matejgroombridge.habittracker.ui.screens.HabitRemindersScreen
import dev.matejgroombridge.habittracker.ui.screens.HomeScreen
import dev.matejgroombridge.habittracker.ui.screens.PastWeekScreen
import dev.matejgroombridge.habittracker.ui.screens.ReorderHabitsScreen
import dev.matejgroombridge.habittracker.ui.screens.SettingsScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

private object Routes {
    /** Single host route for the swipeable Today / Past Week / All Time pager. */
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ARCHIVE = "archive"
    const val REORDER = "reorder"
    const val WRITE_NFC = "write_nfc"
    const val HABIT_REMINDERS = "habit_reminders"
}

private data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

// Order is intentional: pager index 0 → Past Week, 1 → Today, 2 → All Time.
// Today sits in the middle so the user can swipe to it from either side; it's
// also the page the app launches on (see [TODAY_PAGE_INDEX] / initialPage).
// Adjust both this list AND the `when (page)` switch in MainPager() to add a tab.
private const val TODAY_PAGE_INDEX = 1
private val BOTTOM_TABS = listOf(
    BottomTab("Past Week", Icons.Outlined.CalendarViewWeek),
    BottomTab("Today", Icons.Outlined.CheckCircle),
    BottomTab("All Time", Icons.Outlined.BarChart),
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

        // Make sure the notification channel exists and the user's chosen
        // reminder schedule is in place. Cheap and safe to call on every
        // cold launch — if reminders are disabled it just cancels alarms.
        dev.matejgroombridge.habittracker.notifications.Notifications.ensureChannel(this)
        lifecycleScope.launch {
            dev.matejgroombridge.habittracker.notifications
                .ReminderScheduler.rescheduleAll(applicationContext)
        }

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(application),
            )
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            dev.matejgroombridge.habittracker.ui.theme.AppTheme(
                themeMode = settings.themeMode,
                amoled = settings.amoled,
            ) {
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
     * app to the Today page is good enough; the user will see the new tick.
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

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.MAIN) {
            MainPager(
                homeViewModel = homeViewModel,
                settingsViewModel = settingsViewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
                onOpenWriteNfc = { navController.navigate(Routes.WRITE_NFC) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                homeViewModel = homeViewModel,
                onBack = { navController.popBackStack() },
                onOpenReorder = { navController.navigate(Routes.REORDER) },
                onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
                onOpenHabitReminders = { navController.navigate(Routes.HABIT_REMINDERS) },
                onOpenWriteNfc = { navController.navigate(Routes.WRITE_NFC) },
            )
        }
        composable(Routes.ARCHIVE) {
            ArchivedHabitsScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.REORDER) {
            ReorderHabitsScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.WRITE_NFC) {
            dev.matejgroombridge.habittracker.ui.screens.WriteNfcTagScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.HABIT_REMINDERS) {
            HabitRemindersScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Hosts the three top-level screens (Today, Past Week, All Time) inside a
 * [HorizontalPager], so the user can swipe between them. The bottom
 * NavigationBar mirrors the pager's selected index — tapping a tab animates
 * the pager, swiping the pager updates the highlighted tab.
 *
 * The FAB only appears on the Today page; we hide it on the others to avoid
 * misleading affordance ("Add habit" doesn't make sense on All Time).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainPager(
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenWriteNfc: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = TODAY_PAGE_INDEX,
        pageCount = { BOTTOM_TABS.size },
    )
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    // Light buzz whenever the pager actually settles on a new page (whether
    // initiated by a swipe or a tab tap). We snapshot the previous page so
    // the initial composition (page == initialPage) doesn't fire a buzz.
    var lastPage by remember { mutableStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPage) {
            haptics.light()
            lastPage = pagerState.currentPage
        }
    }

    // Driving the FAB from the shell so it sits above the bottom bar correctly.
    var requestCreate by remember { mutableStateOf(false) }

    // When zen mode is enabled the user is locked to the Today page —
    // snap there so the bottom-nav-less view doesn't strand them on
    // Past Week or All Time after re-entry.
    LaunchedEffect(settings.zenMode) {
        if (settings.zenMode && pagerState.currentPage != TODAY_PAGE_INDEX) {
            pagerState.scrollToPage(TODAY_PAGE_INDEX)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Zen mode hides the bottom navigation completely — there's
            // nothing to navigate to, only Today exists.
            if (settings.zenMode) return@Scaffold
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                BOTTOM_TABS.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                // The page-change LaunchedEffect above will
                                // emit the haptic once the pager settles —
                                // no need to duplicate here.
                                scope.launch { pagerState.animateScrollToPage(index) }
                            } else {
                                // Tapping the already-selected tab still
                                // gives a small confirmation tick.
                                haptics.light()
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            // No habit creation while in Zen mode.
            if (settings.zenMode) return@Scaffold
            if (pagerState.currentPage == TODAY_PAGE_INDEX) {
                FloatingActionButton(
                    onClick = {
                        haptics.completion()
                        requestCreate = true
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add habit")
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Tiny prefetch keeps adjacent pages composed so swiping is
            // instant; setting beyondViewportPageCount to 1 means at most
            // 3 pages exist at once which is fine for our screens.
            beyondViewportPageCount = 1,
            // Honour the "Swipe to navigate" general setting, and force
            // it off entirely while Zen mode is on so the user can't
            // swipe to Past Week / All Time.
            userScrollEnabled = settings.swipeToNavigate && !settings.zenMode,
        ) { page ->
            when (page) {
                0 -> PastWeekScreen(
                    viewModel = homeViewModel,
                    contentPadding = padding,
                    allowSkips = settings.allowSkips,
                    allowPauses = settings.allowPauses,
                    allowInverseHabits = settings.allowInverseHabits,
                )
                TODAY_PAGE_INDEX -> HomeScreen(
                    viewModel = homeViewModel,
                    settingsViewModel = settingsViewModel,
                    onOpenSettings = onOpenSettings,
                    onOpenArchive = onOpenArchive,
                    onOpenWriteNfc = onOpenWriteNfc,
                    contentPadding = padding,
                    requestCreate = requestCreate,
                    onCreateDialogConsumed = { requestCreate = false },
                )
                2 -> AnalyticsScreen(
                    viewModel = homeViewModel,
                    contentPadding = padding,
                    allowInverseHabits = settings.allowInverseHabits,
                )
            }
        }
    }

    // Defensive: if a non-Today page is somehow showing while a create
    // request is pending (e.g. swipe just after tapping FAB), drop it so
    // we never auto-open the editor on the wrong page.
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != TODAY_PAGE_INDEX && requestCreate) requestCreate = false
    }
}
