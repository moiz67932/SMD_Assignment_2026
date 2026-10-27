package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

data class Conversation(
    val chatId: String,
    val peerUid: String,
    var lastMessage: String = "",
    var lastSenderId: String = "",
    var lastTime: Long = 0L,
    var unread: Int = 0
)

/**
 * Chats (20): my conversations from userChats/{me}, newest first, each with its lastMessage, my unread count
 * and the other person's presence. The strip at the top shows friends who are online now.
 */
class ChatsActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    private val conversations = linkedMapOf<String, Conversation>()
    private val perChat = mutableMapOf<String, MutableList<Pair<Query, ValueEventListener>>>()
    private val presence = mutableMapOf<String, Pair<Query, ValueEventListener>>()
    private val online = mutableSetOf<String>()
    private val friends = mutableListOf<String>()
    private var loaded = false
    private var filter = ""
    private lateinit var adapter: ConversationAdapter
    private lateinit var activeAdapter: ActiveAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        if (me.isBlank()) {
            finish()
            return
        }
        adapter = ConversationAdapter(me, isOnline = { it in online }) { openChat(it.peerUid) }
        findViewById<RecyclerView>(R.id.chatsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@ChatsActivity)
            adapter = this@ChatsActivity.adapter
        }
        activeAdapter = ActiveAdapter { openChat(it) }
        findViewById<RecyclerView>(R.id.chatsActiveRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@ChatsActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = activeAdapter
        }
        findViewById<EditText>(R.id.chatsSearchInput).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filter = s?.toString().orEmpty().trim().lowercase()
                refresh()
            }
        })
        findViewById<View>(R.id.chatsNewButton).setOnClickListener { pickFriend() }
        findViewById<View>(R.id.chatsYourNote).setOnClickListener { Toast.makeText(this, "Notes are not part of this version", Toast.LENGTH_SHORT).show() }
        findViewById<View>(R.id.chatsTabPeople).setOnClickListener { Nav.goTab(this, FriendsActivity::class.java) }
        findViewById<View>(R.id.chatsTabStories).setOnClickListener { Nav.goTab(this, HomeActivity::class.java) }
    }

    override fun onStart() {
        super.onStart()
        if (me.isBlank()) return
        listeners.value(FirebaseData.userChats.child(me), onError = { showEmpty(it.message) }) { snapshot ->
            val current = snapshot.children.mapNotNull { child -> child.getValue(String::class.java)?.let { child.key.orEmpty() to it } }.toMap()
            perChat.keys.filter { it !in current }.forEach { removeChat(it) }
            current.forEach { (chatId, peer) -> if (chatId !in perChat) listenChat(chatId, peer) }
            loaded = true
            refresh()
        }
        listeners.value(FirebaseData.friends.child(me)) { snapshot ->
            friends.clear()
            snapshot.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNullTo(friends) { it.key }
            friends.take(40).forEach { watchPresence(it) }
            refresh()
        }
    }

    override fun onStop() {
        listeners.removeAll()
        perChat.keys.toList().forEach { removeChat(it) }
        presence.values.forEach { (query, listener) -> query.removeEventListener(listener) }
        presence.clear()
        super.onStop()
    }

    private fun listenChat(chatId: String, peerUid: String) {
        val conversation = Conversation(chatId, peerUid)
        conversations[chatId] = conversation
        val attached = mutableListOf<Pair<Query, ValueEventListener>>()
        fun add(query: Query, action: (DataSnapshot) -> Unit) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { action(snapshot); refresh() }
                override fun onCancelled(error: DatabaseError) = Unit
            }
            query.addValueEventListener(listener)
            attached.add(query to listener)
        }
        val chat = FirebaseData.database.getReference("chats").child(chatId)
        add(chat.child("lastMessage")) { message ->
            conversation.lastMessage = message.child("text").getValue(String::class.java).orEmpty()
            conversation.lastSenderId = message.child("senderId").getValue(String::class.java).orEmpty()
            conversation.lastTime = message.child("timestamp").getValue(Long::class.java) ?: 0L
        }
        add(chat.child("unread").child(me)) { count -> conversation.unread = count.getValue(Int::class.java) ?: 0 }
        perChat[chatId] = attached
        watchPresence(peerUid)
    }

    private fun removeChat(chatId: String) {
        perChat.remove(chatId)?.forEach { (query, listener) -> query.removeEventListener(listener) }
        conversations.remove(chatId)
    }

    /** presence/{uid}/online, shared by the conversation rows and the active strip. */
    private fun watchPresence(uid: String) {
        if (uid in presence) return
        val query = FirebaseData.database.getReference("presence").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.child("online").getValue(Boolean::class.java) == true) online.add(uid) else online.remove(uid)
                refresh()
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        query.addValueEventListener(listener)
        presence[uid] = query to listener
    }

    private fun refresh() {
        val items = conversations.values.filter { it.lastTime > 0 || it.lastMessage.isNotBlank() }
            .filter { filter.isBlank() || ProfileCache.peekName(it.peerUid).lowercase().contains(filter) }
            .sortedByDescending { it.lastTime }
        adapter.setItems(items)
        activeAdapter.setItems(friends.filter { it in online })
        when {
            !loaded -> showEmpty("Loading chats…")
            items.isEmpty() && filter.isNotBlank() -> showEmpty("No chats match \"$filter\"")
            items.isEmpty() -> showEmpty("No conversations yet. Tap the pencil to message a friend.")
            else -> showEmpty(null)
        }
    }

    private fun showEmpty(text: String?) {
        findViewById<TextView>(R.id.chatsEmpty).apply {
            visibility = if (text == null) View.GONE else View.VISIBLE
            this.text = text.orEmpty()
        }
    }

    /** Pencil button: choose a friend to start (or continue) a conversation. */
    private fun pickFriend() {
        if (friends.isEmpty()) {
            Toast.makeText(this, "Add friends first to message them", Toast.LENGTH_SHORT).show()
            return
        }
        val names = arrayOfNulls<String>(friends.size)
        var left = friends.size
        friends.forEachIndexed { index, uid ->
            ProfileCache.get(uid) { profile ->
                names[index] = profile.name
                left--
                if (left == 0 && !isFinishing) {
                    AlertDialog.Builder(this).setTitle("New message")
                        .setItems(names.map { it.orEmpty() }.toTypedArray()) { _, which -> openChat(friends[which]) }
                        .show()
                }
            }
        }
    }

    private fun openChat(peerUid: String) {
        startActivity(Intent(this, ChatActivity::class.java).putExtra("person_uid", peerUid).putExtra("person_name", ProfileCache.peekName(peerUid)))
    }
}

