package com.myalarm.clock.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myalarm.clock.data.Alarm
import com.myalarm.clock.data.AlarmGroup
import com.myalarm.clock.data.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DebugInfoData(
    val alarms: List<Alarm> = emptyList(),
    val groups: List<AlarmGroup> = emptyList()
)

@HiltViewModel
class DebugInfoViewModel @Inject constructor(
    repository: AlarmRepository
) : ViewModel() {

    val data: StateFlow<DebugInfoData> =
        combine(repository.observeAll(), repository.observeGroups()) { alarms, groups ->
            DebugInfoData(alarms, groups)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugInfoData())
}
