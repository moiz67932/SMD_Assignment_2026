package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener

class ReactionPickerActivity : AppCompatActivity() {
    private val postCountListeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reaction_picker)

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }
        val postId = intent.getStringExtra("post_id")
        findViewById<View>(R.id.reactionPostImage).visibility = View.GONE
        findViewById<View>(R.id.reactionSampleComment).visibility = View.GONE
        if (!postId.isNullOrBlank()) loadPost(postId)
        val reactionViews = mapOf(
            R.id.reactionLike to "Like",
            R.id.reactionLove to "Love",
            R.id.reactionLaugh to "Laugh",
            R.id.reactionWow to "Wow",
            R.id.reactionSad to "Sad",
            R.id.reactionAngry to "Angry"
        )
        reactionViews.forEach { (viewId, reaction) ->
            findViewById<View>(viewId).setOnClickListener {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null || postId.isNullOrBlank()) {
                    Toast.makeText(this, "Open reactions from a post while signed in", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val reference = FirebaseData.postReactions.child(postId).child(uid)
                reference.get().addOnSuccessListener { snapshot ->
                    if (snapshot.getValue(String::class.java) == reaction) reference.removeValue()
                    else reference.setValue(reaction)
                    finish()
                }.addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not update reaction", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun loadPost(postId: String) {
        FirebaseData.posts.child(postId).get().addOnSuccessListener { post ->
            val authorId = post.child("authorId").getValue(String::class.java).orEmpty()
            val name = post.child("authorName").getValue(String::class.java) ?: "Kinnect member"
            findViewById<TextView>(R.id.reactionAuthorName).text = name
            findViewById<TextView>(R.id.reactionAuthorButton).text = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            findViewById<TextView>(R.id.reactionPostText).text = post.child("text").getValue(String::class.java).orEmpty()
            val createdAt = post.child("createdAt").getValue(Long::class.java) ?: 0L
            findViewById<TextView>(R.id.reactionPostTime).text = if (createdAt > 0L) android.text.format.DateUtils.getRelativeTimeSpanString(createdAt).toString() else "Just now"
            if (authorId.isNotBlank()) {
                findViewById<View>(R.id.reactionAuthorButton).setOnClickListener {
                    startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", authorId))
                }
            }
        }
        observeCount(FirebaseData.postReactions.child(postId), R.id.reactionSummaryText, "reactions")
        observeCount(FirebaseData.comments.child(postId), R.id.reactionCommentCount, "comments")
    }

    private fun observeCount(reference: DatabaseReference, viewId: Int, label: String) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                findViewById<TextView>(viewId).text = "${snapshot.childrenCount} $label"
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        reference.addValueEventListener(listener)
        postCountListeners.add(reference to listener)
    }

    override fun onDestroy() {
        postCountListeners.forEach { (reference, listener) -> reference.removeEventListener(listener) }
        super.onDestroy()
    }
}
