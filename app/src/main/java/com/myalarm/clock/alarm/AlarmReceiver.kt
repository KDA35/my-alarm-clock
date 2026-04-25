package com.myalarm.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.myalarm.clock.data.AlarmRepository
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var logger: AppLogger

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.myalarm.clock.ALARM_TRIGGER"
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val TAG = "Receiver"
        private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) {
            logger.e(TAG, "Received alarm with no alarmId")
            return
        }
        logger.i(
            TAG,
            "=== ALARM TRIGGERED === alarmId=$alarmId, time=${timeFormat.format(Date())}"
        )

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        logger.i(TAG, "Foreground service start requested")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = repository.getById(alarmId)
                if (alarm == null) {
                    logger.e(TAG, "Alarm id=$alarmId not found in database")
                    return@launch
                }
                logger.d(
                    TAG,
                    "Loaded alarm: label='${alarm.label}' daysOfWeek=${alarm.daysOfWeek}"
                )
                if (alarm.daysOfWeek == 0) {
                    repository.toggleEnabled(alarmId, false)
                    logger.i(TAG, "One-shot alarm id=$alarmId disabled after firing")
                } else {
                    scheduler.schedule(alarm)
                    logger.i(TAG, "Recurring alarm id=$alarmId rescheduled for next occurrence")
                }
            } catch (e: Exception) {
                logger.e(TAG, "Error in AlarmReceiver async work", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
