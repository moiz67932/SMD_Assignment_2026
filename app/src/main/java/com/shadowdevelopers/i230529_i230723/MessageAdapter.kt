package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/** chats/{chatId}/messages/{messageId} */
data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val edited: Boolean,
    val replyToText: String,
    val system: Boolean,
    /** "text" | "image" | "post" | "system" | "call" */
    val type: String = "text",
    val imageRef: String = "",
    /** type "post": the shared post's id and a small snapshot for the card */
    val sharedPostId: String = "",
    val sharedAuthorName: String = "",
    val sharedSnippet: String = "",
    val sharedImage: String = "",
    val replyToSenderId: String = ""
)

/** What the chat list shows: the intro card, a message (with its display flags) or the typing bubble. */
sealed class ChatRow {
    object Intro : ChatRow()
    object Typing : ChatRow()
    data class Message(
        val message: ChatMessage,
        val dateLabel: String?,
        val showAvatar: Boolean,
        val replyHeader: String,
        val heartMine: Boolean,
        val heartOther: Boolean,
        val seen: Boolean,
        val showTime: Boolean
    ) : ChatRow()
}

class MessageAdapter(
    private val currentUid: String,
    private val onLongPress: (ChatMessage) -> Unit,
    private val onDoubleTap: (ChatMessage) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val rows = mutableListOf<ChatRow>()
    /** The other person (for avatars on their bubbles, the intro card, the typing row and the seen dot). */
    var other: UserProfile = UserProfile("", "")
    var introSubtitle = ""
    private var lastTapId = ""
    private var lastTapAt = 0L

    fun setRows(items: List<ChatRow>) {
        rows.clear()
        rows.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (rows[position]) {
        ChatRow.Intro -> TYPE_INTRO
        ChatRow.Typing -> TYPE_TYPING
        else -> TYPE_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_INTRO -> SimpleHolder(inflater.inflate(R.layout.item_chat_intro, parent, false))
            TYPE_TYPING -> SimpleHolder(inflater.inflate(R.layout.item_chat_typing, parent, false))
            else -> MessageViewHolder(inflater.inflate(R.layout.item_message, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            ChatRow.Intro -> {
                holder.itemView.findViewById<TextView>(R.id.chatIntroName).text = other.name
                holder.itemView.findViewById<TextView>(R.id.chatIntroSubtitle).text = introSubtitle
                AvatarBinder.bind(holder.itemView.findViewById(R.id.chatIntroAvatar), other.name, other.photo)
            }
            ChatRow.Typing -> AvatarBinder.bind(holder.itemView.findViewById(R.id.typingAvatar), other.name, other.photo)
            is ChatRow.Message -> (holder as MessageViewHolder).bind(row)
        }
    }

    override fun getItemCount() = rows.size

    class SimpleHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val date = view.findViewById<TextView>(R.id.messageDate)
        private val system = view.findViewById<TextView>(R.id.messageSystem)
        private val replyHeader = view.findViewById<TextView>(R.id.messageReplyHeader)
        private val bubbleRow = view.findViewById<View>(R.id.messageBubbleRow)
        private val avatar = view.findViewById<TextView>(R.id.messageAvatar)
        private val column = view.findViewById<LinearLayout>(R.id.messageColumn)
        private val quote = view.findViewById<TextView>(R.id.messageReplyText)
        private val text = view.findViewById<TextView>(R.id.messageText)
        private val image = view.findViewById<ImageView>(R.id.messageImage)
        private val postCard = view.findViewById<View>(R.id.messagePostCard)
        private val postImage = view.findViewById<ImageView>(R.id.messagePostImage)
        private val heart = view.findViewById<TextView>(R.id.messageHeart)
        private val status = view.findViewById<TextView>(R.id.messageStatus)
        private val seen = view.findViewById<TextView>(R.id.messageSeen)

        fun bind(row: ChatRow.Message) {
            val message = row.message
            val context = itemView.context
            val mine = message.senderId == currentUid
            date.visibility = if (row.dateLabel == null) View.GONE else View.VISIBLE
            date.text = row.dateLabel.orEmpty()

            // System lines ("Aisha took a screenshot", call summaries) are centred grey text without a bubble.
            val isSystem = message.system || message.type == "system" || message.type == "call"
            system.visibility = if (isSystem) View.VISIBLE else View.GONE
            bubbleRow.visibility = if (isSystem) View.GONE else View.VISIBLE
            replyHeader.visibility = View.GONE
            seen.visibility = View.GONE
            if (isSystem) {
                system.text = message.text
                return
            }

            column.gravity = if (mine) Gravity.END else Gravity.START
            avatar.visibility = when {
                mine -> View.GONE
                row.showAvatar -> View.VISIBLE
                else -> View.INVISIBLE
            }
            if (!mine && row.showAvatar) AvatarBinder.bind(avatar, other.name, other.photo)

            replyHeader.visibility = if (row.replyHeader.isBlank()) View.GONE else View.VISIBLE
            replyHeader.text = row.replyHeader
            replyHeader.gravity = if (mine) Gravity.END else Gravity.START
            quote.visibility = if (message.replyToText.isBlank()) View.GONE else View.VISIBLE
            quote.text = message.replyToText

            text.text = message.text
            text.setBackgroundResource(if (mine) R.drawable.rounded_teal else R.drawable.rounded_soft)
            text.setTextColor(ContextCompat.getColor(context, if (mine) R.color.white else R.color.text_primary))
            val isImage = message.type == "image" && message.imageRef.isNotBlank()
            val isPost = message.type == "post" && message.sharedPostId.isNotBlank()
            text.visibility = if ((isImage || isPost) && message.text.isBlank()) View.GONE else View.VISIBLE
            image.visibility = if (isImage) View.VISIBLE else View.GONE
            if (isImage) {
                MediaLoader.load(image, message.imageRef)
                image.setOnClickListener {
                    if (!isDoubleTap(message)) context.startActivity(Intent(context, MediaViewerActivity::class.java).putStringArrayListExtra("media_refs", arrayListOf(message.imageRef)))
                }
            }
            bindPostCard(message, isPost)

            // Heart badge: mine and/or theirs ("♥ 2" when both), at the inner corner of the bubble.
            val hearts = listOf(row.heartMine, row.heartOther).count { it }
            heart.visibility = if (hearts == 0) View.GONE else View.VISIBLE
            heart.text = if (hearts == 2) "♥ 2" else "♥"
            (heart.layoutParams as RelativeLayout.LayoutParams).apply {
                removeRule(RelativeLayout.ALIGN_PARENT_START)
                removeRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(if (mine) RelativeLayout.ALIGN_PARENT_START else RelativeLayout.ALIGN_PARENT_END)
            }

            val parts = listOfNotNull(if (message.edited) "Edited" else null, if (row.showTime) TimeFormat.clock(message.timestamp) else null)
            status.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
            status.text = parts.joinToString(" · ")

            seen.visibility = if (row.seen) View.VISIBLE else View.GONE
            if (row.seen) AvatarBinder.bind(seen, other.name, other.photo)

            listOf(text, postCard, image, heart).forEach { target ->
                target.setOnLongClickListener { onLongPress(message); true }
            }
            text.setOnClickListener { isDoubleTap(message) }
            heart.setOnClickListener { onDoubleTap(message) }
        }

        /** Two taps on the same message within 300 ms toggle my heart. */
        private fun isDoubleTap(message: ChatMessage): Boolean {
            val now = SystemClock.uptimeMillis()
            val double = lastTapId == message.id && now - lastTapAt < 300
            lastTapId = message.id
            lastTapAt = now
            if (double) onDoubleTap(message)
            return double
        }

        /** Shared post card; tapping it opens that post's Comments (06). */
        private fun bindPostCard(message: ChatMessage, isPost: Boolean) {
            postCard.visibility = if (isPost) View.VISIBLE else View.GONE
            if (!isPost) return
            itemView.findViewById<TextView>(R.id.messagePostAuthor).text = message.sharedAuthorName
            itemView.findViewById<TextView>(R.id.messagePostSnippet).text = message.sharedSnippet.ifBlank { "Photo" }
            postImage.visibility = if (message.sharedImage.isBlank()) View.GONE else View.VISIBLE
            if (message.sharedImage.isNotBlank()) MediaLoader.load(postImage, message.sharedImage)
            postCard.setOnClickListener {
                if (isDoubleTap(message)) return@setOnClickListener
                val context = itemView.context
                context.startActivity(Intent(context, CommentsActivity::class.java).putExtra("post_id", message.sharedPostId))
            }
        }
    }

    companion object {
        private const val TYPE_INTRO = 0
        private const val TYPE_MESSAGE = 1
        private const val TYPE_TYPING = 2
    }
}
