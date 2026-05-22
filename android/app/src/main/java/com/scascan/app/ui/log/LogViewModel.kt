package com.scascan.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scascan.app.data.local.LogEntry
import com.scascan.app.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {

    val todayEntries: StateFlow<List<LogEntry>> = logRepository.todayEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dailyTarget: Int get() = logRepository.dailyCalorieTarget()

    fun deleteEntry(entry: LogEntry) {
        viewModelScope.launch { logRepository.deleteEntry(entry) }
    }
}
