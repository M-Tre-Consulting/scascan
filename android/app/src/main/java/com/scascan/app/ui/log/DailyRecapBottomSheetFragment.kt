package com.scascan.app.ui.log

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
import com.scascan.app.data.repository.LogRepository
import com.scascan.app.databinding.FragmentDailyRecapBottomSheetBinding

/**
 * Settles a single day's calorie ledger — eaten, burned, carried over — as a one-time
 * end-of-day verdict, rather than folding the burn into the live target shown all day.
 * See ios/ARCHITECTURE.md §5.4/§10 for the model this mirrors.
 */
class DailyRecapBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentDailyRecapBottomSheetBinding? = null
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
        _binding = FragmentDailyRecapBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val a = requireArguments()
        binding.tvRecapDate.text = a.getString(KEY_DATE_LABEL, "")

        val verdict = LogRepository.RecapVerdict.valueOf(a.getString(KEY_VERDICT)!!)
        val noData = verdict == LogRepository.RecapVerdict.NO_DATA
        binding.layoutRecapNoData.isVisible = noData
        binding.layoutRecapContent.isVisible = !noData
        if (noData) return

        val eaten = a.getInt(KEY_EATEN)
        val burned = a.getInt(KEY_BURNED)
        val carryOver = a.getInt(KEY_CARRY_OVER)
        val net = a.getInt(KEY_NET)
        val target = a.getInt(KEY_TARGET)
        val waterMl = a.getInt(KEY_WATER_ML)
        val waterTargetMl = a.getInt(KEY_WATER_TARGET_ML)

        val (verdictText, verdictColorAttr) = when (verdict) {
            LogRepository.RecapVerdict.OVER -> getString(R.string.log_recap_verdict_over) to com.google.android.material.R.attr.colorError
            LogRepository.RecapVerdict.UNDER -> getString(R.string.log_recap_verdict_under) to com.google.android.material.R.attr.colorTertiary
            LogRepository.RecapVerdict.ON_TARGET -> getString(R.string.log_recap_verdict_on_target) to com.google.android.material.R.attr.colorPrimary
            LogRepository.RecapVerdict.NO_DATA -> "" to com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        binding.tvRecapVerdict.text = verdictText
        binding.tvRecapVerdict.setTextColor(
            com.google.android.material.color.MaterialColors.getColor(binding.root, verdictColorAttr)
        )

        binding.tvRecapNet.text = getString(R.string.log_adaptive_kcal, net)
        binding.tvRecapNetOfTarget.text = getString(R.string.log_summary_consumed_of, net, target)

        binding.tvRecapEaten.text = getString(R.string.log_adaptive_kcal, eaten)
        binding.tvRecapBurned.text = getString(R.string.log_adaptive_minus_kcal, burned)
        binding.tvRecapCarryOver.text = if (carryOver >= 0)
            getString(R.string.log_adaptive_plus_kcal, carryOver)
        else
            getString(R.string.log_adaptive_minus_kcal, -carryOver)
        binding.tvRecapTarget.text = getString(R.string.log_adaptive_kcal, target)

        binding.tvRecapWater.text = getString(R.string.log_summary_consumed_of, waterMl, waterTargetMl)
        binding.progressRecapWater.max = waterTargetMl.coerceAtLeast(1)
        binding.progressRecapWater.progress = waterMl.coerceIn(0, waterTargetMl)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_DATE_LABEL = "date_label"
        private const val KEY_VERDICT = "verdict"
        private const val KEY_EATEN = "eaten"
        private const val KEY_BURNED = "burned"
        private const val KEY_CARRY_OVER = "carry_over"
        private const val KEY_NET = "net"
        private const val KEY_TARGET = "target"
        private const val KEY_WATER_ML = "water_ml"
        private const val KEY_WATER_TARGET_ML = "water_target_ml"

        fun newInstance(recap: LogRepository.DailyRecap, dateLabel: String) = DailyRecapBottomSheetFragment().apply {
            arguments = bundleOf(
                KEY_DATE_LABEL to dateLabel,
                KEY_VERDICT to recap.verdict.name,
                KEY_EATEN to recap.eaten,
                KEY_BURNED to recap.burned,
                KEY_CARRY_OVER to recap.carryOver,
                KEY_NET to recap.net,
                KEY_TARGET to recap.target,
                KEY_WATER_ML to recap.waterMl,
                KEY_WATER_TARGET_ML to recap.waterTargetMl
            )
        }
    }
}
