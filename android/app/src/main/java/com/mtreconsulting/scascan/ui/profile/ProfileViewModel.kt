package com.mtreconsulting.scascan.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtreconsulting.scascan.data.health.HealthConnectManager
import com.mtreconsulting.scascan.data.local.GeminiKeyStore
import com.mtreconsulting.scascan.data.local.UserProfileStore
import com.mtreconsulting.scascan.data.remote.GeminiRestClient
import com.mtreconsulting.scascan.data.remote.ModelInfo
import com.mtreconsulting.scascan.data.repository.NutritionRepository
import com.mtreconsulting.scascan.data.sync.AuthManager
import com.mtreconsulting.scascan.data.sync.FirestoreSyncManager
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
    private val authManager: AuthManager,
    private val syncManager: FirestoreSyncManager,
    private val reminderManager: com.mtreconsulting.scascan.data.reminder.ReminderManager
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
                    com.mtreconsulting.scascan.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
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

    /** Signs in with Google (via Credential Manager) if needed, then syncs. [activityContext]
     *  must be an Activity context — required by Credential Manager to host its picker UI. */
    fun signInAndSync(activityContext: Context) {
        _syncState.value = SyncState.Syncing
        viewModelScope.launch {
            authManager.signIn(activityContext)
                .onSuccess { user ->
                    profileStore.syncEmail = user.email ?: "Connected"
                    if (profileStore.name.isBlank() && !user.displayName.isNullOrBlank()) {
                        profileStore.name = user.displayName!!
                    }
                    runSync()
                }
                .onFailure { _syncState.value = SyncState.Error(it.message ?: "Sign-in failed") }
        }
    }

    /** Syncs using the already-signed-in account (e.g. a manual "Sync now" tap). */
    fun syncNow() {
        _syncState.value = SyncState.Syncing
        viewModelScope.launch { runSync() }
    }

    private suspend fun runSync() {
        syncManager.sync()
            .onSuccess { _syncState.value = SyncState.Success }
            .onFailure { _syncState.value = SyncState.Error(it.message ?: "Sync failed") }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            authManager.signOut()
            profileStore.syncEmail = ""
            _syncState.value = SyncState.Idle
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
                com.mtreconsulting.scascan.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
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

    fun updateReminderQuietHours(startHour: Int, endHour: Int) {
        profileStore.reminderQuietHoursStart = startHour
        profileStore.reminderQuietHoursEnd = endHour
        if (profileStore.waterRemindersEnabled) {
            reminderManager.onQuietHoursChanged()
        }
    }
}
