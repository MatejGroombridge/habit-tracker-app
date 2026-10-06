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
import androidx.compose.material.icons.outlined.Insights
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
import dev.matejgroombridge.habittracker.ui.screens.HabitStatsScreen
import dev.matejgroombridge.habittracker.ui.screens.StatsScreen
import dev.matejgroombridge.habittracker.ui.screens.HomeScreen
import dev.matejgroombridge.habittracker.ui.screens.PastWeekScreen
import dev.matejgroombridge.habittracker.ui.screens.ReorderHabitsScreen
import dev.matejgroombridge.habittracker.ui.screens.SettingsScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

private object Routes {
    /** Single host route for the swipeable Past Week / Today / All Time / Stats pager. */
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ARCHIVE = "archive"
    const val REORDER = "reorder"
    const val WRITE_NFC = "write_nfc"
    const val HABIT_REMINDERS = "habit_reminders"
    const val HABIT_STATS = "habit_stats/{habitId}"
    fun habitStats(habitId: String) = "habit_stats/$habitId"
}

private data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

// Order is intentional: pager index 0 → Past Week, 1 → Today, 2 → All Time,
// 3 → Stats. Today sits second so the user can swipe to it from either side;
// it's also the page the app launches on (see [TODAY_PAGE_INDEX] / initialPage).
// Adjust both this list AND the `when (page)` switch in MainPager() to add a tab.
private const val TODAY_PAGE_INDEX = 1
private const val STATS_PAGE_INDEX = 3
private val BOTTOM_TABS = listOf(
    BottomTab("Past Week", Icons.Outlined.CalendarViewWeek),
    BottomTab("Today", Icons.Outlined.CheckCircle),
    BottomTab("All Time", Icons.Outlined.BarChart),
    BottomTab("Stats", Icons.Outlined.Insights),
)

/** Intent extra naming a tab to open on, e.g. from the monthly recap notification. */
const val EXTRA_OPEN_TAB = "open_tab"
const val TAB_STATS = "stats"

class MainActivity : ComponentActivity() {

    /** Set when an intent asks for the Stats tab; the pager clears it once shown. */
    private val openStatsRequested = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readTabRequest(intent)
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
                    AppShell(
                        settingsViewModel = settingsViewModel,
                        openStatsRequested = openStatsRequested.value,
                        onOpenStatsHandled = { openStatsRequested.value = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        completeHabitFromIntent(intent)
        readTabRequest(intent)
    }

    private fun readTabRequest(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN_TAB) == TAB_STATS) openStatsRequested.value = true
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
private fun AppShell(
    settingsViewModel: SettingsViewModel,
    openStatsRequested: Boolean,
    onOpenStatsHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val app = rememberApplication()

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(app),
    )

    // A request for the Stats tab (e.g. tapping the monthly recap) should
    // land there even if the app was left on a pushed screen like Settings.
    LaunchedEffect(openStatsRequested) {
        if (openStatsRequested) navController.popBackStack(Routes.MAIN, inclusive = false)
    }

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
                onOpenHabitStats = { id -> navController.navigate(Routes.habitStats(id)) },
                openStatsRequested = openStatsRequested,
                onOpenStatsHandled = onOpenStatsHandled,
            )
        }
        composable(Routes.HABIT_STATS) { entry ->
            HabitStatsScreen(
                viewModel = homeViewModel,
                habitId = entry.arguments?.getString("habitId").orEmpty(),
                onBack = { navController.popBackStack() },
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
 * Hosts the four top-level screens (Past Week, Today, All Time, Stats) inside a
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
    onOpenHabitStats: (String) -> Unit,
    openStatsRequested: Boolean,
    onOpenStatsHandled: () -> Unit,
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

    // Zen mode keeps the user on Today, so a Stats request is just dropped.
    LaunchedEffect(openStatsRequested) {
        if (!openStatsRequested) return@LaunchedEffect
        if (!settings.zenMode) pagerState.scrollToPage(STATS_PAGE_INDEX)
        onOpenStatsHandled()
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
                )
                STATS_PAGE_INDEX -> StatsScreen(
                    viewModel = homeViewModel,
                    contentPadding = padding,
                    onOpenHabit = onOpenHabitStats,
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
