package com.myalarm.clock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.myalarm.clock.MainActivity
import com.myalarm.clock.R
import com.myalarm.clock.data.AlarmRepository
import com.myalarm.clock.data.DayOfWeekMask
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NextAlarmWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NextAlarmWidgetEntryPoint {
        fun repository(): AlarmRepository
    }

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext,
            NextAlarmWidgetEntryPoint::class.java
        ).repository()
        ids.forEach { id -> updateOne(context, manager, id, repo) }
    }

    private fun updateOne(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        repo: AlarmRepository
    ) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val now = System.currentTimeMillis()
            val alarms = runCatching { repo.getAllEnabled() }.getOrDefault(emptyList())
            val next = alarms.minByOrNull { DayOfWeekMask.calculateNextTrigger(it, now) }

            val views = RemoteViews(context.packageName, R.layout.widget_next_alarm)
            if (next == null) {
                views.setTextViewText(R.id.widget_time, "—")
                views.setTextViewText(
                    R.id.widget_alarm_name,
                    context.getString(R.string.widget_no_alarms)
                )
            } else {
                val triggerAt = DayOfWeekMask.calculateNextTrigger(next, now)
                val timeStr =
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(triggerAt))
                views.setTextViewText(R.id.widget_time, timeStr)
                views.setTextViewText(
                    R.id.widget_alarm_name,
                    next.label.ifBlank { context.getString(R.string.widget_no_label) }
                )
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pi = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            withContext(Dispatchers.Main) {
                manager.updateAppWidget(widgetId, views)
            }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val component = ComponentName(context, NextAlarmWidget::class.java)
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val intent = Intent(context, NextAlarmWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
