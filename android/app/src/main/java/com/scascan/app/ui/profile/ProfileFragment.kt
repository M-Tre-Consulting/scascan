package com.scascan.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.scascan.app.R
import com.scascan.app.data.health.HealthConnectManager
import com.scascan.app.data.remote.ModelInfo
import com.scascan.app.databinding.FragmentProfileBinding
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
    }

    // ── Personal info ─────────────────────────────────────────────────────────

    private fun setupPersonalInfo() {
        val profile = viewModel.profileStore
        val activityLabels = resources.getStringArray(R.array.activity_levels)

        // Pre-populate saved values
        if (profile.hasProfile()) {
            binding.etAge.setText(profile.age.toString())
            binding.etHeight.setText(profile.heightCm.toString())
            binding.etWeight.setText(profile.weightKg.toString())
            if (!profile.isMale) binding.chipGroupSex.check(R.id.chipFemale)
        }

        // Activity dropdown
        binding.activitySelector.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, activityLabels)
        )
        binding.activitySelector.setText(activityLabels[profile.activityIndex], false)

        binding.btnSaveProfile.setOnClickListener { savePersonalInfo(activityLabels) }
    }

    private fun savePersonalInfo(activityLabels: Array<String>) {
        val age = binding.etAge.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val height = binding.etHeight.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val weight = binding.etWeight.text?.toString()?.trim()?.toFloatOrNull() ?: 0f

        if (age <= 0) { binding.tilAge.error = "Required"; return }
        if (height <= 0) { binding.tilHeight.error = "Required"; return }
        if (weight <= 0f) { binding.tilWeight.error = "Required"; return }

        binding.tilAge.error = null
        binding.tilHeight.error = null
        binding.tilWeight.error = null

        val profile = viewModel.profileStore
        profile.age = age
        profile.heightCm = height
        profile.weightKg = weight
        profile.isMale = binding.chipGroupSex.checkedChipId == R.id.chipMale
        val idx = activityLabels.indexOf(binding.activitySelector.text.toString())
        if (idx >= 0) profile.activityIndex = idx

        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.etWeight.windowToken, 0)

        Snackbar.make(binding.root, R.string.profile_info_saved, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.btnSaveProfile)
            .show()
    }

    override fun onResume() {
        super.onResume()
        checkHcStatus()
    }

    // ── Health Connect ────────────────────────────────────────────────────────

    private fun setupHealthConnect() {
        val hm = viewModel.healthManager
        if (!hm.isAvailable) {
            // HC not present on this device — keep chip as "Coming soon", hide button
            return
        }
        binding.btnConnectHcProfile.setOnClickListener {
            requestHcPermissions.launch(HealthConnectManager.PERMISSIONS)
        }
        checkHcStatus()
    }

    private fun checkHcStatus() {
        val hm = viewModel.healthManager
        if (!hm.isAvailable) return
        viewLifecycleOwner.lifecycleScope.launch {
            val connected = hm.hasPermissions()
            binding.chipHcProfile.text = getString(
                if (connected) R.string.hc_connected else R.string.coming_soon
            )
            binding.btnConnectHcProfile.isVisible = !connected
        }
    }

    // ── API key ───────────────────────────────────────────────────────────────

    private fun setupApiKey() {
        val keyStore = viewModel.keyStore
        if (keyStore.hasKey()) binding.etApiKey.setText(keyStore.apiKey)

        binding.btnSaveKey.setOnClickListener { saveKey() }
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
