package com.example.i230529_a1

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import java.util.Date

data class Conversation(val chatId: String, val peerUid: String, var name: String = "Kinnect member", var lastMessage: String = "", var lastTime: Long = 0L, var unread: Int = 0, var online: Boolean = false)

class ChatsActivity : AppCompatActivity() {
    private val uid by lazy { FirebaseAuth.getInstance().currentUser?.uid }
    private val conversations = linkedMapOf<String, Conversation>()
    private val listeners = mutableMapOf<String, MutableList<Pair<DatabaseReference, ValueEventListener>>>()
    private var chatsListener: ValueEventListener? = null
    private lateinit var adapter: ConversationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        adapter = ConversationAdapter { conversation ->
            startActivity(Intent(this, ChatActivity::class.java)
                .putExtra("person_uid", conversation.peerUid)
                .putExtra("person_name", conversation.name)
                .putExtra("chat_id", conversation.chatId))
        }
        findViewById<RecyclerView>(R.id.chatsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@ChatsActivity)
            adapter = this@ChatsActivity.adapter
        }
        uid?.let { userId ->
            val observer = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val currentIds = snapshot.children.mapNotNull { child ->
                        val peer = child.getValue(String::class.java) ?: return@mapNotNull null
                        val chatId = child.key ?: return@mapNotNull null
                        chatId to peer
                    }.toMap()
                    listeners.keys.filter { it !in currentIds }.forEach { removeConversationListeners(it); conversations.remove(it) }
                    currentIds.forEach { (chatId, peerUid) -> if (chatId !in listeners) listenConversation(userId, chatId, peerUid) }
                    refresh()
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
            }
            chatsListener = observer
            FirebaseData.userChats.child(userId).addValueEventListener(observer)
        }
    }

    private fun listenConversation(userId: String, chatId: String, peerUid: String) {
        val conversation = Conversation(chatId, peerUid)
        conversations[chatId] = conversation
        val attached = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()
        fun add(reference: DatabaseReference, action: (DataSnapshot) -> Unit) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { action(snapshot); refresh() }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
            }
            reference.addValueEventListener(listener)
            attached.add(reference to listener)
        }
        add(FirebaseData.profiles.child(peerUid)) { profile -> conversation.name = profile.child("name").getValue(String::class.java) ?: "Kinnect member" }
        add(FirebaseData.database.getReference("chats").child(chatId).child("lastMessage")) { message ->
            conversation.lastMessage = message.child("text").getValue(String::class.java).orEmpty()
            conversation.lastTime = message.child("timestamp").getValue(Long::class.java) ?: 0L
        }
        add(FirebaseData.database.getReference("chats").child(chatId).child("unread").child(userId)) { count -> conversation.unread = count.getValue(Int::class.java) ?: 0 }
        add(FirebaseData.database.getReference("presence").child(peerUid)) { status -> conversation.online = status.child("online").getValue(Boolean::class.java) == true }
        listeners[chatId] = attached
    }

    private fun refresh() {
        adapter.setItems(conversations.values.sortedByDescending { it.lastTime })
    }

    private fun removeConversationListeners(chatId: String) {
        listeners.remove(chatId)?.forEach { (reference, listener) -> reference.removeEventListener(listener) }
    }

    override fun onDestroy() {
        val userId = uid
        if (userId != null) chatsListener?.let { FirebaseData.userChats.child(userId).removeEventListener(it) }
        listeners.keys.toList().forEach { removeConversationListeners(it) }
        super.onDestroy()
    }
}

class ConversationAdapter(private val onOpen: (Conversation) -> Unit) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {
    private val items = mutableListOf<Conversation>()

    fun setItems(conversations: List<Conversation>) {
        items.clear()
        items.addAll(conversations)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        return ConversationViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false))
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class ConversationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(conversation: Conversation) {
            itemView.findViewById<TextView>(R.id.chatListName).text = conversation.name
            itemView.findViewById<TextView>(R.id.chatListAvatar).text = conversation.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            itemView.findViewById<TextView>(R.id.chatLastMessage).text = conversation.lastMessage
            itemView.findViewById<TextView>(R.id.chatLastTime).text = if (conversation.lastTime > 0) android.text.format.DateUtils.getRelativeTimeSpanString(conversation.lastTime, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString() else ""
            val unread = itemView.findViewById<TextView>(R.id.chatUnreadCount)
            unread.visibility = if (conversation.unread > 0) View.VISIBLE else View.GONE
            unread.text = if (conversation.unread > 99) "99+" else conversation.unread.toString()
            itemView.findViewById<View>(R.id.chatOnlineDot).visibility = if (conversation.online) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onOpen(conversation) }
        }
    }
}
