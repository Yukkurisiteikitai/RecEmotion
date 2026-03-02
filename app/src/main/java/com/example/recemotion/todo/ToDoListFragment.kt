package com.example.recemotion.todo

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recemotion.R
import com.example.recemotion.domain.model.ToDo
import com.example.recemotion.notification.ReminderScheduler
import com.example.recemotion.presentation.ThoughtAnalysisViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class ToDoListFragment : Fragment() {

    private val viewModel: ThoughtAnalysisViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_todo_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewTodo)
        val textEmpty = view.findViewById<TextView>(R.id.textEmpty)
        val adapter = ToDoAdapter(
            onChecked = { todo, checked -> viewModel.toggleToDo(todo.id, checked) },
            onRemind = { todo -> showTimePicker(todo) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allTodos.collect { todos ->
                    adapter.submitList(todos)
                    textEmpty.visibility = if (todos.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showTimePicker(todo: ToDo) {
        val cal = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                val trigger = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                ReminderScheduler.schedule(
                    requireContext(),
                    todoId = todo.id,
                    title = "リマインド",
                    description = todo.description,
                    triggerAtMillis = trigger.timeInMillis
                )
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    companion object {
        const val TAG = "TODO"
    }
}

private class ToDoAdapter(
    private val onChecked: (ToDo, Boolean) -> Unit,
    private val onRemind: (ToDo) -> Unit
) : RecyclerView.Adapter<ToDoAdapter.ViewHolder>() {

    private var items: List<ToDo> = emptyList()

    fun submitList(list: List<ToDo>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val checkBox: CheckBox = view.findViewById(R.id.checkBoxTodo)
        private val textDescription: TextView = view.findViewById(R.id.textDescription)
        private val btnRemind: Button = view.findViewById(R.id.btnRemind)

        fun bind(todo: ToDo) {
            checkBox.isChecked = todo.isCompleted
            textDescription.text = todo.description
            checkBox.setOnCheckedChangeListener { _, checked -> onChecked(todo, checked) }
            btnRemind.setOnClickListener { onRemind(todo) }
        }
    }
}
