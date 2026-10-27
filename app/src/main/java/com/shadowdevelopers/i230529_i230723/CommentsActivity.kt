package com.shadowdevelopers.i230529_i230723

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot

/**
 * Comments (06) for one post: top-level comments with their replies nested underneath (two shown,
 * "View N more replies" for the rest), comment likes, photo comments, and the "Replying to … / Cancel" bar.
 */
class CommentsActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    private val headerListeners = ListenerBag()
    private val likeQueries = mutableMapOf<String, Pair<com.google.firebase.database.Query, com.google.firebase.database.ValueEventListener>>()
    private val comments = mutableListOf<PostComment>()
    private val likes = mutableMapOf<String, Pair<Int, Boolean>>()
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
        FirebaseData.posts.child(postId).child("authorId").get().addOnSuccessListener { postAuthorId = it.getValue(String::class.java).orEmpty() }

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
        // Header: live reaction summary of the post; tapping it opens the picker.
        headerListeners.value(FirebaseData.postReactions.child(postId)) { snapshot ->
            val summary = Reactions.summarize(snapshot, me)
            val label = findViewById<TextView>(R.id.commentsReactionSummary)
            Reactions.bindSummary(listOf(findViewById(R.id.commentsReaction1), findViewById(R.id.commentsReaction2), findViewById(R.id.commentsReaction3)), label, summary)
            if (summary.total == 0) label.text = "Be the first to react"
            (label.parent as View).setOnClickListener {
                startActivity(Intent(this, ReactionPickerActivity::class.java).putExtra("post_id", postId))
            }
        }
        listeners.value(FirebaseData.comments.child(postId).orderByChild("createdAt"), onError = {
            findViewById<TextView>(R.id.commentsEmpty).apply { visibility = View.VISIBLE; text = it.message }
        }) { snapshot ->
            comments.clear()
            snapshot.children.mapNotNullTo(comments) { parse(it) }
            loaded = true
            syncLikeListeners()
            render()
        }
    }

    override fun onStop() {
        headerListeners.removeAll()
        listeners.removeAll()
        likeQueries.values.forEach { (query, listener) -> query.removeEventListener(listener) }
        likeQueries.clear()
        super.onStop()
    }

    private fun parse(item: DataSnapshot): PostComment? {
        val authorId = item.child("authorId").getValue(String::class.java) ?: return null
        return PostComment(
            id = item.key.orEmpty(),
            authorId = authorId,
            authorName = item.child("authorName").getValue(String::class.java) ?: "Kinnect member",
            text = item.child("text").getValue(String::class.java).orEmpty(),
            imageRef = item.child("imageRef").getValue(String::class.java).orEmpty(),
            createdAt = item.child("createdAt").getValue(Long::class.java) ?: 0L,
            parentCommentId = item.child("parentCommentId").getValue(String::class.java).orEmpty(),
            parentName = item.child("parentName").getValue(String::class.java).orEmpty()
        )
    }

    /** One like listener per comment: commentLikes/{commentId}/{uid} = true. */
    private fun syncLikeListeners() {
        val ids = comments.map { it.id }.toSet()
        likeQueries.keys.filter { it !in ids }.forEach { id -> likeQueries.remove(id)?.let { (q, l) -> q.removeEventListener(l) } }
        ids.filter { it !in likeQueries }.forEach { id ->
            val query = FirebaseData.commentLikes.child(id)
            val listener = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    likes[id] = snapshot.childrenCount.toInt() to (snapshot.child(me).getValue(Boolean::class.java) == true)
                    render()
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
            }
            query.addValueEventListener(listener)
            likeQueries[id] = query to listener
        }
    }

    /** Builds the list: each top-level comment, then its replies (oldest first, 2 visible until expanded). */
    private fun render() {
        val withLikes = comments.map { c -> likes[c.id]?.let { c.copy(likeCount = it.first, liked = it.second) } ?: c }
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

    private fun send() {
        if (sending) return
        val text = commentInput.text.toString().trim()
        val image = attachment
        if (text.isBlank() && image == null) return
        setSending(true)
        if (image == null) {
            write(text, "")
            return
        }
        val status = findViewById<TextView>(R.id.commentUploadStatus)
        MediaUploader.upload(this, image, MediaUploader.IMAGE,
            onProgress = { status.text = "$it%" },
            onSuccess = { ref -> write(text, ref) },
            onError = { message ->
                setSending(false)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            })
    }

    private fun write(text: String, imageRef: String) {
        val commentId = FirebaseData.comments.child(postId).push().key ?: return setSending(false)
        val reply = replyTo
        // A reply to a reply still belongs to the top-level comment of that thread.
        val threadId = reply?.let { if (it.parentCommentId.isBlank()) it.id else it.parentCommentId }.orEmpty()
        ProfileCache.get(me) { profile ->
            val comment = mapOf(
                "commentId" to commentId,
                "authorId" to me,
                "authorName" to profile.name,
                "text" to text,
                "imageRef" to imageRef,
                "parentCommentId" to threadId,
                "parentName" to (reply?.authorName ?: ""),
                "createdAt" to mapOf(".sv" to "timestamp")
            )
            FirebaseData.comments.child(postId).child(commentId).setValue(comment)
                .addOnSuccessListener {
                    notifyPeople(reply, text.ifBlank { "Photo" })
                    commentInput.text.clear()
                    setSending(false)
                    setAttachment(null)
                    clearReply()
                }
                .addOnFailureListener {
                    setSending(false)
                    Toast.makeText(this, it.localizedMessage ?: "Could not add comment", Toast.LENGTH_LONG).show()
                }
        }
    }

    /** The post author hears about new comments; the person replied to hears about the reply. */
    private fun notifyPeople(reply: PostComment?, preview: String) {
        val notifications = FirebaseData.database.getReference("notifications")
        val now = mapOf(".sv" to "timestamp")
        val target = reply?.authorId ?: postAuthorId
        if (target.isBlank() || target == me) return
        notifications.child(target).push().setValue(mapOf(
            "senderId" to me, "type" to if (reply != null) "reply" else "comment", "body" to preview.take(80),
            "postId" to postId, "createdAt" to now, "read" to false
        ))
    }

    private fun setSending(active: Boolean) {
        sending = active
        findViewById<View>(R.id.sendCommentButton).apply { isEnabled = !active; alpha = if (active) 0.5f else 1f }
        findViewById<TextView>(R.id.commentUploadStatus).visibility = if (active && attachment != null) View.VISIBLE else View.GONE
    }

    private fun toggleLike(comment: PostComment) {
        val reference = FirebaseData.commentLikes.child(comment.id).child(me)
        val task = if (comment.liked) reference.removeValue() else reference.setValue(true)
        task.addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not update the like", Toast.LENGTH_SHORT).show() }
    }

    private fun confirmDelete(comment: PostComment) {
        AlertDialog.Builder(this).setMessage("Delete this comment?")
            .setPositiveButton("Delete") { _, _ ->
                FirebaseData.comments.child(postId).child(comment.id).removeValue()
                    .addOnSuccessListener { FirebaseData.commentLikes.child(comment.id).child(me).removeValue() }
                    .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not delete the comment", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Cancel", null).show()
    }

    companion object {
        private const val PICK_IMAGE = 830
    }
}
