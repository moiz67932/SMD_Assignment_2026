package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ValueEventListener

class NotificationsActivity : AppCompatActivity() {
    private val uid by lazy { FirebaseAuth.getInstance().currentUser?.uid }
    private var notificationListener: ValueEventListener? = null
    private lateinit var newAdapter: NotificationAdapter
    private lateinit var earlierAdapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        open(R.id.searchButton, SearchActivity::class.java)
        open(R.id.chatsButton, ChatsActivity::class.java)
        open(R.id.homeTab, HomeActivity::class.java)
        open(R.id.friendsTab, FriendsActivity::class.java)
        open(R.id.marketTab, MarketplaceActivity::class.java)
        open(R.id.menuTab, MenuActivity::class.java)

        newAdapter = NotificationAdapter(::openNotification)
        earlierAdapter = NotificationAdapter(::openNotification)
        findViewById<RecyclerView>(R.id.newNotificationsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@NotificationsActivity)
            isNestedScrollingEnabled = false
            adapter = newAdapter
        }
        findViewById<RecyclerView>(R.id.earlierNotificationsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@NotificationsActivity)
            isNestedScrollingEnabled = false
            adapter = earlierAdapter
        }
        uid?.let { userId ->
            val observer = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val items = snapshot.children.mapNotNull { item ->
                        val senderId = item.child("senderId").getValue(String::class.java) ?: return@mapNotNull null
                        KinnectNotification(
                            id = item.key.orEmpty(),
                            senderId = senderId,
                            type = item.child("type").getValue(String::class.java).orEmpty(),
                            body = item.child("body").getValue(String::class.java).orEmpty(),
                            chatId = item.child("chatId").getValue(String::class.java).orEmpty(),
                            createdAt = item.child("createdAt").getValue(Long::class.java) ?: 0L,
                            read = item.child("read").getValue(Boolean::class.java) == true
                        )
                    }.sortedByDescending { it.createdAt }
                    val newItems = items.filter { !it.read }
                    val earlierItems = items.filter { it.read }
                    newAdapter.setItems(newItems)
                    earlierAdapter.setItems(earlierItems)
                    findViewById<View>(R.id.newNotificationsHeading).visibility = if (newItems.isEmpty()) View.GONE else View.VISIBLE
                    findViewById<View>(R.id.earlierNotificationsHeading).visibility = if (earlierItems.isEmpty()) View.GONE else View.VISIBLE
                    findViewById<View>(R.id.notificationsEmpty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Toast.makeText(this@NotificationsActivity, error.message, Toast.LENGTH_LONG).show()
                }
            }
            notificationListener = observer
            FirebaseData.database.getReference("notifications").child(userId).addValueEventListener(observer)
        }
    }

    private fun openNotification(item: KinnectNotification, name: String) {
        val userId = uid ?: return
        FirebaseData.database.getReference("notifications").child(userId).child(item.id).child("read").setValue(true)
        when (item.type) {
            "message", "screenshot", "call" -> startActivity(Intent(this, ChatActivity::class.java).putExtra("person_uid", item.senderId).putExtra("person_name", name).putExtra("chat_id", item.chatId))
            "friendRequest", "friendAccepted" -> startActivity(Intent(this, FriendsActivity::class.java))
            else -> startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", item.senderId))
        }
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener { startActivity(Intent(this, target)) }
    }

    override fun onDestroy() {
        uid?.let { userId -> notificationListener?.let { FirebaseData.database.getReference("notifications").child(userId).removeEventListener(it) } }
        super.onDestroy()
    }
}
