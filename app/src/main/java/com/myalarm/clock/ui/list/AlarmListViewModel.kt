package com.myalarm.clock.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myalarm.clock.alarm.AlarmScheduler
import com.myalarm.clock.data.Alarm
import com.myalarm.clock.data.AlarmGroup
import com.myalarm.clock.data.AlarmRepository
import com.myalarm.clock.data.DayOfWeekMask
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class NextTriggerInfo(
    val timeText: String,
    val deltaText: String
)

data class GroupedAlarms(
    val groups: List<GroupSection>,
    val ungrouped: List<Alarm>
)

data class GroupSection(
    val group: AlarmGroup,
    val alarms: List<Alarm>
)

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val logger: AppLogger
) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<AlarmGroup>> = repository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val grouped: StateFlow<GroupedAlarms> =
        combine(alarms, groups) { allAlarms, allGroups ->
            val byGroup = allAlarms.groupBy { it.groupId }
            val sections = allGroups.map { g -> GroupSection(g, byGroup[g.id].orEmpty()) }
            GroupedAlarms(sections, byGroup[null].orEmpty())
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            GroupedAlarms(emptyList(), emptyList())
        )

    val nextTriggerInfo: StateFlow<NextTriggerInfo?> =
        combine(alarms, tickerFlow(60_000L)) { list, _ ->
            computeNextTriggerInfo(list)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    fun toggleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.toggleEnabled(id, enabled) }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch { repository.delete(alarm) }
    }

    fun createTestAlarm() {
        viewModelScope.launch {
            val target = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis() + 30_000
            }
            val alarm = Alarm(
                hour = target.get(Calendar.HOUR_OF_DAY),
                minute = target.get(Calendar.MINUTE),
                label = "Тест",
                daysOfWeek = 0,
                enabled = true
            )
            repository.save(alarm)
            logger.i("UI", "Test alarm created for now+30s")
        }
    }

    fun toggleGroup(groupId: Long, enabled: Boolean) {
        viewModelScope.launch { repository.toggleGroupEnabled(groupId, enabled) }
    }

    fun renameGroup(groupId: Long, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) repository.renameGroup(groupId, newName.trim())
        }
    }

    fun deleteGroup(group: AlarmGroup) {
        viewModelScope.launch { repository.deleteGroup(group) }
    }

    private fun computeNextTriggerInfo(list: List<Alarm>): NextTriggerInfo? {
        val now = System.currentTimeMillis()
        val nextTrigger = list
            .filter { it.enabled }
            .map { DayOfWeekMask.calculateNextTrigger(it, now) }
            .minOrNull() ?: return null

        val cal = Calendar.getInstance().apply { timeInMillis = nextTrigger }
        val timeText = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val deltaMs = nextTrigger - now
        val deltaText = formatDelta(deltaMs)
        return NextTriggerInfo(timeText, deltaText)
    }

    private fun formatDelta(deltaMs: Long): String {
        if (deltaMs < 60_000L) return "сейчас"
        val totalMinutes = deltaMs / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours == 0L) "через $minutes мин" else "через $hours ч $minutes мин"
    }

    private fun tickerFlow(periodMs: Long) = flow {
        while (true) {
            emit(Unit)
            delay(periodMs)
        }
    }
}
