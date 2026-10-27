package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/** Your story (12): my active stories, who has seen each one ("Seen by N"), hearts for Love reactions, delete. */
class YourStoryActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    private var storyListeners = mutableListOf<Pair<Query, ValueEventListener>>()
    private val byAudience = mutableMapOf<String, List<Story>>()
    private var shownIds: List<String> = emptyList()
    private var viewers: List<Pair<String, Long>> = emptyList()
    private lateinit var player: StoryPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_your_story)
        findViewById<View>(R.id.doneButton).setOnClickListener { finish() }
        if (me.isBlank()) {
            finish()
            return
        }
        ProfileCache.get(me) { AvatarBinder.bind(findViewById(R.id.storyAvatar), it.name, it.photo) }
        player = StoryPlayer(this, onShow = { story, _ -> watch(story) }, onFinished = { finish() })
        findViewById<View>(R.id.yourCreateButton).setOnClickListener { startActivity(Intent(this, CameraActivity::class.java)) }
        findViewById<View>(R.id.yourSeenBy).setOnClickListener { showViewers() }
        listOf(R.id.yourHighlightButton, R.id.yourSendButton).forEach { id ->
            findViewById<View>(id).setOnClickListener { Toast.makeText(this, "Not part of this version", Toast.LENGTH_SHORT).show() }
        }
        findViewById<View>(R.id.yourMoreButton).setOnClickListener { anchor ->
            val story = player.current() ?: return@setOnClickListener
            player.setHolding(true)
            PopupMenu(this, anchor).apply {
                menu.add("Delete this story")
                setOnMenuItemClickListener { confirmDelete(story); true }
                setOnDismissListener { player.setHolding(false) }
            }.show()
        }
    }

    override fun onStart() {
        super.onStart()
        if (me.isBlank()) return
        setEmpty("Loading your story…")
        listOf(Stories.PUBLIC, Stories.CLOSE_FRIENDS).forEach { audience ->
            listeners.value(Stories.stories.child(me).child(audience)) { snapshot ->
                byAudience[audience] = Stories.active(snapshot, me)
                refresh()
            }
        }
    }

    override fun onStop() {
        listeners.removeAll()
        clearStoryListeners()
        player.setHolding(true)
        super.onStop()
    }

    override fun onDestroy() {
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    /** Restarts playback only when the set of stories changed (a new one was added or one was deleted). */
    private fun refresh() {
        if (byAudience.size < 2) return
        val all = byAudience.values.flatten().sortedBy { it.createdAt }
        if (all.isEmpty()) {
            setEmpty("You have no active stories. Tap Create to add one.")
            return
        }
        setEmpty(null)
        val ids = all.map { it.id }
        if (ids != shownIds) {
            shownIds = ids
            player.play(all, player.index.coerceAtMost(all.size - 1))
        } else {
            player.setHolding(false)
        }
    }

    private fun setEmpty(text: String?) {
        findViewById<TextView>(R.id.yourEmpty).apply {
            visibility = if (text == null) View.GONE else View.VISIBLE
            this.text = text.orEmpty()
        }
    }

    private fun clearStoryListeners() {
        storyListeners.forEach { (query, listener) -> query.removeEventListener(listener) }
        storyListeners.clear()
    }

    /** Live viewers and Love reactions for the story on screen. */
    private fun watch(story: Story) {
        val audience = if (story.audience == Stories.CLOSE_FRIENDS) "Close friends" else "Public"
        findViewById<TextView>(R.id.storyMeta).text = "${TimeFormat.ago(story.createdAt)} · $audience"
        clearStoryListeners()
        val viewsQuery = Stories.views.child(me).child(story.id)
        val viewsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewers = snapshot.children.map { it.key.orEmpty() to (it.getValue(Long::class.java) ?: 0L) }.sortedByDescending { it.second }
                showSeenBy()
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        viewsQuery.addValueEventListener(viewsListener)
        storyListeners.add(viewsQuery to viewsListener)
        val reactionsQuery = Stories.reactions.child(me).child(story.id)
        val reactionsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = showHearts(snapshot.children.count { it.getValue(String::class.java) == "Love" })
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        reactionsQuery.addValueEventListener(reactionsListener)
        storyListeners.add(reactionsQuery to reactionsListener)
    }

    private fun showSeenBy() {
        findViewById<TextView>(R.id.yourSeenCount).text = if (viewers.isEmpty()) "No views yet" else "Seen by ${viewers.size}"
        listOf(R.id.yourSeen1, R.id.yourSeen2, R.id.yourSeen3).forEachIndexed { index, id ->
            val view = findViewById<TextView>(id)
            val uid = viewers.getOrNull(index)?.first
            view.visibility = if (uid == null) View.GONE else View.VISIBLE
            if (uid != null) ProfileCache.get(uid) { AvatarBinder.bind(view, it.name, it.photo) }
        }
    }

    /** Up to four hearts, growing and getting more opaque towards the bottom, as in the design. */
    private fun showHearts(count: Int) {
        val column = findViewById<LinearLayout>(R.id.yourHearts)
        column.removeAllViews()
        val density = resources.displayMetrics.density
        val sizes = listOf(24, 28, 31, 35)
        val alphas = listOf(0.45f, 0.65f, 0.8f, 1f)
        val shown = count.coerceAtMost(4)
        (4 - shown until 4).forEach { i ->
            column.addView(ImageView(this).apply {
                setImageResource(R.drawable.reaction_love)
                alpha = alphas[i]
                layoutParams = LinearLayout.LayoutParams((sizes[i] * density).toInt(), (sizes[i] * density).toInt()).apply { topMargin = (8 * density).toInt() }
            })
        }
    }

    private fun showViewers() {
        if (viewers.isEmpty()) return
        player.setHolding(true)
        val names = arrayOfNulls<String>(viewers.size)
        var left = viewers.size
        viewers.forEachIndexed { index, (uid, time) ->
            ProfileCache.get(uid) { profile ->
                names[index] = "${profile.name} · ${TimeFormat.ago(time)}"
                left--
                if (left == 0 && !isFinishing) {
                    AlertDialog.Builder(this).setTitle("Seen by ${viewers.size}").setItems(names.filterNotNull().toTypedArray(), null)
                        .setOnDismissListener { player.setHolding(false) }.show()
                }
            }
        }
    }

    private fun confirmDelete(story: Story) {
        AlertDialog.Builder(this).setMessage("Delete this story?")
            .setPositiveButton("Delete") { _, _ ->
                FirebaseData.database.reference.updateChildren(mapOf(
                    "stories/$me/${story.audience}/${story.id}" to null,
                    "storyViews/$me/${story.id}" to null,
                    "storyReactions/$me/${story.id}" to null
                )).addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not delete the story", Toast.LENGTH_LONG).show() }
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { player.setHolding(false) }
            .show()
    }
}
