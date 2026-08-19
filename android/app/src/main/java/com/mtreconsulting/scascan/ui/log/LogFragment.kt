package com.mtreconsulting.scascan.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.mtreconsulting.scascan.R
import com.mtreconsulting.scascan.data.local.LogEntry
import com.mtreconsulting.scascan.data.local.WaterLog
import com.mtreconsulting.scascan.data.model.MacroTargets
import com.mtreconsulting.scascan.databinding.FragmentLogBinding
import com.mtreconsulting.scascan.databinding.ItemLogEntryBinding
import com.mtreconsulting.scascan.ui.util.applyHeroGradient
import com.mtreconsulting.scascan.ui.util.hapticClick
import com.mtreconsulting.scascan.ui.util.hapticConfirm
import com.mtreconsulting.scascan.ui.util.hapticReject
import com.mtreconsulting.scascan.ui.util.hapticTick
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

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

        binding.heroCaloriesContent.applyHeroGradient(
            startAttr = com.google.android.material.R.attr.colorPrimaryContainer,
            endAttr = com.google.android.material.R.attr.colorSecondaryContainer,
            cornerRadiusPx = 28f * resources.displayMetrics.density
        )

        setupDateNavigation()
        setupFixResultListener()
        setupSummaryCards()
        setupRecapCard()
        setupWaterTracking()
        observeState()
        viewModel.loadHealthData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadHealthData()
        viewModel.refreshTargets()
    }

    // Date navigation
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

    // Summary cards
    private fun setupSummaryCards() {
        val openSummary = View.OnClickListener { v ->
            v.hapticClick()
            showDailySummary()
        }
        binding.cardCalories.setOnClickListener(openSummary)
        binding.cardMacros.setOnClickListener(openSummary)
    }

    private fun setupWaterTracking() {
        binding.btnAddWater100.setOnClickListener {
            it.hapticClick()
            viewModel.addWater(100)
        }
        binding.btnAddWater250.setOnClickListener {
            it.hapticClick()
            viewModel.addWater(250)
        }
        binding.btnAddWater500.setOnClickListener {
            it.hapticClick()
            viewModel.addWater(500)
        }
        binding.btnRemoveWater.setOnClickListener {
            it.hapticTick()
            viewModel.removeLastWater()
        }
    }

    private fun showDailySummary() {
        if (childFragmentManager.findFragmentByTag("daily_summary") != null) return
        val entries     = viewModel.logEntries.value
        val water       = viewModel.waterEntries.value
        val targetInfo  = viewModel.targetInfo.value
        val adaptive    = viewModel.adaptiveState.value
        val activeKcal  = (adaptive as? LogViewModel.AdaptiveState.Active)?.activeKcal?.roundToInt() ?: 0
        val trendAdj    = (adaptive as? LogViewModel.AdaptiveState.Active)?.trendAdjustment ?: 0
        val steps       = (adaptive as? LogViewModel.AdaptiveState.Active)?.steps ?: 0L

        DailySummaryBottomSheetFragment.newInstance(
            totalCalories  = entries.sumOf { it.calories }.roundToInt(),
            totalProtein   = entries.sumOf { it.protein }.roundToInt(),
            totalCarbs     = entries.sumOf { it.carbohydrates }.roundToInt(),
            totalFat       = entries.sumOf { it.fat }.roundToInt(),
            totalFiber     = entries.sumOf { it.fiber },
            totalSodiumMg  = entries.sumOf { it.sodium },
            totalWater     = water.sumOf { it.amountMl },
            calorieTarget  = targetInfo.caloriesKcal + trendAdj + targetInfo.bleedthroughKcal,
            proteinTarget  = targetInfo.macros.proteinG,
            carbsTarget    = targetInfo.macros.carbsG,
            fatTarget      = targetInfo.macros.fatG,
            activeKcal     = activeKcal,
            steps          = steps,
            bleedthrough   = targetInfo.bleedthroughKcal,
            goalIndex      = targetInfo.goalIndex,
            isAiComputed   = targetInfo.isAiComputed,
            dateLabel      = viewModel.selectedDateLabel.value
        ).show(childFragmentManager, "daily_summary")
    }

    // Evening recap
    private fun setupRecapCard() {
        binding.cardRecap.setOnClickListener {
            it.hapticClick()
            showRecap()
        }
        updateRecapCard()
    }

    private fun isRecapUnlocked(): Boolean {
        if (!viewModel.isToday.value) return true
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= 21
    }

    private fun updateRecapCard() {
        binding.tvRecapStatus.text = if (isRecapUnlocked())
            getString(R.string.log_recap_cta)
        else
            getString(R.string.log_recap_locked_status)
    }

    private fun showRecap() {
        if (!isRecapUnlocked()) return
        if (childFragmentManager.findFragmentByTag("daily_recap") != null) return
        val offsetDays = viewModel.currentDateOffset
        val dateLabel = viewModel.selectedDateLabel.value
        viewLifecycleOwner.lifecycleScope.launch {
            val recap = viewModel.getDailyRecap(offsetDays)
            DailyRecapBottomSheetFragment.newInstance(recap, dateLabel)
                .show(childFragmentManager, "daily_recap")
        }
    }

    // Fix entry
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

    // State observation
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.logEntries,
                viewModel.waterEntries,
                viewModel.adaptiveState,
                viewModel.targetInfo
            ) { entries: List<LogEntry>, water: List<WaterLog>, adaptive: LogViewModel.AdaptiveState, targetInfo: LogViewModel.TargetInfo ->
                RenderState(entries, water, adaptive, targetInfo)
            }.collect { state ->
                renderScreen(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fixState.collect { state ->
                when (state) {
                    is LogViewModel.FixState.Idle    -> {
                        binding.progressGlobal.visibility = View.GONE
                    }
                    is LogViewModel.FixState.Loading -> {
                        binding.progressGlobal.visibility = View.VISIBLE
                    }
                    is LogViewModel.FixState.Success -> {
                        binding.progressGlobal.visibility = View.GONE
                        binding.root.hapticConfirm()
                        
                        // Dismiss the fix entry bottom sheet if it's still showing
                        (childFragmentManager.findFragmentByTag("fix_entry") as? FixEntryBottomSheetFragment)?.dismiss()
                        
                        Snackbar.make(binding.root, R.string.fix_entry_fixed, Snackbar.LENGTH_SHORT).show()
                        viewModel.resetFixState()
                    }
                    is LogViewModel.FixState.Error   -> {
                        binding.progressGlobal.visibility = View.GONE
                        
                        // Reset the fix entry bottom sheet if it's still showing
                        (childFragmentManager.findFragmentByTag("fix_entry") as? FixEntryBottomSheetFragment)?.dismiss()

                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        viewModel.resetFixState()
                    }
                }
            }
        }
    }

    private data class RenderState(
        val entries: List<LogEntry>,
        val water: List<WaterLog>,
        val adaptive: LogViewModel.AdaptiveState,
        val targetInfo: LogViewModel.TargetInfo
    )

    // Rendering
    private fun renderScreen(state: RenderState) {
        val trendAdj   = (state.adaptive as? LogViewModel.AdaptiveState.Active)?.trendAdjustment ?: 0
        val bleedthrough = state.targetInfo.bleedthroughKcal
        
        // Use repository to compute final target with health floors (coercion).
        // Today's live activity burn is deliberately excluded — it's settled once, in the
        // evening recap, not folded into a target that would otherwise shift on every HC sync.
        val totalTarget = if (viewModel.isToday.value) {
            viewModel.getFinalTarget(state.targetInfo.caloriesKcal, bleedthrough, trendAdj)
        } else {
            state.targetInfo.caloriesKcal
        }
        
        renderEntries(state.entries, totalTarget, state.targetInfo.macros)
        renderWater(state.water)
        renderHealthMetrics(state.adaptive)
        renderAdaptiveCard(state.adaptive, state.targetInfo)
        updateRecapCard()
    }

    private fun renderWater(water: List<WaterLog>) {
        val totalMl = water.sumOf { it.amountMl }
        binding.tvWaterTotal.text = getString(R.string.log_water_total, totalMl)
        binding.btnRemoveWater.isVisible = water.isNotEmpty()
    }

    private fun renderEntries(entries: List<LogEntry>, calorieTarget: Int, macros: MacroTargets) {
        val totalCalories = entries.sumOf { it.calories }.roundToInt()
        val totalProtein  = entries.sumOf { it.protein }.roundToInt()
        val totalCarbs    = entries.sumOf { it.carbohydrates }.roundToInt()
        val totalFat      = entries.sumOf { it.fat }.roundToInt()

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

        entries.forEachIndexed { index, entry ->
            val item = ItemLogEntryBinding.inflate(layoutInflater, binding.entriesContainer, true)
            item.tvFoodName.text = entry.foodName
            item.tvNutrientSummary.text = buildString {
                append("${entry.calories.roundToInt()} kcal")
                append(" • ${entry.protein.roundToInt()}g P")
                append(" • ${entry.carbohydrates.roundToInt()}g C")
                append(" • ${entry.fat.roundToInt()}g F")
            }
            item.btnFix.setOnClickListener {
                it.hapticClick()
                FixEntryBottomSheetFragment.newInstance(entry.id, entry.foodName)
                    .show(childFragmentManager, "fix_entry")
            }
            item.btnRemove.setOnClickListener {
                it.hapticReject()
                // Shrink-and-fade before the actual delete, so removal reads as a deliberate
                // action instead of the row just vanishing when the list re-renders.
                item.root.animate()
                    .scaleX(0.85f).scaleY(0.85f).alpha(0f)
                    .setDuration(180L)
                    .withEndAction { viewModel.deleteEntry(entry) }
                    .start()
            }

            item.root.alpha = 0f
            item.root.translationY = 20f * resources.displayMetrics.density
            item.root.scaleX = 0.96f
            item.root.scaleY = 0.96f
            item.root.animate()
                .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(index * 35L)
                .setDuration(320L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                .start()
        }
    }

    private fun renderHealthMetrics(adaptive: LogViewModel.AdaptiveState) {
        val active = adaptive as? LogViewModel.AdaptiveState.Active
        binding.cardHcMetrics.isVisible = active != null
        if (active != null) {
            val km = String.format(Locale.getDefault(), "%.1f km", active.steps * 0.0008)
            binding.tvHcSteps.text    = getString(R.string.hc_steps, active.steps)
            binding.tvHcCalories.text = getString(R.string.hc_active_kcal, active.activeKcal.roundToInt())
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
                binding.tvAdaptiveHint.setOnClickListener { viewModel.loadHealthData() }
                binding.tvAdaptiveWeightHint.isVisible = false
            }
            is LogViewModel.AdaptiveState.HcDisconnected -> {
                binding.chipAdaptiveStatus.isVisible = false
                binding.layoutAdaptiveBreakdown.isVisible = false
                binding.tvAdaptiveHint.isVisible = true
                binding.tvAdaptiveHint.text = getString(R.string.log_adaptive_hint_connect)
                binding.tvAdaptiveHint.setOnClickListener { viewModel.loadHealthData() }
                binding.tvAdaptiveWeightHint.isVisible = false
            }
            is LogViewModel.AdaptiveState.Active -> {
                binding.chipAdaptiveStatus.isVisible = true
                binding.layoutAdaptiveBreakdown.isVisible = true
                binding.tvAdaptiveHint.isVisible = false
                binding.chipAdaptiveStatus.setChipBackgroundColorResource(android.R.color.transparent)
                binding.chipAdaptiveStatus.text = when (adaptive.trendStatus) {
                    LogViewModel.AdaptiveState.TrendStatus.OnTrack ->
                        getString(R.string.log_adaptive_status_on_track)
                    LogViewModel.AdaptiveState.TrendStatus.TooFast,
                    LogViewModel.AdaptiveState.TrendStatus.TooSlow ->
                        getString(R.string.log_adaptive_status_adjusting)
                    LogViewModel.AdaptiveState.TrendStatus.NoData ->
                        getString(R.string.log_adaptive_status_active)
                }
                
                // Color the chip based on status
                val color = when (adaptive.trendStatus) {
                    LogViewModel.AdaptiveState.TrendStatus.OnTrack -> R.color.colorPrimary
                    LogViewModel.AdaptiveState.TrendStatus.NoData -> R.color.colorSecondary
                    else -> R.color.colorTertiary
                }
                binding.chipAdaptiveStatus.setTextColor(ContextCompat.getColor(requireContext(), color))
                binding.chipAdaptiveStatus.chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), color)
                )

                val baseTarget = targetInfo.caloriesKcal
                val activeKcal = adaptive.activeKcal.roundToInt()
                val trendAdj   = adaptive.trendAdjustment
                val bleedthrough = targetInfo.bleedthroughKcal

                binding.tvAdaptiveBase.text     = getString(R.string.log_adaptive_kcal, baseTarget)
                binding.tvAdaptiveActivity.text = if (activeKcal > 0)
                    getString(R.string.log_adaptive_plus_kcal, activeKcal)
                else
                    getString(R.string.log_adaptive_kcal, 0)

                binding.tvAdaptiveBleedthrough.text = if (bleedthrough >= 0)
                    getString(R.string.log_adaptive_plus_kcal, bleedthrough)
                else
                    getString(R.string.log_adaptive_minus_kcal, -bleedthrough)

                binding.tvAdaptiveTrend.text = when (adaptive.trendStatus) {
                    LogViewModel.AdaptiveState.TrendStatus.NoData ->
                        getString(R.string.log_adaptive_trend_collecting)
                    else -> {
                        val adjStr = if (trendAdj >= 0) "+$trendAdj" else trendAdj.toString()
                        val rate = adaptive.weeklyRateKgPerWeek
                        if (rate != null) {
                            getString(R.string.log_adaptive_trend_rate, adjStr, rate)
                        } else {
                            getString(R.string.log_adaptive_kcal, trendAdj)
                        }
                    }
                }

                binding.tvAdaptiveTotal.text = getString(
                    R.string.log_adaptive_kcal, baseTarget + trendAdj + bleedthrough
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
