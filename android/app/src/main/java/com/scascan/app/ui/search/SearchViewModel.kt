package com.scascan.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.domain.usecase.SearchFoodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchFoodUseCase: SearchFoodUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    fun searchFood(query: String) {
        if (query.isBlank()) return
        _uiState.value = SearchUiState.Loading
        viewModelScope.launch {
            searchFoodUseCase(query)
                .onSuccess { _uiState.value = SearchUiState.Success(it) }
                .onFailure { _uiState.value = SearchUiState.Error(it.message ?: "Unknown error") }
        }
    }

    fun resetToIdle() {
        _uiState.value = SearchUiState.Idle
    }
}

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val nutritionFacts: NutritionFacts) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
