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
import com.scascan.app.R
import com.scascan.app.data.local.LogEntry
import com.scascan.app.databinding.FragmentLogBinding
import com.scascan.app.databinding.ItemLogEntryBinding
import dagger.hilt.android.AndroidEntryPoint
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.todayEntries.collect { entries ->
                renderEntries(entries)
            }
        }
    }

    private fun renderEntries(entries: List<LogEntry>) {
        val target = viewModel.dailyTarget
        val totalCalories = entries.sumOf { it.calories }.toInt()
        val totalProtein = entries.sumOf { it.protein }.toInt()
        val totalCarbs = entries.sumOf { it.carbohydrates }.toInt()
        val totalFat = entries.sumOf { it.fat }.toInt()

        // Calorie summary
        binding.tvCalorieSummary.text = if (entries.isEmpty()) {
            getString(R.string.log_kcal_of, 0, target)
        } else {
            getString(R.string.log_kcal_of, totalCalories, target)
        }
        binding.progressCalories.max = target
        binding.progressCalories.progress = totalCalories.coerceIn(0, target)

        // Macro summary
        val dash = "—"
        binding.tvLogProtein.text = if (entries.isEmpty()) dash else "${totalProtein}g"
        binding.tvLogCarbs.text = if (entries.isEmpty()) dash else "${totalCarbs}g"
        binding.tvLogFat.text = if (entries.isEmpty()) dash else "${totalFat}g"

        // Entry list
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
