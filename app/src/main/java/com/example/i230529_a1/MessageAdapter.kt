package com.example.i230529_a1

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.Date

data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val edited: Boolean,
    val replyToText: String,
    val system: Boolean
)

class MessageAdapter(
    private val currentUid: String,
    private val onReply: (ChatMessage) -> Unit,
    private val onHeart: (ChatMessage) -> Unit,
    private val onOptions: (ChatMessage) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    private val messages = mutableListOf<ChatMessage>()
    private val likedMessageIds = mutableSetOf<String>()

    fun setMessages(items: List<ChatMessage>) {
        messages.clear()
        messages.addAll(items)
        notifyDataSetChanged()
    }

    fun setHeartState(messageId: String, liked: Boolean) {
        if (liked) likedMessageIds.add(messageId) else likedMessageIds.remove(messageId)
        val index = messages.indexOfFirst { it.id == messageId }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return MessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) = holder.bind(messages[position])
    override fun getItemCount() = messages.size

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val row = view.findViewById<LinearLayout>(R.id.messageRow)
        private val text = view.findViewById<TextView>(R.id.messageText)
        private val reply = view.findViewById<TextView>(R.id.messageReplyText)
        private val time = view.findViewById<TextView>(R.id.messageTime)
        private val edited = view.findViewById<TextView>(R.id.messageEdited)
        private val heart = view.findViewById<TextView>(R.id.messageHeart)

        fun bind(message: ChatMessage) {
            val mine = message.senderId == currentUid
            row.gravity = if (message.system) Gravity.CENTER else if (mine) Gravity.END else Gravity.START
            text.text = message.text
            text.setBackgroundResource(if (mine) R.drawable.rounded_teal else R.drawable.rounded_soft)
            text.setTextColor(if (mine) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
            reply.visibility = if (message.replyToText.isBlank()) View.GONE else View.VISIBLE
            reply.text = "Replying to: ${message.replyToText}"
            time.text = if (message.timestamp > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp)) else "Sending"
            edited.visibility = if (message.edited) View.VISIBLE else View.GONE
            edited.text = "Edited"
            heart.visibility = if (message.system) View.GONE else View.VISIBLE
            heart.text = if (message.id in likedMessageIds) "♥" else "♡"
            heart.setOnClickListener { onHeart(message) }
            text.setOnClickListener { onReply(message) }
            text.setOnLongClickListener {
                if (mine && !message.system && System.currentTimeMillis() - message.timestamp < 300000) onOptions(message)
                true
            }
        }
    }
}
