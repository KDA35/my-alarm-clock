package com.myalarm.clock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myalarm.clock.ui.list.AlarmListScreen
import com.myalarm.clock.ui.logs.LogsScreen

private object Routes {
    const val ALARM_LIST = "alarm_list"
    const val LOGS = "logs"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.ALARM_LIST) {
        composable(Routes.ALARM_LIST) {
            AlarmListScreen(
                onOpenLogs = { navController.navigate(Routes.LOGS) },
                onCreateAlarm = { /* TODO: stage 5 */ },
                onEditAlarm = { _ -> /* TODO: stage 5 */ }
            )
        }
        composable(Routes.LOGS) {
            LogsScreen(onBack = { navController.popBackStack() })
        }
    }
}
