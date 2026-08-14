package com.scascan.app.ui.log

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.scascan.app.R
import com.scascan.app.databinding.FragmentDailySummaryBottomSheetBinding

class DailySummaryBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentDailySummaryBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        super.onCreateDialog(savedInstanceState).also { dialog ->
            (dialog as BottomSheetDialog).behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDailySummaryBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val a = requireArguments()

        val totalCalories = a.getInt(KEY_TOTAL_CALORIES)
        val totalProtein  = a.getInt(KEY_TOTAL_PROTEIN)
        val totalCarbs    = a.getInt(KEY_TOTAL_CARBS)
        val totalFat      = a.getInt(KEY_TOTAL_FAT)
        val totalFiber    = a.getDouble(KEY_TOTAL_FIBER)
        val totalSodium   = a.getDouble(KEY_TOTAL_SODIUM)
        val totalWater    = a.getInt(KEY_TOTAL_WATER)
        val calorieTarget = a.getInt(KEY_CALORIE_TARGET)
        val proteinTarget = a.getInt(KEY_PROTEIN_TARGET)
        val carbsTarget   = a.getInt(KEY_CARBS_TARGET)
        val fatTarget     = a.getInt(KEY_FAT_TARGET)
        val activeKcal    = a.getInt(KEY_ACTIVE_KCAL)
        val steps         = a.getLong(KEY_STEPS)
        val bleedthrough  = a.getInt(KEY_BLEEDTHROUGH)
        val goalIndex     = a.getInt(KEY_GOAL_INDEX)
        val isAiComputed  = a.getBoolean(KEY_IS_AI)
        val dateLabel     = a.getString(KEY_DATE_LABEL, "")

        binding.tvSummaryDate.text = dateLabel

