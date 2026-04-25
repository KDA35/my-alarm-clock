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
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
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
    private var wakeLock: PowerManager.WakeLock? = null
    private var previousAlarmVolume: Int = -1
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val ACTION_START = "com.myalarm.clock.SERVICE_START"
        const val ACTION_STOP = "com.myalarm.clock.SERVICE_STOP"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "Service"
        private const val FIRE_TAG = "AlarmFire"

        @Volatile
        private var currentlyRingingAlarmId: Long? = null

        @Volatile
        var lastFireTimestampElapsed: Long = 0L
            private set
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
                lastFireTimestampElapsed = SystemClock.elapsedRealtime()
                logger.i(FIRE_TAG, "=== ALARM FIRED === alarmId=$alarmId")
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

        // Layer 4: wake lock first — bring the screen up before anything else tries to render.
        acquireWakeLock()

        // Layer 1: full-screen-intent notification (legacy primary path).
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
            val canFsi = canUseFullScreenIntent()
            logger.i(
                FIRE_TAG,
                "Layer 1 (FSI): canUseFullScreenIntent=$canFsi, posted notification with full-screen intent"
            )
        } catch (e: Exception) {
            logger.e(FIRE_TAG, "Layer 1 (FSI): startForeground failed", e)
            stopSelf()
            return
        }

        // Layer 2: direct Activity launch via SYSTEM_ALERT_WINDOW (works around FSI restrictions).
        tryLaunchActivityDirectly(alarmId)

        // Layer 3 logging happens implicitly when the notification was built with actions.
        logger.i(FIRE_TAG, "Layer 3 (Actions): notification actions attached (Snooze/Dismiss)")

        serviceScope.launch {
            val alarm = repository.getById(alarmId) ?: run {
                logger.e(TAG, "Alarm id=$alarmId not found, stopping service")
                stopSelfCleanly()
                return@launch
            }
            // Re-post notification with the real label for the lock-screen shade.
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(alarmId, alarm.label, alarm.snoozeIntervalMinutes))

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

    private fun tryLaunchActivityDirectly(alarmId: Long) {
        val canDraw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
        if (!canDraw) {
            logger.i(FIRE_TAG, "Layer 2 (Overlay): canDrawOverlays=false, skipped")
            return
        }
        try {
            val intent = Intent(this, AlarmRingingActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
                putExtra(AlarmRingingActivity.EXTRA_ALARM_ID, alarmId)
            }
            startActivity(intent)
            logger.i(FIRE_TAG, "Layer 2 (Overlay): canDrawOverlays=true, started Activity directly")
        } catch (e: Exception) {
            logger.e(FIRE_TAG, "Layer 2 (Overlay): startActivity failed", e)
        }
    }

    private fun canUseFullScreenIntent(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()
        } else true

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "MyAlarm:RingingWake"
            )
            wl.setReferenceCounted(false)
            wl.acquire(60_000L)
            wakeLock = wl
            logger.i(FIRE_TAG, "Layer 4 (Wake): wake lock acquired (60s timeout)")
        } catch (e: Exception) {
            logger.e(FIRE_TAG, "Layer 4 (Wake): failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.runCatching { release() }
        wakeLock = null
    }

    private fun buildNotification(
        alarmId: Long,
        label: String?,
        snoozeMinutes: Int = 10
    ): Notification {
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

        val snoozePi = PendingIntent.getBroadcast(
            this,
            (alarmId * 10 + 1).toInt(),
            Intent(this, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_SNOOZE
                putExtra(AlarmActionReceiver.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmActionReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes.coerceAtLeast(1))
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissPi = PendingIntent.getBroadcast(
            this,
            (alarmId * 10 + 2).toInt(),
            Intent(this, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_DISMISS
                putExtra(AlarmActionReceiver.EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.alarm_ringing_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPi)

        if (snoozeMinutes > 0) {
            builder.addAction(
                0,
                getString(R.string.notif_action_snooze, snoozeMinutes.coerceAtLeast(1)),
                snoozePi
            )
        }
        builder.addAction(0, getString(R.string.notif_action_dismiss), dismissPi)

        return builder.build()
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
        releaseWakeLock()
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
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
