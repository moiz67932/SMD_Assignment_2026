package com.shadowdevelopers.i230529_i230723

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth

/**
 * Close friends (new screen in the Kinnect style). Lists my friends; tapping one adds or removes them
 * from closeFriends/{me}/{friendUid}. Only they can read stories/{me}/closeFriends.
 */
class CloseFriendsActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    private val friends = mutableListOf<String>()
    private val chosen = mutableSetOf<String>()
    private var friendsLoaded = false
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_close_friends)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        if (me.isBlank()) {
            finish()
            return
        }
        adapter = Adapter()
        findViewById<RecyclerView>(R.id.closeFriendsList).apply {
            layoutManager = LinearLayoutManager(this@CloseFriendsActivity)
            adapter = this@CloseFriendsActivity.adapter
        }
    }

    override fun onStart() {
        super.onStart()
        if (me.isBlank()) return
        listeners.value(FirebaseData.friends.child(me)) { snapshot ->
            friends.clear()
            snapshot.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNullTo(friends) { it.key }
            friendsLoaded = true
            render()
        }
        listeners.value(Stories.closeFriends.child(me)) { snapshot ->
            chosen.clear()
            snapshot.children.mapNotNullTo(chosen) { it.key }
            render()
        }
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    private fun render() {
        adapter.notifyDataSetChanged()
        val count = friends.count { it in chosen }
        findViewById<TextView>(R.id.closeFriendsCount).text = if (count == 0) "" else "$count selected"
        findViewById<TextView>(R.id.closeFriendsEmpty).apply {
            visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
            text = if (friendsLoaded) "Add friends first; only friends can be close friends." else "Loading your friends…"
        }
    }

    private fun toggle(uid: String) {
        val reference = Stories.closeFriends.child(me).child(uid)
        val task = if (uid in chosen) reference.removeValue() else reference.setValue(true)
        task.addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not update the list", Toast.LENGTH_SHORT).show() }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_close_friend, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(friends[position])
        override fun getItemCount() = friends.size

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(uid: String) {
                itemView.tag = uid
                val name = itemView.findViewById<TextView>(R.id.closeFriendName)
                val avatar = itemView.findViewById<TextView>(R.id.closeFriendAvatar)
                ProfileCache.get(uid) { profile ->
                    if (itemView.tag != uid) return@get
                    name.text = profile.name
                    AvatarBinder.bind(avatar, profile.name, profile.photo)
                }
                val star = itemView.findViewById<ImageView>(R.id.closeFriendStar)
                val selected = uid in chosen
                star.setBackgroundResource(if (selected) R.drawable.circle_green else R.drawable.circle_soft)
                star.setColorFilter(ContextCompat.getColor(itemView.context, if (selected) R.color.white else R.color.text_secondary))
                itemView.setOnClickListener { toggle(uid) }
            }
        }
    }
}
