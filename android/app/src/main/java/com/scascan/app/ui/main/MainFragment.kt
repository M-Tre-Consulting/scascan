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

        arguments?.getInt("start_tab")?.let { tab ->
            if (tab != 0) {
                binding.viewPager.post {
                    binding.viewPager.setCurrentItem(tab, false)
                }
            }
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = MainTabsAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)
        binding.viewPager.offscreenPageLimit = 2
        
        // Reduce swipe sensitivity (require more horizontal movement to start swiping)
        reduceSwipeSensitivity()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val menuId = when (position) {
                    0 -> R.id.homeFragment
                    1 -> R.id.logFragment
                    else -> R.id.profileFragment
                }
                if (binding.bottomNav.selectedItemId != menuId) {
                    binding.bottomNav.selectedItemId = menuId
                }
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
        binding.bottomNav.setOnItemSelectedListener { item ->
            val page = when (item.itemId) {
                R.id.homeFragment -> 0
                R.id.logFragment -> 1
                R.id.profileFragment -> 2
                else -> 0
            }
            
            // Trigger haptics on selection
            binding.bottomNav.hapticClick()
            
            if (binding.viewPager.currentItem != page) {
                binding.viewPager.setCurrentItem(page, true)
            }
            true
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
                    // Safety check to avoid IllegalStateException
                    if (isResumed && !childFragmentManager.isStateSaved) {
                        showResultSheet(state.facts)
                    }
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
