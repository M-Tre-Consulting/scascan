package com.scascan.app.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scascan.app.data.local.LogEntry
import com.scascan.app.data.repository.LogRepository
import com.scascan.app.data.voice.VoiceRecognitionManager
import com.scascan.app.data.voice.VoiceState
import com.scascan.app.domain.usecase.SearchFoodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Speak-what-you-ate logging: transcribe → look up nutrition facts → add to the log
 * immediately, no confirm step. Mirrors iOS's VoiceLogController (ios/ARCHITECTURE.md §9),
 * bypassing AnalysisManager entirely since that pipeline's shared state is always intercepted
 * by MainFragment to show a confirm sheet, which this flow deliberately skips.
 */
@HiltViewModel
class VoiceLogViewModel @Inject constructor(
    private val voiceManager: VoiceRecognitionManager,
    private val searchFoodUseCase: SearchFoodUseCase,
    private val logRepository: LogRepository
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Listening : UiState()
        data class Transcribed(val text: String) : UiState()
        object Working : UiState()
        data class Added(val entry: LogEntry) : UiState()
        data class Error(val reason: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private var listenJob: Job? = null

    fun isAvailable(): Boolean = voiceManager.isAvailable()

    fun startListening() {
        listenJob?.cancel()
        _uiState.value = UiState.Listening
        listenJob = viewModelScope.launch {
            voiceManager.listen().collect { state ->
                when (state) {
                    is VoiceState.Listening -> _uiState.value = UiState.Listening
                    is VoiceState.PartialResult -> _uiState.value = UiState.Transcribed(state.text)
                    is VoiceState.FinalResult -> {
                        if (state.text.isBlank()) {
                            _uiState.value = UiState.Error(VoiceRecognitionManager.REASON_NO_MATCH)
                        } else {
                            logFromTranscript(state.text)
                        }
                    }
                    is VoiceState.Error -> _uiState.value = UiState.Error(state.reason)
                }
            }
        }
    }

    fun cancelListening() {
        listenJob?.cancel()
        voiceManager.cancel()
        _uiState.value = UiState.Idle
    }

    private fun logFromTranscript(transcript: String) {
        _uiState.value = UiState.Working
        viewModelScope.launch {
            searchFoodUseCase(transcript)
                .onSuccess { facts ->
                    val entry = logRepository.addEntry(facts)
                    _uiState.value = UiState.Added(entry)
                }
                .onFailure { e ->
                    _uiState.value = UiState.Error(e.message ?: VoiceRecognitionManager.REASON_GENERIC)
                }
        }
    }

    fun undoLastEntry(entry: LogEntry) {
        viewModelScope.launch { logRepository.deleteEntry(entry) }
    }

    override fun onCleared() {
        super.onCleared()
        listenJob?.cancel()
        voiceManager.cancel()
    }
}
