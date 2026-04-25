package com.myalarm.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myalarm.clock.alarm.AlarmScheduler
import com.myalarm.clock.data.Alarm
import com.myalarm.clock.data.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler
) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    fun createTestAlarmIn30Seconds() {
        viewModelScope.launch {
            val target = Calendar.getInstance().apply {
                add(Calendar.SECOND, 30)
            }
            val alarm = Alarm(
                hour = target.get(Calendar.HOUR_OF_DAY),
                minute = target.get(Calendar.MINUTE),
                label = "Тест",
                daysOfWeek = 0,
                enabled = true
            )
            repository.save(alarm)
        }
    }

    fun toggle(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.toggleEnabled(id, enabled) }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch { repository.delete(alarm) }
    }
}
