package com.mtreconsulting.scascan.ui.result

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
import com.mtreconsulting.scascan.R
import com.mtreconsulting.scascan.data.model.NutritionFacts
import com.mtreconsulting.scascan.databinding.FragmentNutritionResultBinding
import com.mtreconsulting.scascan.ui.util.applyHeroGradient
import com.mtreconsulting.scascan.ui.util.staggerChildrenIn
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
        binding.contentContainer.staggerChildrenIn()

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

        val caloriesTarget = facts.calories.roundToInt()
        android.animation.ValueAnimator.ofInt(0, caloriesTarget).apply {
            duration = 800L
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener { binding.tvCaloriesValue.text = (it.animatedValue as Int).toString() }
            start()
        }

        // A single-hue fade (green → neutral surface) rather than a two-hue sweep: the
        // macro ring already carries three distinct hues (primary/secondary/tertiary), so a
        // primaryContainer→tertiaryContainer background competed with it for attention instead
        // of framing it.
        binding.heroContent.applyHeroGradient(
            startAttr = com.google.android.material.R.attr.colorPrimaryContainer,
            endAttr = com.google.android.material.R.attr.colorSurfaceContainerHigh,
            cornerRadiusPx = 28f * resources.displayMetrics.density
        )
        binding.macroRing.setMacros(facts.protein, facts.carbohydrates, facts.fat)

        val proteinKcal = facts.protein * 4.0
        val carbsKcal = facts.carbohydrates * 4.0
        val fatKcal = facts.fat * 9.0
        val totalKcal = (proteinKcal + carbsKcal + fatKcal).coerceAtLeast(1.0)
        binding.tvProteinValue.text = getString(R.string.value_grams_of_percent, facts.protein, proteinKcal / totalKcal * 100)
        binding.tvCarbsValue.text = getString(R.string.value_grams_of_percent, facts.carbohydrates, carbsKcal / totalKcal * 100)
        binding.tvFatValue.text = getString(R.string.value_grams_of_percent, facts.fat, fatKcal / totalKcal * 100)

        binding.tvFiberValue.text = getString(R.string.value_grams, facts.fiber)
        binding.tvSugarValue.text = getString(R.string.value_grams, facts.sugar)
        binding.tvSodiumValue.text = getString(R.string.value_milligrams, facts.sodium)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
