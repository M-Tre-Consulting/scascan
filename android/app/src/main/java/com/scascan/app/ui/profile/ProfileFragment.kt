package com.scascan.app.ui.profile

import android.app.Activity
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
        if (result.resultCode == Activity.RESULT_OK) {
            val authResult = Identity.getAuthorizationClient(requireActivity())
                .getAuthorizationResultFromIntent(result.data)
            authResult.accessToken?.let { viewModel.triggerSync(it) }
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
    }

    private fun setupGoogleSync() {
        binding.btnGoogleSync.setOnClickListener {
            it.hapticClick()
            startDriveSync()
        }
    }

    private fun startDriveSync() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata")))
            .build()
        
        Identity.getAuthorizationClient(requireActivity())
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    requestDriveAuth.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(pendingIntent!!).build()
                    )
                } else {
                    result.accessToken?.let { viewModel.triggerSync(it) }
                }
            }
            .addOnFailureListener { e ->
                Snackbar.make(binding.root, "Auth failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    private fun observeSyncState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncState.collect { state ->
                when (state) {
                    is ProfileViewModel.SyncState.Idle -> {
                        binding.btnGoogleSync.isEnabled = true
                        binding.btnGoogleSync.text = getString(R.string.profile_sync_btn_google)
                    }
                    is ProfileViewModel.SyncState.Syncing -> {
                        binding.btnGoogleSync.isEnabled = false
                        binding.btnGoogleSync.text = getString(R.string.profile_sync_status_syncing)
                    }
                    is ProfileViewModel.SyncState.Success -> {
                        binding.btnGoogleSync.isEnabled = true
                        Snackbar.make(binding.root, R.string.profile_sync_status_complete, Snackbar.LENGTH_SHORT).show()
                        viewModel.resetSyncState()
                    }
                    is ProfileViewModel.SyncState.Error -> {
                        binding.btnGoogleSync.isEnabled = true
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

        val actIdx = activityLabels.indexOf(binding.activitySelector.text.toString())
        if (actIdx >= 0) profile.activityIndex = actIdx

        val goalIdx = goalLabels.indexOf(binding.goalSelector.text.toString())
        if (goalIdx >= 0) profile.goalIndex = goalIdx

        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.etWeight.windowToken, 0)

        binding.cardSetupReminder.isVisible = false
        binding.btnSaveProfile.hapticConfirm()
        Snackbar.make(binding.root, R.string.profile_info_saved, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.btnSaveProfile)
            .show()

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
        viewLifecycleOwner.lifecycleScope.launch {
            val kg = viewModel.healthManager.readLatestWeightKg()
            val cm = viewModel.healthManager.readLatestHeightCm()
            
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
        viewModel.keyStore.apiKey = input

        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.etApiKey.windowToken, 0)

        Snackbar.make(binding.root, R.string.profile_key_saved, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.btnSaveKey)
            .show()

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
