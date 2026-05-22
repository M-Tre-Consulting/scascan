package com.scascan.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scascan.app.data.health.HealthConnectManager
import com.scascan.app.data.local.LogEntry
import com.scascan.app.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepository: LogRepository,
    val healthManager: HealthConnectManager
) : ViewModel() {

    val todayEntries: StateFlow<List<LogEntry>> = logRepository.todayEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    sealed class HealthUiState {
        object Unavailable : HealthUiState()
        object Disconnected : HealthUiState()
        data class Connected(val steps: Long, val activeKcal: Double) : HealthUiState()
    }

    private val _healthState = MutableStateFlow<HealthUiState>(HealthUiState.Unavailable)
    val healthState: StateFlow<HealthUiState> = _healthState

    val dailyTarget: Int get() = logRepository.dailyCalorieTarget()

    fun loadHealthData() {
        viewModelScope.launch {
            if (!healthManager.isAvailable) {
                _healthState.value = HealthUiState.Unavailable
                return@launch
            }
            if (!healthManager.hasPermissions()) {
                _healthState.value = HealthUiState.Disconnected
                return@launch
            }
            val steps = healthManager.readTodaySteps()
            val activeKcal = healthManager.readTodayActiveCalories()
            _healthState.value = HealthUiState.Connected(steps, activeKcal)
        }
    }

    fun deleteEntry(entry: LogEntry) {
        viewModelScope.launch { logRepository.deleteEntry(entry) }
    }
}
