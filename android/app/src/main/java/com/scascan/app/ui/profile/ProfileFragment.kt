package com.scascan.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.scascan.app.R
import com.scascan.app.data.local.GeminiKeyStore
import com.scascan.app.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var keyStore: GeminiKeyStore

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

        // Pre-populate with the current key so the user can see it's saved
        if (keyStore.hasKey()) {
            binding.etApiKey.setText(keyStore.apiKey)
        }

        binding.btnSaveKey.setOnClickListener { saveKey() }

        binding.etApiKey.setOnEditorActionListener { _, _, _ ->
            saveKey()
            true
        }
    }

    private fun saveKey() {
        val input = binding.etApiKey.text?.toString()?.trim() ?: ""

        if (input.isBlank()) {
            binding.tilApiKey.error = getString(R.string.setup_key_error)
            return
        }

        binding.tilApiKey.error = null
        keyStore.apiKey = input

        // Dismiss keyboard
        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.etApiKey.windowToken, 0)

        Snackbar.make(binding.root, getString(R.string.profile_key_saved), Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.btnSaveKey)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
