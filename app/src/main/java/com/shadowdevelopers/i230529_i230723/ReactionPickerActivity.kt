package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Reaction picker (05): the post under a dim overlay with the six reactions on top. The current reaction
 * is enlarged with its name above it; tapping it again removes it, tapping another one changes it.
 */
class ReactionPickerActivity : AppCompatActivity() {
    private val me by lazy { Backend.uid.orEmpty() }
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

    /** The post, its reaction summary and the newest comment: SQLite first, then the server (also while offline). */
    private fun loadPost(fromServer: Boolean = false) {
        Backend.call({ PostRepo.post(postId, fromServer) to runCatching { PostRepo.comments(postId, fromServer) }.getOrDefault(emptyList()) }) { result ->
            val (post, comments) = result.getOrNull() ?: return@call
            if (post == null) {
                if (fromServer) {
                    Toast.makeText(this, "This post is not available", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@call
            }
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
            val summary = Reactions.summarize(post, me)
            myReaction = summary.mine
            Reactions.bindSummary(
                listOf(findViewById(R.id.reactionSummary1), findViewById(R.id.reactionSummary2), findViewById(R.id.reactionSummary3)),
                findViewById(R.id.reactionSummaryText), summary
            )
            highlight()
            val count = post.commentCount
            findViewById<TextView>(R.id.reactionCommentCount).text = if (count == 1) "1 comment" else if (count == 0) "" else "$count comments"
            // "Most relevant": the newest top-level comment.
            val latest = comments.filter { it.parentId.isBlank() }.maxByOrNull { it.createdAt }
            val row = findViewById<View>(R.id.reactionSampleComment)
            findViewById<View>(R.id.reactionMostRelevant).visibility = if (latest == null) View.GONE else View.VISIBLE
            row.visibility = if (latest == null) View.GONE else View.VISIBLE
            if (latest != null) {
                findViewById<TextView>(R.id.reactionCommentText).text = "${latest.authorName.ifBlank { "Kinnect member" }}\n${latest.text.ifBlank { "Photo" }}"
                val commenter = findViewById<TextView>(R.id.reactionCommenterButton)
                ProfileCache.get(latest.authorId) { AvatarBinder.bind(commenter, it.name, it.photo) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (postId.isBlank()) return
        listeners.poll(8_000) { loadPost(fromServer = true) }
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
