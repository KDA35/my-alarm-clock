package com.myalarm.clock.ui.ringing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myalarm.clock.alarm.AlarmScheduler
import com.myalarm.clock.data.Alarm
import com.myalarm.clock.data.AlarmRepository
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RingingViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val logger: AppLogger,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        val SNOOZE_PRESETS = listOf(10, 20, 30, 40, 50, 60)
        const val DEFAULT_SNOOZE_INDEX = 0
        val POSTPONE_PRESETS = listOf(30, 60, 120, 240)
        private const val TAG = "RingingVM"
    }

    private val alarmId: Long = savedStateHandle["alarm_id"] ?: -1L

    private val _state = MutableStateFlow(RingingState())
    val state: StateFlow<RingingState> = _state.asStateFlow()

    init {
        loadAlarm()
        startTimeTicker()
    }

    private fun loadAlarm() {
        viewModelScope.launch {
            if (alarmId < 0) {
                logger.e(TAG, "No alarmId provided, cannot load")
                return@launch
            }
            val alarm = repository.getById(alarmId)
            if (alarm == null) {
                logger.e(TAG, "Alarm id=$alarmId not found")
                return@launch
            }
            val defaultIdx = SNOOZE_PRESETS.indexOfFirst { it >= alarm.snoozeIntervalMinutes }
                .takeIf { it >= 0 } ?: DEFAULT_SNOOZE_INDEX
            _state.update {
                it.copy(alarm = alarm, snoozeIndex = defaultIdx)
            }
            logger.i(
                TAG,
                "Alarm loaded: id=$alarmId label='${alarm.label}' snoozeDefault=${SNOOZE_PRESETS[defaultIdx]}min"
            )
        }
    }

    private fun startTimeTicker() {
        viewModelScope.launch {
            while (true) {
                _state.update { it.copy(currentTime = System.currentTimeMillis()) }
                delay(30_000L)
            }
        }
    }

    fun incrementSnooze() {
        _state.update {
            val newIdx = (it.snoozeIndex + 1).coerceAtMost(SNOOZE_PRESETS.lastIndex)
            it.copy(snoozeIndex = newIdx)
        }
    }

    fun decrementSnooze() {
        _state.update {
            val newIdx = (it.snoozeIndex - 1).coerceAtLeast(0)
            it.copy(snoozeIndex = newIdx)
        }
    }

    fun snooze(): Int {
        val minutes = SNOOZE_PRESETS[_state.value.snoozeIndex]
        val alarm = _state.value.alarm ?: return minutes
        scheduler.scheduleSnooze(alarm, minutes)
        logger.i(TAG, "User snoozed alarm id=$alarmId for $minutes minutes")
        return minutes
    }

    fun postpone(durationMinutes: Int) {
        val alarm = _state.value.alarm ?: return
        scheduler.scheduleSnooze(alarm, durationMinutes)
        logger.i(TAG, "User postponed alarm id=$alarmId for $durationMinutes minutes")
    }

    fun postponeUntil(targetHour: Int, targetMinute: Int) {
        val alarm = _state.value.alarm ?: return
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val durationMinutes =
            ((target.timeInMillis - now.timeInMillis) / 60_000L).toInt().coerceAtLeast(1)
        scheduler.scheduleSnooze(alarm, durationMinutes)
        logger.i(
            TAG,
            "User postponed alarm id=$alarmId until %02d:%02d (in $durationMinutes minutes)"
                .format(targetHour, targetMinute)
        )
    }

    fun dismiss() {
        logger.i(TAG, "User dismissed alarm id=$alarmId")
    }
}

data class RingingState(
    val alarm: Alarm? = null,
    val snoozeIndex: Int = RingingViewModel.DEFAULT_SNOOZE_INDEX,
    val currentTime: Long = System.currentTimeMillis()
) {
    val snoozeMinutes: Int
        get() = RingingViewModel.SNOOZE_PRESETS[snoozeIndex]

    val canDecrement: Boolean
        get() = snoozeIndex > 0

    val canIncrement: Boolean
        get() = snoozeIndex < RingingViewModel.SNOOZE_PRESETS.lastIndex

    fun resultingRingTime(extraMinutes: Int = snoozeMinutes): String {
        val target = currentTime + extraMinutes * 60_000L
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(target))
    }
}
