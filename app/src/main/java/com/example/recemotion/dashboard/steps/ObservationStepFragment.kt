package com.example.recemotion.dashboard.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.recemotion.dashboard.WizardStepFragment
import com.example.recemotion.databinding.FragmentStepObservationBinding
import com.example.recemotion.presentation.TaskViewModel
import com.example.recemotion.ui.hapticError
import com.example.recemotion.ui.hapticTick
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ObservationStepFragment : Fragment(), WizardStepFragment {

    private var _binding: FragmentStepObservationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by activityViewModels()
    private var lastSeekFeedbackTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepObservationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.seekImportance.setOnSeekBarChangeListener(valueLabel(binding.textImportanceVal))
        binding.seekUrgency.setOnSeekBarChangeListener(valueLabel(binding.textUrgencyVal))
        binding.seekScope.setOnSeekBarChangeListener(valueLabel(binding.textScopeVal))
    }

    private fun valueLabel(label: android.widget.TextView) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
            label.text = (progress + 1).toString()
            if (fromUser) {
                val now = System.currentTimeMillis()
                if (now - lastSeekFeedbackTime >= 100L) {
                    lastSeekFeedbackTime = now
                    sb?.hapticTick()
                }
            }
        }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    override fun validate(): Boolean {
        if (binding.editTitle.text?.toString().orEmpty().isBlank()) {
            Toast.makeText(requireContext(), "タイトルを入力してください", Toast.LENGTH_SHORT).show()
            binding.editTitle.hapticError()
            return false
        }
        return true
    }

    override fun save() {
        viewModel.createTask(
            title = binding.editTitle.text?.toString().orEmpty().trim(),
            description = binding.editDescription.text?.toString().orEmpty().trim(),
            importance = binding.seekImportance.progress + 1,
            urgency = binding.seekUrgency.progress + 1,
            scope = binding.seekScope.progress + 1
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
