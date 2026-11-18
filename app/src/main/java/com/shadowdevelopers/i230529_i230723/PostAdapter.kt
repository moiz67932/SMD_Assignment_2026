package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class FeedPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Long,
    val images: List<String> = emptyList(),
    /** Colour card behind a text-only post ("#RRGGBB"), or "" for none. */
    val background: String = "",
    /** Details chosen in Create post: "feeling", "location" (check-in), "event", and tagged friends (uid -> name). */
    val feeling: String = "",
    val location: String = "",
    val event: String = "",
    val tags: Map<String, String> = emptyMap(),
    /** From the server: reaction totals per type, my own reaction, number of comments, author photo. */
    val reactionCounts: Map<String, Int> = emptyMap(),
    val myReaction: String? = null,
    val commentCount: Int = 0,
    val authorPhoto: String = "",
    /** True while this post (or my change to it) is still queued offline and not yet on the server. */
    val pending: Boolean = false
) {
    /** One line under the author name: "feeling happy · at Lahore · with Ali, Sara · Event: ...". */
    fun details(): String = listOfNotNull(
        feeling.takeIf { it.isNotBlank() }?.let { "is feeling $it" },
        location.takeIf { it.isNotBlank() }?.let { "at $it" },
        tags.values.takeIf { it.isNotEmpty() }?.let { "with ${it.joinToString(", ")}" },
        event.takeIf { it.isNotBlank() }?.let { "Event: $it" }
    ).joinToString(" · ")

    companion object {
        /** A post from the PHP API (posts/feed.php, posts/get.php) or one created offline (local_images = files on the phone). */
        fun fromJson(json: org.json.JSONObject): FeedPost {
            val media = json.optJSONArray("media") ?: org.json.JSONArray()
            val local = json.optJSONArray("local_images") ?: org.json.JSONArray()
            val images = (0 until media.length()).map { ProfileCache.mediaRef(media.getJSONObject(it).getString("id")) }
                .ifEmpty { (0 until local.length()).map { local.getString(it) } }
            val details = json.optJSONObject("details") ?: org.json.JSONObject()
            val tags = details.optJSONObject("tags")
            val reactions = json.optJSONArray("reactions") ?: org.json.JSONArray()
            return FeedPost(
                id = json.optString("id"),
                authorId = json.optString("author_id"),
                authorName = json.optString("author_name").ifBlank { "Kinnect member" },
                text = json.optString("body"),
                createdAt = TimeFormat.parse(json.optString("created_at")),
                images = images,
                background = details.optString("background"),
                feeling = details.optString("feeling"),
                location = details.optString("location"),
                event = details.optString("event"),
                tags = tags?.keys()?.asSequence()?.associateWith { tags.optString(it) }.orEmpty(),
                reactionCounts = (0 until reactions.length()).associate { reactions.getJSONObject(it).let { r -> r.getString("reaction") to r.getInt("n") } },
                myReaction = json.optString("my_reaction").takeUnless { json.isNull("my_reaction") || it.isBlank() },
                commentCount = json.optInt("comment_count"),
                authorPhoto = ProfileCache.mediaRef(json.optString("author_photo_media_id"))
            )
        }

    }
}

/**
 * Feed card for Home (04) and Profile (15). Each visible card listens to its own postReactions/{postId}
 * and comments/{postId} while it is on screen (attached), so counts and icons update in real time.
 * The screen calls pause() in onStop and resume() in onStart.
 */
