package com.mtreconsulting.scascan.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtreconsulting.scascan.data.model.NutritionFacts
import com.mtreconsulting.scascan.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NutritionResultViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {

    sealed class LogState {
        object Idle : LogState()
        object Added : LogState()
    }

    private val _logState = MutableStateFlow<LogState>(LogState.Idle)
    val logState: StateFlow<LogState> = _logState

    fun addToLog(facts: NutritionFacts) {
        viewModelScope.launch {
            logRepository.addEntry(facts)
            _logState.value = LogState.Added
        }
    }
}
