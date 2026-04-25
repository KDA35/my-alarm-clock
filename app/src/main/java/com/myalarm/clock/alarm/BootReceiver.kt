package com.myalarm.clock.alarm

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
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var logger: AppLogger

    companion object {
        private const val TAG = "Boot"
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        logger.i(TAG, "Reschedule trigger received, action=$action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                logger.i(TAG, "Rescheduling all enabled alarms...")
                val count = repository.rescheduleAll()
                logger.i(TAG, "Rescheduling complete: $count alarms")
            } catch (e: Exception) {
                logger.e(TAG, "Failed to reschedule alarms", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
