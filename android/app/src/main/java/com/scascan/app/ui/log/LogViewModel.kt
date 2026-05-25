package com.scascan.app.ui.log

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scascan.app.R
import com.scascan.app.data.health.HealthConnectManager
import com.scascan.app.data.local.LogEntry
import com.scascan.app.data.model.MacroTargets
import com.scascan.app.data.repository.LogRepository
import com.scascan.app.data.repository.NutritionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

    private val _dateOffset = MutableStateFlow(0)

    val isToday: StateFlow<Boolean> = _dateOffset
        .map { it == 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val selectedDateLabel: StateFlow<String> = _dateOffset
        .map { offset ->
            when (offset) {
                0  -> context.getString(R.string.log_date_today)
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

    fun goToPreviousDay() { _dateOffset.value--; loadHealthData() }
    fun goToNextDay() { if (_dateOffset.value < 0) { _dateOffset.value++; loadHealthData() } }

    // ── Adaptive targets ───────────────────────────────────────────────────────

    /**
     * Unified Health Connect + adaptive-target state.
     *
     * [Active] carries the raw HC metrics (steps, activeKcal), the weight-trend
     * correction, and the final adapted calorie target so the Fragment only needs
     * to read from one flow.
     */
    sealed class AdaptiveState {
        /** Health Connect SDK not installed on this device. */
        object HcUnavailable : AdaptiveState()
        /** SDK present but permissions not granted. */
        object HcDisconnected : AdaptiveState()
        /** Permissions granted; all fields computed. */
        data class Active(
            val steps: Long,
            val activeKcal: Double,
            val trendAdjustment: Int,
            val trendStatus: TrendStatus,
            val weeklyRateKgPerWeek: Double?
        ) : AdaptiveState()

        enum class TrendStatus {
            /** < 5 days of weight data available. */
            NoData,
            /** Change rate is within ±0.15 kg/wk of the goal rate. */
            OnTrack,
            /** Losing/gaining faster than desired → +200 kcal correction. */
            TooFast,
            /** Losing/gaining slower than desired → −200 kcal correction. */
            TooSlow
        }
    }

    private val _adaptiveState = MutableStateFlow<AdaptiveState>(AdaptiveState.HcUnavailable)
    val adaptiveState: StateFlow<AdaptiveState> = _adaptiveState

    fun loadHealthData() {
        val offset = _dateOffset.value
        viewModelScope.launch {
            val isAvailable = healthManager.isAvailable
            val hasPermissions = healthManager.hasPermissions()
            
            Log.d("LogViewModel", "loadHealthData: available=$isAvailable, permissions=$hasPermissions")
            
            if (!isAvailable) {
                _adaptiveState.value = AdaptiveState.HcUnavailable
                return@launch
            }
            if (!hasPermissions) {
                _adaptiveState.value = AdaptiveState.HcDisconnected
                return@launch
            }

            val steps        = healthManager.readSteps(offset)
            val activeKcal   = healthManager.readActiveCalories(offset)
            
            // Weight trend is only relevant for "Today's" adaptive goal. 
            // For past days, we might want to hide it or show what it was, 
            // but for now let's only compute it for today.
            val trend = if (offset == 0) {
                val weightHist = healthManager.readWeightHistory(28)
                computeTrend(weightHist)
            } else {
                TrendResult(0, AdaptiveState.TrendStatus.NoData, null)
            }

            _adaptiveState.value = AdaptiveState.Active(
                steps            = steps,
                activeKcal       = activeKcal,
                trendAdjustment  = trend.adjustment,
                trendStatus      = trend.status,
                weeklyRateKgPerWeek = trend.weeklyRate
            )
            refreshTargets()
        }
    }

    private data class TrendResult(
        val adjustment: Int,
        val status: AdaptiveState.TrendStatus,
        val weeklyRate: Double?
    )

    /**
     * Computes a stepped ±200 kcal/day correction from weight readings.
     *
     * Goal rates (kg/wk):
     *   Lose weight  → −0.5   (aggressive loss threshold: −0.75; slow threshold: −0.2)
     *   Maintain     → 0       (no trend adjustment needed)
     *   Build muscle → +0.25  (fast threshold: +0.45; slow threshold: +0.1)
     */
    private fun computeTrend(readings: List<Pair<Long, Double>>): TrendResult {
        if (readings.size < 2) return TrendResult(0, AdaptiveState.TrendStatus.NoData, null)
        val sorted   = readings.sortedBy { it.first }
        val daysDiff = (sorted.last().first - sorted.first().first) / 86_400_000.0
        if (daysDiff < 5) return TrendResult(0, AdaptiveState.TrendStatus.NoData, null)

        val weeklyRate = (sorted.last().second - sorted.first().second) / (daysDiff / 7.0)

        val (adj, status) = when (logRepository.goalIndex()) {
            0 -> when {  // lose weight
                weeklyRate < -0.75 -> +200 to AdaptiveState.TrendStatus.TooFast
                weeklyRate > -0.20 -> -200 to AdaptiveState.TrendStatus.TooSlow
                else               ->    0 to AdaptiveState.TrendStatus.OnTrack
            }
            2 -> when {  // build muscle
                weeklyRate > 0.45 -> -200 to AdaptiveState.TrendStatus.TooFast
                weeklyRate < 0.10 -> +200 to AdaptiveState.TrendStatus.TooSlow
                else              ->    0 to AdaptiveState.TrendStatus.OnTrack
            }
            else ->          0 to AdaptiveState.TrendStatus.OnTrack  // maintain
        }
        return TrendResult(adj, status, weeklyRate)
    }

    // ── Targets ────────────────────────────────────────────────────────────────

    data class TargetInfo(
        val caloriesKcal: Int,
        val bmrKcal: Int,
        val goalOffsetKcal: Int,
        val macros: MacroTargets,
        val goalIndex: Int,
        val isAiComputed: Boolean,
        val bleedthroughKcal: Int = 0
    )

    private suspend fun buildTargetInfo(): TargetInfo {
        val bleedthrough = if (_dateOffset.value == 0) logRepository.getYesterdayBleedthrough() else 0
        return TargetInfo(
            caloriesKcal   = logRepository.dailyCalorieTarget(),
            bmrKcal         = logRepository.bmr(),
            goalOffsetKcal = logRepository.goalOffset(),
            macros         = logRepository.macroTargets(),
            goalIndex      = logRepository.goalIndex(),
            isAiComputed   = logRepository.isAiComputed(),
            bleedthroughKcal = bleedthrough
        )
    }

    private val _targetInfo = MutableStateFlow(TargetInfo(2000, 1500, 0, MacroTargets(0,0,0), 1, false))
    val targetInfo: StateFlow<TargetInfo> = _targetInfo

    fun refreshTargets() {
        viewModelScope.launch {
            _targetInfo.value = buildTargetInfo()
        }
    }

    // ── Log entry operations ───────────────────────────────────────────────────

    fun deleteEntry(entry: LogEntry) {
        viewModelScope.launch { logRepository.deleteEntry(entry) }
    }

    sealed class FixState {
        object Idle : FixState()
        object Loading : FixState()
        data class Success(val foodName: String) : FixState()
        data class Error(val message: String) : FixState()
    }

    private val _fixState = MutableStateFlow<FixState>(FixState.Idle)
    val fixState: StateFlow<FixState> = _fixState

    fun fixEntry(entry: LogEntry, correction: String) {
        _fixState.value = FixState.Loading
        viewModelScope.launch {
            nutritionRepository.fixEntry(entry.foodName, entry.servingSize, correction)
                .onSuccess { facts ->
                    logRepository.updateEntry(
                        entry.copy(
                            foodName       = facts.foodName,
                            servingSize    = facts.servingSize,
                            calories       = facts.calories,
                            protein        = facts.protein,
                            carbohydrates  = facts.carbohydrates,
                            fat            = facts.fat,
                            fiber          = facts.fiber,
                            sugar          = facts.sugar,
                            sodium         = facts.sodium
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
