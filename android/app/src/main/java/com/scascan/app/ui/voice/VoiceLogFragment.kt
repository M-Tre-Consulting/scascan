package com.scascan.app.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.scascan.app.databinding.FragmentVoiceLogBinding
import com.scascan.app.ui.util.hapticClick
import com.scascan.app.ui.util.hapticConfirm
import com.scascan.app.ui.util.hapticReject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VoiceLogFragment : Fragment() {

    private var _binding: FragmentVoiceLogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VoiceLogViewModel by viewModels()

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginListening() else showPermissionNeeded()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoiceLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.root.updatePadding(top = statusBar)
            insets
        }

        binding.topBar.setOnClickListener { findNavController().navigateUp() }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.cardMic.setOnClickListener {
            it.hapticClick()
            ensurePermissionThenListen()
        }
        binding.btnOpenSettings.setOnClickListener {
            it.hapticClick()
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", requireContext().packageName, null))
            )
        }

        observeState()
        ensurePermissionThenListen()
    }

    private fun ensurePermissionThenListen() {
        if (!viewModel.isAvailable()) {
            binding.tvVoiceStatus.text = getString(R.string.voice_log_error_generic)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            beginListening()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun beginListening() {
        binding.btnOpenSettings.isVisible = false
        viewModel.startListening()
    }

    private fun showPermissionNeeded() {
        binding.tvVoiceStatus.text = getString(R.string.voice_log_permission_needed)
        binding.tvVoiceTranscript.isVisible = false
        binding.btnOpenSettings.isVisible = true
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                render(state)
            }
        }
    }

    private fun render(state: VoiceLogViewModel.UiState) {
        binding.progressWorking.isVisible = state is VoiceLogViewModel.UiState.Working
        when (state) {
            is VoiceLogViewModel.UiState.Idle -> {
                binding.tvVoiceStatus.text = getString(R.string.voice_log_hint)
                binding.tvVoiceTranscript.isVisible = false
            }
            is VoiceLogViewModel.UiState.Listening -> {
                binding.tvVoiceStatus.text = getString(R.string.voice_log_listening)
                binding.tvVoiceTranscript.isVisible = false
            }
            is VoiceLogViewModel.UiState.Transcribed -> {
                binding.tvVoiceStatus.text = getString(R.string.voice_log_listening)
                binding.tvVoiceTranscript.text = state.text
                binding.tvVoiceTranscript.isVisible = true
            }
            is VoiceLogViewModel.UiState.Working -> {
                binding.tvVoiceStatus.text = getString(R.string.voice_log_processing)
            }
            is VoiceLogViewModel.UiState.Added -> {
                binding.root.hapticConfirm()
                binding.tvVoiceStatus.text = getString(R.string.voice_log_added, state.entry.foodName)
                binding.tvVoiceTranscript.isVisible = false
                showUndoAndReturn(state.entry)
            }
            is VoiceLogViewModel.UiState.Error -> {
                binding.root.hapticReject()
                binding.tvVoiceStatus.text = if (state.transcript != null) {
                    getString(R.string.voice_log_error_lookup, state.transcript)
                } else {
                    getString(R.string.voice_log_error_generic)
                }
                binding.tvVoiceTranscript.isVisible = false
            }
        }
    }

    private fun showUndoAndReturn(entry: com.scascan.app.data.local.LogEntry) {
        Snackbar.make(binding.root, getString(R.string.voice_log_added, entry.foodName), Snackbar.LENGTH_LONG)
            .setDuration(4000)
            .setAction(R.string.action_undo) {
                it.hapticClick()
                viewModel.undoLastEntry(entry)
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (isAdded) findNavController().navigateUp()
                }
            })
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
