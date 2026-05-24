package com.scascan.app.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.MaterialColors
import com.scascan.app.R
import com.scascan.app.data.analysis.AnalysisManager
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.data.repository.LogRepository
import com.scascan.app.databinding.FragmentMainBinding
import com.scascan.app.ui.result.AnalysisResultBottomSheetFragment
import com.scascan.app.ui.util.hapticTick
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var analysisManager: AnalysisManager
    @Inject lateinit var logRepository: LogRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupBottomNav()
        setupAnalysisObserver()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = MainTabsAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)
        binding.viewPager.offscreenPageLimit = 2

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavSelection(position)
            }
        })
    }

    private fun setupBottomNav() {
        binding.navHome.setOnClickListener { onNavClicked(0) }
        binding.navLog.setOnClickListener { onNavClicked(1) }
        binding.navProfile.setOnClickListener { onNavClicked(2) }
        updateNavSelection(0)
    }

    private fun onNavClicked(page: Int) {
        binding.navCard.hapticTick()
        binding.viewPager.setCurrentItem(page, true)
    }

    private fun updateNavSelection(position: Int) {
        val containerWidth = 280 // dp
        val itemWidth = containerWidth / 3.0
        val indicatorWidth = 86 // dp
        
        // Convert dp to pixels for the translation
        val density = resources.displayMetrics.density
        val targetX = ((itemWidth * position) + (itemWidth / 2.0) - (indicatorWidth / 2.0)) * density

        binding.navIndicator.animate()
            .translationX(targetX.toFloat())
            .setDuration(250)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        val activeColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnPrimaryContainer, 0)
        val inactiveColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0)

        // Animate icons
        listOf(binding.iconHome, binding.iconLog, binding.iconProfile).forEachIndexed { index, icon ->
            val isActive = index == position
            icon.setColorFilter(if (isActive) activeColor else inactiveColor)
            icon.animate()
                .scaleX(if (isActive) 1.2f else 1.0f)
                .scaleY(if (isActive) 1.2f else 1.0f)
                .setDuration(250)
                .start()
        }
    }

    private fun setupAnalysisObserver() {
        childFragmentManager.setFragmentResultListener(
            AnalysisResultBottomSheetFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val action = bundle.getString(AnalysisResultBottomSheetFragment.KEY_ACTION) ?: return@setFragmentResultListener
            val facts = (analysisManager.state.value as? AnalysisManager.State.Complete)?.facts ?: return@setFragmentResultListener
            handleAnalysisAction(action, facts)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            analysisManager.state.collect { state ->
                binding.analysisProgress.isVisible = state is AnalysisManager.State.Processing
                if (state is AnalysisManager.State.Complete) {
                    showResultSheet(state.facts)
                }
            }
        }
    }

    private fun showResultSheet(facts: NutritionFacts) {
        if (childFragmentManager.findFragmentByTag(RESULT_SHEET_TAG) != null) return
        AnalysisResultBottomSheetFragment.newInstance(facts)
            .show(childFragmentManager, RESULT_SHEET_TAG)
    }

    private fun handleAnalysisAction(action: String, facts: NutritionFacts) {
        when (action) {
            AnalysisResultBottomSheetFragment.ACTION_ADD -> {
                viewLifecycleOwner.lifecycleScope.launch { logRepository.addEntry(facts) }
                analysisManager.dismiss()
            }
            AnalysisResultBottomSheetFragment.ACTION_DETAILS -> {
                analysisManager.dismiss()
                findNavController().navigate(
                    R.id.action_main_to_result,
                    bundleOf("nutritionFacts" to facts)
                )
            }
            AnalysisResultBottomSheetFragment.ACTION_DISMISS -> analysisManager.dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val RESULT_SHEET_TAG = "analysis_result_sheet"
    }
}
