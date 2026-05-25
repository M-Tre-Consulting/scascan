package com.scascan.app.ui.main

import android.view.MotionEvent
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
import com.scascan.app.ui.util.hapticClick
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
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                updateIndicator(position, positionOffset)
            }
            override fun onPageSelected(position: Int) {
                updateNavSelection(position)
            }
        })
    }

    private fun updateIndicator(position: Int, offset: Float) {
        binding.navCard.post {
            val totalWidth = binding.navCard.width
            val itemWidth = totalWidth / 3.0
            
            val indicatorWidth = binding.navIndicator.width.toDouble()
            
            // Linear interpolation of the center position
            val currentCenterX = (itemWidth * position) + (itemWidth / 2.0)
            val nextPosition = (position + 1).coerceAtMost(2)
            val nextCenterX = (itemWidth * nextPosition) + (itemWidth / 2.0)
            
            val targetCenterX = currentCenterX + (nextCenterX - currentCenterX) * offset
            
            // Position the indicator so its center matches targetCenterX
            val targetX = targetCenterX - (indicatorWidth / 2.0)
            binding.navIndicator.translationX = targetX.toFloat()
        }
    }

    private fun setupBottomNav() {
        listOf(
            binding.navHome to 0,
            binding.navLog to 1,
            binding.navProfile to 2
        ).forEach { (view, page) ->
            // Ensure the view is capable of haptics
            view.isHapticFeedbackEnabled = true
            
            view.setOnClickListener { 
                onNavClicked(page) 
            }
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Switch to hapticClick (KEYBOARD_TAP) for more consistent feedback
                        // and call it directly on the view that received the touch.
                        v.hapticClick()
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                    }
                }
                true
            }
        }
        updateNavSelection(0)
        updateIndicator(0, 0f)
    }

    private fun onNavClicked(page: Int) {
        if (binding.viewPager.currentItem == page) return
        binding.viewPager.setCurrentItem(page, true)
    }

    private fun updateNavSelection(position: Int) {
        binding.navCard.post {
            val icons = listOf(binding.iconHome, binding.iconLog, binding.iconProfile)
            
            val activeContentColor = android.graphics.Color.parseColor("#EADDFF")
            val inactiveColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0)

            icons.forEachIndexed { index, icon ->
                val isActive = index == position
                icon.setColorFilter(if (isActive) activeContentColor else inactiveColor)
                
                // Active icon pop animation
                if (isActive) {
                    icon.scaleX = 0.8f
                    icon.scaleY = 0.8f
                    icon.animate()
                        .scaleX(1.2f)
                        .scaleY(1.2f)
                        .setDuration(300)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                        .start()
                } else {
                    icon.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .start()
                }
            }
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
