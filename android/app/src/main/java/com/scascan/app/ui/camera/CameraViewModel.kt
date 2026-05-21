package com.scascan.app.ui.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.domain.usecase.AnalyzeFoodImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val analyzeFoodImageUseCase: AnalyzeFoodImageUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Idle)
    val uiState: StateFlow<CameraUiState> = _uiState

    fun analyzeImage(bitmap: Bitmap) {
        _uiState.value = CameraUiState.Loading
        viewModelScope.launch {
            analyzeFoodImageUseCase(bitmap)
                .onSuccess { _uiState.value = CameraUiState.Success(it) }
                .onFailure { _uiState.value = CameraUiState.Error(it.message ?: "Unknown error") }
        }
    }

    fun resetToIdle() {
        _uiState.value = CameraUiState.Idle
    }
}

sealed class CameraUiState {
    object Idle : CameraUiState()
    object Loading : CameraUiState()
    data class Success(val nutritionFacts: NutritionFacts) : CameraUiState()
    data class Error(val message: String) : CameraUiState()
}
