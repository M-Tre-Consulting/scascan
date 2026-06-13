package com.scascan.app.ui.search

import androidx.lifecycle.ViewModel
import com.scascan.app.data.model.NutritionFacts
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val analysisManager: com.scascan.app.data.analysis.AnalysisManager
) : ViewModel() {

    fun searchFood(query: String) {
        if (query.isBlank()) return
        analysisManager.analyzeSearch(query)
    }
}

sealed class SearchUiState {
    object Loading : SearchUiState()
    data class Success(val nutritionFacts: NutritionFacts) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
