package com.shadowdevelopers.i230529_i230723

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

data class FeedPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Long,
    val reactionCount: Int,
    val commentCount: Int
)

class PostAdapter(
    private val onReaction: (FeedPost) -> Unit,
    private val onComments: (FeedPost) -> Unit,
    private val onAuthor: (FeedPost) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {
    private val posts = mutableListOf<FeedPost>()

    fun setPosts(items: List<FeedPost>) {
        posts.clear()
        posts.addAll(items)
        notifyDataSetChanged()
    }

    fun updateCounts(postId: String, reactionCount: Int? = null, commentCount: Int? = null) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index < 0) return
        val current = posts[index]
        posts[index] = current.copy(
            reactionCount = reactionCount ?: current.reactionCount,
            commentCount = commentCount ?: current.commentCount
        )
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) = holder.bind(posts[position])
    override fun getItemCount() = posts.size

    inner class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar = view.findViewById<TextView>(R.id.postAvatar)
        private val author = view.findViewById<TextView>(R.id.postAuthorName)
        private val time = view.findViewById<TextView>(R.id.postTime)
        private val body = view.findViewById<TextView>(R.id.postText)
        private val counts = view.findViewById<TextView>(R.id.postCounts)

        fun bind(post: FeedPost) {
            avatar.text = post.authorName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            author.text = post.authorName
            time.text = elapsed(post.createdAt)
            body.text = post.text
            counts.text = "${post.reactionCount} reactions · ${post.commentCount} comments"
            author.setOnClickListener { onAuthor(post) }
            avatar.setOnClickListener { onAuthor(post) }
            itemView.findViewById<View>(R.id.reactionButton).setOnClickListener { onReaction(post) }
            itemView.findViewById<View>(R.id.commentsButton).setOnClickListener { onComments(post) }
        }

        private fun elapsed(time: Long): String {
            val minutes = TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - time).coerceAtLeast(0))
            return when {
                minutes < 1 -> "Just now · Public"
                minutes < 60 -> "$minutes min · Public"
                minutes < 1440 -> "${minutes / 60}h · Public"
                else -> "${minutes / 1440}d · Public"
            }
        }
    }
}
