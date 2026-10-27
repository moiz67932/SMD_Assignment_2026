package com.shadowdevelopers.i230529_i230723

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot

data class FeedPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Long,
    val images: List<String> = emptyList(),
    /** Colour card behind a text-only post ("#RRGGBB"), or "" for none. */
    val background: String = ""
) {
    companion object {
        /** posts/{postId} -> FeedPost. images is stored as an ordered list (0, 1, 2 ...). */
        fun from(snapshot: DataSnapshot) = FeedPost(
            id = snapshot.key.orEmpty(),
            authorId = snapshot.child("authorId").getValue(String::class.java).orEmpty(),
            authorName = snapshot.child("authorName").getValue(String::class.java) ?: "Kinnect member",
            text = snapshot.child("text").getValue(String::class.java).orEmpty(),
            createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
            images = snapshot.child("images").children.mapNotNull { it.getValue(String::class.java) },
            background = snapshot.child("background").getValue(String::class.java).orEmpty()
        )
    }
}

/**
 * Feed card for Home (04) and Profile (15). Each visible card listens to its own postReactions/{postId}
 * and comments/{postId} while it is on screen (attached), so counts and icons update in real time.
 * The screen calls pause() in onStop and resume() in onStart.
 */
class PostAdapter(
    private val me: String,
    private val onOpenPicker: (FeedPost) -> Unit,
    private val onComments: (FeedPost) -> Unit,
    private val onAuthor: (FeedPost) -> Unit,
    private val onShare: (FeedPost) -> Unit = {},
    private val onHide: ((FeedPost) -> Unit)? = null
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {
    private val posts = mutableListOf<FeedPost>()
    private val attached = mutableSetOf<PostViewHolder>()
    private var paused = false

    fun setPosts(items: List<FeedPost>) {
        posts.clear()
        posts.addAll(items)
        notifyDataSetChanged()
    }

    fun pause() {
        paused = true
        attached.forEach { it.stopListening() }
    }

    fun resume() {
        paused = false
        attached.forEach { it.startListening() }
    }

    override fun onViewAttachedToWindow(holder: PostViewHolder) {
        attached.add(holder)
        if (!paused) holder.startListening()
    }

    override fun onViewDetachedFromWindow(holder: PostViewHolder) {
        attached.remove(holder)
        holder.stopListening()
    }

    override fun onViewRecycled(holder: PostViewHolder) = holder.stopListening()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        return PostViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false))
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) = holder.bind(posts[position])
    override fun getItemCount() = posts.size

    inner class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar = view.findViewById<TextView>(R.id.postAvatar)
        private val author = view.findViewById<TextView>(R.id.postAuthorName)
        private val time = view.findViewById<TextView>(R.id.postTime)
        private val body = view.findViewById<TextView>(R.id.postText)
        private val grid = view.findViewById<View>(R.id.postImages)
        private val rightColumn = view.findViewById<View>(R.id.postImagesRight)
        private val thirdFrame = view.findViewById<View>(R.id.postImage3Frame)
        private val imageViews = listOf<ImageView>(view.findViewById(R.id.postImage1), view.findViewById(R.id.postImage2), view.findViewById(R.id.postImage3))
        private val more = view.findViewById<TextView>(R.id.postImageMore)
        private val summary = view.findViewById<TextView>(R.id.postReactionSummary)
        private val commentCount = view.findViewById<TextView>(R.id.postCommentCount)
        private val reactionIcons = listOf<ImageView>(view.findViewById(R.id.postReaction1), view.findViewById(R.id.postReaction2), view.findViewById(R.id.postReaction3))
        private val likeIcon = view.findViewById<ImageView>(R.id.postLikeIcon)
        private val likeLabel = view.findViewById<TextView>(R.id.postLikeLabel)
        private val listeners = ListenerBag()
        private var post: FeedPost? = null
        private var myReaction: String? = null

        fun startListening() {
            val current = post ?: return
            listeners.removeAll()
            listeners.value(FirebaseData.postReactions.child(current.id)) { snapshot ->
                val result = Reactions.summarize(snapshot, me)
                myReaction = result.mine
                Reactions.bindSummary(reactionIcons, summary, result)
                showMyReaction()
            }
            listeners.value(FirebaseData.comments.child(current.id)) { snapshot ->
                val count = snapshot.childrenCount.toInt()
                commentCount.text = when (count) {
                    0 -> ""
                    1 -> "1 comment"
                    else -> "$count comments"
                }
            }
        }

        fun stopListening() = listeners.removeAll()

        /** The Like button shows my reaction (icon, name and colour) or a plain "Like". */
        private fun showMyReaction() {
            val mine = myReaction
            likeIcon.setImageResource(if (mine == null) R.drawable.ic_thumb_up else Reactions.icon(mine))
            likeLabel.text = mine ?: "Like"
            likeLabel.setTextColor(itemView.context.getColor(Reactions.color(mine)))
            likeLabel.setTypeface(null, if (mine == null) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD)
        }

        fun bind(post: FeedPost) {
            val changed = this.post?.id != post.id
            this.post = post
            if (changed) {
                myReaction = null
                summary.text = ""
                commentCount.text = ""
                reactionIcons.forEach { it.visibility = View.GONE }
                showMyReaction()
                if (this in attached && !paused) startListening()
            }
            itemView.tag = post.id
            author.text = post.authorName
            avatar.text = AvatarBinder.initials(post.authorName)
            ProfileCache.get(post.authorId) { if (itemView.tag == post.id) AvatarBinder.bind(avatar, it.name, it.photo) }
            time.text = "${TimeFormat.ago(post.createdAt)} · Public"
            bindText(post)
            bindImages(post)
            author.setOnClickListener { onAuthor(post) }
            avatar.setOnClickListener { onAuthor(post) }
            // Tap: Like / remove my reaction. Long-press: the reaction picker (05) with all six reactions.
            itemView.findViewById<View>(R.id.postLikeButton).apply {
                setOnClickListener { Reactions.react(context, post.id, post.authorId, me, myReaction ?: "Like", myReaction) }
                setOnLongClickListener { onOpenPicker(post); true }
            }
            itemView.findViewById<View>(R.id.postSummaryRow).setOnClickListener { onOpenPicker(post) }
            itemView.findViewById<View>(R.id.postCommentButton).setOnClickListener { onComments(post) }
            itemView.findViewById<View>(R.id.postShareButton).setOnClickListener { onShare(post) }
            itemView.findViewById<View>(R.id.postHideButton).apply {
                visibility = if (onHide == null) View.GONE else View.VISIBLE
                setOnClickListener { onHide?.invoke(post) }
            }
            itemView.findViewById<View>(R.id.postMoreButton).setOnClickListener { onComments(post) }
        }

        /** A text-only post with a colour shows as a big centred card, like Create post's colour picker. */
        private fun bindText(post: FeedPost) {
            val coloured = post.images.isEmpty() && post.background.isNotBlank()
            body.visibility = if (post.text.isBlank()) View.GONE else View.VISIBLE
            body.text = post.text
            if (coloured) {
                body.setBackgroundColor(runCatching { Color.parseColor(post.background) }.getOrDefault(Color.DKGRAY))
                body.setTextColor(Color.WHITE)
                body.textSize = 22f
                body.gravity = Gravity.CENTER
                body.minHeight = (220 * itemView.resources.displayMetrics.density).toInt()
            } else {
                body.background = null
                body.setTextColor(itemView.context.getColor(R.color.text_primary))
                body.textSize = 14f
                body.gravity = Gravity.START
                body.minHeight = 0
            }
        }

        /** 1 image: full width. 2: side by side. 3+: one large left, two stacked right, "+N" on the third. */
        private fun bindImages(post: FeedPost) {
            val count = post.images.size
            grid.visibility = if (count == 0) View.GONE else View.VISIBLE
            if (count == 0) return
            rightColumn.visibility = if (count >= 2) View.VISIBLE else View.GONE
            thirdFrame.visibility = if (count >= 3) View.VISIBLE else View.GONE
            imageViews.forEachIndexed { index, view ->
                val ref = post.images.getOrNull(index)
                if (ref != null) MediaLoader.load(view, ref)
                view.setOnClickListener { openViewer(post, index) }
            }
            more.visibility = if (count > 3) View.VISIBLE else View.GONE
            more.text = "+${count - 3}"
            more.setOnClickListener { openViewer(post, 2) }
        }

        private fun openViewer(post: FeedPost, index: Int) {
            val context = itemView.context
            context.startActivity(android.content.Intent(context, MediaViewerActivity::class.java)
                .putStringArrayListExtra("media_refs", ArrayList(post.images))
                .putExtra("index", index))
        }
    }
}
