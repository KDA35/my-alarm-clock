package com.myalarm.clock.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface DiagnosticState {
    data object Idle : DiagnosticState
    data object Building : DiagnosticState
    data class Ready(val file: File) : DiagnosticState
    data class Error(val message: String) : DiagnosticState
}

@HiltViewModel
class DiagnosticPackageViewModel @Inject constructor(
    private val builder: DiagnosticPackageBuilder,
    private val logger: AppLogger
) : ViewModel() {

    private val _state = MutableStateFlow<DiagnosticState>(DiagnosticState.Idle)
    val state: StateFlow<DiagnosticState> = _state.asStateFlow()

    fun build() {
        if (_state.value is DiagnosticState.Building) return
        _state.value = DiagnosticState.Building
        viewModelScope.launch {
            try {
                val file = builder.build()
                _state.value = DiagnosticState.Ready(file)
            } catch (e: Exception) {
                logger.e("Diagnostic", "Failed to build diagnostic package", e)
                _state.value = DiagnosticState.Error(e.message ?: "unknown error")
            }
        }
    }

    fun reset() {
        _state.value = DiagnosticState.Idle
    }
}
