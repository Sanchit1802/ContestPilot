package com.sanchit.contestpilot.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sanchit.contestpilot.ui.contests.ContestsScreen
import com.sanchit.contestpilot.ui.settings.SettingsScreen

private object Routes {
    const val CONTESTS = "contests"
    const val SETTINGS = "settings"
}

@Composable
fun ContestPilotApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.CONTESTS) {
        composable(Routes.CONTESTS) {
            ContestsScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
