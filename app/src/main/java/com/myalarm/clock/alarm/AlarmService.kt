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
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.myalarm.clock.R
import com.myalarm.clock.data.AlarmRepository
import com.myalarm.clock.data.VibrationPattern
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
    private var previousAlarmVolume: Int = -1
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val ACTION_START = "com.myalarm.clock.SERVICE_START"
        const val ACTION_STOP = "com.myalarm.clock.SERVICE_STOP"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "Service"

        @Volatile
        private var currentlyRingingAlarmId: Long? = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        logger.i(TAG, "Service onStartCommand action=${intent?.action} alarmId=$alarmId")
        when (intent?.action) {
            ACTION_START -> {
                if (currentlyRingingAlarmId == alarmId && alarmId != -1L) {
                    logger.w(TAG, "Alarm id=$alarmId already ringing, ignoring duplicate start")
                    return START_NOT_STICKY
                }
                currentlyRingingAlarmId = alarmId
                startAlarm(alarmId)
            }
            ACTION_STOP -> {
                currentlyRingingAlarmId = null
                stopSelfCleanly()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(alarmId: Long) {
        createNotificationChannel()

        val initialNotification = buildNotification(alarmId, label = null)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
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
            if (alarm.label.isNotBlank()) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(alarmId, alarm.label))
            }
            if (alarm.volume > 0) {
                playSound(alarm.ringtoneUri, alarm.volume)
            } else {
                logger.i(TAG, "Volume is 0 — sound suppressed")
            }
            if (alarm.vibrationEnabled) {
                startVibration(alarm.vibrationPattern)
            }
        }
    }

    private fun buildNotification(alarmId: Long, label: String?): Notification {
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
        val contentText = label?.takeIf { it.isNotBlank() }
            ?: getString(R.string.alarm_ringing_text)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.alarm_ringing_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun playSound(uriString: String?, volume: Int) {
        logger.d(TAG, "Preparing to play sound, uri=$uriString volume=$volume/10")
        applyAlarmStreamVolume(volume)

        val parsedUri = try {
            uriString?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } catch (e: Exception) {
            logger.e(TAG, "Invalid ringtone URI: $uriString", e)
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }

        var r: Ringtone? = try {
            RingtoneManager.getRingtone(this, parsedUri)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to get ringtone for $parsedUri", e)
            null
        }

        if (r == null) {
            logger.w(TAG, "Falling back to default alarm sound")
            r = runCatching {
                RingtoneManager.getRingtone(
                    this,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                )
            }.getOrNull()
        }

        if (r == null) {
            logger.e(TAG, "Could not obtain any ringtone, no sound will play")
            return
        }

        try {
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

    private fun applyAlarmStreamVolume(volume: Int) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val target = (maxVolume * volume / 10).coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            logger.d(TAG, "Set STREAM_ALARM volume to $target/$maxVolume (was $previousAlarmVolume)")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to adjust alarm stream volume", e)
        }
    }

    private fun restoreAlarmStreamVolume() {
        if (previousAlarmVolume < 0) return
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
        } catch (e: Exception) {
            logger.w(TAG, "Failed to restore alarm stream volume: ${e.message}")
        } finally {
            previousAlarmVolume = -1
        }
    }

    private fun startVibration(patternName: String) {
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = vib.apply {
                vibrate(VibrationEffect.createWaveform(VibrationPattern.toLongArray(patternName), 0))
            }
            logger.d(TAG, "Vibration started: pattern=$patternName")
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
        restoreAlarmStreamVolume()
        currentlyRingingAlarmId = null
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
            setBypassDnd(true)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator?.cancel()
        restoreAlarmStreamVolume()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
