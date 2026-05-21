package com.scascan.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.scascan.app.R
import com.scascan.app.databinding.FragmentSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Whole pill bar is the back target
        binding.topBar.setOnClickListener { findNavController().navigateUp() }
        binding.btnAnalyze.setOnClickListener { submitSearch() }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { submitSearch(); true } else false
        }

        // Pad the root down by the status-bar height so the floating bar and content
        // clear the status bar together (both have relative positioning inside the root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.root.updatePadding(top = statusBar)
            insets
        }

        observeUiState()
    }

    private fun submitSearch() {
        val query = binding.etSearch.text?.toString() ?: return
        viewModel.searchFood(query)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is SearchUiState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnAnalyze.isEnabled = true
                    }
                    is SearchUiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.btnAnalyze.isEnabled = false
                    }
                    is SearchUiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        findNavController().navigate(
                            R.id.action_searchFragment_to_nutritionResultFragment,
                            bundleOf("nutritionFacts" to state.nutritionFacts)
                        )
                        viewModel.resetToIdle()
                    }
                    is SearchUiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnAnalyze.isEnabled = true
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        viewModel.resetToIdle()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