class ConversationAdapter(
    private val me: String,
    private val isOnline: (String) -> Boolean,
    private val onOpen: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {
    private val items = mutableListOf<Conversation>()

    fun setItems(conversations: List<Conversation>) {
        items.clear()
        items.addAll(conversations.map { it.copy() })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ConversationViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false))

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class ConversationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(conversation: Conversation) {
            itemView.tag = conversation.chatId
            val name = itemView.findViewById<TextView>(R.id.chatListName)
            val avatar = itemView.findViewById<TextView>(R.id.chatListAvatar)
            ProfileCache.get(conversation.peerUid) { profile ->
                if (itemView.tag != conversation.chatId) return@get
                name.text = profile.name
                AvatarBinder.bind(avatar, profile.name, profile.photo)
            }
            // "You: See you at the pier · 1h"; bold while there are unread messages.
            val preview = (if (conversation.lastSenderId == me) "You: " else "") + conversation.lastMessage
            val time = if (conversation.lastTime > 0) " · ${TimeFormat.ago(conversation.lastTime)}" else ""
            val unread = conversation.unread > 0
            itemView.findViewById<TextView>(R.id.chatLastMessage).apply {
                text = preview + time
                setTypeface(null, if (unread) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(ContextCompat.getColor(context, if (unread) R.color.text_primary else R.color.text_secondary))
            }
            itemView.findViewById<View>(R.id.chatUnreadDot).visibility = if (unread) View.VISIBLE else View.GONE
            itemView.findViewById<View>(R.id.chatOnlineDot).visibility = if (isOnline(conversation.peerUid)) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onOpen(conversation) }
        }
    }
}

/** Friends who are online now (Chats active strip). */
class ActiveAdapter(private val onOpen: (String) -> Unit) : RecyclerView.Adapter<ActiveAdapter.Holder>() {
    private val uids = mutableListOf<String>()

    fun setItems(items: List<String>) {
        uids.clear()
        uids.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_active_friend, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(uids[position])
    override fun getItemCount() = uids.size

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(uid: String) {
            itemView.tag = uid
            ProfileCache.get(uid) { profile ->
                if (itemView.tag != uid) return@get
                itemView.findViewById<TextView>(R.id.activeName).text = profile.name.substringBefore(" ")
                AvatarBinder.bind(itemView.findViewById(R.id.activeAvatar), profile.name, profile.photo)
            }
            itemView.setOnClickListener { onOpen(uid) }
        }
    }
}
