package com.example.recemotion.dashboard.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.recemotion.dashboard.WizardStepFragment
import com.example.recemotion.databinding.FragmentStepRetroBinding
import com.example.recemotion.presentation.TaskViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RetroStepFragment : Fragment(), WizardStepFragment {

    private var _binding: FragmentStepRetroBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepRetroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun validate(): Boolean {
        if (binding.editActualOutcome.text?.toString().orEmpty().isBlank()) {
            Toast.makeText(requireContext(), "実際の成果を入力してください", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun save() {
        viewModel.completeTask(
            actualOutcome = binding.editActualOutcome.text?.toString().orEmpty().trim(),
            gapAnalysis = binding.editGapAnalysis.text?.toString().orEmpty().trim()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
