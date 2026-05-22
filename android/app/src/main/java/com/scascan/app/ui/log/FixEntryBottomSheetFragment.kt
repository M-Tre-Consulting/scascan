package com.scascan.app.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.scascan.app.R
import com.scascan.app.databinding.FragmentFixEntryBottomSheetBinding

class FixEntryBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentFixEntryBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFixEntryBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val entryId = requireArguments().getLong(ARG_ENTRY_ID)
        binding.tvCurrentFood.text = requireArguments().getString(ARG_FOOD_NAME, "")

        binding.btnApplyFix.setOnClickListener {
            val correction = binding.etCorrection.text?.toString()?.trim() ?: ""
            if (correction.isBlank()) {
                binding.tilCorrection.error = getString(R.string.fix_entry_error_empty)
                return@setOnClickListener
            }
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                bundleOf(RESULT_ENTRY_ID to entryId, RESULT_CORRECTION to correction)
            )
            dismiss()
        }

        binding.btnCancelFix.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "fix_entry"
        const val RESULT_ENTRY_ID = "entry_id"
        const val RESULT_CORRECTION = "correction"
        private const val ARG_ENTRY_ID = "entry_id"
        private const val ARG_FOOD_NAME = "food_name"

        fun newInstance(entryId: Long, foodName: String) =
            FixEntryBottomSheetFragment().apply {
                arguments = bundleOf(ARG_ENTRY_ID to entryId, ARG_FOOD_NAME to foodName)
            }
    }
}
