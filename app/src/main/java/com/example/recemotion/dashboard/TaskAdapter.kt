package com.example.recemotion.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.recemotion.R
import com.example.recemotion.data.db.TaskEntity

class TaskAdapter(
    private val onClick: (TaskEntity) -> Unit
) : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

    private var items: List<TaskEntity> = emptyList()

    fun submitList(list: List<TaskEntity>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textTitle: TextView = view.findViewById(R.id.textTitle)
        private val progressPhase: ProgressBar = view.findViewById(R.id.progressPhase)
        private val textPhase: TextView = view.findViewById(R.id.textPhase)
        private val badgeImportance: TextView = view.findViewById(R.id.badgeImportance)
        private val badgeUrgency: TextView = view.findViewById(R.id.badgeUrgency)

        fun bind(task: TaskEntity) {
            textTitle.text = task.title
            progressPhase.progress = phaseToProgress(task.currentPhase, task.status)
            textPhase.text = phaseLabel(task.currentPhase, task.status)
            badgeImportance.text = "重要${task.importance}"
            badgeUrgency.text = "緊急${task.urgency}"
            itemView.setOnClickListener { onClick(task) }
        }

        private fun phaseToProgress(phase: String, status: String): Int {
            if (status == "DONE") return 100
            return when (phase) {
                "OBSERVATION" -> 25
                "STRATEGY" -> 50
                "PRACTICE" -> 75
                "RETRO" -> 100
                else -> 0
            }
        }

        private fun phaseLabel(phase: String, status: String): String {
            if (status == "DONE") return "完了 ✓"
            return when (phase) {
                "OBSERVATION" -> "観察中 (1/4)"
                "STRATEGY" -> "方針決定中 (2/4)"
                "PRACTICE" -> "実践中 (3/4)"
                "RETRO" -> "振り返り中 (4/4)"
                else -> phase
            }
        }
    }
}
