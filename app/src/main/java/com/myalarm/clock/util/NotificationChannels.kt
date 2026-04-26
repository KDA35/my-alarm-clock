package com.myalarm.clock.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.myalarm.clock.R
import com.myalarm.clock.alarm.AlarmService

/**
 * Android remembers a NotificationChannel's parameters only at FIRST creation. Once a user has
 * the app installed, calling [NotificationManager.createNotificationChannel] again on an existing
 * channel is a no-op for [NotificationChannel.setBypassDnd], [NotificationChannel.setLockscreenVisibility]
 * and [NotificationChannel.setImportance]. Earlier versions of MyAlarm shipped without
 * `setBypassDnd(true)` so users who installed those builds end up with a channel that does NOT
 * pierce DND or render publicly on the lock screen — even after updating to a fixed build.
 *
 * This helper detects that mismatch and re-creates the channel. The call is idempotent and cheap.
 */
object NotificationChannels {

    private const val TAG = "ChannelMigration"

    fun ensureAlarmChannel(context: Context, logger: AppLogger? = null) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(AlarmService.CHANNEL_ID)
        if (existing != null) {
            val needsRecreate =
                !existing.canBypassDnd() ||
                    existing.lockscreenVisibility != Notification.VISIBILITY_PUBLIC ||
                    existing.importance < NotificationManager.IMPORTANCE_HIGH
            if (!needsRecreate) return
            logger?.w(
                TAG,
                "Recreating channel: bypassDnd=${existing.canBypassDnd()}, " +
                    "lockscreen=${existing.lockscreenVisibility}, " +
                    "importance=${existing.importance}"
            )
            nm.deleteNotificationChannel(AlarmService.CHANNEL_ID)
        }
        val channel = NotificationChannel(
            AlarmService.CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        nm.createNotificationChannel(channel)
        logger?.i(TAG, "Channel created with bypassDnd=true, lockscreen=PUBLIC, importance=HIGH")
    }
}