class PostAdapter(
    private val me: String,
    private val onComments: (FeedPost) -> Unit,
    private val onAuthor: (FeedPost) -> Unit,
    private val onShare: (FeedPost) -> Unit = {},
    private val onHide: ((FeedPost) -> Unit)? = null,
    /** Called after my own change (reaction, delete) was saved locally, so the screen redraws at once. */
    private val onChanged: () -> Unit = {}
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

        /** Reactions and comment count now come with the post itself (the feed is refreshed while the screen is open). */
        fun startListening() {
            val current = post ?: return
            myReaction = current.myReaction
            Reactions.bindSummary(reactionIcons, summary, Reactions.summarize(current, me))
            showMyReaction()
            commentCount.text = when (current.commentCount) {
                0 -> ""
                1 -> "1 comment"
                else -> "${current.commentCount} comments"
            }
        }

        fun stopListening() = Unit

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
            val details = post.details()
            // "· Pending" while the post or my change to it is still queued offline (it syncs automatically).
            time.text = "${TimeFormat.ago(post.createdAt)} · Public" + (if (post.pending) " · Pending" else "") + if (details.isBlank()) "" else "\n$details"
            bindText(post)
            bindImages(post)
            author.setOnClickListener { onAuthor(post) }
            avatar.setOnClickListener { onAuthor(post) }
            // Tap: Like / remove my reaction. Long-press: the reaction picker (05) with all six reactions.
            itemView.findViewById<View>(R.id.postLikeButton).apply {
                setOnClickListener { Reactions.react(context, post.id, post.authorId, me, myReaction ?: "Like", myReaction, onChanged) }
                setOnLongClickListener { showReactionPicker(post); true }
            }
            itemView.findViewById<View>(R.id.postSummaryRow).setOnClickListener { showReactionPicker(post) }
            itemView.findViewById<View>(R.id.postCommentButton).setOnClickListener { onComments(post) }
            itemView.findViewById<View>(R.id.postShareButton).setOnClickListener { onShare(post) }
            itemView.findViewById<View>(R.id.postHideButton).apply {
                visibility = if (onHide == null) View.GONE else View.VISIBLE
                setOnClickListener { onHide?.invoke(post) }
            }
            itemView.findViewById<View>(R.id.postMoreButton).setOnClickListener { anchor ->
                // Saved posts are kept on this phone (SQLite); delete goes to the server through the offline queue.
                val context = anchor.context
                Backend.call({ Backend.store.entity(me, "saved", post.id) != null }) { result ->
                    val isSaved = result.getOrDefault(false)
                    android.widget.PopupMenu(context, anchor).apply {
                        menu.add(0, 1, 0, "Open comments")
                        menu.add(0, 2, 1, if (isSaved) "Remove from saved" else "Save post")
                        if (post.authorId == me) menu.add(0, 3, 2, "Delete post")
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                1 -> onComments(post)
                                2 -> Backend.call({
                                    if (isSaved) Backend.store.deleteEntity(me, "saved", post.id)
                                    else Backend.store.putEntity(me, "saved", post.id, org.json.JSONObject().put("id", post.id).put("saved_at", System.currentTimeMillis()).toString())
                                }) { android.widget.Toast.makeText(context, if (isSaved) "Removed from saved" else "Saved", android.widget.Toast.LENGTH_SHORT).show() }
                                3 -> androidx.appcompat.app.AlertDialog.Builder(context).setTitle("Delete this post?")
                                    .setMessage("This cannot be undone.")
                                    .setPositiveButton("Delete") { _, _ -> Backend.call({ PostRepo.delete(post.id) }) { onChanged() } }
                                    .setNegativeButton("Cancel", null).show()
                            }
                            true
                        }
                    }.show()
                }
            }
        }

        private fun showReactionPicker(post: FeedPost) {
            val context = itemView.context
            val density = itemView.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()
            val row = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setBackgroundResource(R.drawable.rounded_white)
                clipChildren = false
            }
            val width = minOf(dp(340), itemView.resources.displayMetrics.widthPixels - dp(24))
            val popup = android.widget.PopupWindow(row, width, dp(76), true).apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                isOutsideTouchable = true
                elevation = dp(12).toFloat()
            }
            Reactions.TYPES.forEach { type ->
                val icon = ImageView(context).apply {
                    setImageResource(Reactions.icon(type))
                    contentDescription = type
                    if (android.os.Build.VERSION.SDK_INT >= 26) tooltipText = type
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    if (type == myReaction) {
                        setBackgroundResource(R.drawable.circle_teal_mid)
                        scaleX = 1.1f
                        scaleY = 1.1f
                    }
                    setOnClickListener {
                        Reactions.react(context, post.id, post.authorId, me, type, myReaction, onChanged)
                        popup.dismiss()
                    }
                }
                row.addView(icon, android.widget.LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            val anchor = itemView.findViewById<View>(R.id.postLikeButton)
            val location = IntArray(2)
            anchor.getLocationInWindow(location)
            val visible = android.graphics.Rect()
            anchor.getWindowVisibleDisplayFrame(visible)
            val x = location[0].coerceIn(dp(12), maxOf(dp(12), itemView.resources.displayMetrics.widthPixels - width - dp(12)))
            val y = if (location[1] - dp(80) >= visible.top) location[1] - dp(80) else location[1] + anchor.height
            popup.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, x, y)
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
