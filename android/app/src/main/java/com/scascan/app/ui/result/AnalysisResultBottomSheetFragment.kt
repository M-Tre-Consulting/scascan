package com.scascan.app.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.databinding.FragmentAnalysisResultBottomSheetBinding

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

        val facts = requireArguments().getParcelable<NutritionFacts>(ARG_FACTS)!!

        binding.tvFoodName.text = facts.foodName
        binding.tvCalories.text = "${facts.calories.toInt()} kcal · ${facts.servingSize}"
        binding.tvMacros.text = buildString {
            append("${facts.protein.toInt()}g protein")
            append(" · ${facts.carbohydrates.toInt()}g carbs")
            append(" · ${facts.fat.toInt()}g fat")
        }

        binding.btnAddToLog.setOnClickListener {
            sendResult(ACTION_ADD)
        }
        binding.btnViewDetails.setOnClickListener {
            sendResult(ACTION_DETAILS)
        }
        binding.btnDismissAnalysis.setOnClickListener {
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
