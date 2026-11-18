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
            // GET groups/list.php (cached in SQLite); new groups appear without leaving the screen.
            "groups" -> listeners.poll(10_000) {
                Backend.call({
                    runCatching { Backend.api.get("groups/list.php").getJSONArray("groups") }.onSuccess { Backend.store.putEntity(me, "groups", "all", it.toString()) }
                        .getOrNull() ?: Backend.store.entity(me, "groups", "all")?.let { org.json.JSONArray(it) } ?: org.json.JSONArray()
                }) { result ->
                    val list = result.getOrNull() ?: return@call
                    show((0 until list.length()).map { list.getJSONObject(it) }.map { TextRow(it.getString("id"), it.optString("name").ifBlank { "Group" }, "${it.optInt("member_count")} members · tap to open the chat") },
                        "No groups yet. Tap + to create one with your friends.")
                }
            }
            // Saved posts are kept on this phone (SQLite); each post is shown from the cache or the server.
            "saved" -> Backend.call({
                val ids = Backend.store.entities(me, "saved").map { org.json.JSONObject(it).getString("id") }
                ids.mapNotNull { id -> runCatching { PostRepo.post(id, true) }.getOrNull() ?: PostRepo.post(id, false) }
            }) { result -> show(result.getOrDefault(emptyList()).sortedByDescending { it.createdAt }.map(::rowFor), "Nothing saved yet. Use the ⋯ button on a post and choose Save.") }
            // Memories: my own posts, oldest first.
            "memories" -> Backend.call({ runCatching { PostRepo.feed(me, true) }.getOrElse { PostRepo.feed(me, false) } }) { result ->
                show(result.getOrDefault(emptyList()).sortedBy { it.createdAt }.map(::rowFor), "Your memories appear here once you post.")
            }
            // Events: posts with an event from me and my friends (the feed).
            else -> Backend.call({ runCatching { PostRepo.feed(fromServer = true) }.getOrElse { PostRepo.feed(fromServer = false) } }) { result ->
                show(result.getOrDefault(emptyList()).filter { it.event.isNotBlank() }.map(::rowFor), "No events yet. Create one from Create post → Create event.")
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

    /** POST groups/create.php (queued): me + the chosen friends. */
    private fun saveGroup(name: String, friends: List<String>) {
        val id = Backend.newId()
        val payload = org.json.JSONObject().put("id", id).put("name", name).put("members", org.json.JSONArray(friends))
        Backend.call({ Backend.enqueue("group", id, org.json.JSONObject().put("id", id).put("name", name), "group.create", payload) }) { result ->
            result.onSuccess { Toast.makeText(this, if (Backend.online()) "Group created" else "Group saved. It will be created when you're online.", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(this, Forms.message(it as? Exception, "Could not create the group"), Toast.LENGTH_LONG).show() }
        }
    }

}
