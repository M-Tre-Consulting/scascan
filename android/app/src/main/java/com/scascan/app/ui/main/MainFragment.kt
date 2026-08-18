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
        setupFixResultListener()
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
        // A plain, mutable GradientDrawable per item (not a state-list selector) so the fill
        // color can be cross-faded by animateNavButton instead of hard-cut on selection change.
        listOf(binding.btnNavHome, binding.btnNavLog, binding.btnNavProfile).forEach { btn ->
            btn.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 24f * resources.displayMetrics.density
                setColor(android.graphics.Color.TRANSPARENT)
            }
        }

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
        val activeColor = com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimary)
        val inactiveColor = com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val activeFill = com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)

        // One explicit, self-contained transition per call (not `animateLayoutChanges`) covers
        // the width/position change as labels appear/disappear. Implicit LayoutTransition could
        // be left mid-animation — with orphaned internal animators — if a page swipe fired the
        // next call before the previous one settled; TransitionManager instead cancels/replaces
        // cleanly on every call, so the pill can never get stuck mid-morph.
        val transition = androidx.transition.AutoTransition().apply {
            duration = NAV_ANIM_DURATION
            interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
        }
        androidx.transition.TransitionManager.beginDelayedTransition(binding.customNav, transition)

        animateNavButton(binding.btnNavHome, binding.ivNavHome, binding.tvNavHomeLabel, position == 0, activeColor, inactiveColor, activeFill)
        animateNavButton(binding.btnNavLog, binding.ivNavLog, binding.tvNavLogLabel, position == 1, activeColor, inactiveColor, activeFill)
        animateNavButton(binding.btnNavProfile, binding.ivNavProfile, binding.tvNavProfileLabel, position == 2, activeColor, inactiveColor, activeFill)
    }

    /**
     * The active tab expands into a filled, labeled capsule (Material 3's expressive nav-bar
     * shape); inactive tabs stay icon-only. Unlike a state-list drawable (an instant hard cut
     * between two fixed drawables), the pill's own fill color is cross-faded via ValueAnimator
     * alongside the icon tint and a small overshoot "pop" on the icon, so selecting a tab reads
     * as one continuous morph rather than a snap.
     */
    private fun animateNavButton(
        container: View,
        iv: android.widget.ImageView,
        label: android.widget.TextView,
        isSelected: Boolean,
        activeColor: Int,
        inactiveColor: Int,
        activeFill: Int
    ) {
        val wasSelected = container.isSelected
        if (wasSelected == isSelected) return
        container.isSelected = isSelected
        label.isVisible = isSelected

        val targetIconColor = if (isSelected) activeColor else inactiveColor
        val currentIconColor = iv.tag as? Int ?: inactiveColor
        iv.tag = targetIconColor

        android.animation.ValueAnimator.ofArgb(currentIconColor, targetIconColor).apply {
            duration = NAV_ANIM_DURATION
            addUpdateListener { iv.setColorFilter(it.animatedValue as Int) }
            start()
        }

        val bg = container.background as android.graphics.drawable.GradientDrawable
        val previousFill = if (wasSelected) activeFill else android.graphics.Color.TRANSPARENT
        val targetFill = if (isSelected) activeFill else android.graphics.Color.TRANSPARENT
        android.animation.ValueAnimator.ofArgb(previousFill, targetFill).apply {
            duration = NAV_ANIM_DURATION
            addUpdateListener { bg.setColor(it.animatedValue as Int) }
            start()
        }

        if (isSelected) {
            iv.scaleX = 0.75f
            iv.scaleY = 0.75f
            iv.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(340L)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.5f))
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

    private fun setupFixResultListener() {
        childFragmentManager.setFragmentResultListener(
            com.scascan.app.ui.log.FixEntryBottomSheetFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val entryId = bundle.getLong(com.scascan.app.ui.log.FixEntryBottomSheetFragment.RESULT_ENTRY_ID)
            val correction = bundle.getString(com.scascan.app.ui.log.FixEntryBottomSheetFragment.RESULT_CORRECTION) ?: return@setFragmentResultListener
            
            // If entryId is -1, it's a pre-log fix
            if (entryId == -1L) {
                val currentFacts = (analysisManager.state.value as? AnalysisManager.State.Complete)?.facts
                if (currentFacts != null) {
                    analysisManager.fixPending(currentFacts, correction)
                }
                // Dismiss the fix sheet
                (childFragmentManager.findFragmentByTag("fix_pending") as? com.scascan.app.ui.log.FixEntryBottomSheetFragment)?.dismiss()
            }
        }
    }

    private fun handleAnalysisAction(action: String, facts: NutritionFacts) {
        when (action) {
            AnalysisResultBottomSheetFragment.ACTION_ADD -> {
                viewLifecycleOwner.lifecycleScope.launch { logRepository.addEntry(facts) }
                analysisManager.dismiss()
            }
            AnalysisResultBottomSheetFragment.ACTION_FIX -> {
                com.scascan.app.ui.log.FixEntryBottomSheetFragment.newInstance(-1L, facts.foodName)
                    .show(childFragmentManager, "fix_pending")
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
        private const val NAV_ANIM_DURATION = 260L
    }
}
