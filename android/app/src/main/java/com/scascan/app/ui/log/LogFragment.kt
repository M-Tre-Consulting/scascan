package com.scascan.app.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.scascan.app.R
import com.scascan.app.data.local.LogEntry
import com.scascan.app.data.model.MacroTargets
import com.scascan.app.databinding.FragmentLogBinding
import com.scascan.app.databinding.ItemLogEntryBinding
import com.scascan.app.ui.util.hapticClick
import com.scascan.app.ui.util.hapticConfirm
import com.scascan.app.ui.util.hapticReject
import com.scascan.app.ui.util.hapticTick
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LogFragment : Fragment() {

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LogViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.root.updatePadding(top = statusBar)
            insets
        }

        setupDateNavigation()
        setupFixResultListener()
        setupSummaryCards()
        observeState()
        viewModel.loadHealthData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadHealthData()
        viewModel.refreshTargets()
    }

    // ── Date navigation ───────────────────────────────────────────────────────

    private fun setupDateNavigation() {
        binding.btnPrevDay.setOnClickListener { it.hapticTick(); viewModel.goToPreviousDay() }
        binding.btnNextDay.setOnClickListener { it.hapticTick(); viewModel.goToNextDay() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedDateLabel.collect { binding.tvSelectedDate.text = it }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isToday.collect { binding.btnNextDay.isEnabled = !it }
        }
    }

    // ── Summary cards ─────────────────────────────────────────────────────────

    private fun setupSummaryCards() {
        val openSummary = View.OnClickListener { v ->
            v.hapticClick()
            showDailySummary()
        }
        binding.cardCalories.setOnClickListener(openSummary)
        binding.cardMacros.setOnClickListener(openSummary)
    }

    private fun showDailySummary() {
        if (childFragmentManager.findFragmentByTag("daily_summary") != null) return
        val entries     = viewModel.logEntries.value
        val targetInfo  = viewModel.targetInfo.value
        val adaptive    = viewModel.adaptiveState.value
        val activeKcal  = (adaptive as? LogViewModel.AdaptiveState.Active)?.activeKcal?.toInt() ?: 0
        val trendAdj    = (adaptive as? LogViewModel.AdaptiveState.Active)?.trendAdjustment ?: 0
        val steps       = (adaptive as? LogViewModel.AdaptiveState.Active)?.steps ?: 0L

        DailySummaryBottomSheetFragment.newInstance(
            totalCalories  = Math.round(entries.sumOf { it.calories }).toInt(),
            totalProtein   = Math.round(entries.sumOf { it.protein }).toInt(),
            totalCarbs     = Math.round(entries.sumOf { it.carbohydrates }).toInt(),
            totalFat       = Math.round(entries.sumOf { it.fat }).toInt(),
            totalFiber     = entries.sumOf { it.fiber },
            totalSodiumMg  = entries.sumOf { it.sodium },
            calorieTarget  = targetInfo.caloriesKcal + activeKcal + trendAdj,
            proteinTarget  = targetInfo.macros.proteinG,
            carbsTarget    = targetInfo.macros.carbsG,
            fatTarget      = targetInfo.macros.fatG,
            activeKcal     = activeKcal,
            steps          = steps,
            goalIndex      = targetInfo.goalIndex,
            isAiComputed   = targetInfo.isAiComputed,
            dateLabel      = viewModel.selectedDateLabel.value
        ).show(childFragmentManager, "daily_summary")
    }

    // ── Fix entry ─────────────────────────────────────────────────────────────

    private fun setupFixResultListener() {
        childFragmentManager.setFragmentResultListener(
            FixEntryBottomSheetFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val entryId = bundle.getLong(FixEntryBottomSheetFragment.RESULT_ENTRY_ID)
            val correction = bundle.getString(FixEntryBottomSheetFragment.RESULT_CORRECTION) ?: return@setFragmentResultListener
            val entry = viewModel.logEntries.value.find { it.id == entryId } ?: return@setFragmentResultListener
            viewModel.fixEntry(entry, correction)
        }
    }

    // ── State observation ─────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.logEntries,
                viewModel.adaptiveState,
                viewModel.targetInfo
            ) { entries, adaptive, targetInfo -> Triple(entries, adaptive, targetInfo) }
                .collect { (entries, adaptive, targetInfo) ->
                    renderScreen(entries, adaptive, targetInfo)
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fixState.collect { state ->
                when (state) {
                    is LogViewModel.FixState.Idle    -> Unit
                    is LogViewModel.FixState.Success -> {
                        binding.root.hapticConfirm()
                        Snackbar.make(binding.root, R.string.fix_entry_fixed, Snackbar.LENGTH_SHORT).show()
                        viewModel.resetFixState()
                    }
                    is LogViewModel.FixState.Error   -> {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        viewModel.resetFixState()
                    }
                }
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun renderScreen(
        entries: List<LogEntry>,
        adaptive: LogViewModel.AdaptiveState,
        targetInfo: LogViewModel.TargetInfo
    ) {
        val activeKcal = (adaptive as? LogViewModel.AdaptiveState.Active)?.activeKcal?.toInt() ?: 0
        val trendAdj   = (adaptive as? LogViewModel.AdaptiveState.Active)?.trendAdjustment ?: 0
        renderEntries(entries, targetInfo.caloriesKcal + activeKcal + trendAdj, targetInfo.macros)
        renderHealthMetrics(adaptive)
        renderAdaptiveCard(adaptive, targetInfo)
    }

    private fun renderEntries(entries: List<LogEntry>, calorieTarget: Int, macros: MacroTargets) {
        val totalCalories = Math.round(entries.sumOf { it.calories }).toInt()
        val totalProtein  = Math.round(entries.sumOf { it.protein }).toInt()
        val totalCarbs    = Math.round(entries.sumOf { it.carbohydrates }).toInt()
        val totalFat      = Math.round(entries.sumOf { it.fat }).toInt()

        binding.tvCalorieSummary.text = getString(R.string.log_kcal_of, totalCalories, calorieTarget)
        binding.progressCalories.max      = calorieTarget
        binding.progressCalories.progress = totalCalories.coerceIn(0, calorieTarget)

        val hasData    = entries.isNotEmpty()
        val hasTargets = macros.proteinG > 0

        binding.tvLogProtein.text = if (hasData) "${totalProtein}g" else "—"
        binding.tvLogCarbs.text   = if (hasData) "${totalCarbs}g"   else "—"
        binding.tvLogFat.text     = if (hasData) "${totalFat}g"     else "—"

        if (hasTargets) {
            binding.tvLogProteinTarget.text = getString(R.string.log_macro_of, macros.proteinG)
            binding.tvLogCarbsTarget.text   = getString(R.string.log_macro_of, macros.carbsG)
            binding.tvLogFatTarget.text     = getString(R.string.log_macro_of, macros.fatG)

            binding.progressProtein.max = macros.proteinG
            binding.progressCarbs.max   = macros.carbsG
            binding.progressFat.max     = macros.fatG
            binding.progressProtein.progress = totalProtein.coerceIn(0, macros.proteinG)
            binding.progressCarbs.progress   = totalCarbs.coerceIn(0, macros.carbsG)
            binding.progressFat.progress     = totalFat.coerceIn(0, macros.fatG)
        }

        listOf(binding.tvLogProteinTarget, binding.progressProtein).forEach { it.isVisible = hasTargets }
        listOf(binding.tvLogCarbsTarget,   binding.progressCarbs  ).forEach { it.isVisible = hasTargets }
        listOf(binding.tvLogFatTarget,     binding.progressFat    ).forEach { it.isVisible = hasTargets }

        binding.entriesContainer.removeAllViews()
        binding.tvEmptyLog.isVisible = entries.isEmpty()

        entries.forEach { entry ->
            val item = ItemLogEntryBinding.inflate(layoutInflater, binding.entriesContainer, true)
            item.tvFoodName.text = entry.foodName
            item.tvNutrientSummary.text = buildString {
                append("${Math.round(entry.calories).toInt()} kcal")
                append(" · ${Math.round(entry.protein).toInt()}g protein")
                append(" · ${Math.round(entry.carbohydrates).toInt()}g carbs")
                append(" · ${Math.round(entry.fat).toInt()}g fat")
            }
            item.btnFix.setOnClickListener {
                it.hapticClick()
                FixEntryBottomSheetFragment.newInstance(entry.id, entry.foodName)
                    .show(childFragmentManager, "fix_entry")
            }
            item.btnRemove.setOnClickListener { it.hapticReject(); viewModel.deleteEntry(entry) }
            item.root.alpha = 0f
            item.root.animate().alpha(1f).setDuration(200).start()
        }
    }

    private fun renderHealthMetrics(adaptive: LogViewModel.AdaptiveState) {
        val active = adaptive as? LogViewModel.AdaptiveState.Active
        binding.cardHcMetrics.isVisible = active != null
        if (active != null) {
            val km = String.format("%.1f km", active.steps * 0.0008)
            binding.tvHcSteps.text    = getString(R.string.hc_steps, active.steps)
            binding.tvHcCalories.text = getString(R.string.hc_active_kcal, active.activeKcal.toInt())
            binding.tvHcDistance.text = km
        }
    }

    private fun renderAdaptiveCard(
        adaptive: LogViewModel.AdaptiveState,
        targetInfo: LogViewModel.TargetInfo
    ) {
        when (adaptive) {
            is LogViewModel.AdaptiveState.HcUnavailable -> {
                binding.chipAdaptiveStatus.isVisible = false
                binding.layoutAdaptiveBreakdown.isVisible = false
                binding.tvAdaptiveHint.isVisible = true
                binding.tvAdaptiveHint.text = getString(R.string.log_adaptive_hint_unavailable)
                binding.tvAdaptiveWeightHint.isVisible = false
            }
            is LogViewModel.AdaptiveState.HcDisconnected -> {
                binding.chipAdaptiveStatus.isVisible = false
                binding.layoutAdaptiveBreakdown.isVisible = false
                binding.tvAdaptiveHint.isVisible = true
                binding.tvAdaptiveHint.text = getString(R.string.log_adaptive_hint_connect)
                binding.tvAdaptiveWeightHint.isVisible = false
            }
            is LogViewModel.AdaptiveState.Active -> {
                binding.chipAdaptiveStatus.isVisible = true
                binding.chipAdaptiveStatus.text = when (adaptive.trendStatus) {
                    LogViewModel.AdaptiveState.TrendStatus.OnTrack ->
                        getString(R.string.log_adaptive_status_on_track)
                    LogViewModel.AdaptiveState.TrendStatus.TooFast,
                    LogViewModel.AdaptiveState.TrendStatus.TooSlow ->
                        getString(R.string.log_adaptive_status_adjusting)
                    LogViewModel.AdaptiveState.TrendStatus.NoData ->
                        getString(R.string.log_adaptive_status_active)
                }
                binding.tvAdaptiveHint.isVisible = false
                binding.layoutAdaptiveBreakdown.isVisible = true

                val baseTarget = targetInfo.caloriesKcal
                val activeKcal = adaptive.activeKcal.toInt()
                val trendAdj   = adaptive.trendAdjustment

                binding.tvAdaptiveBase.text     = getString(R.string.log_adaptive_kcal, baseTarget)
                binding.tvAdaptiveActivity.text = if (activeKcal > 0)
                    getString(R.string.log_adaptive_plus_kcal, activeKcal)
                else
                    getString(R.string.log_adaptive_kcal, 0)

                binding.tvAdaptiveTrend.text = when (adaptive.trendStatus) {
                    LogViewModel.AdaptiveState.TrendStatus.NoData ->
                        getString(R.string.log_adaptive_trend_collecting)
                    else -> {
                        val adjStr = if (trendAdj >= 0) "+$trendAdj" else "$trendAdj"
                        val rate = adaptive.weeklyRateKgPerWeek
                        if (rate != null) {
                            getString(R.string.log_adaptive_trend_rate, adjStr, rate)
                        } else {
                            getString(R.string.log_adaptive_kcal, trendAdj)
                        }
                    }
                }

                binding.tvAdaptiveTotal.text = getString(
                    R.string.log_adaptive_kcal, baseTarget + activeKcal + trendAdj
                )

                val noData = adaptive.trendStatus == LogViewModel.AdaptiveState.TrendStatus.NoData
                binding.tvAdaptiveWeightHint.isVisible = noData
                if (noData) binding.tvAdaptiveWeightHint.text =
                    getString(R.string.log_adaptive_hint_weight)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
