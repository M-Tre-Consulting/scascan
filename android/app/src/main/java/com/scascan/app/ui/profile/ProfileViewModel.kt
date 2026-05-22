package com.scascan.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scascan.app.data.health.HealthConnectManager
import com.scascan.app.data.local.GeminiKeyStore
import com.scascan.app.data.local.UserProfileStore
import com.scascan.app.data.remote.GeminiRestClient
import com.scascan.app.data.remote.ModelInfo
import com.scascan.app.data.repository.NutritionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    val keyStore: GeminiKeyStore,
    val profileStore: UserProfileStore,
    val healthManager: HealthConnectManager,
    private val client: GeminiRestClient,
    private val nutritionRepository: NutritionRepository
) : ViewModel() {

    sealed class ModelState {
        object Idle    : ModelState()
        object Loading : ModelState()
        data class Ready(val models: List<ModelInfo>) : ModelState()
        data class Error(val message: String)         : ModelState()
    }

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Idle)
    val modelState: StateFlow<ModelState> = _modelState

    fun loadModels() {
        if (!keyStore.hasKey()) return
        _modelState.value = ModelState.Loading
        viewModelScope.launch {
            runCatching { client.listModels(keyStore.apiKey) }
                .onSuccess { _modelState.value = ModelState.Ready(it) }
                .onFailure { _modelState.value = ModelState.Error(it.message ?: "Unknown error") }
        }
    }

    sealed class TargetState {
        object Idle      : TargetState()
        object Computing : TargetState()
        data class Done(val calories: Int) : TargetState()
        data class Error(val message: String) : TargetState()
    }

    private val _targetState = MutableStateFlow<TargetState>(TargetState.Idle)
    val targetState: StateFlow<TargetState> = _targetState

    fun computeTargets() {
        if (!profileStore.hasProfile() || !keyStore.hasKey()) return
        _targetState.value = TargetState.Computing
        viewModelScope.launch {
            nutritionRepository.computeTargets(profileStore)
                .onSuccess { targets ->
                    profileStore.aiCalorieTarget = targets.dailyCalories
                    _targetState.value = TargetState.Done(targets.dailyCalories)
                }
                .onFailure { e ->
                    _targetState.value = TargetState.Error(e.message ?: "Computation failed")
                }
        }
    }

    fun resetTargetState() {
        _targetState.value = TargetState.Idle
    }
}
