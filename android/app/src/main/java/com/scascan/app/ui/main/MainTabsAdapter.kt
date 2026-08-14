package com.scascan.app.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.scascan.app.ui.home.HomeFragment
import com.scascan.app.ui.log.LogFragment
import com.scascan.app.ui.profile.ProfileFragment

class MainTabsAdapter(fm: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(fm, lifecycle) {
    override fun getItemCount() = 3
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> HomeFragment()
        1 -> LogFragment()
        else -> ProfileFragment()
    }
}
