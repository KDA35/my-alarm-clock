package com.myalarm.clock.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myalarm.clock.ui.ringing.RingingScreen
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingingActivity : ComponentActivity() {

    @Inject lateinit var logger: AppLogger

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_TEST_MODE_NO_SOUND = "test_mode_no_sound"
        private const val TAG = "RingingUI"
        private const val FIRE_TAG = "AlarmFire"
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val testMode = intent.getBooleanExtra(EXTRA_TEST_MODE_NO_SOUND, false)
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        logger.i(
            TAG,
            "Activity created, alarmId=$alarmId, isLocked=${keyguardManager.isKeyguardLocked}, isDeviceSecure=${keyguardManager.isDeviceSecure}, testMode=$testMode"
        )

        // Diagnostic: how long did it take from "alarm fired" to "activity actually shown"?
        val fireAt = AlarmService.lastFireTimestampElapsed
        val nowElapsed = SystemClock.elapsedRealtime()
        val deltaMs = if (fireAt > 0) nowElapsed - fireAt else -1L
        logger.i(
            FIRE_TAG,
            "Activity actually appeared at: ${timeFormat.format(Date())}; time from fire to UI: ${deltaMs}ms"
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                logger.d(TAG, "Back press blocked")
            }
        })

        setContent {
            RingingScreen(
                testMode = testMode,
                onSnooze = { _ -> handleClose(testMode) },
                onPostpone = { _ -> handleClose(testMode) },
                onPostponeUntil = { _, _ -> handleClose(testMode) },
                onDismiss = { handleClose(testMode) }
            )
        }
    }

    private fun handleClose(testMode: Boolean) {
        if (!testMode) stopAlarmService()
        finish()
    }

    private fun stopAlarmService() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        }
        startService(intent)
    }
}
