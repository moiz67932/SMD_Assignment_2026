package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * Comments (06) for one post: top-level comments with their replies nested underneath (two shown,
 * "View N more replies" for the rest), comment likes, photo comments, and the "Replying to … / Cancel" bar.
 */
class CommentsActivity : AppCompatActivity() {
    private val me by lazy { Backend.uid.orEmpty() }
    private val listeners = ListenerBag()
    private val comments = mutableListOf<PostComment>()
    private val expanded = mutableSetOf<String>()
    private var newestFirst = false
    private var loaded = false
    private var replyTo: PostComment? = null
    private var attachment: Uri? = null
    private var sending = false
    private var postAuthorId = ""
    private lateinit var postId: String
    private lateinit var commentInput: EditText
    private lateinit var adapter: CommentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)
        postId = intent.getStringExtra("post_id").orEmpty()
        commentInput = findViewById(R.id.commentInput)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        if (postId.isBlank() || me.isBlank()) {
            Toast.makeText(this, "Open comments from a post", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        adapter = CommentAdapter(
            me = me,
            onReply = ::startReply,
            onLike = ::toggleLike,
            onExpand = { parentId -> expanded.add(parentId); render() },
            onLongPress = ::confirmDelete
        )
        findViewById<RecyclerView>(R.id.commentsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@CommentsActivity)
            adapter = this@CommentsActivity.adapter
        }
        findViewById<View>(R.id.cancelReplyButton).setOnClickListener { clearReply() }
        findViewById<View>(R.id.sendCommentButton).setOnClickListener { send() }
        findViewById<View>(R.id.commentImageButton).setOnClickListener {
            startActivityForResult(Intent(this, PhotoPickerActivity::class.java).putExtra("images_only", true).putExtra("single", true), PICK_IMAGE)
        }
        findViewById<View>(R.id.commentAttachmentRemove).setOnClickListener { setAttachment(null) }
        findViewById<View>(R.id.commentsSortButton).setOnClickListener {
            newestFirst = !newestFirst
            findViewById<TextView>(R.id.commentsSortLabel).text = if (newestFirst) "Newest" else "Most relevant"
            render()
        }
    }

    override fun onStart() {
        super.onStart()
        if (postId.isBlank() || me.isBlank()) return
        // Header (reaction summary) and comments: SQLite first, then the server every 6 s and after offline actions sync.
        load(fromServer = false)
        listeners.poll(6_000) { load(fromServer = true) }
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    private fun load(fromServer: Boolean) {
        Backend.call({ PostRepo.post(postId, fromServer) to PostRepo.comments(postId, fromServer) }) { result ->
            result.onSuccess { (post, list) ->
                if (post != null) showHeader(post)
                comments.clear()
                comments.addAll(toThreads(list))
                loaded = true
                render()
            }.onFailure {
                if (!fromServer) return@onFailure
                loaded = true
                if (comments.isEmpty()) findViewById<TextView>(R.id.commentsEmpty).apply { visibility = View.VISIBLE; text = Forms.message(it as? Exception, "Could not load comments") }
            }
        }
    }

    private fun showHeader(post: FeedPost) {
        postAuthorId = post.authorId
        val summary = Reactions.summarize(post, me)
        val label = findViewById<TextView>(R.id.commentsReactionSummary)
        Reactions.bindSummary(listOf(findViewById(R.id.commentsReaction1), findViewById(R.id.commentsReaction2), findViewById(R.id.commentsReaction3)), label, summary)
        if (summary.total == 0) label.text = "Be the first to react"
        (label.parent as View).setOnClickListener {
            startActivity(Intent(this, ReactionPickerActivity::class.java).putExtra("post_id", postId))
        }
    }

    /**
     * The server stores which comment a reply answers (parent_id). The screen groups a thread under its top-level
     * comment and names the person answered ("replying to"), as in Assignment 2.
     */
    private fun toThreads(list: List<PostRepo.Comment>): List<PostComment> {
        val byId = list.associateBy { it.id }
        fun thread(c: PostRepo.Comment): String {
            var current = c
            repeat(10) { current = byId[current.parentId] ?: return current.id }
            return current.id
        }
        return list.map { c ->
            val parent = byId[c.parentId]
            PostComment(
                id = c.id, authorId = c.authorId, authorName = c.authorName.ifBlank { "Kinnect member" }, text = c.text,
                imageRef = c.imageRef, createdAt = c.createdAt,
                parentCommentId = if (parent == null) "" else thread(parent),
                parentName = parent?.authorName.orEmpty(), likeCount = c.likes, liked = c.likedByMe, pending = c.pending
            )
        }
    }

    /** Builds the list: each top-level comment, then its replies (oldest first, 2 visible until expanded). */
    private fun render() {
        val withLikes = comments
        val byId = withLikes.associateBy { it.id }
        val topLevel = withLikes.filter { it.parentCommentId.isBlank() || it.parentCommentId !in byId }
        val ordered = if (newestFirst) topLevel.sortedByDescending { it.createdAt }
        else topLevel.sortedWith(compareByDescending<PostComment> { it.likeCount }.thenBy { it.createdAt })
        val rows = mutableListOf<CommentRow>()
        ordered.forEach { parent ->
            rows.add(CommentRow.Item(parent, isReply = false, mention = ""))
            val replies = withLikes.filter { it.parentCommentId == parent.id && it.id != parent.id }.sortedBy { it.createdAt }
            val visible = if (parent.id in expanded) replies else replies.take(2)
            visible.forEach { reply ->
                // Only mention the name when the reply answers someone other than the thread's author.
                val mention = if (reply.parentName.isNotBlank() && reply.parentName != parent.authorName) reply.parentName else ""
                rows.add(CommentRow.Item(reply, isReply = true, mention = mention))
            }
            if (visible.size < replies.size) rows.add(CommentRow.More(parent.id, replies.size - visible.size))
        }
        adapter.setRows(rows)
        findViewById<TextView>(R.id.commentsEmpty).apply {
            visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            text = if (loaded) "No comments yet. Be the first to comment." else "Loading comments…"
        }
    }

    private fun startReply(comment: PostComment) {
        replyTo = comment
        findViewById<View>(R.id.replyingBar).visibility = View.VISIBLE
        findViewById<TextView>(R.id.replyingLabel).text = "Replying to ${comment.authorName}"
        commentInput.hint = "Write a reply..."
        commentInput.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(commentInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun clearReply() {
        replyTo = null
        findViewById<View>(R.id.replyingBar).visibility = View.GONE
        commentInput.hint = "Write a comment..."
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra("media_uris")?.firstOrNull()?.let { setAttachment(Uri.parse(it)) }
        }
    }

    private fun setAttachment(uri: Uri?) {
        if (sending) return
        attachment = uri
        findViewById<View>(R.id.commentAttachment).visibility = if (uri == null) View.GONE else View.VISIBLE
        if (uri != null) Glide.with(this).load(uri).centerCrop().into(findViewById<ImageView>(R.id.commentAttachmentImage))
    }

    /**
     * Comment, reply, or photo comment. Saved and queued at once (the photo first, then the comment that uses it), so it
     * shows immediately with "Pending" when offline and syncs in order later.
     */
    private fun send() {
        if (sending) return
        val text = commentInput.text.toString().trim()
        val image = attachment
        if (text.isBlank() && image == null) return
        setSending(true)
        val parent = replyTo?.id
        Backend.call({ PostRepo.comment(this, postId, text, parent, image) }) { result ->
            setSending(false)
            result.onSuccess {
                commentInput.text.clear()
                setAttachment(null)
                clearReply()
                load(fromServer = false)
            }.onFailure { Toast.makeText(this, Forms.message(it as? Exception, "Could not add comment"), Toast.LENGTH_LONG).show() }
        }
    }

    private fun setSending(active: Boolean) {
        sending = active
        findViewById<View>(R.id.sendCommentButton).apply { isEnabled = !active; alpha = if (active) 0.5f else 1f }
        findViewById<TextView>(R.id.commentUploadStatus).visibility = if (active && attachment != null) View.VISIBLE else View.GONE
    }

    private fun toggleLike(comment: PostComment) {
        Backend.call({ PostRepo.likeComment(comment.id, !comment.liked) }) { load(fromServer = false) }
    }

    private fun confirmDelete(comment: PostComment) {
        AlertDialog.Builder(this).setMessage("Delete this comment?")
            .setPositiveButton("Delete") { _, _ -> Backend.call({ PostRepo.deleteComment(comment.id) }) { load(fromServer = false) } }
            .setNegativeButton("Cancel", null).show()
    }

    companion object {
        private const val PICK_IMAGE = 830
    }
}
