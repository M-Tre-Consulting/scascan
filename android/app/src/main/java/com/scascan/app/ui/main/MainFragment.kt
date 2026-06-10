package com.scascan.app.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.scascan.app.R
import com.scascan.app.data.analysis.AnalysisManager
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.data.repository.LogRepository
import com.scascan.app.databinding.FragmentMainBinding
import com.scascan.app.ui.result.AnalysisResultBottomSheetFragment
import com.scascan.app.ui.util.hapticClick
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

    override fun onResume() {
        super.onResume()
        checkPendingActions()
    }

    private fun checkPendingActions() {
        val args = arguments ?: return
        
        if (args.containsKey("start_tab")) {
            val tab = args.getInt("start_tab")
            if (tab != binding.viewPager.currentItem) {
                binding.viewPager.post {
                    binding.viewPager.setCurrentItem(tab, false)
                }
            }
            args.remove("start_tab")
        }

        if (args.containsKey("pending_facts")) {
            @Suppress("DEPRECATION")
            args.getParcelable<NutritionFacts>("pending_facts")?.let { facts ->
                binding.root.post {
                    showResultSheet(facts)
                }
            }
            args.remove("pending_facts")
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = MainTabsAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)
        binding.viewPager.offscreenPageLimit = 2
        
        // Use default PageTransformer for a clean, Google-standard horizontal slide.
        
        // Reduce swipe sensitivity (require more horizontal movement to start swiping)
        reduceSwipeSensitivity()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavIcons(position)
            }
        })
    }

    private fun reduceSwipeSensitivity() {
        try {
            val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView ?: return
            val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
            touchSlopField.isAccessible = true
            val currentTouchSlop = touchSlopField.get(recyclerView) as Int
            // Increase the slop - higher value means lower sensitivity (requires more movement to trigger)
            touchSlopField.set(recyclerView, (currentTouchSlop * 4)) 
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBottomNav() {
        binding.btnNavHome.setOnClickListener { it.hapticClick(); updateSelection(0) }
        binding.btnNavLog.setOnClickListener { it.hapticClick(); updateSelection(1) }
        binding.btnNavProfile.setOnClickListener { it.hapticClick(); updateSelection(2) }
        
        // Initial state
        updateNavIcons(0)
    }

    private fun updateSelection(position: Int) {
        if (binding.viewPager.currentItem != position) {
            binding.viewPager.setCurrentItem(position, true)
        }
    }

    private fun updateNavIcons(position: Int) {
        val activeColor = com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        val inactiveColor = com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)

        animateNavButton(binding.bgNavHome, binding.ivNavHome, position == 0, activeColor, inactiveColor)
        animateNavButton(binding.bgNavLog, binding.ivNavLog, position == 1, activeColor, inactiveColor)
        animateNavButton(binding.bgNavProfile, binding.ivNavProfile, position == 2, activeColor, inactiveColor)
    }

    private fun animateNavButton(bg: View, iv: android.widget.ImageView, isSelected: Boolean, activeColor: Int, inactiveColor: Int) {
        val duration = 350L
        val targetAlpha = if (isSelected) 1f else 0f
        val targetScale = if (isSelected) 1f else 0.4f
        val targetColor = if (isSelected) activeColor else inactiveColor

        // Pixel-like spring animation for the indicator
        bg.animate()
            .alpha(targetAlpha)
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(duration)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .start()

        // Icon color transition
        val currentColor = iv.tag as? Int ?: inactiveColor
        iv.tag = targetColor
        
        android.animation.ValueAnimator.ofArgb(currentColor, targetColor).apply {
            setDuration(duration)
            addUpdateListener { iv.setColorFilter(it.animatedValue as Int) }
            start()
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

                when (state) {
                    is AnalysisManager.State.Complete -> {
                        // Safety check to avoid IllegalStateException
                        if (isResumed && !childFragmentManager.isStateSaved) {
                            showResultSheet(state.facts)
                        }
                    }
                    is AnalysisManager.State.Error -> {
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            state.message,
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).show()
                        analysisManager.dismiss()
                    }
                    else -> {}
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
