package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Notifications (18): New (unread) and Earlier (read), newest first, 30 at a time. */
class NotificationsActivity : AppCompatActivity() {
    private val badges by lazy { TabBadges(this) }
    private val me by lazy { Backend.uid.orEmpty() }
    private val listeners = ListenerBag()
    private lateinit var state: FriendState
    private lateinit var newAdapter: NotificationAdapter
    private lateinit var earlierAdapter: NotificationAdapter
    private var pageSize = PAGE

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
        // New notifications appear while the screen is open (and after queued changes reach the server).
        listeners.poll(8_000) { listenNotifications() }
    }

    override fun onStop() {
        badges.stop()
        listeners.removeAll()
        super.onStop()
    }

    private fun listenNotifications() {
        Inbox.load(onCached = ::show) { result ->
            result.onSuccess(::show).onFailure {
                if (newAdapter.itemCount + earlierAdapter.itemCount == 0) Toast.makeText(this, Forms.message(it as? Exception, "Could not load notifications"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun show(all: List<KinnectNotification>) {
        val items = all.sortedByDescending { it.createdAt }.take(pageSize)
        val newItems = items.filter { !it.read }
        val earlierItems = items.filter { it.read }
        newAdapter.setItems(newItems)
        earlierAdapter.setItems(earlierItems)
        findViewById<View>(R.id.newNotificationsHeading).visibility = if (newItems.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.earlierNotificationsHeading).visibility = if (earlierItems.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.notificationsEmpty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.olderNotificationsButton).visibility = if (all.size > pageSize) View.VISIBLE else View.GONE
    }

    private fun markRead(item: KinnectNotification) {
        Inbox.markRead(item)
    }

    private fun showMore(item: KinnectNotification, anchor: View) {
        PopupMenu(this, anchor).apply {
            if (!item.read) menu.add(0, 1, 0, "Mark as read")
            menu.add(0, 2, 1, "Remove this notification")
            setOnMenuItemClickListener { option ->
                if (option.itemId == 1) markRead(item) else Inbox.remove(item)
                listenNotifications()
                true
            }
        }.show()
    }

    private fun openNotification(item: KinnectNotification, name: String) {
        markRead(item)
        when (item.type) {
            "message", "screenshot", "call" -> startActivity(Intent(this, ChatActivity::class.java).putExtra("person_uid", item.senderId).putExtra("person_name", name))
            "live" -> startActivity(Intent(this, LiveActivity::class.java).putExtra("host_uid", item.senderId))
            "tag", "event" ->
                if (item.postId.isNotBlank()) startActivity(Intent(this, CommentsActivity::class.java).putExtra("post_id", item.postId))
                else startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", item.senderId))
            "reaction", "comment", "reply" ->
                if (item.postId.isNotBlank()) startActivity(Intent(this, CommentsActivity::class.java).putExtra("post_id", item.postId))
                else startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", item.senderId))
            else -> startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", item.senderId))
        }
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener { Nav.goTab(this, target) }
    }

    companion object {
        private const val PAGE = 30
    }
}
