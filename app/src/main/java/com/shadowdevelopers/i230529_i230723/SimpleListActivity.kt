package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * One list screen for the Menu shortcuts. Extra "mode":
 *   groups   - my groups (+ creates one); tap opens its chat
 *   saved    - posts I saved from the "..." button on a post
 *   memories - my own posts, oldest first ("on this day" style look back)
 *   events   - posts with an event from me and my friends
 */
class SimpleListActivity : AppCompatActivity() {
    private val me by lazy { Backend.uid.orEmpty() }
    private val listeners = ListenerBag()
    private val mode by lazy { intent.getStringExtra("mode") ?: "groups" }
    private lateinit var adapter: TextRowAdapter
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.screen_background)) }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0) }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            contentDescription = "Back"
            val p = (10 * density).toInt(); setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt())
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = titleFor(mode)
            textSize = 20f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (6 * density).toInt() }
        }
        bar.addView(back); bar.addView(title)
        if (mode == "groups") {
            bar.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_add)
                contentDescription = "Create group"
                setColorFilter(getColor(R.color.primary))
                val p = (10 * density).toInt(); setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt())
                setOnClickListener { createGroup() }
            })
        }
        empty = TextView(this).apply {
            gravity = Gravity.CENTER
            setPadding((24 * density).toInt(), (40 * density).toInt(), (24 * density).toInt(), 0)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        }
        adapter = TextRowAdapter(::open)
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@SimpleListActivity)
            this.adapter = this@SimpleListActivity.adapter
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (56 * density).toInt()))
        root.addView(empty)
        root.addView(list)
        setContentView(root)
        if (me.isBlank()) finish()
    }

    private fun titleFor(mode: String) = when (mode) {
        "saved" -> "Saved"
        "memories" -> "Memories"
        "events" -> "Events"
        else -> "Groups"
    }

    override fun onStart() {
        super.onStart()
        if (me.isBlank()) return
        when (mode) {
            "groups" -> listeners.value(FirebaseData.database.getReference("userGroups").child(me), onError = { show(emptyList(), Forms.message(it)) }) { snapshot ->
                show(snapshot.children.map { TextRow(it.key.orEmpty(), it.child("name").getValue(String::class.java) ?: "Group", "Tap to open the chat") },
                    "No groups yet. Tap + to create one with your friends.")
            }
            "saved" -> listeners.value(FirebaseData.database.getReference("saved").child(me), onError = { show(emptyList(), Forms.message(it)) }) { snapshot ->
                loadPosts(snapshot.children.mapNotNull { it.key }, "Nothing saved yet. Use the ⋯ button on a post and choose Save.") { true }
            }
            "memories" -> listeners.value(FirebaseData.posts.orderByChild("authorId").equalTo(me), onError = { show(emptyList(), Forms.message(it)) }) { snapshot ->
                val posts = snapshot.children.map { FeedPost.from(it) }.sortedBy { it.createdAt }
                show(posts.map(::rowFor), "Your memories appear here once you post.")
            }
            else -> {
                FirebaseData.friends.child(me).get().addOnSuccessListener { friends ->
                    val authors = listOf(me) + friends.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNull { it.key }
                    val found = mutableListOf<FeedPost>()
                    var left = authors.size
                    authors.forEach { uid ->
                        FirebaseData.posts.orderByChild("authorId").equalTo(uid).limitToLast(30).get().addOnCompleteListener { task ->
                            if (task.isSuccessful) found += task.result.children.map { FeedPost.from(it) }.filter { it.event.isNotBlank() }
                            if (--left == 0 && !isFinishing) show(found.sortedByDescending { it.createdAt }.map(::rowFor), "No events yet. Create one from Create post → Create event.")
                        }
                    }
                }.addOnFailureListener { show(emptyList(), Forms.message(it, "Could not load events")) }
            }
        }
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    private fun rowFor(post: FeedPost) = TextRow(
        post.id,
        if (post.event.isNotBlank()) "${post.event} · ${post.authorName}" else post.authorName,
        listOf(TimeFormat.ago(post.createdAt), post.text).filter { it.isNotBlank() }.joinToString(" · "),
        post.id
    )

    private fun loadPosts(ids: List<String>, emptyMessage: String, keep: (FeedPost) -> Boolean) {
        if (ids.isEmpty()) {
            show(emptyList(), emptyMessage)
            return
        }
        val found = mutableListOf<FeedPost>()
        var left = ids.size
        ids.forEach { id ->
            FirebaseData.posts.child(id).get().addOnCompleteListener { task ->
                if (task.isSuccessful && task.result.exists()) FeedPost.from(task.result).takeIf(keep)?.let { found += it }
                if (--left == 0 && !isFinishing) show(found.sortedByDescending { it.createdAt }.map(::rowFor), emptyMessage)
            }
        }
    }

    private fun show(rows: List<TextRow>, emptyMessage: String) {
        adapter.set(rows)
        empty.text = emptyMessage
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun open(row: TextRow) {
        if (mode == "groups") startActivity(Intent(this, GroupChatActivity::class.java).putExtra("group_id", row.id).putExtra("group_name", row.title))
        else startActivity(Intent(this, CommentsActivity::class.java).putExtra("post_id", row.id))
    }

    /** Group name first, then the friends who join. Everyone chosen (and me) can read and write in the group chat. */
    private fun createGroup() {
        val name = EditText(this).apply {
            hint = "Group name"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(android.text.InputFilter.LengthFilter(40))
            val pad = (20 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(this).setTitle("New group").setView(name)
            .setPositiveButton("Next") { _, _ ->
                val groupName = name.text.toString().trim()
                if (groupName.isBlank()) {
                    Toast.makeText(this, "Give the group a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                FriendPicker.multi(this, "Add friends to \"$groupName\"") { picked ->
                    if (picked.isEmpty()) {
                        Toast.makeText(this, "Choose at least one friend", Toast.LENGTH_SHORT).show()
                        return@multi
                    }
                    saveGroup(groupName, picked.map { it.first })
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun saveGroup(name: String, friends: List<String>) {
        val root = FirebaseData.database.reference
        val id = root.child("groups").push().key ?: return
        val members = (friends + me).associateWith { true }
        val updates = mutableMapOf<String, Any>(
            "groups/$id" to mapOf("name" to name, "createdBy" to me, "createdAt" to mapOf(".sv" to "timestamp"), "members" to members)
        )
        members.keys.forEach { uid -> updates["userGroups/$uid/$id"] = mapOf("name" to name) }
        root.updateChildren(updates)
            .addOnSuccessListener { Toast.makeText(this, "Group created", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(this, Forms.message(it, "Could not create the group"), Toast.LENGTH_LONG).show() }
    }
}
