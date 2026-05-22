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
    }

    private fun setupDateNavigation() {
        binding.btnPrevDay.setOnClickListener { viewModel.goToPreviousDay() }
        binding.btnNextDay.setOnClickListener { viewModel.goToNextDay() }

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
            combine(viewModel.logEntries, viewModel.healthState) { entries, health ->
                entries to health
            }.collect { (entries, health) ->
                renderScreen(entries, health)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fixState.collect { state ->
                when (state) {
                    is LogViewModel.FixState.Idle -> Unit
                    is LogViewModel.FixState.Success -> {
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

    private fun renderScreen(entries: List<LogEntry>, health: LogViewModel.HealthUiState) {
        val base = viewModel.dailyTarget
        val activeKcal = (health as? LogViewModel.HealthUiState.Connected)?.activeKcal?.toInt() ?: 0
        val target = base + activeKcal

        renderEntries(entries, target)
        renderHealthMetrics(health)
    }

    private fun renderEntries(entries: List<LogEntry>, target: Int) {
        val totalCalories = entries.sumOf { it.calories }.toInt()
        val totalProtein = entries.sumOf { it.protein }.toInt()
        val totalCarbs = entries.sumOf { it.carbohydrates }.toInt()
        val totalFat = entries.sumOf { it.fat }.toInt()

        binding.tvCalorieSummary.text = getString(R.string.log_kcal_of, totalCalories, target)
        binding.progressCalories.max = target
        binding.progressCalories.progress = totalCalories.coerceIn(0, target)

        val dash = "—"
        binding.tvLogProtein.text = if (entries.isEmpty()) dash else "${totalProtein}g"
        binding.tvLogCarbs.text = if (entries.isEmpty()) dash else "${totalCarbs}g"
        binding.tvLogFat.text = if (entries.isEmpty()) dash else "${totalFat}g"

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
                FixEntryBottomSheetFragment.newInstance(entry.id, entry.foodName)
                    .show(childFragmentManager, "fix_entry")
            }
            item.btnRemove.setOnClickListener { viewModel.deleteEntry(entry) }

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
