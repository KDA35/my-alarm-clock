package com.myalarm.clock.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myalarm.clock.data.Alarm
import com.myalarm.clock.data.AlarmGroup
import com.myalarm.clock.data.AlarmRepository
import com.myalarm.clock.data.VibrationPattern
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AlarmEditState(
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val daysOfWeek: Int = 0,
    val ringtoneUri: String? = null,
    val volume: Int = 7,
    val vibrationEnabled: Boolean = true,
    val vibrationPattern: String = VibrationPattern.BASIC,
    val snoozeEnabled: Boolean = true,
    val snoozeIntervalMinutes: Int = 10,
    val snoozeMaxRepeats: Int = 3,
    val snoozeExpanded: Boolean = false,
    val groupId: Long? = null,
    val isLoaded: Boolean = false
)

@HiltViewModel
class AlarmEditViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val logger: AppLogger,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "EditVM"
    }

    private val alarmId: Long = savedStateHandle["alarmId"] ?: -1L
    val isEditing: Boolean = alarmId != -1L

    private val _state = MutableStateFlow(AlarmEditState())
    val state: StateFlow<AlarmEditState> = _state.asStateFlow()

    val groups: StateFlow<List<AlarmGroup>> = repository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (isEditing) loadExisting() else initDefaults()
    }

    private fun loadExisting() {
        viewModelScope.launch {
            val alarm = repository.getById(alarmId)
            if (alarm == null) {
                logger.e(TAG, "Alarm id=$alarmId not found")
                return@launch
            }
            _state.value = AlarmEditState(
                hour = alarm.hour,
                minute = alarm.minute,
                label = alarm.label,
                daysOfWeek = alarm.daysOfWeek,
                ringtoneUri = alarm.ringtoneUri,
                volume = alarm.volume,
                vibrationEnabled = alarm.vibrationEnabled,
                vibrationPattern = alarm.vibrationPattern,
                snoozeEnabled = alarm.snoozeIntervalMinutes > 0,
                snoozeIntervalMinutes = alarm.snoozeIntervalMinutes.coerceAtLeast(1),
                snoozeMaxRepeats = alarm.snoozeMaxRepeats,
                snoozeExpanded = false,
                groupId = alarm.groupId,
                isLoaded = true
            )
            logger.i(TAG, "Loaded alarm id=$alarmId for edit")
        }
    }

    private fun initDefaults() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1)
            val rounded = ((get(Calendar.MINUTE) + 4) / 5) * 5
            if (rounded >= 60) {
                set(Calendar.MINUTE, 0)
                add(Calendar.HOUR_OF_DAY, 1)
            } else {
                set(Calendar.MINUTE, rounded)
            }
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        _state.value = _state.value.copy(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            isLoaded = true
        )
        logger.i(
            TAG,
            "New alarm with defaults: %02d:%02d".format(
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE)
            )
        )
    }

    fun setHour(h: Int) = _state.update { it.copy(hour = h.coerceIn(0, 23)) }
    fun setMinute(m: Int) = _state.update { it.copy(minute = m.coerceIn(0, 59)) }
    fun toggleDay(dayBit: Int) = _state.update { it.copy(daysOfWeek = it.daysOfWeek xor dayBit) }
    fun setLabel(s: String) = _state.update { it.copy(label = s.take(100)) }
    fun setRingtone(uri: Uri?) = _state.update { it.copy(ringtoneUri = uri?.toString()) }
    fun setVolume(v: Int) = _state.update { it.copy(volume = v.coerceIn(0, 10)) }
    fun setVibration(enabled: Boolean) = _state.update { it.copy(vibrationEnabled = enabled) }
    fun setVibrationPattern(name: String) = _state.update { it.copy(vibrationPattern = name) }
    fun toggleSnoozeExpanded() = _state.update { it.copy(snoozeExpanded = !it.snoozeExpanded) }
    fun setSnoozeEnabled(enabled: Boolean) = _state.update { it.copy(snoozeEnabled = enabled) }
    fun setSnoozeInterval(minutes: Int) =
        _state.update { it.copy(snoozeIntervalMinutes = minutes.coerceIn(1, 60)) }
    fun setSnoozeRepeats(count: Int) = _state.update { it.copy(snoozeMaxRepeats = count) }
    fun setGroupId(groupId: Long?) = _state.update { it.copy(groupId = groupId) }

    fun createGroup(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createGroup(name.trim())
            setGroupId(id)
            onCreated(id)
        }
    }

    fun save(onComplete: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val alarm = if (isEditing) {
                val existing = repository.getById(alarmId) ?: run {
                    logger.e(TAG, "Cannot save: alarm id=$alarmId disappeared")
                    return@launch
                }
                existing.copy(
                    hour = s.hour,
                    minute = s.minute,
                    label = s.label.trim(),
                    daysOfWeek = s.daysOfWeek,
                    ringtoneUri = s.ringtoneUri,
                    volume = s.volume,
                    vibrationEnabled = s.vibrationEnabled,
                    vibrationPattern = s.vibrationPattern,
                    snoozeIntervalMinutes = if (s.snoozeEnabled) s.snoozeIntervalMinutes else 0,
                    snoozeMaxRepeats = s.snoozeMaxRepeats,
                    groupId = s.groupId,
                    enabled = true
                )
            } else {
                Alarm(
                    hour = s.hour,
                    minute = s.minute,
                    label = s.label.trim(),
                    daysOfWeek = s.daysOfWeek,
                    ringtoneUri = s.ringtoneUri,
                    volume = s.volume,
                    vibrationEnabled = s.vibrationEnabled,
                    vibrationPattern = s.vibrationPattern,
                    snoozeIntervalMinutes = if (s.snoozeEnabled) s.snoozeIntervalMinutes else 0,
                    snoozeMaxRepeats = s.snoozeMaxRepeats,
                    groupId = s.groupId,
                    enabled = true
                )
            }
            val savedId = repository.save(alarm)
            logger.i(
                TAG,
                "Saved alarm id=$savedId time=%02d:%02d label='${alarm.label}' volume=${alarm.volume} pattern=${alarm.vibrationPattern} groupId=${alarm.groupId}"
                    .format(alarm.hour, alarm.minute)
            )
            onComplete()
        }
    }

    fun delete(onComplete: () -> Unit) {
        viewModelScope.launch {
            val alarm = repository.getById(alarmId) ?: run {
                logger.e(TAG, "Cannot delete: alarm id=$alarmId not found")
                onComplete()
                return@launch
            }
            repository.delete(alarm)
            logger.i(TAG, "Deleted alarm id=$alarmId")
            onComplete()
        }
    }
}
