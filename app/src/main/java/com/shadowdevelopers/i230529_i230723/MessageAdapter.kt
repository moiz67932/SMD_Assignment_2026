package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.graphics.drawable.GradientDrawable
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
    val replyToSenderId: String = "",
    /** type "video": a short clip; type "audio": a voice message and its length in seconds */
    val videoRef: String = "",
    val audioRef: String = "",
    val durationSec: Int = 0,
    /** type "file": any document (PDF, ZIP...); text holds its name */
    val fileRef: String = ""
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
        val showTime: Boolean,
        /** First / last bubble of a run from the same person: decides which corners are rounded and the spacing. */
        val firstInRun: Boolean = true,
        val lastInRun: Boolean = true
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
    /** My messages (or edits) still queued offline: shown with "Pending" under the bubble. */
    var pendingIds: Set<String> = emptySet()
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
        private val audioBubble = view.findViewById<View>(R.id.messageAudio)
        private val audioPlay = view.findViewById<ImageView>(R.id.messageAudioPlay)
        private val audioLabel = view.findViewById<TextView>(R.id.messageAudioLabel)
        private val postCard = view.findViewById<View>(R.id.messagePostCard)
        private val postImage = view.findViewById<ImageView>(R.id.messagePostImage)
        private val heart = view.findViewById<TextView>(R.id.messageHeart)
        private val status = view.findViewById<TextView>(R.id.messageStatus)
        private val seen = view.findViewById<TextView>(R.id.messageSeen)

        private fun dp(value: Int) = (value * itemView.resources.displayMetrics.density).toInt()

        /**
         * Bubble look: 18dp corners, but the corners that touch the next/previous bubble of the same person are
         * only 4dp so a run reads as one group. Padding is set here (the shapes carry none), spacing is larger
         * between different people than inside a run, and a bubble never grows wider than about 75% of the screen.
         */
        private fun styleBubble(row: ChatRow.Message, mine: Boolean) {
            val context = itemView.context
            text.background = bubbleShape(context, mine, row.firstInRun, row.lastInRun)
            text.setPadding(dp(14), dp(9), dp(14), dp(10))
            text.setTextColor(ContextCompat.getColor(context, if (mine) R.color.white else R.color.text_primary))
            text.textSize = 15f
            text.setLineSpacing(0f, 1.08f)
            text.maxWidth = (itemView.resources.displayMetrics.widthPixels * 0.74f).toInt()
            // Gap above this bubble: small inside a run, larger when the sender changes.
            itemView.setPadding(dp(4), if (row.firstInRun) dp(8) else dp(2), dp(4), 0)
            // Photos and videos get rounded corners that match the bubbles.
            image.background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.surface_soft))
                cornerRadius = dp(16).toFloat()
            }
            image.clipToOutline = true
        }

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
            styleBubble(row, mine)
            val isImage = message.type == "image" && message.imageRef.isNotBlank()
            val isVideo = message.type == "video" && message.videoRef.isNotBlank()
            val isAudio = message.type == "audio" && message.audioRef.isNotBlank()
            val isPost = message.type == "post" && message.sharedPostId.isNotBlank()
            // The text bubble is only a caption for media; it stays hidden when there is no caption.
            text.visibility = if ((isImage || isVideo || isAudio || isPost) && message.text.isBlank()) View.GONE else View.VISIBLE
            image.visibility = if (isImage || isVideo) View.VISIBLE else View.GONE
            image.foreground = if (isVideo) ContextCompat.getDrawable(context, R.drawable.video_play_overlay) else null
            if (isImage) {
                MediaLoader.load(image, message.imageRef)
                image.setOnClickListener {
                    if (!isDoubleTap(message)) context.startActivity(Intent(context, MediaViewerActivity::class.java).putStringArrayListExtra("media_refs", arrayListOf(message.imageRef)))
                }
            } else if (isVideo) {
                MediaLoader.loadVideoThumbnail(image, message.videoRef)
                image.setOnClickListener {
                    if (!isDoubleTap(message)) context.startActivity(Intent(context, MediaViewerActivity::class.java)
                        .putStringArrayListExtra("media_refs", arrayListOf(message.videoRef))
                        .putStringArrayListExtra("media_types", arrayListOf(MediaUploader.VIDEO)))
                }
            }
            // File message: "📄 report.pdf"; tap to open it with an app on the phone (downloaded once, then offline too).
            if (message.type == "file" && message.fileRef.isNotBlank()) {
                text.visibility = View.VISIBLE
                text.text = "📄 ${message.text.ifBlank { "File" }}"
                text.setOnClickListener {
                    MediaLoader.resolveModel(message.fileRef) { model ->
                        val file = (model as? java.io.File) ?: android.net.Uri.parse(model?.toString() ?: return@resolveModel).path?.let { java.io.File(it) }
                        if (file == null || !file.exists()) {
                            android.widget.Toast.makeText(context, "File unavailable offline", android.widget.Toast.LENGTH_SHORT).show()
                            return@resolveModel
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                        val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(Intent.createChooser(open, message.text)) }
                    }
                }
            } else if (message.type != "file") text.setOnClickListener(null)
            // Voice message: tap the round button to play / stop.
            audioBubble.visibility = if (isAudio) View.VISIBLE else View.GONE
            if (isAudio) {
                val playing = AudioPlayer.playingId == message.id
                audioBubble.background = bubbleShape(context, mine, row.firstInRun, row.lastInRun)
                audioBubble.setPadding(dp(10), dp(8), dp(14), dp(8))
                audioPlay.setImageResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play)
                audioPlay.setBackgroundResource(if (mine) R.drawable.circle_white else R.drawable.circle_teal)
                audioPlay.setColorFilter(if (mine) ContextCompat.getColor(context, R.color.primary) else android.graphics.Color.WHITE)
                audioLabel.setTextColor(ContextCompat.getColor(context, if (mine) R.color.white else R.color.text_primary))
                audioLabel.text = "Voice message · ${TimeFormat.duration(message.durationSec)}"
                audioPlay.setOnClickListener { AudioPlayer.toggle(context, message.id, message.audioRef) { notifyDataSetChanged() } }
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

            val pending = message.id in pendingIds
            val parts = listOfNotNull(if (message.edited) "Edited" else null, if (row.showTime || pending) TimeFormat.clock(message.timestamp) else null,
                if (pending) "Pending" else null)
            status.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
            status.text = parts.joinToString(" · ")

            seen.visibility = if (row.seen) View.VISIBLE else View.GONE
            if (row.seen) AvatarBinder.bind(seen, other.name, other.photo)

            listOf(text, postCard, image, heart, audioBubble).forEach { target ->
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

/** A padding-free rounded bubble. The side next to a neighbouring bubble of the same run gets a small radius. */
private fun bubbleShape(context: android.content.Context, mine: Boolean, firstInRun: Boolean, lastInRun: Boolean): GradientDrawable {
    val density = context.resources.displayMetrics.density
    val big = 18 * density
    val small = 4 * density
    // Order: top-left, top-right, bottom-right, bottom-left (each as x, y).
    val topInner = if (firstInRun) big else small
    val bottomInner = if (lastInRun) big else small
    val radii = if (mine) floatArrayOf(big, big, topInner, topInner, bottomInner, bottomInner, big, big)
    else floatArrayOf(topInner, topInner, big, big, big, big, bottomInner, bottomInner)
    return GradientDrawable().apply {
        setColor(ContextCompat.getColor(context, if (mine) R.color.primary else R.color.surface_soft))
        cornerRadii = radii
    }
}
