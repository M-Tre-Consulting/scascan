package com.mtreconsulting.scascan.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mtreconsulting.scascan.ui.home.HomeFragment
import com.mtreconsulting.scascan.ui.log.LogFragment
import com.mtreconsulting.scascan.ui.profile.ProfileFragment

class MainTabsAdapter(fm: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(fm, lifecycle) {
    override fun getItemCount() = 3
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> HomeFragment()
        1 -> LogFragment()
        else -> ProfileFragment()
    }
}
