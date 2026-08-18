package com.mtreconsulting.scascan.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mtreconsulting.scascan.R
import com.mtreconsulting.scascan.databinding.FragmentHomeBinding
import com.mtreconsulting.scascan.ui.util.addPressScale
import com.mtreconsulting.scascan.ui.util.hapticClick
import com.mtreconsulting.scascan.ui.util.staggerChildrenIn
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.root.updatePadding(top = statusBar)
            insets
        }

        binding.cardCamera.setOnClickListener {
            it.hapticClick()
            findNavController().navigate(R.id.action_main_to_camera)
        }
        binding.cardBarcode.setOnClickListener {
            it.hapticClick()
            findNavController().navigate(R.id.action_main_to_barcode)
        }
        binding.cardSearch.setOnClickListener {
            it.hapticClick()
            findNavController().navigate(R.id.action_main_to_search)
        }
        binding.cardVoice.setOnClickListener {
            it.hapticClick()
            findNavController().navigate(R.id.action_main_to_voice)
        }

        listOf(binding.cardCamera, binding.cardBarcode, binding.cardSearch, binding.cardVoice)
            .forEach { it.addPressScale() }

        binding.contentContainer.staggerChildrenIn()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
