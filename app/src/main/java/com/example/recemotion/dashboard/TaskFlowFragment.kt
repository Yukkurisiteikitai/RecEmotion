package com.example.recemotion.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.recemotion.MainActivity
import com.example.recemotion.dashboard.steps.ObservationStepFragment
import com.example.recemotion.dashboard.steps.PracticeStepFragment
import com.example.recemotion.dashboard.steps.RetroStepFragment
import com.example.recemotion.dashboard.steps.StrategyStepFragment
import com.example.recemotion.databinding.FragmentTaskFlowBinding
import com.example.recemotion.presentation.TaskViewModel
import dagger.hilt.android.AndroidEntryPoint

/** Implemented by each step fragment so TaskFlowFragment can drive validation + save. */
interface WizardStepFragment {
    fun validate(): Boolean
    fun save()
}

@AndroidEntryPoint
class TaskFlowFragment : Fragment() {

    private var _binding: FragmentTaskFlowBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskFlowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewPager.adapter = StepAdapter()
        binding.viewPager.isUserInputEnabled = false

        updateUI(0)

        binding.btnBack.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current == 0) {
                (activity as? MainActivity)?.navigateToDashboard()
            } else {
                val prev = current - 1
                binding.viewPager.currentItem = prev
                updateUI(prev)
            }
        }

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            // ViewPager2 with FragmentStateAdapter uses tag "f{position}"
            val stepFragment =
                childFragmentManager.findFragmentByTag("f$current") as? WizardStepFragment
            if (stepFragment == null || !stepFragment.validate()) return@setOnClickListener
            stepFragment.save()
            if (current < 3) {
                val next = current + 1
                binding.viewPager.currentItem = next
                updateUI(next)
            } else {
                (activity as? MainActivity)?.navigateToDashboard()
            }
        }
    }

    private fun updateUI(step: Int) {
        binding.textStepIndicator.text = "${step + 1} / 4"
        binding.progressWizard.progress = step + 1
        binding.btnNext.text = if (step == 3) "完了" else "次へ"
        binding.btnBack.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class StepAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = 4
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> ObservationStepFragment()
            1 -> StrategyStepFragment()
            2 -> PracticeStepFragment()
            3 -> RetroStepFragment()
            else -> ObservationStepFragment()
        }
    }

    companion object {
        const val TAG = "TASK_FLOW"
    }
}
