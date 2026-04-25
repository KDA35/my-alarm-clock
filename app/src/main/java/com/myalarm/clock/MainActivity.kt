package com.myalarm.clock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myalarm.clock.ui.navigation.AppNavigation
import com.myalarm.clock.ui.theme.MyAlarmTheme
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var logger: AppLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logger.i("Main", "App started, version=${BuildConfig.VERSION_NAME}")
        setContent {
            MyAlarmTheme {
                AppNavigation()
            }
        }
    }
}
