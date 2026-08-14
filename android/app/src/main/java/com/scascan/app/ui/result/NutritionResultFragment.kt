package com.scascan.app.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.scascan.app.R
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.databinding.FragmentNutritionResultBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class NutritionResultFragment : Fragment() {

    private var _binding: FragmentNutritionResultBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NutritionResultViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNutritionResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topBar.setOnClickListener { findNavController().navigateUp() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.root.updatePadding(top = statusBar)
            insets
        }

        val facts = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable("nutritionFacts", NutritionFacts::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable("nutritionFacts")!!
        }

        bindFacts(facts)
        observeLogState()

        binding.btnAddToLog.setOnClickListener {
            viewModel.addToLog(facts)
        }
    }

    private fun observeLogState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.logState.collect { state ->
                if (state is NutritionResultViewModel.LogState.Added) {
                    binding.btnAddToLog.isEnabled = false
                    binding.btnAddToLog.text = getString(R.string.log_added)
                    Snackbar.make(binding.root, R.string.log_added, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bindFacts(facts: NutritionFacts) {
        binding.tvFoodName.text = facts.foodName
        binding.tvServingSize.text = facts.servingSize
        binding.tvCaloriesValue.text = facts.calories.roundToInt().toString()
        binding.tvProteinValue.text = getString(R.string.value_grams, facts.protein)
        binding.tvCarbsValue.text = getString(R.string.value_grams, facts.carbohydrates)
        binding.tvFatValue.text = getString(R.string.value_grams, facts.fat)
        binding.tvFiberValue.text = getString(R.string.value_grams, facts.fiber)
        binding.tvSugarValue.text = getString(R.string.value_grams, facts.sugar)
        binding.tvSodiumValue.text = getString(R.string.value_milligrams, facts.sodium)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
