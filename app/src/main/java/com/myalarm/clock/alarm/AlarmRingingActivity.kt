package com.myalarm.clock.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myalarm.clock.ui.ringing.RingingScreen
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingingActivity : ComponentActivity() {

    @Inject lateinit var logger: AppLogger

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val TAG = "RingingUI"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        logger.i(
            TAG,
            "Activity created, alarmId=$alarmId, isLocked=${keyguardManager.isKeyguardLocked}, isDeviceSecure=${keyguardManager.isDeviceSecure}"
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                logger.d(TAG, "Back press blocked")
            }
        })

        setContent {
            RingingScreen(
                onSnooze = { _ -> handleClose() },
                onPostpone = { _ -> handleClose() },
                onPostponeUntil = { _, _ -> handleClose() },
                onDismiss = { handleClose() }
            )
        }
    }

    private fun handleClose() {
        stopAlarmService()
        finish()
    }

    private fun stopAlarmService() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        }
        startService(intent)
    }
}
