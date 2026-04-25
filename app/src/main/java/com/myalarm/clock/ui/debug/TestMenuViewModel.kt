package com.myalarm.clock.ui.debug

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myalarm.clock.alarm.AlarmRingingActivity
import com.myalarm.clock.alarm.AlarmService
import com.myalarm.clock.data.Alarm
import com.myalarm.clock.data.AlarmRepository
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TestMenuViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val logger: AppLogger,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "TestMenu"
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        private val mainHandler = Handler(Looper.getMainLooper())
    }

    fun createTestAlarm(seconds: Int, label: String, onScheduled: (String) -> Unit) {
        viewModelScope.launch {
            val target = Calendar.getInstance().apply {
                add(Calendar.SECOND, seconds)
            }
            val groupId = repository.ensureTestGroup()
            val alarm = Alarm(
                hour = target.get(Calendar.HOUR_OF_DAY),
                minute = target.get(Calendar.MINUTE),
                label = label,
                daysOfWeek = 0,
                enabled = true,
                groupId = groupId
            )
            val id = repository.save(alarm)
            val ts = timeFormat.format(Date(target.timeInMillis))
            logger.i(TAG, "Created test alarm: id=$id label='$label', fires in ${seconds}s at $ts")
            onScheduled(ts)
        }
    }

    fun createTestSeries(onScheduled: (Int) -> Unit) {
        viewModelScope.launch {
            val groupId = repository.ensureTestGroup()
            (1..3).forEach { i ->
                val target = Calendar.getInstance().apply { add(Calendar.MINUTE, i) }
                val alarm = Alarm(
                    hour = target.get(Calendar.HOUR_OF_DAY),
                    minute = target.get(Calendar.MINUTE),
                    label = "Тест #${i} серии",
                    daysOfWeek = 0,
                    enabled = true,
                    groupId = groupId
                )
                repository.save(alarm)
            }
            logger.i(TAG, "Created 3-alarm test series")
            onScheduled(3)
        }
    }

    fun setLayerFlags(disableL1: Boolean, disableL2: Boolean, disableL3: Boolean) {
        AlarmService.forceDisableLayer1 = disableL1
        AlarmService.forceDisableLayer2 = disableL2
        AlarmService.forceDisableLayer3 = disableL3
        logger.i(TAG, "Layer flags set: L1=${!disableL1}, L2=${!disableL2}, L3=${!disableL3}")
        mainHandler.removeCallbacksAndMessages(RESET_TOKEN)
        mainHandler.postAtTime({
            AlarmService.resetTestFlags()
            logger.i(TAG, "Layer flags auto-reset after 5 minutes")
        }, RESET_TOKEN, android.os.SystemClock.uptimeMillis() + 5 * 60_000L)
    }

    fun resetLayerFlags() {
        AlarmService.resetTestFlags()
        logger.i(TAG, "Layer flags reset manually")
        mainHandler.removeCallbacksAndMessages(RESET_TOKEN)
    }

    fun openRingingUiDirect(onLaunched: () -> Unit) {
        viewModelScope.launch {
            val alarmId = withContext(Dispatchers.IO) {
                val existing = repository.observeAll().first().lastOrNull()
                existing?.id ?: run {
                    val groupId = repository.ensureTestGroup()
                    val now = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
                    repository.save(
                        Alarm(
                            hour = now.get(Calendar.HOUR_OF_DAY),
                            minute = now.get(Calendar.MINUTE),
                            label = "Тест UI",
                            daysOfWeek = 0,
                            enabled = false,
                            groupId = groupId
                        )
                    )
                }
            }
            logger.i(TAG, "Opening RingingActivity directly with alarmId=$alarmId (test UI mode)")
            val intent = Intent(context, AlarmRingingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmRingingActivity.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmRingingActivity.EXTRA_TEST_MODE_NO_SOUND, true)
            }
            context.startActivity(intent)
            onLaunched()
        }
    }

    fun deleteAllTestAlarms(onDeleted: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.deleteAllTestAlarms()
            onDeleted(count)
        }
    }

    fun clearLogs(onDone: () -> Unit) {
        logger.clearAll()
        onDone()
    }

    private object RESET_TOKEN
}
