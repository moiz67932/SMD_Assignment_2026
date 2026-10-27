package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Reaction picker (05): the post under a dim overlay with the six reactions on top. The current reaction
 * is enlarged with its name above it; tapping it again removes it, tapping another one changes it.
 */
class ReactionPickerActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    private var postId = ""
    private var authorId = ""
    private var myReaction: String? = null
    private val reactionViews by lazy {
        mapOf(
            "Like" to R.id.reactionLike, "Love" to R.id.reactionLove, "Laugh" to R.id.reactionLaugh,
            "Wow" to R.id.reactionWow, "Sad" to R.id.reactionSad, "Angry" to R.id.reactionAngry
        ).mapValues { findViewById<ImageView>(it.value) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reaction_picker)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        postId = intent.getStringExtra("post_id").orEmpty()
        if (postId.isBlank() || me.isBlank()) {
            Toast.makeText(this, "Open reactions from a post", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        findViewById<View>(R.id.reactionPostImage).visibility = View.GONE
        findViewById<View>(R.id.reactionSampleComment).visibility = View.GONE
        findViewById<View>(R.id.reactionMostRelevant).visibility = View.GONE
        reactionViews.forEach { (type, view) ->
            view.setOnClickListener {
                Reactions.react(this, postId, authorId, me, type, myReaction)
                finish()
            }
        }
        loadPost()
    }

    private fun loadPost() {
        FirebaseData.posts.child(postId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                Toast.makeText(this, "This post was removed", Toast.LENGTH_SHORT).show()
                finish()
                return@addOnSuccessListener
            }
            val post = FeedPost.from(snapshot)
            authorId = post.authorId
            val firstName = post.authorName.substringBefore(" ")
            findViewById<TextView>(R.id.reactionToolbarTitle).text = if (post.authorId == me) "Your post" else "$firstName's post"
            findViewById<TextView>(R.id.reactionAuthorName).text = post.authorName
            findViewById<TextView>(R.id.reactionPostText).text = post.text
            findViewById<TextView>(R.id.reactionPostTime).text = TimeFormat.ago(post.createdAt)
            val avatar = findViewById<TextView>(R.id.reactionAuthorButton)
            ProfileCache.get(post.authorId) { AvatarBinder.bind(avatar, it.name, it.photo) }
            avatar.setOnClickListener { startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", post.authorId)) }
            post.images.firstOrNull()?.let { ref ->
                findViewById<ImageView>(R.id.reactionPostImage).apply { visibility = View.VISIBLE; MediaLoader.load(this, ref) }
            }
        }.addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not load the post", Toast.LENGTH_LONG).show() }
    }

    override fun onStart() {
        super.onStart()
        if (postId.isBlank()) return
        listeners.value(FirebaseData.postReactions.child(postId)) { snapshot ->
            val summary = Reactions.summarize(snapshot, me)
            myReaction = summary.mine
            Reactions.bindSummary(
                listOf(findViewById(R.id.reactionSummary1), findViewById(R.id.reactionSummary2), findViewById(R.id.reactionSummary3)),
                findViewById(R.id.reactionSummaryText), summary
            )
            highlight()
        }
        listeners.value(FirebaseData.comments.child(postId)) { snapshot ->
            val count = snapshot.childrenCount.toInt()
            findViewById<TextView>(R.id.reactionCommentCount).text = if (count == 1) "1 comment" else if (count == 0) "" else "$count comments"
            // "Most relevant": the newest top-level comment.
            val latest = snapshot.children.filter { it.child("parentCommentId").getValue(String::class.java).isNullOrBlank() }
                .maxByOrNull { it.child("createdAt").getValue(Long::class.java) ?: 0L }
            val row = findViewById<View>(R.id.reactionSampleComment)
            findViewById<View>(R.id.reactionMostRelevant).visibility = if (latest == null) View.GONE else View.VISIBLE
            row.visibility = if (latest == null) View.GONE else View.VISIBLE
            if (latest != null) {
                val name = latest.child("authorName").getValue(String::class.java) ?: "Kinnect member"
                val text = latest.child("text").getValue(String::class.java).orEmpty().ifBlank { "Photo" }
                findViewById<TextView>(R.id.reactionCommentText).text = "$name\n$text"
                val commenter = findViewById<TextView>(R.id.reactionCommenterButton)
                ProfileCache.get(latest.child("authorId").getValue(String::class.java).orEmpty()) { AvatarBinder.bind(commenter, it.name, it.photo) }
            }
        }
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    /** Enlarges my current reaction and puts its name in the dark bubble above it. */
    private fun highlight() {
        val tooltip = findViewById<TextView>(R.id.reactionTooltip)
        reactionViews.forEach { (type, view) ->
            val selected = type == myReaction
            view.animate().scaleX(if (selected) 1.3f else 1f).scaleY(if (selected) 1.3f else 1f).setDuration(120).start()
        }
        val selectedView = myReaction?.let { reactionViews[it] }
        if (selectedView == null) {
            tooltip.visibility = View.INVISIBLE
            return
        }
        tooltip.text = myReaction
        tooltip.visibility = View.VISIBLE
        selectedView.post {
            val bar = selectedView.parent as View
            tooltip.translationX = bar.left + selectedView.left + selectedView.width / 2f - tooltip.width / 2f - tooltip.left
        }
    }
}
