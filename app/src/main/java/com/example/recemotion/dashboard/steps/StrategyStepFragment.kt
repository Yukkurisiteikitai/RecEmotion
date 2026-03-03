package com.example.recemotion.dashboard.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.recemotion.dashboard.WizardStepFragment
import com.example.recemotion.databinding.FragmentStepStrategyBinding
import com.example.recemotion.presentation.TaskViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StrategyStepFragment : Fragment(), WizardStepFragment {

    private var _binding: FragmentStepStrategyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepStrategyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun validate(): Boolean {
        val hypo = binding.editHypothesis.text?.toString().orEmpty().trim()
        val outcome = binding.editExpectedOutcome.text?.toString().orEmpty().trim()
        val minutes = binding.editPlannedMinutes.text?.toString().orEmpty().trim()
        if (hypo.isEmpty() || outcome.isEmpty()) {
            Toast.makeText(requireContext(), "仮説と期待成果を入力してください", Toast.LENGTH_SHORT).show()
            return false
        }
        if (minutes.toIntOrNull() == null) {
            Toast.makeText(requireContext(), "計画時間を数字で入力してください", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun save() {
        viewModel.saveStrategy(
            hypothesis = binding.editHypothesis.text?.toString().orEmpty().trim(),
            expectedOutcome = binding.editExpectedOutcome.text?.toString().orEmpty().trim(),
            plannedMinutes = binding.editPlannedMinutes.text?.toString().orEmpty().trim().toIntOrNull() ?: 0
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
