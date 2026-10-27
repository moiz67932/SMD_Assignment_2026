package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Story viewer (11). Plays a friend's active stories, then continues with the next friend in "owner_ids".
 * Opening a story writes storyViews/{owner}/{storyId}/{me}; a reply or quick reaction goes to the owner as a chat message.
 */
class StoryViewerActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private var owners: List<String> = emptyList()
    private var ownerIndex = 0
    private lateinit var player: StoryPlayer
    private lateinit var replyInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_viewer)
        owners = intent.getStringArrayListExtra("owner_ids").orEmpty().ifEmpty { listOfNotNull(intent.getStringExtra("owner_id")) }
        ownerIndex = intent.getIntExtra("index", 0)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        if (owners.isEmpty() || me.isBlank()) {
            finish()
            return
        }
        player = StoryPlayer(this, onShow = ::onStoryShown, onFinished = { openOwner(ownerIndex + 1) })
        replyInput = findViewById(R.id.storyReplyInput)
        replyInput.setOnFocusChangeListener { _, focused -> player.setHolding(focused) }
        replyInput.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) { sendReply(); true } else false
        }
        mapOf(R.id.storyQuickLike to "Like", R.id.storyQuickLove to "Love", R.id.storyQuickLaugh to "Laugh").forEach { (id, type) ->
            findViewById<View>(id).setOnClickListener { react(type) }
        }
        findViewById<View>(R.id.storyMore).setOnClickListener { anchor ->
            player.setHolding(true)
            PopupMenu(this, anchor).apply {
                menu.add("View profile")
                setOnMenuItemClickListener {
                    startActivity(Intent(this@StoryViewerActivity, OtherProfileActivity::class.java).putExtra("person_uid", owners[ownerIndex]))
                    true
                }
                setOnDismissListener { player.setHolding(false) }
            }.show()
        }
        openOwner(ownerIndex)
    }

    /** Loads one person's active stories (public + close friends I may read) and plays them; skips people with none. */
    private fun openOwner(position: Int) {
        if (position !in owners.indices) {
            finish()
            return
        }
        ownerIndex = position
        val owner = owners[position]
        ProfileCache.get(owner) { profile ->
            findViewById<TextView>(R.id.storyAuthor).text = profile.name
            AvatarBinder.bind(findViewById(R.id.storyAvatar), profile.name, profile.photo)
        }
        val collected = mutableListOf<Story>()
        var pending = 2
        listOf(Stories.PUBLIC, Stories.CLOSE_FRIENDS).forEach { audience ->
            Stories.stories.child(owner).child(audience).get().addOnCompleteListener { task ->
                if (task.isSuccessful) collected.addAll(Stories.active(task.result, owner))
                pending--
                if (pending == 0) {
                    if (isFinishing || isDestroyed) return@addOnCompleteListener
                    if (collected.isEmpty()) openOwner(position + 1) else player.play(collected.sortedBy { it.createdAt })
                }
            }
        }
    }

    private fun onStoryShown(story: Story, index: Int) {
        val audience = if (story.audience == Stories.CLOSE_FRIENDS) "Close friends" else "Public"
        findViewById<TextView>(R.id.storyMeta).text = "${TimeFormat.ago(story.createdAt)} · $audience"
        if (story.ownerId != me) {
            Stories.views.child(story.ownerId).child(story.id).child(me).setValue(mapOf(".sv" to "timestamp"))
        }
    }

    private fun sendReply() {
        val story = player.current() ?: return
        val text = replyInput.text.toString().trim()
        if (text.isBlank()) return
        ChatSender.send(this, me, story.ownerId, type = "text", text = "Replied to your story: $text", preview = "Replied to your story",
            onSent = { Toast.makeText(this, "Reply sent", Toast.LENGTH_SHORT).show() })
        replyInput.text.clear()
        replyInput.clearFocus()
    }

    /** Quick reaction: stored on the story (for the hearts in Your story) and sent to the owner as a message. */
    private fun react(type: String) {
        val story = player.current() ?: return
        Stories.reactions.child(story.ownerId).child(story.id).child(me).setValue(type)
        val emoji = when (type) { "Love" -> "❤️"; "Laugh" -> "😆"; else -> "👍" }
        ChatSender.send(this, me, story.ownerId, type = "text", text = "Reacted $emoji to your story", preview = "Reacted $emoji to your story",
            onSent = { Toast.makeText(this, "Sent $emoji", Toast.LENGTH_SHORT).show() })
    }

    override fun onPause() {
        if (::player.isInitialized) player.setHolding(true)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::player.isInitialized && !replyInput.hasFocus()) player.setHolding(false)
    }

    override fun onDestroy() {
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
