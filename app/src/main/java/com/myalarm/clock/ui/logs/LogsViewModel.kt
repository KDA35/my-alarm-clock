package com.myalarm.clock.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myalarm.clock.util.AppLogger
import com.myalarm.clock.util.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logger: AppLogger
) : ViewModel() {

    val entries: StateFlow<List<LogEntry>> = logger.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() = logger.clearAll()

    fun getAllLogText(): String = logger.getAllLogText()
}
