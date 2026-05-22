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
import com.scascan.app.data.model.MacroTargets
import com.scascan.app.ui.util.hapticClick
import com.scascan.app.ui.util.hapticConfirm
import com.scascan.app.ui.util.hapticReject
import com.scascan.app.ui.util.hapticTick
import com.scascan.app.data.local.LogEntry
import com.scascan.app.databinding.FragmentLogBinding
import com.scascan.app.databinding.ItemLogEntryBinding
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
        observeState()
        viewModel.loadHealthData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadHealthData()
        viewModel.refreshTargets()
    }

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

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.logEntries,
                viewModel.healthState,
                viewModel.targetInfo
            ) { entries, health, targetInfo -> Triple(entries, health, targetInfo) }
                .collect { (entries, health, targetInfo) ->
                    renderScreen(entries, health, targetInfo)
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fixState.collect { state ->
                when (state) {
                    is LogViewModel.FixState.Idle -> Unit
                    is LogViewModel.FixState.Success -> {
                        binding.root.hapticConfirm()
                        Snackbar.make(binding.root, R.string.fix_entry_fixed, Snackbar.LENGTH_SHORT).show()
                        viewModel.resetFixState()
                    }
                    is LogViewModel.FixState.Error -> {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        viewModel.resetFixState()
                    }
                }
            }
        }
    }

    private fun renderScreen(
        entries: List<LogEntry>,
        health: LogViewModel.HealthUiState,
        targetInfo: LogViewModel.TargetInfo
    ) {
        val activeKcal = (health as? LogViewModel.HealthUiState.Connected)?.activeKcal?.toInt() ?: 0
        val calorieTarget = targetInfo.caloriesKcal + activeKcal
        renderEntries(entries, calorieTarget, targetInfo.macros)
        renderHealthMetrics(health)
    }

    private fun renderEntries(entries: List<LogEntry>, calorieTarget: Int, macros: MacroTargets) {
        val totalCalories = entries.sumOf { it.calories }.toInt()
        val totalProtein = entries.sumOf { it.protein }.toInt()
        val totalCarbs = entries.sumOf { it.carbohydrates }.toInt()
        val totalFat = entries.sumOf { it.fat }.toInt()

        binding.tvCalorieSummary.text = getString(R.string.log_kcal_of, totalCalories, calorieTarget)
        binding.progressCalories.max = calorieTarget
        binding.progressCalories.progress = totalCalories.coerceIn(0, calorieTarget)

        val hasData = entries.isNotEmpty()
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
                append("${entry.calories.toInt()} kcal")
                append(" · ${entry.protein.toInt()}g protein")
                append(" · ${entry.carbohydrates.toInt()}g carbs")
                append(" · ${entry.fat.toInt()}g fat")
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

    private fun renderHealthMetrics(health: LogViewModel.HealthUiState) {
        val connected = health is LogViewModel.HealthUiState.Connected
        binding.cardHcMetrics.isVisible = connected
        if (health is LogViewModel.HealthUiState.Connected) {
            val km = String.format("%.1f km", health.steps * 0.0008)
            binding.tvHcSteps.text = getString(R.string.hc_steps, health.steps)
            binding.tvHcCalories.text = getString(R.string.hc_active_kcal, health.activeKcal.toInt())
            binding.tvHcDistance.text = km
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
