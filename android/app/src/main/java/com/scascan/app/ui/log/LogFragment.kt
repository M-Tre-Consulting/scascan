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
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.scascan.app.R
import com.scascan.app.data.health.HealthConnectManager
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

    private val requestHcPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(HealthConnectManager.PERMISSIONS)) {
            viewModel.loadHealthData()
        }
    }

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

        binding.btnConnectHealth.setOnClickListener {
            requestHcPermissions.launch(HealthConnectManager.PERMISSIONS)
        }

        viewModel.loadHealthData()

        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.todayEntries, viewModel.healthState) { entries, health ->
                entries to health
            }.collect { (entries, health) ->
                renderScreen(entries, health)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadHealthData()
    }

    private fun renderScreen(entries: List<LogEntry>, health: LogViewModel.HealthUiState) {
        val base = viewModel.dailyTarget
        val activeKcal = (health as? LogViewModel.HealthUiState.Connected)?.activeKcal?.toInt() ?: 0
        val target = base + activeKcal

        renderEntries(entries, target)
        renderHealthCard(health)
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
            item.btnRemove.setOnClickListener { viewModel.deleteEntry(entry) }
        }
    }

    private fun renderHealthCard(health: LogViewModel.HealthUiState) {
        when (health) {
            is LogViewModel.HealthUiState.Unavailable -> {
                binding.chipHcStatus.isVisible = true
                binding.chipHcStatus.text = getString(R.string.coming_soon)
                binding.btnConnectHealth.isVisible = false
                binding.hcDataRow.isVisible = false
            }
            is LogViewModel.HealthUiState.Disconnected -> {
                binding.chipHcStatus.isVisible = false
                binding.btnConnectHealth.isVisible = true
                binding.hcDataRow.isVisible = false
            }
            is LogViewModel.HealthUiState.Connected -> {
                binding.chipHcStatus.isVisible = true
                binding.chipHcStatus.text = getString(R.string.hc_connected)
                binding.btnConnectHealth.isVisible = false
                binding.hcDataRow.isVisible = true
                binding.tvHcSteps.text = getString(R.string.hc_steps, health.steps)
                binding.tvHcCalories.text = getString(R.string.hc_active_kcal, health.activeKcal.toInt())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
