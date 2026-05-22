package com.scascan.app.ui.profile

import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.scascan.app.data.health.HealthConnectManager
import com.scascan.app.data.local.GeminiKeyStore
import com.scascan.app.data.local.UserProfileStore
import com.scascan.app.data.remote.GeminiRestClient
import com.scascan.app.data.remote.ModelInfo
import com.scascan.app.data.repository.NutritionRepository
import com.scascan.app.data.sync.DriveSyncManager
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
    private val nutritionRepository: NutritionRepository,
    private val syncManager: DriveSyncManager
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

    data class AuthState(
        val name: String? = null,
        val email: String? = null,
        val photoUrl: String? = null,
        val isError: Boolean = false
    )

    private val _authState = MutableStateFlow<AuthState>(
        AuthState(name = profileStore.name.ifBlank { null })
    )
    val authState: StateFlow<AuthState> = _authState

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

    fun signIn(context: android.content.Context) {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId("REPLACE_WITH_YOUR_CLIENT_ID") // User will need to provide this
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is GoogleIdTokenCredential) {
                    val token = credential.idToken
                    // In a real app, we'd send this to a backend. 
                    // For now, we'll just extract the name/email from the token.
                    _authState.value = AuthState(
                        name = credential.displayName,
                        email = credential.id
                    )
                    if (!credential.displayName.isNullOrBlank()) {
                        profileStore.name = credential.displayName!!
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState(isError = true)
            }
        }
    }
}
