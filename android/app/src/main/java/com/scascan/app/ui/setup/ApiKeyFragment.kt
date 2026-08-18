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
import com.google.android.material.snackbar.Snackbar
import com.scascan.app.R
import com.scascan.app.data.local.GeminiKeyStore
import com.scascan.app.data.remote.ModelInfo
import com.scascan.app.databinding.FragmentApiKeyBinding
import com.scascan.app.ui.profile.ProfileViewModel
import com.scascan.app.ui.util.applyHeroGradient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri

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

        binding.heroSetupBackdrop.applyHeroGradient(
            startAttr = com.google.android.material.R.attr.colorPrimaryContainer,
            endAttr = com.google.android.material.R.attr.colorTertiaryContainer,
            cornerRadiusPx = 36f * resources.displayMetrics.density
        )

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
        binding.btnStartupSync.setOnClickListener { viewModel.signInAndSync(requireActivity()) }
        
        binding.btnGetKey.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                "https://aistudio.google.com/app/apikey".toUri())
            startActivity(intent)
        }

        observeModelState()
        observeSyncState()
    }

    override fun onResume() {
        super.onResume()
        checkClipboardForKey()
    }

    private fun checkClipboardForKey() {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clipData = clipboard.primaryClip ?: return
        if (clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: return
            // Typical Gemini key pattern
            if (text.length > 30 && text.startsWith("AIza")) {
                Snackbar.make(binding.root, R.string.setup_key_detected, Snackbar.LENGTH_LONG)
                    .setAction("Paste") {
                        binding.etApiKey.setText(text)
                        attemptSaveKey()
                    }
                    .show()
            }
        }
    }

    private fun observeSyncState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.syncState.collect { state ->
                when (state) {
                    is ProfileViewModel.SyncState.Idle -> {
                        val email = viewModel.profileStore.syncEmail
                        if (email.isNotBlank()) {
                            binding.btnStartupSync.text = getString(R.string.profile_sync_status_connected, email)
                            binding.btnStartupSync.isEnabled = false
                        }
                    }
                    is ProfileViewModel.SyncState.Syncing -> {
                        binding.btnStartupSync.isEnabled = false
                        binding.btnStartupSync.text = getString(R.string.profile_sync_status_syncing)
                    }
                    is ProfileViewModel.SyncState.Success -> {
                        val email = viewModel.profileStore.syncEmail
                        binding.btnStartupSync.text = getString(R.string.profile_sync_status_connected, email)
                        binding.btnStartupSync.isEnabled = false
                        viewModel.resetSyncState()
                    }
                    is ProfileViewModel.SyncState.Error -> {
                        binding.btnStartupSync.isEnabled = true
                        binding.btnStartupSync.text = getString(R.string.profile_sync_btn_google)
                        Snackbar.make(binding.root, "Sync error: ${state.message}", Snackbar.LENGTH_LONG).show()
                        viewModel.resetSyncState()
                    }
                }
            }
        }
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
                        binding.btnGetStarted.isVisible = true
                        binding.btnGetStarted.isEnabled = false
                        binding.btnSave.isEnabled = false
                    }
                    is ProfileViewModel.ModelState.Ready -> {
                        binding.modelSection.isVisible = true
                        binding.syncSection.isVisible = true
                        binding.btnSave.isEnabled = true
                        binding.btnGetStarted.isVisible = true
                        applyModels(state.models)
                    }
                    is ProfileViewModel.ModelState.Error -> {
                        binding.modelSection.isVisible = true
                        binding.modelSelector.setText("", false)
                        binding.modelSelector.isEnabled = false
                        binding.tilModel.helperText = state.message
                        binding.btnGetStarted.isVisible = true
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
