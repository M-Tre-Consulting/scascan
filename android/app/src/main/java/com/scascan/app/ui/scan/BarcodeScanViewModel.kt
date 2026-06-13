package com.scascan.app.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.scascan.app.data.model.NutritionFacts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
}

sealed class BarcodeScanUiState {
    object Scanning : BarcodeScanUiState()
    object Loading : BarcodeScanUiState()
    data class Success(val nutritionFacts: NutritionFacts) : BarcodeScanUiState()
    data class Error(val message: String) : BarcodeScanUiState()
}
