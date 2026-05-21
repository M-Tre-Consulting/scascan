package com.scascan.app.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.scascan.app.R
import com.scascan.app.data.local.GeminiKeyStore
import com.scascan.app.databinding.FragmentApiKeyBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ApiKeyFragment : Fragment() {

    private var _binding: FragmentApiKeyBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var keyStore: GeminiKeyStore

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

        // Returning user on cold launch — skip setup
        if (keyStore.hasKey() && isInitialLaunch()) {
            navigateToHome()
            return
        }

        binding.btnSave.setOnClickListener { attemptSave() }
    }

    private fun attemptSave() {
        val key = binding.etApiKey.text?.toString()?.trim() ?: ""
        if (key.isBlank()) {
            binding.tilApiKey.error = getString(R.string.setup_key_error)
            return
        }
        binding.tilApiKey.error = null
        keyStore.apiKey = key
        if (isInitialLaunch()) navigateToHome() else findNavController().navigateUp()
    }

    private fun isInitialLaunch(): Boolean =
        findNavController().previousBackStackEntry == null

    private fun navigateToHome() {
        findNavController().navigate(
            R.id.action_apiKeyFragment_to_homeFragment,
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
