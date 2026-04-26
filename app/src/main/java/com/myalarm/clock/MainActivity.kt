package com.myalarm.clock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.myalarm.clock.ui.navigation.AppNavigation
import com.myalarm.clock.ui.onboarding.OnboardingPrefs
import com.myalarm.clock.ui.onboarding.OnboardingScreen
import com.myalarm.clock.ui.theme.MyAlarmTheme
import com.myalarm.clock.util.AppLogger
import com.myalarm.clock.util.NotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var logger: AppLogger
    @Inject lateinit var onboardingPrefs: OnboardingPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logger.i("Main", "App started, version=${BuildConfig.VERSION_NAME}")
        NotificationChannels.ensureAlarmChannel(applicationContext, logger)
        setContent {
            MyAlarmTheme {
                var showOnboarding by remember { mutableStateOf(!onboardingPrefs.completed) }
                if (showOnboarding) {
                    OnboardingScreen(
                        onFinished = {
                            onboardingPrefs.completed = true
                            showOnboarding = false
                            logger.i("Main", "Onboarding completed")
                        }
                    )
                } else {
                    AppNavigation(
                        onShowOnboarding = {
                            onboardingPrefs.reset()
                            showOnboarding = true
                            logger.i("Main", "Onboarding requested manually")
                        }
                    )
                }
            }
        }
    }
}
