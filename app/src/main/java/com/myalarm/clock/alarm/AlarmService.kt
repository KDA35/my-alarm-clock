package com.myalarm.clock.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.myalarm.clock.R
import com.myalarm.clock.data.AlarmRepository
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var logger: AppLogger

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val ACTION_START = "com.myalarm.clock.SERVICE_START"
        const val ACTION_STOP = "com.myalarm.clock.SERVICE_STOP"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "Service"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        logger.i(TAG, "Service onStartCommand action=${intent?.action} alarmId=$alarmId")
        when (intent?.action) {
            ACTION_START -> startAlarm(alarmId)
            ACTION_STOP -> stopSelfCleanly()
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(alarmId: Long) {
        createNotificationChannel()

        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmRingingActivity.EXTRA_ALARM_ID, alarmId)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.alarm_ringing_title))
            .setContentText(getString(R.string.alarm_ringing_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            logger.i(TAG, "Foreground started, notification posted with full-screen intent")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to start foreground", e)
            stopSelf()
            return
        }

        serviceScope.launch {
            val alarm = repository.getById(alarmId) ?: run {
                logger.e(TAG, "Alarm id=$alarmId not found, stopping service")
                stopSelfCleanly()
                return@launch
            }
            playSound(alarm.ringtoneUri)
            if (alarm.vibrationEnabled) startVibration()
        }
    }

    private fun playSound(uriString: String?) {
        logger.d(TAG, "Preparing to play sound, uri=$uriString")
        val uri = uriString?.toUri()
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try {
            val r = RingtoneManager.getRingtone(this, uri)
            if (r == null) {
                logger.e(TAG, "Failed to obtain ringtone for uri=$uriString")
                return
            }
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            r.isLooping = true
            r.play()
            ringtone = r
            logger.i(TAG, "Ringtone playing successfully")
        } catch (e: Exception) {
            logger.e(TAG, "Error while starting ringtone", e)
        }
    }

    private fun startVibration() {
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = vib.apply {
                val pattern = longArrayOf(0, 1000, 1000)
                vibrate(VibrationEffect.createWaveform(pattern, 0))
            }
            logger.d(TAG, "Vibration started with pattern")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to start vibration", e)
        }
    }

    private fun stopSelfCleanly() {
        logger.i(TAG, "Service stopping cleanly")
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
