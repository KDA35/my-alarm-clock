package com.myalarm.clock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.myalarm.clock.MainActivity
import com.myalarm.clock.data.Alarm
import com.myalarm.clock.data.DayOfWeekMask
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "Scheduler"
        private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: Alarm) {
        logger.i(
            TAG,
            "Schedule alarm id=${alarm.id} label='${alarm.label}' time=${alarm.hour}:${alarm.minute} enabled=${alarm.enabled}"
        )
        if (!alarm.enabled) {
            cancel(alarm.id)
            return
        }
        val triggerAt = DayOfWeekMask.calculateNextTrigger(alarm)
        val now = System.currentTimeMillis()
        if (triggerAt <= now) {
            logger.w(TAG, "Computed triggerAt is in the past, skipping schedule")
            return
        }
        logger.i(
            TAG,
            "Trigger time: ${formatTime(triggerAt)} (in ${(triggerAt - now) / 1000}s)"
        )
        scheduleAt(alarm.id, triggerAt)
    }

    fun scheduleSnooze(alarm: Alarm, snoozeMinutes: Int) {
        val triggerAt = System.currentTimeMillis() + snoozeMinutes * 60_000L
        logger.i(TAG, "Snooze alarm id=${alarm.id} for $snoozeMinutes minutes (trigger at ${formatTime(triggerAt)})")
        scheduleAt(alarm.id, triggerAt)
    }

    private fun scheduleAt(alarmId: Long, triggerAt: Long) {
        val pendingIntent = buildAlarmPendingIntent(alarmId)
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                logger.w(
                    TAG,
                    "Exact alarms NOT permitted by user, falling back to setAndAllowWhileIdle (less precise)"
                )
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                    pendingIntent
                )
                logger.d(TAG, "setAlarmClock called successfully for id=$alarmId")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to schedule alarm id=$alarmId", e)
        }
    }

    fun cancel(alarmId: Long) {
        logger.i(TAG, "Cancel alarm id=$alarmId")
        alarmManager.cancel(buildAlarmPendingIntent(alarmId))
    }

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

    private fun buildAlarmPendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatTime(millis: Long): String = timeFormat.format(Date(millis))
}
