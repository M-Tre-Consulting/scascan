package com.scascan.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scascan.app.data.health.HealthConnectManager
import com.scascan.app.data.local.LogEntry
import com.scascan.app.data.repository.LogRepository
import com.scascan.app.data.repository.NutritionRepository
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
    private val nutritionRepository: NutritionRepository,
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

    sealed class FixState {
        object Idle : FixState()
        data class Success(val foodName: String) : FixState()
        data class Error(val message: String) : FixState()
    }

    private val _fixState = MutableStateFlow<FixState>(FixState.Idle)
    val fixState: StateFlow<FixState> = _fixState

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

    fun fixEntry(entry: LogEntry, correction: String) {
        viewModelScope.launch {
            nutritionRepository.fixEntry(entry.foodName, entry.servingSize, correction)
                .onSuccess { facts ->
                    logRepository.updateEntry(
                        entry.copy(
                            foodName = facts.foodName,
                            servingSize = facts.servingSize,
                            calories = facts.calories,
                            protein = facts.protein,
                            carbohydrates = facts.carbohydrates,
                            fat = facts.fat,
                            fiber = facts.fiber,
                            sugar = facts.sugar,
                            sodium = facts.sodium
                        )
                    )
                    _fixState.value = FixState.Success(facts.foodName)
                }
                .onFailure { e ->
                    _fixState.value = FixState.Error(e.message ?: "Failed to fix entry")
                }
        }
    }

    fun resetFixState() {
        _fixState.value = FixState.Idle
    }
}
