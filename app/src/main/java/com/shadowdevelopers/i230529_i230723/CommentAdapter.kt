package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * comments/{postId}/{commentId}. A reply stores parentCommentId = the top-level comment it belongs to,
 * and parentName = the name of the person it answers (shown as "@Name" when that is a reply to a reply).
 */
data class PostComment(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val imageRef: String,
    val createdAt: Long,
    val parentCommentId: String,
    val parentName: String,
    val likeCount: Int = 0,
    val liked: Boolean = false
)

/** A row in the list: either a comment (indented when it is a reply) or "View N more replies". */
sealed class CommentRow {
    data class Item(val comment: PostComment, val isReply: Boolean, val mention: String) : CommentRow()
    data class More(val parentId: String, val hidden: Int) : CommentRow()
}

class CommentAdapter(
    private val me: String,
    private val onReply: (PostComment) -> Unit,
    private val onLike: (PostComment) -> Unit,
    private val onExpand: (String) -> Unit,
    private val onLongPress: (PostComment) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val rows = mutableListOf<CommentRow>()

    fun setRows(items: List<CommentRow>) {
        rows.clear()
        rows.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = if (rows[position] is CommentRow.More) TYPE_MORE else TYPE_COMMENT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_MORE) MoreHolder(inflater.inflate(R.layout.item_comment_more, parent, false))
        else CommentViewHolder(inflater.inflate(R.layout.item_comment, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is CommentRow.Item -> (holder as CommentViewHolder).bind(row)
            is CommentRow.More -> (holder as MoreHolder).bind(row)
        }
    }

    override fun getItemCount() = rows.size

    inner class MoreHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(row: CommentRow.More) {
            (itemView as TextView).text = if (row.hidden == 1) "View 1 more reply" else "View ${row.hidden} more replies"
            itemView.setOnClickListener { onExpand(row.parentId) }
        }
    }

    inner class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val indent = view.findViewById<View>(R.id.commentIndent)
        private val avatar = view.findViewById<TextView>(R.id.commentAvatar)
        private val author = view.findViewById<TextView>(R.id.commentAuthor)
        private val text = view.findViewById<TextView>(R.id.commentText)
        private val image = view.findViewById<ImageView>(R.id.commentImage)
        private val time = view.findViewById<TextView>(R.id.commentTime)
        private val like = view.findViewById<TextView>(R.id.commentLikeButton)
        private val badge = view.findViewById<View>(R.id.commentLikeBadge)
        private val badgeCount = view.findViewById<TextView>(R.id.commentLikeCount)

        fun bind(row: CommentRow.Item) {
            val comment = row.comment
            val context = itemView.context
            itemView.tag = comment.id
            indent.visibility = if (row.isReply) View.VISIBLE else View.GONE
            val size = (if (row.isReply) 30 else 38) * context.resources.displayMetrics.density
            avatar.layoutParams = avatar.layoutParams.apply { width = size.toInt(); height = size.toInt() }
            avatar.text = AvatarBinder.initials(comment.authorName)
            ProfileCache.get(comment.authorId) { if (itemView.tag == comment.id) AvatarBinder.bind(avatar, it.name, it.photo) }
            author.text = comment.authorName
            // "@Lina Marsh next time ..." with the mention in teal, as in the design.
            val body = SpannableStringBuilder()
            if (row.mention.isNotBlank()) {
                body.append("@${row.mention} ")
                body.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.primary)), 0, row.mention.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            body.append(comment.text)
            text.text = body
            text.visibility = if (body.isBlank()) View.GONE else View.VISIBLE
            image.visibility = if (comment.imageRef.isBlank()) View.GONE else View.VISIBLE
            if (comment.imageRef.isNotBlank()) {
                MediaLoader.load(image, comment.imageRef)
                image.setOnClickListener {
                    context.startActivity(Intent(context, MediaViewerActivity::class.java).putStringArrayListExtra("media_refs", arrayListOf(comment.imageRef)))
                }
            }
            time.text = TimeFormat.ago(comment.createdAt)
            like.setTextColor(ContextCompat.getColor(context, if (comment.liked) R.color.primary else R.color.text_secondary))
            like.text = if (comment.liked) "Liked" else "Like"
            badge.visibility = if (comment.likeCount > 0) View.VISIBLE else View.GONE
            badgeCount.text = comment.likeCount.toString()
            like.setOnClickListener { onLike(comment) }
            itemView.findViewById<View>(R.id.commentReplyButton).setOnClickListener { onReply(comment) }
            itemView.setOnLongClickListener {
                if (comment.authorId == me) onLongPress(comment)
                comment.authorId == me
            }
        }
    }

    companion object {
        private const val TYPE_COMMENT = 0
        private const val TYPE_MORE = 1
    }
}
