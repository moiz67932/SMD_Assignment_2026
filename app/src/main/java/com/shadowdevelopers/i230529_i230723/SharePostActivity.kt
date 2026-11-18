package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Share post (new screen, Kinnect style). Sends a post into a friend's chat as a message with type "post":
 * sharedPostId plus a small snapshot (author, snippet, first photo) so the chat can draw a card without
 * reading the post again. Extra in: "post_id".
 */
class SharePostActivity : AppCompatActivity() {
    private val me by lazy { Backend.uid.orEmpty() }
    private val listeners = ListenerBag()
    private val friends = mutableListOf<String>()
    private val sent = mutableSetOf<String>()
    private var post: FeedPost? = null
    private var loaded = false
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_post)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        val postId = intent.getStringExtra("post_id").orEmpty()
        if (postId.isBlank() || me.isBlank()) {
            finish()
            return
        }
        adapter = Adapter()
        findViewById<RecyclerView>(R.id.shareFriendsList).apply {
            layoutManager = LinearLayoutManager(this@SharePostActivity)
            adapter = this@SharePostActivity.adapter
        }
        Backend.call({ runCatching { PostRepo.post(postId, fromServer = true) }.getOrNull() ?: PostRepo.post(postId, fromServer = false) }) { result ->
            val value = result.getOrNull()
            if (value == null) {
                Toast.makeText(this, "This post is not available", Toast.LENGTH_SHORT).show()
                finish()
                return@call
            }
            post = value
            findViewById<TextView>(R.id.sharePostAuthor).text = value.authorName
            findViewById<TextView>(R.id.sharePostSnippet).text = value.text.ifBlank { if (value.images.isEmpty()) "" else "${value.images.size} photo(s)" }
            value.images.firstOrNull()?.let { ref ->
                findViewById<ImageView>(R.id.sharePostImage).apply { visibility = View.VISIBLE; MediaLoader.load(this, ref) }
            }
            adapter.notifyDataSetChanged()
        }
    }

    override fun onStart() {
        super.onStart()
        if (me.isBlank()) return
        // My friends (to send the post to), kept fresh while the screen is open.
        val friendsChanged: () -> Unit = {
            friends.clear()
            friends.addAll(Friends.friends)
            loaded = true
            adapter.notifyDataSetChanged()
            findViewById<TextView>(R.id.shareEmpty).apply {
                visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
                text = if (loaded) "Add friends to share posts with them" else "Loading your friends…"
            }
        }
        Friends.observe(friendsChanged)
        listeners.onRemove { Friends.unobserve(friendsChanged) }
        Friends.refresh()
        friendsChanged()
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    private fun send(friend: String, button: Button) {
        val value = post ?: return
        button.isEnabled = false
        button.text = "…"
        val note = findViewById<EditText>(R.id.shareNoteInput).text.toString().trim()
        val fields = mapOf<String, Any>(
            "sharedPostId" to value.id,
            "sharedAuthorName" to value.authorName,
            "sharedSnippet" to value.text.take(140),
            "sharedImage" to value.images.firstOrNull().orEmpty()
        )
        ChatSender.send(this, me, friend, type = "post", text = note, preview = "Shared a post", fields = fields,
            onSent = {
                sent.add(friend)
                adapter.notifyDataSetChanged()
            },
            onFailed = {
                button.isEnabled = true
                button.text = "Send"
            })
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_share_friend, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(friends[position])
        override fun getItemCount() = friends.size

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(uid: String) {
                itemView.tag = uid
                ProfileCache.get(uid) { profile ->
                    if (itemView.tag != uid) return@get
                    itemView.findViewById<TextView>(R.id.shareFriendName).text = profile.name
                    AvatarBinder.bind(itemView.findViewById(R.id.shareFriendAvatar), profile.name, profile.photo)
                }
                val button = itemView.findViewById<Button>(R.id.shareFriendSend)
                val done = uid in sent
                button.text = if (done) "Sent" else "Send"
                button.isEnabled = !done && post != null
                button.alpha = if (done) 0.6f else 1f
                button.setOnClickListener { send(uid, button) }
            }
        }
    }
}
