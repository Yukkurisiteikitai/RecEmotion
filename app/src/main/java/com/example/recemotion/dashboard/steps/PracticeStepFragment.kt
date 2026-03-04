package com.example.recemotion.dashboard.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.recemotion.dashboard.WizardStepFragment
import com.example.recemotion.databinding.FragmentStepPracticeBinding
import com.example.recemotion.presentation.TaskViewModel
import com.example.recemotion.ui.hapticError
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PracticeStepFragment : Fragment(), WizardStepFragment {

    private var _binding: FragmentStepPracticeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepPracticeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun validate(): Boolean {
        if (binding.editActualMinutes.text?.toString().orEmpty().trim().toIntOrNull() == null) {
            Toast.makeText(requireContext(), "実際の時間を数字で入力してください", Toast.LENGTH_SHORT).show()
            binding.root.hapticError()
            return false
        }
        return true
    }

    override fun save() {
        viewModel.completePractice(
            notes = binding.editPracticeNotes.text?.toString().orEmpty().trim(),
            actualMinutes = binding.editActualMinutes.text?.toString().orEmpty().trim().toIntOrNull() ?: 0
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
