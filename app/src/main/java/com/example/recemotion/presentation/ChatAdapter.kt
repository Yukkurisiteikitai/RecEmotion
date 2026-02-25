package com.example.recemotion.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.recemotion.R
import com.example.recemotion.ui.EmotionCursorDrawable
import io.noties.markwon.Markwon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** チャット画面に表示するアイテム */
sealed class ChatDisplayItem {
    abstract val id: Long

    data class UserMessage(
        override val id: Long,
        val text: String,
        val emotion: String,
        val stressLevel: Int,
        val timestamp: Long
    ) : ChatDisplayItem()

    data class AssistantOutput(
        override val id: Long,
        val markdownText: String,
        val emotion: String,
        val timestamp: Long
    ) : ChatDisplayItem()

    data class TopicDivider(
        override val id: Long,
        val title: String,
        val isResolved: Boolean
    ) : ChatDisplayItem()

    data class SystemNotice(
        override val id: Long,
        val message: String,
        val isError: Boolean = false
    ) : ChatDisplayItem()
}

class ChatAdapter : ListAdapter<ChatDisplayItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
        private const val TYPE_TOPIC = 2
        private const val TYPE_SYSTEM = 3

        private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatDisplayItem.UserMessage    -> TYPE_USER
        is ChatDisplayItem.AssistantOutput -> TYPE_ASSISTANT
        is ChatDisplayItem.TopicDivider   -> TYPE_TOPIC
        is ChatDisplayItem.SystemNotice   -> TYPE_SYSTEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER      -> UserMessageViewHolder(inflater.inflate(R.layout.item_chat_message, parent, false))
            TYPE_ASSISTANT -> AssistantOutputViewHolder(inflater.inflate(R.layout.item_chat_output, parent, false))
            TYPE_TOPIC     -> TopicDividerViewHolder(inflater.inflate(R.layout.item_topic_header, parent, false))
            else           -> SystemNoticeViewHolder(inflater.inflate(R.layout.item_system_message, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatDisplayItem.UserMessage    -> (holder as UserMessageViewHolder).bind(item)
            is ChatDisplayItem.AssistantOutput -> (holder as AssistantOutputViewHolder).bind(item)
            is ChatDisplayItem.TopicDivider   -> (holder as TopicDividerViewHolder).bind(item)
            is ChatDisplayItem.SystemNotice   -> (holder as SystemNoticeViewHolder).bind(item)
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────

    class UserMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val emotionBar: View = view.findViewById(R.id.emotionBar)
        private val txtInput: TextView = view.findViewById(R.id.txtChatInput)
        private val txtTimestamp: TextView = view.findViewById(R.id.txtChatTimestamp)

        fun bind(item: ChatDisplayItem.UserMessage) {
            txtInput.text = item.text
            txtTimestamp.text = TIME_FORMAT.format(Date(item.timestamp))
            val color = EmotionCursorDrawable.emotionToColor(item.emotion)
            val alpha = 128 + ((item.stressLevel.coerceIn(1, 5) - 1) * 127 / 4)
            emotionBar.setBackgroundColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)))
        }
    }

    class AssistantOutputViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val emotionBarOutput: View = view.findViewById(R.id.emotionBarOutput)
        private val txtOutput: TextView = view.findViewById(R.id.txtChatOutput)
        private val btnCopy: ImageButton = view.findViewById(R.id.btnCopyOutput)
        private val markwon = Markwon.create(view.context)
        private var currentItem: ChatDisplayItem.AssistantOutput? = null

        init {
            btnCopy.setOnClickListener { view ->
                val item = currentItem ?: return@setOnClickListener
                val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("chat output", item.markdownText))
                Toast.makeText(view.context, "コピーしました", Toast.LENGTH_SHORT).show()
            }
        }

        fun bind(item: ChatDisplayItem.AssistantOutput) {
            currentItem = item
            markwon.setMarkdown(txtOutput, item.markdownText)
            val color = EmotionCursorDrawable.emotionToColor(item.emotion)
            emotionBarOutput.setBackgroundColor(color)
        }
    }

    class TopicDividerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtTitle: TextView = view.findViewById(R.id.txtTopicTitle)
        private val btnResolve: View = view.findViewById(R.id.btnResolve)
        private val txtResolved: View = view.findViewById(R.id.txtResolvedStatus)

        fun bind(item: ChatDisplayItem.TopicDivider) {
            txtTitle.text = item.title
            if (item.isResolved) {
                btnResolve.visibility = View.GONE
                txtResolved.visibility = View.VISIBLE
            } else {
                btnResolve.visibility = View.GONE  // ChatFragment では非表示（タイトル表示のみ）
                txtResolved.visibility = View.GONE
            }
        }
    }

    class SystemNoticeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtMessage: TextView = view.findViewById(R.id.txtSystemMessage)

        fun bind(item: ChatDisplayItem.SystemNotice) {
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

    object DiffCallback : DiffUtil.ItemCallback<ChatDisplayItem>() {
        override fun areItemsTheSame(oldItem: ChatDisplayItem, newItem: ChatDisplayItem): Boolean {
            return oldItem::class == newItem::class && oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: ChatDisplayItem, newItem: ChatDisplayItem): Boolean {
            return oldItem == newItem
        }
    }
}
