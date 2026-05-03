package dev.matejgroombridge.habittracker

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.matejgroombridge.habittracker.ui.HomeViewModel
import dev.matejgroombridge.habittracker.ui.SettingsViewModel
import dev.matejgroombridge.habittracker.ui.screens.HomeScreen
import dev.matejgroombridge.habittracker.ui.screens.SettingsScreen
import dev.matejgroombridge.habittracker.ui.theme.AppTheme

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

/**
 * Single-Activity host for the Habit Tracker app. Hosts a NavController
 * with two destinations (Home + Settings) and a shared [SettingsViewModel]
 * so the chosen theme is applied app-wide.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(application),
            )
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            AppTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost(settingsViewModel = settingsViewModel)
                }
            }
        }
    }
}

@Composable
private fun rememberApplication(): Application {
    val ctx = LocalContext.current.applicationContext
    return ctx as Application
}

@Composable
private fun AppNavHost(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val app = rememberApplication()
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(app),
            )
            HomeScreen(
                viewModel = homeViewModel,
                settingsViewModel = settingsViewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
