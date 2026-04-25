package com.myalarm.clock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myalarm.clock.ui.debug.DebugInfoScreen
import com.myalarm.clock.ui.debug.DebugMenuScreen
import com.myalarm.clock.ui.debug.DiagnosticPackageScreen
import com.myalarm.clock.ui.debug.TestMenuScreen
import com.myalarm.clock.ui.edit.AlarmEditScreen
import com.myalarm.clock.ui.list.AlarmListScreen
import com.myalarm.clock.ui.logs.LogsScreen

private object Routes {
    const val ALARM_LIST = "alarm_list"
    const val LOGS = "logs"
    const val ALARM_EDIT = "alarm_edit/{alarmId}"
    const val DEBUG_MENU = "debug"
    const val DEBUG_TEST = "debug/test_menu"
    const val DEBUG_INFO = "debug/info"
    const val DEBUG_DIAGNOSTIC = "debug/diagnostic"
    fun edit(alarmId: Long) = "alarm_edit/$alarmId"
}

@Composable
fun AppNavigation(onShowOnboarding: () -> Unit = {}) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.ALARM_LIST) {
        composable(Routes.ALARM_LIST) {
            AlarmListScreen(
                onOpenLogs = { navController.navigate(Routes.LOGS) },
                onCreateAlarm = { navController.navigate(Routes.edit(-1L)) },
                onEditAlarm = { id -> navController.navigate(Routes.edit(id)) },
                onShowOnboarding = onShowOnboarding,
                onOpenDebug = { navController.navigate(Routes.DEBUG_MENU) }
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
        composable(Routes.DEBUG_MENU) {
            DebugMenuScreen(
                onBack = { navController.popBackStack() },
                onOpenTestMenu = { navController.navigate(Routes.DEBUG_TEST) },
                onOpenDebugInfo = { navController.navigate(Routes.DEBUG_INFO) },
                onOpenDiagnostic = { navController.navigate(Routes.DEBUG_DIAGNOSTIC) }
            )
        }
        composable(Routes.DEBUG_TEST) {
            TestMenuScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DEBUG_INFO) {
            DebugInfoScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DEBUG_DIAGNOSTIC) {
            DiagnosticPackageScreen(onBack = { navController.popBackStack() })
        }
    }
}
