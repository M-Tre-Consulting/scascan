package com.scascan.app.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.scascan.app.R
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.databinding.FragmentAnalysisResultBottomSheetBinding
import com.scascan.app.ui.util.hapticClick
import com.scascan.app.ui.util.hapticConfirm
import com.scascan.app.ui.util.hapticTick
import kotlin.math.roundToInt

class AnalysisResultBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAnalysisResultBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisResultBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Cleanly handled using BundleCompat to avoid explicit SDK version checking
        val facts = BundleCompat.getParcelable(
            requireArguments(),
            ARG_FACTS,
            NutritionFacts::class.java
        )!!

        val caloriesInt = facts.calories.roundToInt()
        val servingSizeStr = facts.servingSize

        binding.tvFoodName.text = facts.foodName
        binding.tvCalories.text = binding.root.context.getString(
            R.string.calorie_fact_format,
            caloriesInt,
            servingSizeStr
        )
        binding.tvMacros.text = buildString {
            append("${facts.protein.roundToInt()}g protein")
            append(" · ${facts.carbohydrates.roundToInt()}g carbs")
            append(" · ${facts.fat.roundToInt()}g fat")
        }

        binding.btnAddToLog.setOnClickListener {
            it.hapticConfirm()
            sendResult(ACTION_ADD)
        }
        binding.btnViewDetails.setOnClickListener {
            it.hapticClick()
            sendResult(ACTION_DETAILS)
        }
        binding.btnDismissAnalysis.setOnClickListener {
            it.hapticTick()
            sendResult(ACTION_DISMISS)
        }
    }

    private fun sendResult(action: String) {
        parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf(KEY_ACTION to action))
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "analysis_result"
        const val KEY_ACTION = "action"
        const val ACTION_ADD = "add_to_log"
        const val ACTION_DETAILS = "view_details"
        const val ACTION_DISMISS = "dismiss"
        private const val ARG_FACTS = "nutrition_facts"

        fun newInstance(facts: NutritionFacts) =
            AnalysisResultBottomSheetFragment().apply {
                arguments = bundleOf(ARG_FACTS to facts)
            }
    }
}