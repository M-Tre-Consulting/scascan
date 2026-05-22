package com.scascan.app.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.scascan.app.R
import com.scascan.app.data.local.GeminiKeyStore
import com.scascan.app.data.remote.ModelInfo
import com.scascan.app.databinding.FragmentApiKeyBinding
import com.scascan.app.ui.profile.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ApiKeyFragment : Fragment() {

    private var _binding: FragmentApiKeyBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var keyStore: GeminiKeyStore

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApiKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Returning user on cold launch — both key and model already saved
        if (keyStore.hasKey() && keyStore.selectedModel.isNotBlank() && isInitialLaunch()) {
            navigateToHome()
            return
        }

        // Pre-populate for returning users (e.g. reached from Profile menu)
        if (keyStore.hasKey()) {
            binding.etApiKey.setText(keyStore.apiKey)
        }

        binding.btnSave.setOnClickListener { attemptSaveKey() }
        binding.btnGetStarted.setOnClickListener { finish() }

        observeModelState()
    }

    private fun observeModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.modelState.collect { state ->
                when (state) {
                    is ProfileViewModel.ModelState.Idle -> Unit
                    is ProfileViewModel.ModelState.Loading -> {
                        binding.modelSection.isVisible = true
                        binding.modelSelector.setText(getString(R.string.profile_model_loading), false)
                        binding.modelSelector.isEnabled = false
                        binding.tilModel.helperText = null
                        binding.btnGetStarted.isEnabled = false
                        binding.btnSave.isEnabled = false
                    }
                    is ProfileViewModel.ModelState.Ready -> {
                        binding.modelSection.isVisible = true
                        binding.btnSave.isEnabled = true
                        applyModels(state.models)
                    }
                    is ProfileViewModel.ModelState.Error -> {
                        binding.modelSection.isVisible = true
                        binding.modelSelector.setText("", false)
                        binding.modelSelector.isEnabled = false
                        binding.tilModel.helperText = state.message
                        binding.btnGetStarted.isEnabled = false
                        binding.btnSave.isEnabled = true
                    }
                }
            }
        }
    }

    private fun applyModels(models: List<ModelInfo>) {
        if (models.isEmpty()) {
            binding.tilModel.helperText = getString(R.string.profile_model_empty)
            binding.btnGetStarted.isEnabled = false
            return
        }

        val labels = models.map { it.displayName }
        binding.modelSelector.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        )
        binding.modelSelector.isEnabled = true
        binding.tilModel.helperText = null

        // Restore previous selection or default to the first model
        val currentId = keyStore.selectedModel
        val idx = models.indexOfFirst { it.id == currentId }
        val selectIdx = if (idx >= 0) idx else 0
        binding.modelSelector.setText(labels[selectIdx], false)
        keyStore.selectedModel = models[selectIdx].id

        binding.btnGetStarted.isEnabled = true

        binding.modelSelector.setOnItemClickListener { _, _, position, _ ->
            keyStore.selectedModel = models[position].id
        }
    }

    private fun attemptSaveKey() {
        val key = binding.etApiKey.text?.toString()?.trim() ?: ""
        if (key.isBlank()) {
            binding.tilApiKey.error = getString(R.string.setup_key_error)
            return
        }
        binding.tilApiKey.error = null
        keyStore.apiKey = key
        viewModel.loadModels()
    }

    private fun finish() {
        if (isInitialLaunch()) navigateToHome() else findNavController().navigateUp()
    }

    private fun isInitialLaunch(): Boolean =
        findNavController().previousBackStackEntry == null

    private fun navigateToHome() {
        findNavController().navigate(
            R.id.action_apiKeyFragment_to_mainFragment,
            null,
            NavOptions.Builder()
                .setPopUpTo(R.id.apiKeyFragment, true)
                .build()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