        renderCalories(totalCalories, calorieTarget, activeKcal, steps, bleedthrough)
        renderMacros(totalProtein, totalCarbs, totalFat, proteinTarget, carbsTarget, fatTarget)
        renderOther(totalFiber, totalSodium, totalWater)
        renderGoal(goalIndex, isAiComputed)
    }

    @SuppressLint("SetTextI18n")
    private fun renderCalories(consumed: Int, target: Int, activeKcal: Int, steps: Long, bleedthrough: Int) {
        binding.tvSummaryConsumed.text = consumed.toString()
        binding.tvSummaryCalorieTarget.text = "/ $target kcal"

        if (target > 0) {
            binding.progressSummaryCalories.max = target
            binding.progressSummaryCalories.progress = consumed.coerceIn(0, target)
        }

        val remaining = target - consumed
        binding.tvCaloriesStatus.text = if (remaining >= 0)
            getString(R.string.log_summary_remaining, remaining)
        else
            getString(R.string.log_summary_over, -remaining)

        if (bleedthrough != 0) {
            binding.tvActivityContribution.isVisible = true
            binding.tvActivityContribution.text = getString(R.string.log_summary_bleedthrough, bleedthrough)
        }

        val hasHealth = activeKcal > 0 || steps > 0
        binding.layoutHealthSummary.isVisible = hasHealth
        if (hasHealth) {
            binding.tvSummarySteps.text = steps.toString()
            binding.tvSummaryActiveKcal.text = activeKcal.toString()
            binding.tvSummaryDistance.text = String.format(java.util.Locale.getDefault(), "%.1f km", steps * 0.0008)
        }
    }

    private fun renderMacros(
        protein: Int, carbs: Int, fat: Int,
        pTarget: Int, cTarget: Int, fTarget: Int
    ) {
        val hasTargets = pTarget > 0

        fun macroText(consumed: Int, target: Int): String =
            if (hasTargets) getString(R.string.log_summary_consumed_of, consumed, target)
            else "${consumed}g"

        fun statusText(consumed: Int, target: Int): String {
            val diff = target - consumed
            return if (diff >= 0)
                getString(R.string.log_summary_macro_remaining, diff)
            else
                getString(R.string.log_summary_macro_over, -diff)
        }

        binding.tvSummaryProtein.text = macroText(protein, pTarget)
        binding.tvSummaryCarbs.text   = macroText(carbs, cTarget)
        binding.tvSummaryFat.text     = macroText(fat, fTarget)

        if (hasTargets) {
            binding.progressSummaryProtein.max = pTarget
            binding.progressSummaryCarbs.max   = cTarget
            binding.progressSummaryFat.max     = fTarget
            binding.progressSummaryProtein.progress = protein.coerceIn(0, pTarget)
            binding.progressSummaryCarbs.progress   = carbs.coerceIn(0, cTarget)
            binding.progressSummaryFat.progress     = fat.coerceIn(0, fTarget)

            binding.tvProteinStatus.text = statusText(protein, pTarget)
            binding.tvCarbsStatus.text   = statusText(carbs, cTarget)
            binding.tvFatStatus.text     = statusText(fat, fTarget)
        }

        binding.progressSummaryProtein.isVisible = hasTargets
        binding.progressSummaryCarbs.isVisible   = hasTargets
        binding.progressSummaryFat.isVisible     = hasTargets
        binding.tvProteinStatus.isVisible = hasTargets
        binding.tvCarbsStatus.isVisible   = hasTargets
        binding.tvFatStatus.isVisible     = hasTargets
    }

    private fun renderOther(fiberG: Double, sodiumMg: Double, waterMl: Int) {
        binding.tvSummaryFiber.text = getString(R.string.value_grams, fiberG)
        binding.tvSummarySodium.text = getString(R.string.value_milligrams, sodiumMg)
        binding.tvSummaryWater.text = getString(R.string.log_water_total, waterMl)
    }

    private fun renderGoal(goalIndex: Int, isAiComputed: Boolean) {
        val goals = resources.getStringArray(R.array.goal_levels)
        binding.tvSummaryGoal.text = goals.getOrElse(goalIndex) { goals[1] }
        binding.tvTargetSource.text = getString(
            when {
                isAiComputed -> R.string.log_summary_target_ai
                else         -> R.string.log_summary_target_est
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_TOTAL_CALORIES  = "total_cal"
        private const val KEY_TOTAL_PROTEIN   = "total_p"
        private const val KEY_TOTAL_CARBS     = "total_c"
        private const val KEY_TOTAL_FAT       = "total_f"
        private const val KEY_TOTAL_FIBER     = "total_fiber"
        private const val KEY_TOTAL_SODIUM    = "total_sodium"
        private const val KEY_TOTAL_WATER     = "total_water"
        private const val KEY_CALORIE_TARGET  = "cal_target"
        private const val KEY_PROTEIN_TARGET  = "p_target"
        private const val KEY_CARBS_TARGET    = "c_target"
        private const val KEY_FAT_TARGET      = "f_target"
        private const val KEY_ACTIVE_KCAL     = "active_kcal"
        private const val KEY_STEPS           = "steps"
        private const val KEY_BLEEDTHROUGH    = "bleedthrough"
        private const val KEY_GOAL_INDEX      = "goal_idx"
        private const val KEY_IS_AI           = "is_ai"
        private const val KEY_DATE_LABEL      = "date_label"

        fun newInstance(
            totalCalories: Int, totalProtein: Int, totalCarbs: Int,
            totalFat: Int, totalFiber: Double, totalSodiumMg: Double,
            totalWater: Int,
            calorieTarget: Int, proteinTarget: Int, carbsTarget: Int, fatTarget: Int,
            activeKcal: Int, steps: Long, bleedthrough: Int,
            goalIndex: Int, isAiComputed: Boolean, dateLabel: String
        ) = DailySummaryBottomSheetFragment().apply {
            arguments = bundleOf(
                KEY_TOTAL_CALORIES to totalCalories,
                KEY_TOTAL_PROTEIN  to totalProtein,
                KEY_TOTAL_CARBS    to totalCarbs,
                KEY_TOTAL_FAT      to totalFat,
                KEY_TOTAL_FIBER    to totalFiber,
                KEY_TOTAL_SODIUM   to totalSodiumMg,
                KEY_TOTAL_WATER    to totalWater,
                KEY_CALORIE_TARGET to calorieTarget,
                KEY_PROTEIN_TARGET to proteinTarget,
                KEY_CARBS_TARGET   to carbsTarget,
                KEY_FAT_TARGET     to fatTarget,
                KEY_ACTIVE_KCAL    to activeKcal,
                KEY_STEPS          to steps,
                KEY_BLEEDTHROUGH   to bleedthrough,
                KEY_GOAL_INDEX     to goalIndex,
                KEY_IS_AI          to isAiComputed,
                KEY_DATE_LABEL     to dateLabel
            )
        }
    }
}
