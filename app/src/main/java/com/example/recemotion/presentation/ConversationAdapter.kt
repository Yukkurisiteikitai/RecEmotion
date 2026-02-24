package com.example.recemotion.presentation

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.recemotion.R

class ConversationAdapter(
    private val onGenerateToDo: (ConversationDisplayItem.ThoughtAnalysis) -> Unit,
    private val onToggleToDo: (Long, Boolean) -> Unit,
    private val onResolveTopic: (Long) -> Unit
) : ListAdapter<ConversationDisplayItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_TOPIC = 0
        private const val TYPE_THOUGHT = 1
        private const val TYPE_SYSTEM = 2
        private const val TYPE_TODO = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ConversationDisplayItem.TopicHeader -> TYPE_TOPIC
            is ConversationDisplayItem.ThoughtAnalysis -> TYPE_THOUGHT
            is ConversationDisplayItem.SystemMessage -> TYPE_SYSTEM
            is ConversationDisplayItem.ToDoItem -> TYPE_TODO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TOPIC -> TopicViewHolder(
                inflater.inflate(R.layout.item_topic_header, parent, false),
                onResolveTopic
            )
            TYPE_SYSTEM -> SystemViewHolder(inflater.inflate(R.layout.item_system_message, parent, false))
            TYPE_TODO -> ToDoViewHolder(
                inflater.inflate(R.layout.item_todo, parent, false),
                onToggleToDo
            )
            else -> ThoughtViewHolder(
                inflater.inflate(R.layout.item_thought_analysis, parent, false),
                onGenerateToDo
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when {
            holder is TopicViewHolder && item is ConversationDisplayItem.TopicHeader -> holder.bind(item)
            holder is ThoughtViewHolder && item is ConversationDisplayItem.ThoughtAnalysis -> holder.bind(item)
            holder is SystemViewHolder && item is ConversationDisplayItem.SystemMessage -> holder.bind(item)
            holder is ToDoViewHolder && item is ConversationDisplayItem.ToDoItem -> holder.bind(item)
        }
    }

    class TopicViewHolder(view: View, private val onResolve: (Long) -> Unit) : RecyclerView.ViewHolder(view) {
        private val txtTitle: TextView = view.findViewById(R.id.txtTopicTitle)
        private val btnResolve: View = view.findViewById(R.id.btnResolve)
        private val txtResolvedStatus: View = view.findViewById(R.id.txtResolvedStatus)

        fun bind(item: ConversationDisplayItem.TopicHeader) {
            txtTitle.text = item.title
            if (item.isResolved) {
                btnResolve.visibility = View.GONE
                txtResolvedStatus.visibility = View.VISIBLE
            } else {
                btnResolve.visibility = View.VISIBLE
                txtResolvedStatus.visibility = View.GONE
                btnResolve.setOnClickListener { onResolve(item.id) }
            }
        }
    }

    class ToDoViewHolder(view: View, private val onToggle: (Long, Boolean) -> Unit) : RecyclerView.ViewHolder(view) {
        private val check: CheckBox = view.findViewById(R.id.checkTodo)
        private val txtDesc: TextView = view.findViewById(R.id.txtTodoDescription)

        fun bind(item: ConversationDisplayItem.ToDoItem) {
            txtDesc.text = item.description
            check.setOnCheckedChangeListener(null)
            check.isChecked = item.isCompleted
            check.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item.id, isChecked)
            }
        }
    }

    class SystemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtMessage: TextView = view.findViewById(R.id.txtSystemMessage)
        fun bind(item: ConversationDisplayItem.SystemMessage) {
            txtMessage.text = item.message
            if (item.isError) {
                txtMessage.setTextColor(Color.parseColor("#FF5252"))
                txtMessage.setBackgroundColor(Color.parseColor("#44FF5252"))
            } else {
                txtMessage.setTextColor(Color.WHITE)
                txtMessage.setBackgroundColor(Color.parseColor("#33FFFFFF"))
            }
        }
    }

    class ThoughtViewHolder(
        view: View,
        private val onGenerateToDo: (ConversationDisplayItem.ThoughtAnalysis) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val txtRaw: TextView = view.findViewById(R.id.txtRawText)
        private val txtEmotions: TextView = view.findViewById(R.id.txtEmotions)
        private val txtBiases: TextView = view.findViewById(R.id.txtBiases)
        private val txtStatedFacts: TextView = view.findViewById(R.id.txtStatedFacts)
        private val txtAssumptions: TextView = view.findViewById(R.id.txtAssumptions)
        private val btnGenerateToDo: View = view.findViewById(R.id.btnGenerateToDo)

        fun bind(item: ConversationDisplayItem.ThoughtAnalysis) {
            txtRaw.text = item.rawText
            val result = item.result
            if (result != null) {
                txtEmotions.text = result.emotions.joinToString(", ").ifEmpty { "---" }
                txtBiases.text = result.possibleBiases.joinToString(", ") { it.name }.ifEmpty { "None" }
                
                txtStatedFacts.text = result.statedFacts.joinToString("\n• ", prefix = "• ").ifEmpty { "---" }
                
                val assumptionText = result.assumptions.joinToString("\n") { 
                    "• ${it.text} (Priority: ${it.importance}/5)"
                }
                txtAssumptions.text = assumptionText.ifEmpty { "---" }
                
                btnGenerateToDo.visibility = if (result.assumptions.isNotEmpty()) View.VISIBLE else View.GONE
                btnGenerateToDo.setOnClickListener { onGenerateToDo(item) }
            } else {
                txtEmotions.text = "Analyzing..."
                txtBiases.text = "---"
                txtStatedFacts.text = "---"
                txtAssumptions.text = "---"
                btnGenerateToDo.visibility = View.GONE
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ConversationDisplayItem>() {
        override fun areItemsTheSame(oldItem: ConversationDisplayItem, newItem: ConversationDisplayItem): Boolean {
            return when {
                oldItem is ConversationDisplayItem.TopicHeader && newItem is ConversationDisplayItem.TopicHeader -> oldItem.id == newItem.id
                oldItem is ConversationDisplayItem.ThoughtAnalysis && newItem is ConversationDisplayItem.ThoughtAnalysis -> oldItem.id == newItem.id
                oldItem is ConversationDisplayItem.SystemMessage && newItem is ConversationDisplayItem.SystemMessage -> oldItem.id == newItem.id
                oldItem is ConversationDisplayItem.ToDoItem && newItem is ConversationDisplayItem.ToDoItem -> oldItem.id == newItem.id
                else -> false
            }
        }
        override fun areContentsTheSame(oldItem: ConversationDisplayItem, newItem: ConversationDisplayItem): Boolean {
            return oldItem == newItem
        }
    }
}
