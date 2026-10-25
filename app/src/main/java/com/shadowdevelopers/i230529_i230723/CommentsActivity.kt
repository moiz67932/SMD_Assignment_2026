package com.shadowdevelopers.i230529_i230723

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Query
import com.google.firebase.database.DatabaseReference

class CommentsActivity : AppCompatActivity() {
    private var commentsQuery: Query? = null
    private var commentObserver: com.google.firebase.database.ValueEventListener? = null
    private val likeListeners = mutableMapOf<String, Pair<DatabaseReference, com.google.firebase.database.ValueEventListener>>()
    private lateinit var commentAdapter: CommentAdapter
    private var parentComment: PostComment? = null
    private lateinit var replyLabel: TextView
    private lateinit var cancelReply: TextView
    private lateinit var commentInput: EditText
    private lateinit var postId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)
        postId = intent.getStringExtra("post_id").orEmpty()
        replyLabel = findViewById(R.id.replyingLabel)
        cancelReply = findViewById(R.id.cancelReplyButton)
        commentInput = findViewById(R.id.commentInput)
        replyLabel.visibility = View.GONE
        cancelReply.visibility = View.GONE

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        cancelReply.setOnClickListener { clearReply() }

        commentAdapter = CommentAdapter(
            onReply = { comment ->
                parentComment = comment
                replyLabel.text = "Replying to ${comment.authorName}"
                replyLabel.visibility = View.VISIBLE
                cancelReply.visibility = View.VISIBLE
            },
            onLike = { comment -> toggleLike(comment) }
        )
        findViewById<RecyclerView>(R.id.commentsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@CommentsActivity)
            adapter = commentAdapter
        }
        if (postId.isNotBlank()) {
            commentsQuery = FirebaseData.comments.child(postId).orderByChild("createdAt")
            val observer = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val items = snapshot.children.mapNotNull { comment ->
                        val authorId = comment.child("authorId").getValue(String::class.java) ?: return@mapNotNull null
                        PostComment(
                            id = comment.key.orEmpty(),
                            authorId = authorId,
                            authorName = comment.child("authorName").getValue(String::class.java) ?: "Kinnect member",
                            text = comment.child("text").getValue(String::class.java).orEmpty(),
                            createdAt = comment.child("createdAt").getValue(Long::class.java) ?: 0L,
                            parentCommentId = comment.child("parentCommentId").getValue(String::class.java).orEmpty(),
                            parentName = comment.child("parentName").getValue(String::class.java).orEmpty()
                        )
                    }
                    commentAdapter.setComments(items)
                    syncCommentLikes(items)
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Toast.makeText(this@CommentsActivity, error.message, Toast.LENGTH_LONG).show()
                }
            }
            commentObserver = observer
            commentsQuery?.addValueEventListener(observer)
        }

        findViewById<View>(R.id.sendCommentButton).setOnClickListener { sendComment() }
    }

    private fun sendComment() {
        val user = FirebaseAuth.getInstance().currentUser
        val text = commentInput.text.toString().trim()
        if (user == null || postId.isBlank()) {
            Toast.makeText(this, "Open comments from a post while signed in", Toast.LENGTH_SHORT).show()
            return
        }
        if (text.isBlank()) return
        FirebaseData.users.child(user.uid).get().addOnSuccessListener { profile ->
            val commentId = FirebaseData.comments.child(postId).push().key ?: return@addOnSuccessListener
            val parent = parentComment
            val comment = mapOf(
                "commentId" to commentId,
                "authorId" to user.uid,
                "authorName" to (profile.child("name").getValue(String::class.java) ?: "Kinnect member"),
                "text" to text,
                "parentCommentId" to (parent?.id ?: ""),
                "parentName" to (parent?.authorName ?: ""),
                "createdAt" to mapOf(".sv" to "timestamp")
            )
            FirebaseData.comments.child(postId).child(commentId).setValue(comment)
                .addOnSuccessListener {
                    commentInput.text.clear()
                    clearReply()
                }
                .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not add comment", Toast.LENGTH_LONG).show() }
        }.addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not load profile", Toast.LENGTH_LONG).show() }
    }

    private fun syncCommentLikes(comments: List<PostComment>) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val activeIds = comments.map { it.id }.toSet()
        likeListeners.keys.filter { it !in activeIds }.forEach { commentId ->
            val (reference, listener) = likeListeners.remove(commentId) ?: return@forEach
            reference.removeEventListener(listener)
        }
        comments.forEach { comment ->
            if (comment.id in likeListeners) return@forEach
            val reference = FirebaseData.commentLikes.child(comment.id)
            val listener = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    commentAdapter.updateLikes(comment.id, snapshot.childrenCount.toInt(), snapshot.child(uid).getValue(Boolean::class.java) == true)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
            }
            reference.addValueEventListener(listener)
            likeListeners[comment.id] = reference to listener
        }
    }

    private fun toggleLike(comment: PostComment) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val reference = FirebaseData.commentLikes.child(comment.id).child(uid)
        reference.get().addOnSuccessListener { snapshot ->
            if (snapshot.getValue(Boolean::class.java) == true) reference.removeValue() else reference.setValue(true)
        }
    }

    private fun clearReply() {
        parentComment = null
        replyLabel.visibility = View.GONE
        cancelReply.visibility = View.GONE
    }

    override fun onDestroy() {
        commentObserver?.let { commentsQuery?.removeEventListener(it) }
        likeListeners.values.forEach { (reference, listener) -> reference.removeEventListener(listener) }
        likeListeners.clear()
        super.onDestroy()
    }
}
