package com.scascan.app.ui.profile

import android.app.Activity
import android.util.Log
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.material.snackbar.Snackbar
import com.scascan.app.R
import com.scascan.app.data.health.HealthConnectManager
import com.scascan.app.data.remote.ModelInfo
import com.scascan.app.databinding.FragmentProfileBinding
import com.scascan.app.ui.util.hapticClick
import com.scascan.app.ui.util.hapticConfirm
import com.scascan.app.ui.util.hapticTick
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    private val requestHcPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(HealthConnectManager.PERMISSIONS)) {
            checkHcStatus()
        }
    }

    private val requestDriveAuth = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        Log.d("ProfileFragment", "requestDriveAuth result: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val authClient = Identity.getAuthorizationClient(requireActivity())
                val authResult = authClient.getAuthorizationResultFromIntent(result.data)
                
                // 1. Extract the access token for Drive sync
                val token = authResult.accessToken
                
                // 2. Try to extract account info (email/name) from the sign-in intent
                try {
                    val signInClient = Identity.getSignInClient(requireActivity())
                    @Suppress("DEPRECATION")
                    val credential = signInClient.getSignInCredentialFromIntent(result.data)
                    
                    Log.d("ProfileFragment", "Found credential info: ${credential.id}, ${credential.displayName}")
                    
                    if (!credential.id.isNullOrBlank()) {
                        viewModel.profileStore.syncEmail = credential.id
                    }
                    
                    if (viewModel.profileStore.name.isBlank() && !credential.displayName.isNullOrBlank()) {
                        viewModel.profileStore.name = credential.displayName!!
                        binding.etName.setText(credential.displayName)
                    }
                } catch (e: Exception) {
                    Log.d("ProfileFragment", "Could not extract sign-in info: ${e.message}")
                }
                
                // Final fallback: if extraction failed but we have a token, just show "Connected"
                if (viewModel.profileStore.syncEmail.isBlank()) {
                    viewModel.profileStore.syncEmail = "Connected"
                }
                
                token?.let { viewModel.triggerSync(it) }
                
            } catch (e: com.google.android.gms.common.api.ApiException) {
                Log.e("ProfileFragment", "ApiException in result handler: status=${e.statusCode}", e)
                Snackbar.make(binding.root, "Auth error: ${e.statusCode}", Snackbar.LENGTH_LONG).show()
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Log.e("ProfileFragment", "Auth CANCELED (code 0). Check SHA-1 and Package Name in Google Cloud Console.")
            Snackbar.make(binding.root, "Auth canceled - check configuration", Snackbar.LENGTH_LONG).show()
        } else {
            Log.e("ProfileFragment", "Auth failed with result code: ${result.resultCode}")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.root.updatePadding(top = statusBar)
            insets
        }

        setupPersonalInfo()
        setupApiKey()
        setupModelSelector()
        setupHealthConnect()
        setupGoogleSync()
        updateSetupReminder()
        observeTargetState()
        observeModelState()
        observeAuthState()
        observeSyncState()
        observeActionState()
    }

    private fun observeActionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.actionState.collect { state ->
                when (state) {
                    is ProfileViewModel.ActionState.Idle -> {
                        binding.btnSaveProfile.isEnabled = true
                        binding.btnSaveKey.isEnabled = true
                        binding.btnSaveProfile.text = getString(R.string.profile_save_info)
                        binding.btnSaveKey.text = getString(R.string.profile_save_key)
                    }
                    is ProfileViewModel.ActionState.Loading -> {
                        binding.btnSaveProfile.isEnabled = false
                        binding.btnSaveKey.isEnabled = false
                        // Use a generic "saving" text if possible, or just reuse analyzing
                        binding.btnSaveProfile.text = getString(R.string.analyzing)
                        binding.btnSaveKey.text = getString(R.string.analyzing)
                    }
                    is ProfileViewModel.ActionState.Success -> {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                        viewModel.resetActionState()
                        updateSetupReminder()
                    }
                    is ProfileViewModel.ActionState.Error -> {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        viewModel.resetActionState()
                    }
                }
            }
        }
    }

    private fun setupGoogleSync() {
        updateSyncButton()
        binding.btnGoogleSync.setOnClickListener {
            it.hapticClick()
            startDriveSync()
        }
        binding.btnDisconnectGoogle.setOnClickListener {
            it.hapticTick()
            disconnectGoogle()
        }
    }

    private fun updateSyncButton() {
        val email = viewModel.profileStore.syncEmail
        val connected = email.isNotBlank()

        binding.btnGoogleSync.isEnabled = true
        binding.btnGoogleSync.text = if (connected) {
            getString(R.string.profile_sync_now)
        } else {
            getString(R.string.profile_sync_btn_google)
        }

        binding.chipGoogleSyncStatus.text = if (connected) {
            if (email == "Connected") {
                getString(R.string.hc_connected)
            } else {
                getString(R.string.profile_sync_status_connected, email)
            }
        } else {
            getString(R.string.hc_disconnected)
        }
        
        binding.btnDisconnectGoogle.isVisible = connected
    }

    private fun disconnectGoogle() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Clear the local profile store
                viewModel.profileStore.syncEmail = ""
                
                // 2. Clear the Credential Manager state (forces account picker next time)
                val credManager = androidx.credentials.CredentialManager.create(requireContext())
                credManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
                
                // 3. Sign out from Identity services
                // This clears the "default" account so the picker shows up again
                @Suppress("DEPRECATION")
                Identity.getSignInClient(requireActivity()).signOut()
                
                updateSyncButton()
                Snackbar.make(binding.root, R.string.profile_sync_disconnected, Snackbar.LENGTH_SHORT)
                    .setAnchorView(binding.btnGoogleSync)
                    .show()
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error during disconnect", e)
                viewModel.profileStore.syncEmail = ""
                updateSyncButton()
            }
        }
    }

    private fun startDriveSync() {
        Log.d("ProfileFragment", "Starting Drive Sync Auth...")
        // We request Drive scope AND profile/email to extract user info
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(
                Scope("https://www.googleapis.com/auth/drive.appdata"),
                Scope("https://www.googleapis.com/auth/userinfo.email"),
                Scope("https://www.googleapis.com/auth/userinfo.profile")
            ))
            .build()
        
        Identity.getAuthorizationClient(requireActivity())
            .authorize(request)
            .addOnSuccessListener { result ->
                Log.d("ProfileFragment", "Authorize success, hasResolution: ${result.hasResolution()}")
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    requestDriveAuth.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(pendingIntent!!).build()
                    )
                } else {
                    result.accessToken?.let { token ->
                        Log.d("ProfileFragment", "Already authorized")
                        if (viewModel.profileStore.syncEmail.isBlank()) {
                            viewModel.profileStore.syncEmail = "Connected"
                        }
                        viewModel.triggerSync(token) 
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ProfileFragment", "Authorize failed", e)
                Snackbar.make(binding.root, "Auth failed: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.btnGoogleSync)
                    .show()
            }
    }

    private fun observeSyncState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncState.collect { state ->
                when (state) {
                    is ProfileViewModel.SyncState.Idle -> {
                        updateSyncButton()
                    }
                    is ProfileViewModel.SyncState.Syncing -> {
                        binding.btnGoogleSync.isEnabled = false
                        binding.btnGoogleSync.text = getString(R.string.profile_sync_status_syncing)
                        Snackbar.make(binding.root, R.string.profile_sync_status_syncing, Snackbar.LENGTH_SHORT).show()
                    }
                    is ProfileViewModel.SyncState.Success -> {
                        updateSyncButton()
                        Snackbar.make(binding.root, R.string.profile_sync_status_complete, Snackbar.LENGTH_SHORT)
                            .setAnchorView(binding.btnGoogleSync)
                            .show()
                        viewModel.resetSyncState()
                    }
                    is ProfileViewModel.SyncState.Error -> {
                        updateSyncButton()
                        Snackbar.make(binding.root, getString(R.string.profile_sync_error, state.message), Snackbar.LENGTH_LONG).show()
                        viewModel.resetSyncState()
                    }
                }
            }
        }
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.authState.collect { auth ->
                auth.name?.let {
                    if (binding.etName.text.isNullOrBlank()) {
                        binding.etName.setText(it)
                    }
                }
            }
        }
    }

    // ── Personal info ─────────────────────────────────────────────────────────

    private fun setupPersonalInfo() {
        val profile = viewModel.profileStore
        val activityLabels = resources.getStringArray(R.array.activity_levels)
        val goalLabels = resources.getStringArray(R.array.goal_levels)

        if (profile.hasProfile()) {
            binding.etName.setText(profile.name)
            binding.etAge.setText(profile.age.toString())
            binding.etHeight.setText(profile.heightCm.toString())
            binding.etWeight.setText(profile.weightKg.toString())
            if (!profile.isMale) binding.toggleGroupSex.check(R.id.btnSexFemale)
        }

        binding.toggleGroupSex.addOnButtonCheckedListener { _, _, _ ->
            binding.toggleGroupSex.hapticTick()
        }

        binding.activitySelector.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, activityLabels)
        )
        binding.activitySelector.setText(activityLabels[profile.activityIndex], false)

        binding.goalSelector.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, goalLabels)
        )
        binding.goalSelector.setText(goalLabels[profile.goalIndex], false)

        if (profile.aiCalorieTarget > 0) {
            showAiTargetStatus(getString(R.string.profile_target_computed, profile.aiCalorieTarget))
        }

        binding.btnSaveProfile.setOnClickListener {
            it.hapticClick()
            savePersonalInfo(activityLabels, goalLabels)
        }
    }

    private fun savePersonalInfo(activityLabels: Array<String>, goalLabels: Array<String>) {
        val name = binding.etName.text?.toString()?.trim() ?: ""
        val age = binding.etAge.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val height = binding.etHeight.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val weight = binding.etWeight.text?.toString()?.trim()?.toFloatOrNull() ?: 0f

        if (name.isBlank()) { binding.tilName.error = "Required"; return }
        if (age <= 0) { binding.tilAge.error = "Required"; return }
        if (height <= 0) { binding.tilHeight.error = "Required"; return }
        if (weight <= 0f) { binding.tilWeight.error = "Required"; return }

        binding.tilName.error = null
        binding.tilAge.error = null
        binding.tilHeight.error = null
        binding.tilWeight.error = null

        val profile = viewModel.profileStore
        profile.name = name
        profile.age = age
        profile.heightCm = height
        profile.weightKg = weight
        profile.isMale = binding.toggleGroupSex.checkedButtonId == R.id.btnSexMale

        val actIdx = activityLabels.indexOf(binding.activitySelector.text.toString()).coerceAtLeast(0)
        val goalIdx = goalLabels.indexOf(binding.goalSelector.text.toString()).coerceAtLeast(0)

        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.etWeight.windowToken, 0)

        binding.btnSaveProfile.hapticConfirm()
        viewModel.saveProfile(name, age, height, weight, actIdx, goalIdx)
        
        if (viewModel.keyStore.hasKey()) {
            viewModel.computeTargets()
        }
    }

    private fun updateSetupReminder() {
        val needsSetup = viewModel.profileStore.aiCalorieTarget == 0
        binding.cardSetupReminder.isVisible = needsSetup
    }

    private fun showAiTargetStatus(text: String) {
        binding.layoutAiTarget.isVisible = true
        binding.tvAiTargetStatus.text = text
    }

    private fun observeTargetState() {
        binding.btnRecompute.setOnClickListener {
            it.hapticClick()
            viewModel.computeTargets()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.targetState.collect { state ->
                when (state) {
                    is ProfileViewModel.TargetState.Idle -> Unit
                    is ProfileViewModel.TargetState.Computing -> {
                        binding.layoutAiTarget.isVisible = true
                        binding.progressAiTarget.isVisible = true
                        binding.btnRecompute.isVisible = false
                        binding.tvAiTargetStatus.text = getString(R.string.profile_target_computing)
                        binding.btnSaveProfile.isEnabled = false
                    }
                    is ProfileViewModel.TargetState.Done -> {
                        binding.progressAiTarget.isVisible = false
                        binding.btnRecompute.isVisible = true
                        binding.cardSetupReminder.isVisible = false
                        showAiTargetStatus(getString(R.string.profile_target_computed, state.calories))
                        binding.btnSaveProfile.isEnabled = true
                        viewModel.resetTargetState()
                    }
                    is ProfileViewModel.TargetState.Error -> {
                        binding.progressAiTarget.isVisible = false
                        binding.btnRecompute.isVisible = viewModel.profileStore.aiCalorieTarget > 0
                        binding.btnSaveProfile.isEnabled = true
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        viewModel.resetTargetState()
                    }
                }
            }
        }

        // Show recompute button immediately if a target already exists
        if (viewModel.profileStore.aiCalorieTarget > 0) {
            binding.btnRecompute.isVisible = true
        }
    }

    override fun onResume() {
        super.onResume()
        checkHcStatus()
    }

    // ── Health Connect ────────────────────────────────────────────────────────

    private fun setupHealthConnect() {
        val hm = viewModel.healthManager
        if (!hm.isAvailable) {
            binding.chipHcProfile.text = getString(R.string.hc_not_available)
            return
        }

        binding.btnConnectHcProfile.setOnClickListener {
            it.hapticClick()
            requestHcPermissions.launch(HealthConnectManager.PERMISSIONS)
        }
        binding.btnSyncWeight.setOnClickListener { it.hapticClick(); syncFromHc() }
        binding.btnDisconnectHc.setOnClickListener { it.hapticTick(); disconnectHc() }

        checkHcStatus()
    }

    private fun checkHcStatus() {
        val hm = viewModel.healthManager
        if (!hm.isAvailable) return
        viewLifecycleOwner.lifecycleScope.launch {
            val connected = hm.hasPermissions()
            binding.chipHcProfile.text = getString(
                if (connected) R.string.hc_connected else R.string.hc_disconnected
            )
            binding.btnConnectHcProfile.isVisible = !connected
            binding.btnSyncWeight.isVisible = connected
            binding.btnDisconnectHc.isVisible = connected
        }
    }

    private fun syncFromHc() {
        binding.btnSyncWeight.isEnabled = false
        binding.btnSyncWeight.text = getString(R.string.analyzing)
        
        viewLifecycleOwner.lifecycleScope.launch {
            val kg = viewModel.healthManager.readLatestWeightKg()
            val cm = viewModel.healthManager.readLatestHeightCm()
            
            binding.btnSyncWeight.isEnabled = true
            binding.btnSyncWeight.text = getString(R.string.hc_sync_weight)

            if (kg == null && cm == null) {
                Snackbar.make(binding.root, R.string.hc_weight_unavailable, Snackbar.LENGTH_SHORT)
                    .setAnchorView(binding.btnSyncWeight)
                    .show()
                return@launch
            }
            
            kg?.let {
                val rounded = it.toFloat()
                binding.etWeight.setText(rounded.toString())
                viewModel.profileStore.weightKg = rounded
            }
            
            cm?.let {
                val rounded = it.toInt()
                binding.etHeight.setText(rounded.toString())
                viewModel.profileStore.heightCm = rounded
            }

            val msg = getString(R.string.hc_weight_synced)

            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.btnSyncWeight)
                .show()
        }
    }

    private fun disconnectHc() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.healthManager.revokePermissions()
            checkHcStatus()
        }
    }

    // ── API key ───────────────────────────────────────────────────────────────

    private fun setupApiKey() {
        val keyStore = viewModel.keyStore
        if (keyStore.hasKey()) binding.etApiKey.setText(keyStore.apiKey)

        binding.btnSaveKey.setOnClickListener { it.hapticClick(); saveKey() }
        binding.etApiKey.setOnEditorActionListener { _, _, _ -> saveKey(); true }
    }

    private fun saveKey() {
        val input = binding.etApiKey.text?.toString()?.trim() ?: ""
        if (input.isBlank()) {
            binding.tilApiKey.error = getString(R.string.setup_key_error)
            return
        }
        binding.tilApiKey.error = null

        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.etApiKey.windowToken, 0)

        val selectedModel = viewModel.keyStore.selectedModel
        viewModel.saveApiKey(input, selectedModel)

        viewModel.loadModels()
    }

    // ── Model selector ────────────────────────────────────────────────────────

    private fun setupModelSelector() {
        observeModelState()
        if (viewModel.keyStore.hasKey()) {
            viewModel.loadModels()
        } else {
            binding.tilModel.helperText = getString(R.string.profile_model_need_key)
        }
    }

    private fun observeModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.modelState.collect { state ->
                when (state) {
                    is ProfileViewModel.ModelState.Idle -> Unit
                    is ProfileViewModel.ModelState.Loading -> {
                        binding.modelSelector.setText(getString(R.string.profile_model_loading), false)
                        binding.modelSelector.isEnabled = false
                        binding.tilModel.helperText = null
                    }
                    is ProfileViewModel.ModelState.Ready -> applyModels(state.models)
                    is ProfileViewModel.ModelState.Error -> {
                        binding.modelSelector.isEnabled = true
                        binding.tilModel.helperText = state.message
                    }
                }
            }
        }
    }

    private fun applyModels(models: List<ModelInfo>) {
        if (models.isEmpty()) {
            binding.tilModel.helperText = getString(R.string.profile_model_empty)
            return
        }

        val labels = models.map { it.displayName }
        binding.modelSelector.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        )
        binding.modelSelector.isEnabled = true
        binding.tilModel.helperText = null

        val currentId = viewModel.keyStore.selectedModel
        val idx = models.indexOfFirst { it.id == currentId }
        binding.modelSelector.setText(labels[if (idx >= 0) idx else 0], false)
        if (idx < 0) viewModel.keyStore.selectedModel = models[0].id

        binding.modelSelector.setOnItemClickListener { _, _, position, _ ->
            viewModel.keyStore.selectedModel = models[position].id
            Snackbar.make(binding.root, R.string.profile_model_saved, Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.tilModel)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
