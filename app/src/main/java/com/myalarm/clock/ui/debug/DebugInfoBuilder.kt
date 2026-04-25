package com.myalarm.clock.ui.debug

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.myalarm.clock.BuildConfig
import com.myalarm.clock.alarm.AlarmService
import com.myalarm.clock.data.Alarm
import com.myalarm.clock.data.AlarmGroup
import com.myalarm.clock.data.DayOfWeekMask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DebugInfoBuilder {

    fun deviceInfo(context: Context): Map<String, String> {
        val rom = detectRom()
        return linkedMapOf(
            "Manufacturer" to Build.MANUFACTURER,
            "Model" to Build.MODEL,
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Build" to Build.DISPLAY,
            "ROM" to rom,
            "App" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
    }

    private fun detectRom(): String {
        val markers = listOf(Build.HOST, Build.FINGERPRINT, Build.PRODUCT, Build.DISPLAY)
            .joinToString(" ").lowercase()
        return when {
            "graphene" in markers -> "GrapheneOS"
            "calyx" in markers -> "CalyxOS"
            "lineage" in markers -> "LineageOS"
            else -> Build.HOST
        }
    }

    data class PermissionRow(
        val name: String,
        val granted: Boolean,
        val applicable: Boolean = true,
        val howToFix: String? = null
    )

    fun checkPermissions(context: Context): List<PermissionRow> = listOf(
        PermissionRow(
            name = "POST_NOTIFICATIONS",
            granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true,
            howToFix = "Settings → Apps → MyAlarm → Notifications"
        ),
        PermissionRow(
            name = "SCHEDULE_EXACT_ALARM",
            granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                    .canScheduleExactAlarms()
            } else true,
            howToFix = "Settings → Apps → Special access → Alarms & reminders"
        ),
        PermissionRow(
            name = "USE_FULL_SCREEN_INTENT",
            granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .canUseFullScreenIntent()
            } else true,
            howToFix = "Settings → Apps → Special access → Full screen notifications"
        ),
        PermissionRow(
            name = "SYSTEM_ALERT_WINDOW (Overlay)",
            granted = Settings.canDrawOverlays(context),
            howToFix = "Settings → Apps → Special access → Display over other apps"
        ),
        PermissionRow(
            name = "Battery optimization disabled",
            granted = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName),
            howToFix = "Settings → Apps → MyAlarm → Battery → Unrestricted"
        )
    )

    data class ChannelInfo(
        val id: String,
        val importance: String,
        val bypassDnd: Boolean,
        val sound: String,
        val lockscreenVisibility: String
    )

    fun channelInfo(context: Context): ChannelInfo? {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = nm.getNotificationChannel(AlarmService.CHANNEL_ID) ?: return null
        return ChannelInfo(
            id = ch.id,
            importance = "${importanceLabel(ch.importance)} (${ch.importance})",
            bypassDnd = ch.canBypassDnd(),
            sound = ch.sound?.toString() ?: "null",
            lockscreenVisibility = visibilityLabel(ch.lockscreenVisibility)
        )
    }

    private fun importanceLabel(level: Int): String = when (level) {
        NotificationManager.IMPORTANCE_NONE -> "NONE"
        NotificationManager.IMPORTANCE_MIN -> "MIN"
        NotificationManager.IMPORTANCE_LOW -> "LOW"
        NotificationManager.IMPORTANCE_DEFAULT -> "DEFAULT"
        NotificationManager.IMPORTANCE_HIGH -> "HIGH"
        else -> "UNKNOWN"
    }

    private fun visibilityLabel(level: Int): String = when (level) {
        android.app.Notification.VISIBILITY_PUBLIC -> "PUBLIC (1)"
        android.app.Notification.VISIBILITY_PRIVATE -> "PRIVATE (0)"
        android.app.Notification.VISIBILITY_SECRET -> "SECRET (-1)"
        else -> "UNKNOWN ($level)"
    }

    data class DndStatus(val state: String, val filter: String)

    fun dndStatus(context: Context): DndStatus {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val filter = nm.currentInterruptionFilter
        val filterLabel = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "All notifications allowed"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority only"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms only"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "None / total silence"
            else -> "Unknown ($filter)"
        }
        val state = if (filter == NotificationManager.INTERRUPTION_FILTER_ALL) "OFF" else "ON"
        return DndStatus(state, "$filterLabel ($filter)")
    }

    fun testFlagsBlock(): String {
        val l1 = if (AlarmService.forceDisableLayer1) "DISABLED" else "ENABLED"
        val l2 = if (AlarmService.forceDisableLayer2) "DISABLED" else "ENABLED"
        val l3 = if (AlarmService.forceDisableLayer3) "DISABLED" else "ENABLED"
        return "Layer 1 (FSI): $l1\nLayer 2 (Overlay): $l2\nLayer 3 (Actions): $l3"
    }

    fun anyTestFlagActive(): Boolean =
        AlarmService.forceDisableLayer1 ||
            AlarmService.forceDisableLayer2 ||
            AlarmService.forceDisableLayer3

    fun alarmsDescription(
        alarms: List<Alarm>,
        groups: List<AlarmGroup>,
        anonymize: Boolean
    ): String {
        if (alarms.isEmpty()) return "(no alarms)"
        val groupById = groups.associateBy { it.id }
        val now = System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return alarms.joinToString("\n") { a ->
            val label = if (anonymize) "<label ${a.label.length} chars>" else "\"${a.label}\""
            val days = DayOfWeekMask.formatDaysOfWeek(a.daysOfWeek)
            val gName = a.groupId?.let { groupById[it]?.name }
            val groupStr = when {
                gName == null -> "no group"
                anonymize -> "group=<group ${gName.length} chars>"
                else -> "group=$gName"
            }
            val nextTrigger = if (a.enabled) DayOfWeekMask.calculateNextTrigger(a, now) else null
            val nextStr = nextTrigger?.let {
                val deltaMin = (it - now) / 60_000L
                "${timeFormat.format(Date(it))} (in ${formatDelta(deltaMin)})"
            } ?: "—"
            "ID ${a.id} · %02d:%02d · $label · $days · ${if (a.enabled) "enabled" else "disabled"} · $groupStr · next: $nextStr"
                .format(a.hour, a.minute)
        }
    }

    private fun formatDelta(totalMinutes: Long): String {
        if (totalMinutes < 1) return "<1m"
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h == 0L) "${m}m" else "${h}h ${m}m"
    }

    fun renderFullText(
        context: Context,
        alarms: List<Alarm>,
        groups: List<AlarmGroup>,
        anonymize: Boolean = false
    ): String {
        val sb = StringBuilder()
        sb.appendLine("=== MyAlarm Debug Info ===")
        sb.appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine()
        sb.appendLine("[Device]")
        deviceInfo(context).forEach { (k, v) -> sb.appendLine("$k: $v") }
        sb.appendLine()
        sb.appendLine("[Permissions]")
        checkPermissions(context).forEach { p ->
            val status = if (p.granted) "GRANTED" else "NOT GRANTED ← needs fix"
            sb.appendLine("${p.name}: $status")
        }
        sb.appendLine()
        sb.appendLine("[Notification Channel]")
        channelInfo(context)?.let { ch ->
            sb.appendLine("ID: ${ch.id}")
            sb.appendLine("Importance: ${ch.importance}")
            sb.appendLine("Bypass DND: ${ch.bypassDnd}")
            sb.appendLine("Sound: ${ch.sound}")
            sb.appendLine("Lockscreen visibility: ${ch.lockscreenVisibility}")
        } ?: sb.appendLine("(channel not yet created — fire an alarm once)")
        sb.appendLine()
        sb.appendLine("[DND Status]")
        val dnd = dndStatus(context)
        sb.appendLine("DND: ${dnd.state}")
        sb.appendLine("Filter: ${dnd.filter}")
        sb.appendLine()
        sb.appendLine("[Alarms]")
        sb.appendLine(alarmsDescription(alarms, groups, anonymize))
        sb.appendLine()
        sb.appendLine("[Test Mode Flags]")
        sb.appendLine(testFlagsBlock())
        return sb.toString()
    }
}
