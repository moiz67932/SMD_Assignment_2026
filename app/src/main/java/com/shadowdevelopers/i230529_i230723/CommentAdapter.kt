package com.shadowdevelopers.i230529_i230723

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class PostComment(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Long,
    val parentCommentId: String,
    val parentName: String,
    val likeCount: Int = 0,
    val liked: Boolean = false
)

class CommentAdapter(
    private val onReply: (PostComment) -> Unit,
    private val onLike: (PostComment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {
    private val comments = mutableListOf<PostComment>()

    fun setComments(items: List<PostComment>) {
        comments.clear()
        comments.addAll(items)
        notifyDataSetChanged()
    }

    fun updateLikes(commentId: String, count: Int, liked: Boolean) {
        val index = comments.indexOfFirst { it.id == commentId }
        if (index < 0) return
        comments[index] = comments[index].copy(likeCount = count, liked = liked)
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        return CommentViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false))
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) = holder.bind(comments[position])
    override fun getItemCount() = comments.size

    inner class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar = view.findViewById<TextView>(R.id.commentAvatar)
        private val text = view.findViewById<TextView>(R.id.commentText)
        private val time = view.findViewById<TextView>(R.id.commentTime)
        private val like = view.findViewById<TextView>(R.id.commentLikeButton)

        fun bind(comment: PostComment) {
            avatar.text = comment.authorName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            val reply = if (comment.parentName.isBlank()) "" else "Replying to ${comment.parentName}: "
            text.text = "${comment.authorName}\n$reply${comment.text}"
            time.text = if (comment.createdAt > 0) android.text.format.DateUtils.getRelativeTimeSpanString(comment.createdAt).toString() else "Just now"
            like.text = if (comment.liked) "Liked · ${comment.likeCount}" else "Like · ${comment.likeCount}"
            like.setOnClickListener { onLike(comment) }
            itemView.findViewById<View>(R.id.commentReplyButton).setOnClickListener { onReply(comment) }
        }
    }
}
