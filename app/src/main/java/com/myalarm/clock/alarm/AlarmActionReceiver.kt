package com.myalarm.clock.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myalarm.clock.data.AlarmRepository
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var logger: AppLogger

    companion object {
        const val ACTION_SNOOZE = "com.myalarm.clock.NOTIF_SNOOZE"
        const val ACTION_DISMISS = "com.myalarm.clock.NOTIF_DISMISS"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        private const val TAG = "AlarmAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val action = intent.action
        logger.i(TAG, "Action received: $action alarmId=$alarmId")

        // Cancel the heads-up notification immediately for visual feedback.
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(AlarmService.NOTIFICATION_ID)
        }

        // Stop the ringing service in any case (silences sound + vibration).
        val stopIntent = Intent(context, AlarmService::class.java).apply {
            this.action = AlarmService.ACTION_STOP
        }
        context.startService(stopIntent)

        when (action) {
            ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val alarm = repository.getById(alarmId)
                        if (alarm != null) {
                            scheduler.scheduleSnooze(alarm, minutes)
                            logger.i(TAG, "Snoozed alarm id=$alarmId for $minutes min via notification action")
                        } else {
                            logger.e(TAG, "Snooze: alarm id=$alarmId not found")
                        }
                    } catch (e: Exception) {
                        logger.e(TAG, "Snooze action failed", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_DISMISS -> {
                logger.i(TAG, "Dismissed alarm id=$alarmId via notification action")
            }
        }
    }
}
