package com.scascan.app.ui.log

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scascan.app.R
import com.scascan.app.data.health.HealthConnectManager
import com.scascan.app.data.local.LogEntry
import com.scascan.app.data.model.MacroTargets
import com.scascan.app.data.repository.LogRepository
import com.scascan.app.data.repository.NutritionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logRepository: LogRepository,
    private val nutritionRepository: NutritionRepository,
    val healthManager: HealthConnectManager
) : ViewModel() {

    // ── Date navigation ────────────────────────────────────────────────────────

    private val _dateOffset = MutableStateFlow(0) // 0 = today, -1 = yesterday, etc.

    val isToday: StateFlow<Boolean> = _dateOffset
        .map { it == 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val selectedDateLabel: StateFlow<String> = _dateOffset
        .map { offset ->
            when (offset) {
                0 -> context.getString(R.string.log_date_today)
                -1 -> context.getString(R.string.log_date_yesterday)
                else -> {
                    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, context.getString(R.string.log_date_today))

    @OptIn(ExperimentalCoroutinesApi::class)
    val logEntries: StateFlow<List<LogEntry>> = _dateOffset
        .flatMapLatest { offset -> logRepository.entriesForDateOffset(offset) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun goToPreviousDay() { _dateOffset.value-- }
    fun goToNextDay() { if (_dateOffset.value < 0) _dateOffset.value++ }

    // ── Health Connect ─────────────────────────────────────────────────────────

    sealed class HealthUiState {
        object Unavailable : HealthUiState()
        object Disconnected : HealthUiState()
        data class Connected(val steps: Long, val activeKcal: Double) : HealthUiState()
    }

    private val _healthState = MutableStateFlow<HealthUiState>(HealthUiState.Unavailable)
    val healthState: StateFlow<HealthUiState> = _healthState

    // ── Targets (refreshed on resume so Profile changes show immediately) ─────

    data class TargetInfo(val caloriesKcal: Int, val macros: MacroTargets)

    private val _targetInfo = MutableStateFlow(
        TargetInfo(logRepository.dailyCalorieTarget(), logRepository.macroTargets())
    )
    val targetInfo: StateFlow<TargetInfo> = _targetInfo

    fun refreshTargets() {
        _targetInfo.value = TargetInfo(logRepository.dailyCalorieTarget(), logRepository.macroTargets())
    }

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

    // ── Log entry operations ───────────────────────────────────────────────────

    fun deleteEntry(entry: LogEntry) {
        viewModelScope.launch { logRepository.deleteEntry(entry) }
    }

    sealed class FixState {
        object Idle : FixState()
        data class Success(val foodName: String) : FixState()
        data class Error(val message: String) : FixState()
    }

    private val _fixState = MutableStateFlow<FixState>(FixState.Idle)
    val fixState: StateFlow<FixState> = _fixState

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
