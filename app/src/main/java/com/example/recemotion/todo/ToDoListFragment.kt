package com.example.recemotion.todo

import android.Manifest
import android.app.AlertDialog
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    private var isPermissionDialogShowing = false

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.util.Log.d(TAG, "POST_NOTIFICATIONS permission denied")
            }
        }

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

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms() && !isPermissionDialogShowing) {
                isPermissionDialogShowing = true
                AlertDialog.Builder(requireContext())
                    .setTitle("正確なアラームの許可が必要です")
                    .setMessage("リマインダーを指定した時刻に通知するには、正確なアラームの許可が必要です。設定から許可してください。")
                    .setPositiveButton("設定を開く") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${requireContext().packageName}")
                        })
                    }
                    .setNegativeButton("あとで", null)
                    .setOnDismissListener { isPermissionDialogShowing = false }
                    .show()
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
