package com.example.recemotion.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recemotion.MainActivity
import com.example.recemotion.databinding.FragmentDashboardBinding
import com.example.recemotion.presentation.TaskViewModel
import com.example.recemotion.ui.feedbackProgress
import com.example.recemotion.ui.hapticTick
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskViewModel by activityViewModels()
    private var isDoneExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activeAdapter = TaskAdapter { /* future: open task detail */ }
        binding.recyclerTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTasks.adapter = activeAdapter

        val doneAdapter = TaskAdapter { /* future: open task detail */ }
        binding.recyclerDoneTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDoneTasks.adapter = doneAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.activeTasks.collect { tasks ->
                        activeAdapter.submitList(tasks)
                        binding.textEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.doneTasks.collect { tasks ->
                        doneAdapter.submitList(tasks)
                        val hasDone = tasks.isNotEmpty()
                        binding.sectionDoneHeader.visibility = if (hasDone) View.VISIBLE else View.GONE
                        binding.textDoneCount.text = "完了済み (${tasks.size}件)"
                        if (!hasDone) {
                            binding.recyclerDoneTasks.visibility = View.GONE
                            isDoneExpanded = false
                            updateExpandIcon()
                        }
                    }
                }
            }
        }

        binding.sectionDoneHeader.setOnClickListener {
            it.hapticTick()
            isDoneExpanded = !isDoneExpanded
            binding.recyclerDoneTasks.visibility = if (isDoneExpanded) View.VISIBLE else View.GONE
            updateExpandIcon()
        }

        binding.fabNewTask.setOnClickListener {
            it.feedbackProgress()
            viewModel.resetWizard()
            (activity as? MainActivity)?.navigateToTaskFlow()
        }
    }

    private fun updateExpandIcon() {
        val rotation = if (isDoneExpanded) 180f else 0f
        binding.iconDoneExpand.rotation = rotation
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DASHBOARD"
    }
}
