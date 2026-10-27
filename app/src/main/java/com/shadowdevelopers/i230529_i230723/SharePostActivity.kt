package com.shadowdevelopers.i230529_i230723

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
import com.google.firebase.auth.FirebaseAuth

/**
 * Share post (new screen, Kinnect style). Sends a post into a friend's chat as a message with type "post":
 * sharedPostId plus a small snapshot (author, snippet, first photo) so the chat can draw a card without
 * reading the post again. Extra in: "post_id".
 */
class SharePostActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
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
        FirebaseData.posts.child(postId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                Toast.makeText(this, "This post was removed", Toast.LENGTH_SHORT).show()
                finish()
                return@addOnSuccessListener
            }
            val value = FeedPost.from(snapshot)
            post = value
            findViewById<TextView>(R.id.sharePostAuthor).text = value.authorName
            findViewById<TextView>(R.id.sharePostSnippet).text = value.text.ifBlank { if (value.images.isEmpty()) "" else "${value.images.size} photo(s)" }
            value.images.firstOrNull()?.let { ref ->
                findViewById<ImageView>(R.id.sharePostImage).apply { visibility = View.VISIBLE; MediaLoader.load(this, ref) }
            }
            adapter.notifyDataSetChanged()
        }.addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not load the post", Toast.LENGTH_LONG).show() }
    }

    override fun onStart() {
        super.onStart()
        if (me.isBlank()) return
        listeners.value(FirebaseData.friends.child(me)) { snapshot ->
            friends.clear()
            snapshot.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNullTo(friends) { it.key }
            loaded = true
            adapter.notifyDataSetChanged()
            findViewById<TextView>(R.id.shareEmpty).apply {
                visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
                text = if (loaded) "Add friends to share posts with them" else "Loading your friends…"
            }
        }
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
