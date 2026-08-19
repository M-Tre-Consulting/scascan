package com.mtreconsulting.scascan.ui.profile

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
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.mtreconsulting.scascan.R
import com.mtreconsulting.scascan.data.health.HealthConnectManager
import com.mtreconsulting.scascan.data.remote.ModelInfo
import com.mtreconsulting.scascan.databinding.FragmentAppSettingsBinding
import com.mtreconsulting.scascan.ui.util.hapticClick
import com.mtreconsulting.scascan.ui.util.hapticTick
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppSettingsFragment : Fragment() {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    private val requestHcPermissions = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(HealthConnectManager.PERMISSIONS)) {
            checkHcStatus()
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            binding.switchWaterReminder.isChecked = false
            viewModel.setWaterRemindersEnabled(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.topBar.setOnClickListener { findNavController().navigateUp() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.topBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBar + (16 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        setupApiKey()
        setupModelSelector()
        setupHealthConnect()
        setupGoogleSync()
        setupNotifications()
        observeSyncState()
        observeActionState()
        observeModelState()
    }

    private fun setupNotifications() {
        binding.switchWaterReminder.isChecked = viewModel.profileStore.waterRemindersEnabled
        binding.switchWaterReminder.setOnCheckedChangeListener { _, isChecked ->
            binding.switchWaterReminder.hapticTick()
            if (isChecked) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val permission = android.Manifest.permission.POST_NOTIFICATIONS
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            requireContext(),
                            permission
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        requestNotificationPermission.launch(permission)
                    }
                }
            }
            viewModel.setWaterRemindersEnabled(isChecked)
        }
    }

    private fun observeActionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.actionState.collect { state ->
                if (state is ProfileViewModel.ActionState.Success) {
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                    viewModel.resetActionState()
                } else if (state is ProfileViewModel.ActionState.Error) {
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    viewModel.resetActionState()
                }
            }
        }
    }

    private fun setupGoogleSync() {
        updateSyncButton()
        binding.btnGoogleSync.setOnClickListener {
            it.hapticClick()
            if (viewModel.profileStore.syncEmail.isBlank()) {
                viewModel.signInAndSync(requireActivity())
            } else {
                viewModel.syncNow()
            }
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

        // Chip stays short (fixed layout weight) — the email, which can be arbitrarily long,
        // goes in the full-width description line instead so it can wrap normally.
        binding.chipGoogleSyncStatus.text = getString(if (connected) R.string.hc_connected else R.string.hc_disconnected)
        binding.tvSyncDesc.text = if (connected && email != "Connected") {
            getString(R.string.profile_sync_status_connected, email)
        } else {
            getString(R.string.profile_sync_desc)
        }

        binding.btnDisconnectGoogle.isVisible = connected
    }

    private fun disconnectGoogle() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.signOutGoogle()
            updateSyncButton()
            Snackbar.make(binding.root, R.string.profile_sync_disconnected, Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.btnGoogleSync)
                .show()
        }
    }

    private fun observeSyncState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncState.collect { state ->
                when (state) {
                    is ProfileViewModel.SyncState.Idle -> updateSyncButton()
                    is ProfileViewModel.SyncState.Syncing -> {
                        binding.btnGoogleSync.isEnabled = false
                        binding.btnGoogleSync.text = getString(R.string.profile_sync_status_syncing)
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

        setupActiveFallback()
        checkHcStatus()
    }

    private fun setupActiveFallback() {
        binding.etActiveFallback.setText(viewModel.profileStore.activeCalorieFallbackKcal.toString())
        binding.etActiveFallback.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveActiveFallback()
        }
        binding.etActiveFallback.setOnEditorActionListener { _, _, _ -> saveActiveFallback(); true }
    }

    private fun saveActiveFallback() {
        val value = binding.etActiveFallback.text?.toString()?.toIntOrNull()
        if (value != null && value >= 0) {
            viewModel.profileStore.activeCalorieFallbackKcal = value
        } else {
            binding.etActiveFallback.setText(viewModel.profileStore.activeCalorieFallbackKcal.toString())
        }
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
                viewModel.profileStore.weightKg = rounded
            }
            cm?.let {
                val rounded = it.toInt()
                viewModel.profileStore.heightCm = rounded
            }

            Snackbar.make(binding.root, getString(R.string.hc_weight_synced), Snackbar.LENGTH_SHORT)
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

    private fun setupModelSelector() {
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
        val selectIdx = if (idx >= 0) idx else 0
        binding.modelSelector.setText(labels[selectIdx], false)
        viewModel.keyStore.selectedModel = models[selectIdx].id

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
