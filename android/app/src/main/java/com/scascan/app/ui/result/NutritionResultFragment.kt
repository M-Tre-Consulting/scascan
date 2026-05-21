package com.scascan.app.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.databinding.FragmentNutritionResultBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NutritionResultFragment : Fragment() {

    private var _binding: FragmentNutritionResultBinding? = null
    private val binding get() = _binding!!

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
        val facts = requireArguments().getParcelable<NutritionFacts>("nutritionFacts")!!
        bindFacts(facts)
    }

    private fun bindFacts(facts: NutritionFacts) {
        binding.tvFoodName.text = facts.foodName
        binding.tvServingSize.text = facts.servingSize
        binding.tvCaloriesValue.text = facts.calories.toInt().toString()
        binding.tvProteinValue.text = getString(com.scascan.app.R.string.value_grams, facts.protein)
        binding.tvCarbsValue.text = getString(com.scascan.app.R.string.value_grams, facts.carbohydrates)
        binding.tvFatValue.text = getString(com.scascan.app.R.string.value_grams, facts.fat)
        binding.tvFiberValue.text = getString(com.scascan.app.R.string.value_grams, facts.fiber)
        binding.tvSugarValue.text = getString(com.scascan.app.R.string.value_grams, facts.sugar)
        binding.tvSodiumValue.text = getString(com.scascan.app.R.string.value_milligrams, facts.sodium)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
