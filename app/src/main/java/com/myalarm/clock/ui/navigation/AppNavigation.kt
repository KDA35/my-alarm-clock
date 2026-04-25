package com.myalarm.clock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myalarm.clock.ui.edit.AlarmEditScreen
import com.myalarm.clock.ui.list.AlarmListScreen
import com.myalarm.clock.ui.logs.LogsScreen

private object Routes {
    const val ALARM_LIST = "alarm_list"
    const val LOGS = "logs"
    const val ALARM_EDIT = "alarm_edit/{alarmId}"
    fun edit(alarmId: Long) = "alarm_edit/$alarmId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.ALARM_LIST) {
        composable(Routes.ALARM_LIST) {
            AlarmListScreen(
                onOpenLogs = { navController.navigate(Routes.LOGS) },
                onCreateAlarm = { navController.navigate(Routes.edit(-1L)) },
                onEditAlarm = { id -> navController.navigate(Routes.edit(id)) }
            )
        }
        composable(Routes.LOGS) {
            LogsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.ALARM_EDIT,
            arguments = listOf(navArgument("alarmId") { type = NavType.LongType })
        ) {
            AlarmEditScreen(onClose = { navController.popBackStack() })
        }
    }
}
