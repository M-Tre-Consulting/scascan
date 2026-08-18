package com.scascan.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scascan.app.data.health.HealthConnectManager
import com.scascan.app.data.local.GeminiKeyStore
import com.scascan.app.data.local.UserProfileStore
import com.scascan.app.data.remote.GeminiRestClient
import com.scascan.app.data.remote.ModelInfo
import com.scascan.app.data.repository.NutritionRepository
import com.scascan.app.data.sync.DriveSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val keyStore: GeminiKeyStore,
    val profileStore: UserProfileStore,
    val healthManager: HealthConnectManager,
    private val client: GeminiRestClient,
    private val nutritionRepository: NutritionRepository,
    private val syncManager: DriveSyncManager,
    private val reminderManager: com.scascan.app.data.reminder.ReminderManager
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

    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        object Success : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    fun computeTargets() {
        if (!profileStore.hasProfile() || !keyStore.hasKey()) return
        _targetState.value = TargetState.Computing
        viewModelScope.launch {
            nutritionRepository.computeTargets(profileStore)
                .onSuccess { targets ->
                    profileStore.aiCalorieTarget = targets.dailyCalories
                    profileStore.aiProteinTarget = targets.proteinGrams
                    profileStore.aiCarbsTarget   = targets.carbsGrams
                    profileStore.aiFatTarget      = targets.fatGrams
                    com.scascan.app.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
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

    fun triggerSync(accessToken: String, email: String? = null) {
        _syncState.value = SyncState.Syncing
        viewModelScope.launch {
            syncManager.sync(accessToken)
                .onSuccess { 
                    if (email != null) profileStore.syncEmail = email
                    _syncState.value = SyncState.Success 
                }
                .onFailure { _syncState.value = SyncState.Error(it.message ?: "Sync failed") }
        }
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    sealed class ActionState {
        object Idle : ActionState()
        object Loading : ActionState()
        data class Success(val message: String) : ActionState()
        data class Error(val message: String) : ActionState()
    }

    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState

    fun saveProfile(name: String, age: Int, height: Int, weight: Float, activityIdx: Int, goalIdx: Int) {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                profileStore.name = name
                profileStore.age = age
                profileStore.heightCm = height
                profileStore.weightKg = weight
                profileStore.activityIndex = activityIdx
                profileStore.goalIndex = goalIdx
                com.scascan.app.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
                _actionState.value = ActionState.Success("Profile saved successfully")
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.message ?: "Failed to save profile")
            }
        }
    }

    fun saveApiKey(key: String, model: String) {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                keyStore.apiKey = key
                keyStore.selectedModel = model
                _actionState.value = ActionState.Success("API key saved successfully")
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.message ?: "Failed to save API key")
            }
        }
    }

    fun resetActionState() {
        _actionState.value = ActionState.Idle
    }

    fun setWaterRemindersEnabled(enabled: Boolean) {
        profileStore.waterRemindersEnabled = enabled
        if (enabled) {
            reminderManager.topUpTodaySchedule()
        } else {
            reminderManager.cancelHydrationReminder()
        }
    }
}
