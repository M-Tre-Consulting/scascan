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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.scascan.app.R
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

        binding.btnSettings.setOnClickListener {
            it.hapticClick()
            findNavController().navigate(R.id.action_main_to_settings)
        }

        setupPersonalInfo()
        updateSetupReminder()
        observeTargetState()
        observeActionState()
    }

    private fun observeActionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.actionState.collect { state ->
                when (state) {
                    is ProfileViewModel.ActionState.Idle -> {
                        binding.btnSaveProfile.isEnabled = true
                        binding.btnSaveProfile.text = getString(R.string.profile_save_info)
                    }
                    is ProfileViewModel.ActionState.Loading -> {
                        binding.btnSaveProfile.isEnabled = false
                        binding.btnSaveProfile.text = getString(R.string.analyzing)
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

        if (viewModel.profileStore.aiCalorieTarget > 0) {
            binding.btnRecompute.isVisible = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
