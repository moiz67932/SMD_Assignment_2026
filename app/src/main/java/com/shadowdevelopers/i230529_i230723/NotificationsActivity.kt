package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener

/** Notifications (18): New (unread) and Earlier (read), newest first, 30 at a time. */
class NotificationsActivity : AppCompatActivity() {
    private val badges by lazy { TabBadges(this) }
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    private lateinit var state: FriendState
    private lateinit var newAdapter: NotificationAdapter
    private lateinit var earlierAdapter: NotificationAdapter
    private var pageSize = PAGE
    private var notificationsListener: ValueEventListener? = null
    private var notificationsQuery: com.google.firebase.database.Query? = null
    private val notifications get() = FirebaseData.database.getReference("notifications").child(me)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        if (me.isBlank()) {
            finish()
            return
        }
        open(R.id.searchButton, SearchActivity::class.java)
        open(R.id.chatsButton, ChatsActivity::class.java)
        Nav.tab(this, R.id.homeTab, HomeActivity::class.java)
        Nav.tab(this, R.id.friendsTab, FriendsActivity::class.java)
        Nav.tab(this, R.id.marketTab, MarketplaceActivity::class.java)
        Nav.tab(this, R.id.menuTab, MenuActivity::class.java)

        state = FriendState(me) { newAdapter.notifyDataSetChanged(); earlierAdapter.notifyDataSetChanged() }
        newAdapter = makeAdapter()
        earlierAdapter = makeAdapter()
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
        findViewById<View>(R.id.olderNotificationsButton).setOnClickListener {
            pageSize += PAGE
            listenNotifications()
        }
    }

    private fun makeAdapter() = NotificationAdapter(
        isPendingRequest = { state.incoming.containsKey(it) },
        onOpen = ::openNotification,
        onConfirm = { markRead(it); FriendActions.confirm(this, me, it.senderId) },
        onDelete = { markRead(it); FriendActions.deleteIncoming(this, me, it.senderId) },
        onMore = ::showMore
    )

    override fun onStart() {
        super.onStart()
        badges.start()
        if (me.isBlank()) return
        state.attach(listeners)
        listenNotifications()
    }

    override fun onStop() {
        badges.stop()
        listeners.removeAll()
        notificationsListener = null
        notificationsQuery = null
        super.onStop()
    }

    private fun listenNotifications() {
        val previousQuery = notificationsQuery
        val previous = notificationsListener
        if (previousQuery != null && previous != null) listeners.remove(previousQuery, previous)
        val query = notifications.orderByChild("createdAt").limitToLast(pageSize)
        notificationsQuery = query
        notificationsListener = listeners.value(query, onError = { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }) { snapshot ->
            val items = snapshot.children.mapNotNull { item ->
                val senderId = item.child("senderId").getValue(String::class.java) ?: return@mapNotNull null
                KinnectNotification(
                    id = item.key.orEmpty(),
                    senderId = senderId,
                    type = item.child("type").getValue(String::class.java).orEmpty(),
                    body = item.child("body").getValue(String::class.java).orEmpty(),
                    chatId = item.child("chatId").getValue(String::class.java).orEmpty(),
                    postId = item.child("postId").getValue(String::class.java).orEmpty(),
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
            findViewById<View>(R.id.olderNotificationsButton).visibility = if (snapshot.childrenCount >= pageSize) View.VISIBLE else View.GONE
        }
    }

    private fun markRead(item: KinnectNotification) {
        if (!item.read) notifications.child(item.id).child("read").setValue(true)
    }

    private fun showMore(item: KinnectNotification, anchor: View) {
        PopupMenu(this, anchor).apply {
            if (!item.read) menu.add(0, 1, 0, "Mark as read")
            menu.add(0, 2, 1, "Remove this notification")
            setOnMenuItemClickListener { option ->
                if (option.itemId == 1) markRead(item)
                else notifications.child(item.id).removeValue()
                    .addOnFailureListener { Toast.makeText(this@NotificationsActivity, it.localizedMessage ?: "Could not remove it", Toast.LENGTH_SHORT).show() }
                true
            }
        }.show()
    }

    private fun openNotification(item: KinnectNotification, name: String) {
        markRead(item)
        when (item.type) {
            "message", "screenshot", "call" -> startActivity(Intent(this, ChatActivity::class.java).putExtra("person_uid", item.senderId).putExtra("person_name", name))
            "reaction", "comment", "reply" ->
                if (item.postId.isNotBlank()) startActivity(Intent(this, CommentsActivity::class.java).putExtra("post_id", item.postId))
                else startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", item.senderId))
            else -> startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", item.senderId))
        }
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener { startActivity(Intent(this, target)) }
    }

    companion object {
        private const val PAGE = 30
    }
}
