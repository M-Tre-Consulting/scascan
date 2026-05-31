package com.scascan.app.ui.scan

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.data.repository.NutritionRepository
import com.scascan.app.domain.usecase.ScanBarcodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BarcodeScanViewModel @Inject constructor(
    private val analysisManager: com.scascan.app.data.analysis.AnalysisManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<BarcodeScanUiState>(BarcodeScanUiState.Scanning)
    val uiState: StateFlow<BarcodeScanUiState> = _uiState

    fun onShutterClicked(bitmap: Bitmap) {
        analysisManager.analyzeBarcode(bitmap)
    }

    fun resetToScanning() {
        _uiState.value = BarcodeScanUiState.Scanning
    }
}

sealed class BarcodeScanUiState {
    object Scanning : BarcodeScanUiState()
    object Loading : BarcodeScanUiState()
    data class Success(val nutritionFacts: NutritionFacts) : BarcodeScanUiState()
    data class Error(val message: String) : BarcodeScanUiState()
}
