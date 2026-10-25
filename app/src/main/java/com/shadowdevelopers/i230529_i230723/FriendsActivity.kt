package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ValueEventListener

data class FriendRow(val uid: String, val name: String, val username: String, val isRequest: Boolean)

class FriendsActivity : AppCompatActivity() {
    private val uid by lazy { FirebaseAuth.getInstance().currentUser?.uid }
    private val requestIds = mutableListOf<String>()
    private val friendIds = mutableListOf<String>()
    private var requestsListener: ValueEventListener? = null
    private var friendsListener: ValueEventListener? = null
    private var showingFriends = false
    private lateinit var friendAdapter: FriendAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)
        open(R.id.searchButton, SearchActivity::class.java)
        open(R.id.chatsButton, ChatsActivity::class.java)
        Nav.tab(this, R.id.homeTab, HomeActivity::class.java)
        Nav.tab(this, R.id.marketTab, MarketplaceActivity::class.java)
        Nav.tab(this, R.id.notificationsTab, NotificationsActivity::class.java)
        Nav.tab(this, R.id.menuTab, MenuActivity::class.java)

        friendAdapter = FriendAdapter(
            onOpen = { openProfile(it) },
            onConfirm = { confirm(it) },
            onDelete = { delete(it) }
        )
        findViewById<RecyclerView>(R.id.friendsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@FriendsActivity)
            isNestedScrollingEnabled = false
            adapter = friendAdapter
        }
        findViewById<View>(R.id.suggestionsTab).setOnClickListener {
            showingFriends = false
            renderRows()
        }
        findViewById<View>(R.id.yourFriendsTab).setOnClickListener {
            showingFriends = true
            renderRows()
        }
        findViewById<View>(R.id.seeAllFriends).setOnClickListener {
            showingFriends = true
            renderRows()
        }

        uid?.let { userId ->
            val requestObserver = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    requestIds.clear()
                    snapshot.children.forEach { requestIds.add(it.key.orEmpty()) }
                    findViewById<TextView>(R.id.friendRequestCount).text = "Friend requests ${requestIds.size}"
                    renderRows()
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) = showError(error.message)
            }
            requestsListener = requestObserver
            FirebaseData.friendRequests.child(userId).addValueEventListener(requestObserver)

            val friendsObserver = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    friendIds.clear()
                    snapshot.children.forEach { if (it.getValue(Boolean::class.java) == true) friendIds.add(it.key.orEmpty()) }
                    renderRows()
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) = showError(error.message)
            }
            friendsListener = friendsObserver
            FirebaseData.friends.child(userId).addValueEventListener(friendsObserver)
        }
    }

    private fun renderRows() {
        val ids = if (showingFriends) friendIds.toList() else requestIds.toList()
        findViewById<TextView>(R.id.friendsSectionTitle).text = if (showingFriends) "Your friends" else "Friend requests"
        val rows = ids.map { FriendRow(it, "Loading…", "", !showingFriends) }
        friendAdapter.setRows(rows)
        rows.forEachIndexed { index, row ->
            FirebaseData.profiles.child(row.uid).get().addOnSuccessListener { profile ->
                if (showingFriends != !row.isRequest) return@addOnSuccessListener
                val currentIds = if (showingFriends) friendIds else requestIds
                if (currentIds.getOrNull(index) != row.uid) return@addOnSuccessListener
                val name = profile.child("name").getValue(String::class.java) ?: "Kinnect member"
                val username = profile.child("username").getValue(String::class.java).orEmpty()
                friendAdapter.updateRow(index, row.copy(name = name, username = username))
            }.addOnFailureListener { showError(it.localizedMessage ?: "Could not load friend profile") }
        }
    }

    private fun confirm(otherUid: String) {
        val userId = uid ?: return
        val notificationId = FirebaseData.database.getReference("notifications").child(otherUid).push().key ?: return
        val updates = mapOf<String, Any?>(
            "friends/$userId/$otherUid" to true,
            "friends/$otherUid/$userId" to true,
            "friendRequests/$userId/$otherUid" to null,
            "notifications/$otherUid/$notificationId" to mapOf("senderId" to userId, "type" to "friendAccepted", "body" to "accepted your friend request", "createdAt" to mapOf(".sv" to "timestamp"), "read" to false)
        )
        FirebaseData.database.reference.updateChildren(updates).addOnFailureListener { showError(it.localizedMessage ?: "Could not confirm request") }
    }

    private fun delete(otherUid: String) {
        val userId = uid ?: return
        FirebaseData.friendRequests.child(userId).child(otherUid).removeValue()
            .addOnFailureListener { showError(it.localizedMessage ?: "Could not delete request") }
    }

    private fun openProfile(personUid: String) {
        startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", personUid))
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener { startActivity(Intent(this, target)) }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        val userId = uid
        if (userId != null) {
            requestsListener?.let { FirebaseData.friendRequests.child(userId).removeEventListener(it) }
            friendsListener?.let { FirebaseData.friends.child(userId).removeEventListener(it) }
        }
        super.onDestroy()
    }
}

private class FriendAdapter(
    private val onOpen: (String) -> Unit,
    private val onConfirm: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {
    private val rows = mutableListOf<FriendRow>()

    fun setRows(items: List<FriendRow>) {
        rows.clear()
        rows.addAll(items)
        notifyDataSetChanged()
    }

    fun updateRow(position: Int, row: FriendRow) {
        if (position !in rows.indices) return
        rows[position] = row
        notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) = holder.bind(rows[position])
    override fun getItemCount() = rows.size

    inner class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.friendName)
        private val username = view.findViewById<TextView>(R.id.friendUsername)
        private val avatar = view.findViewById<TextView>(R.id.friendAvatar)
        private val actions = view.findViewById<View>(R.id.friendRequestActions)

        fun bind(row: FriendRow) {
            name.text = row.name
            username.text = if (row.username.isBlank()) "" else "@${row.username}"
            avatar.text = row.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            actions.visibility = if (row.isRequest) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onOpen(row.uid) }
            itemView.findViewById<Button>(R.id.confirmFriendRequest).setOnClickListener { onConfirm(row.uid) }
            itemView.findViewById<Button>(R.id.deleteFriendRequest).setOnClickListener { onDelete(row.uid) }
        }
    }
}
