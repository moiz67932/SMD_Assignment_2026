package com.shadowdevelopers.i230529_i230723

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ChildEventListener

class ChatActivity : AppCompatActivity() {
    private val currentUser by lazy { FirebaseAuth.getInstance().currentUser }
    private var otherUid = ""
    private var personName = "Kinnect member"
    private var chatId = ""
    private var messagesQuery: Query? = null
    private var messageListener: ValueEventListener? = null
    private var presenceListener: ValueEventListener? = null
    private var callsListener: ChildEventListener? = null
    private var activeCallListener: ValueEventListener? = null
    private val reactionListeners = mutableMapOf<String, Pair<DatabaseReference, ValueEventListener>>()
    private var activeOutgoingCallId = ""
    private var replyingTo: ChatMessage? = null
    private lateinit var replyLabel: TextView
    private lateinit var input: EditText
    private lateinit var messageAdapter: MessageAdapter
    private var screenshotCallback: Activity.ScreenCaptureCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        personName = intent.getStringExtra("person_name") ?: "Kinnect member"
        otherUid = intent.getStringExtra("person_uid").orEmpty()
        replyLabel = findViewById(R.id.chatReplyLabel)
        replyLabel.visibility = View.GONE
        input = findViewById(R.id.messageInput)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.callButton).setOnClickListener { startCall(false) }
        findViewById<View>(R.id.videoCallButton).setOnClickListener { startCall(true) }
        findViewById<View>(R.id.sendMessageButton).setOnClickListener { sendMessage() }
        findViewById<View>(R.id.chatInfoButton).setOnClickListener {
            if (otherUid.isNotBlank()) startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", otherUid))
        }
        listOf(R.id.chatAttachmentButton, R.id.chatCameraButton, R.id.chatImageButton).forEach { viewId ->
            findViewById<View>(viewId).setOnClickListener { showMediaUnavailable() }
        }
        findViewById<View>(R.id.chatVoiceButton).setOnClickListener {
            Toast.makeText(this, "Voice messages are not available in this version", Toast.LENGTH_SHORT).show()
        }
        if (otherUid.isNotBlank()) {
            loadPerson()
            setupChat()
            registerScreenshotDetection()
        } else {
            Toast.makeText(this, "Choose a real user profile to start a chat", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadPerson() {
        FirebaseData.profiles.child(otherUid).get().addOnSuccessListener { profile ->
            personName = profile.child("name").getValue(String::class.java) ?: personName
            val initials = initials(personName)
            findViewById<TextView>(R.id.chatToolbarName).text = personName
            findViewById<TextView>(R.id.chatProfileName).text = personName
            findViewById<TextView>(R.id.chatToolbarAvatar).text = initials
            findViewById<TextView>(R.id.chatProfileAvatar).text = initials
            findViewById<TextView>(R.id.chatMessageAvatar).text = initials
            findViewById<TextView>(R.id.typingAvatar).text = initials
        }
        val presenceReference = FirebaseData.database.getReference("presence").child(otherUid)
        val observer = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                findViewById<TextView>(R.id.chatToolbarStatus).text = if (snapshot.child("online").getValue(Boolean::class.java) == true) "Active now" else "Offline"
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        presenceListener = observer
        presenceReference.addValueEventListener(observer)
    }

    private fun setupChat() {
        val uid = currentUser?.uid ?: return
        if (otherUid == uid) return
        chatId = listOf(uid, otherUid).sorted().joinToString("_")
        val chat = FirebaseData.database.getReference("chats").child(chatId)
        val members = chat.child("members")
        members.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                members.setValue(mapOf(uid to true, otherUid to true))
                    .addOnSuccessListener { finishChatSetup(chat, uid) }
                    .addOnFailureListener { failure ->
                        members.get().addOnSuccessListener { current ->
                            if (current.child(uid).getValue(Boolean::class.java) == true && current.child(otherUid).getValue(Boolean::class.java) == true) {
                                finishChatSetup(chat, uid)
                            } else {
                                Toast.makeText(this, failure.localizedMessage ?: "Could not create conversation", Toast.LENGTH_LONG).show()
                            }
                        }.addOnFailureListener { error -> Toast.makeText(this, error.localizedMessage ?: "Could not load conversation", Toast.LENGTH_LONG).show() }
                    }
            } else if (snapshot.child(uid).getValue(Boolean::class.java) == true && snapshot.child(otherUid).getValue(Boolean::class.java) == true) {
                finishChatSetup(chat, uid)
            } else {
                Toast.makeText(this, "This conversation is not available to your account", Toast.LENGTH_LONG).show()
                finish()
            }
        }.addOnFailureListener {
            Toast.makeText(this, it.localizedMessage ?: "Could not load conversation", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun finishChatSetup(chat: DatabaseReference, uid: String) {
        val indexes = mapOf("userChats/$uid/$chatId" to otherUid, "userChats/$otherUid/$chatId" to uid)
        FirebaseData.database.reference.updateChildren(indexes)
            .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not update chat list", Toast.LENGTH_LONG).show() }
        chat.child("unread").child(uid).setValue(0)
        val recycler = findViewById<RecyclerView>(R.id.messagesRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messageAdapter = MessageAdapter(
            uid,
            onReply = { message ->
                replyingTo = message
                replyLabel.text = "Replying to: ${message.text.take(48)}"
                replyLabel.visibility = View.VISIBLE
                replyLabel.setOnClickListener { replyingTo = null; replyLabel.visibility = View.GONE }
            },
            onHeart = { message -> toggleHeart(message) },
            onOptions = { message -> showMessageOptions(message) }
        )
        recycler.adapter = messageAdapter
        messagesQuery = chat.child("messages").orderByChild("timestamp").limitToLast(200)
        val observer = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { item ->
                    val senderId = item.child("senderId").getValue(String::class.java) ?: return@mapNotNull null
                    ChatMessage(
                        id = item.key.orEmpty(),
                        senderId = senderId,
                        text = item.child("text").getValue(String::class.java).orEmpty(),
                        timestamp = item.child("timestamp").getValue(Long::class.java) ?: 0L,
                        edited = item.child("edited").getValue(Boolean::class.java) == true,
                        replyToText = item.child("replyToText").getValue(String::class.java).orEmpty(),
                        system = item.child("type").getValue(String::class.java) == "system"
                    )
                }
                messageAdapter.setMessages(messages)
                syncMessageReactions(messages.map { it.id })
                if (messages.isNotEmpty()) recycler.scrollToPosition(messages.lastIndex)
                FirebaseData.database.getReference("chats").child(chatId).child("unread").child(uid).setValue(0)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Toast.makeText(this@ChatActivity, error.message, Toast.LENGTH_LONG).show()
            }
        }
        messageListener = observer
        messagesQuery?.addValueEventListener(observer)
        listenForIncomingCalls()
    }

    private fun syncMessageReactions(messageIds: List<String>) {
        val uid = currentUser?.uid ?: return
        val activeIds = messageIds.toSet()
        reactionListeners.keys.filter { it !in activeIds }.forEach { messageId ->
            val (reference, listener) = reactionListeners.remove(messageId) ?: return@forEach
            reference.removeEventListener(listener)
        }
        messageIds.forEach { messageId ->
            if (messageId in reactionListeners) return@forEach
            val reference = FirebaseData.messageReactions.child(chatId).child(messageId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    messageAdapter.setHeartState(messageId, snapshot.child(uid).getValue(String::class.java) == "heart")
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
            }
            reference.addValueEventListener(listener)
            reactionListeners[messageId] = reference to listener
        }
    }

    private fun sendMessage() {
        val user = currentUser ?: return
        val text = input.text.toString().trim()
        if (text.isBlank() || chatId.isBlank()) return
        val chat = FirebaseData.database.getReference("chats").child(chatId)
        val messageId = chat.child("messages").push().key ?: return
        FirebaseData.users.child(user.uid).child("name").get().addOnSuccessListener { profileName ->
            val reply = replyingTo
            val message = mapOf(
                "messageId" to messageId,
                "senderId" to user.uid,
                "senderName" to (profileName.getValue(String::class.java) ?: "Kinnect member"),
                "text" to text,
                "timestamp" to mapOf(".sv" to "timestamp"),
                "edited" to false,
                "replyToMessageId" to (reply?.id ?: ""),
                "replyToText" to (reply?.text ?: ""),
                "type" to "text"
            )
            chat.child("messages").child(messageId).setValue(message)
                .addOnSuccessListener {
                    val time = mapOf(".sv" to "timestamp")
                    chat.child("lastMessage").setValue(mapOf("text" to text, "senderId" to user.uid, "timestamp" to time))
                    chat.child("unread").child(otherUid).runTransaction(object : com.google.firebase.database.Transaction.Handler {
                        override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                            val count = currentData.getValue(Int::class.java) ?: 0
                            currentData.value = count + 1
                            return com.google.firebase.database.Transaction.success(currentData)
                        }
                        override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, currentData: DataSnapshot?) = Unit
                    })
                    FirebaseData.database.getReference("notifications").child(otherUid).push().setValue(
                        mapOf("senderId" to user.uid, "type" to "message", "chatId" to chatId, "body" to text, "createdAt" to time, "read" to false)
                    )
                    input.text.clear()
                    replyingTo = null
                    replyLabel.visibility = View.GONE
                }
                .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not send message", Toast.LENGTH_LONG).show() }
        }
    }

    private fun showMediaUnavailable() {
        Toast.makeText(this, "Cross-device media sharing needs a media host, which is not configured.", Toast.LENGTH_LONG).show()
    }

    private fun toggleHeart(message: ChatMessage) {
        val uid = currentUser?.uid ?: return
        val reference = FirebaseData.messageReactions.child(chatId).child(message.id).child(uid)
        reference.get().addOnSuccessListener { snapshot ->
            if (snapshot.getValue(String::class.java) == "heart") reference.removeValue() else reference.setValue("heart")
        }
    }

    private fun showMessageOptions(message: ChatMessage) {
        if (System.currentTimeMillis() - message.timestamp >= 300000) return
        AlertDialog.Builder(this)
            .setItems(arrayOf("Edit", "Delete")) { _, selected ->
                val reference = FirebaseData.database.getReference("chats").child(chatId).child("messages").child(message.id)
                if (selected == 0) {
                    val editor = EditText(this)
                    editor.setText(message.text)
                    AlertDialog.Builder(this).setTitle("Edit message").setView(editor)
                        .setPositiveButton("Save") { _, _ -> reference.updateChildren(mapOf("text" to editor.text.toString(), "edited" to true)) }
                        .setNegativeButton("Cancel", null).show()
                } else {
                    AlertDialog.Builder(this).setMessage("Delete this message?")
                        .setPositiveButton("Delete") { _, _ -> reference.removeValue() }
                        .setNegativeButton("Cancel", null).show()
                }
            }.show()
    }

    private fun registerScreenshotDetection() {
        if (Build.VERSION.SDK_INT < 34) return
        val callback = Activity.ScreenCaptureCallback {
            val uid = currentUser?.uid ?: return@ScreenCaptureCallback
            FirebaseData.users.child(uid).child("name").get().addOnSuccessListener { profileName ->
                val messageText = "${profileName.getValue(String::class.java)?.substringBefore(" ") ?: "A user"} took a screenshot"
                val messageId = FirebaseData.database.getReference("chats").child(chatId).child("messages").push().key ?: return@addOnSuccessListener
                FirebaseData.database.getReference("chats").child(chatId).child("messages").child(messageId)
                    .setValue(mapOf("messageId" to messageId, "senderId" to uid, "text" to messageText, "type" to "system", "timestamp" to mapOf(".sv" to "timestamp"), "edited" to false))
                FirebaseData.database.getReference("notifications").child(otherUid).push()
                    .setValue(mapOf("senderId" to uid, "type" to "screenshot", "chatId" to chatId, "body" to messageText, "createdAt" to mapOf(".sv" to "timestamp"), "read" to false))
            }
        }
        screenshotCallback = callback
        registerScreenCaptureCallback(mainExecutor, callback)
    }

    private fun startCall(video: Boolean) {
        val uid = currentUser?.uid ?: return
        if (otherUid.isBlank() || BuildConfig.AGORA_APP_ID.isBlank() || BuildConfig.AGORA_TEMP_TOKEN.isBlank()) {
            Toast.makeText(this, "Add an Agora App ID and temporary channel token in ignored agora.properties", Toast.LENGTH_LONG).show()
            return
        }
        val callId = FirebaseData.callRequests.child(otherUid).push().key ?: return
        activeOutgoingCallId = callId
        val call = mapOf<String, Any>(
            "callerId" to uid,
            "calleeId" to otherUid,
            "callerName" to personName,
            "channel" to "kinnect_assignment_demo",
            "video" to video,
            "status" to "ringing",
            "createdAt" to mapOf(".sv" to "timestamp")
        )
        FirebaseData.callRequests.child(otherUid).child(callId).setValue(call)
            .addOnSuccessListener {
                FirebaseData.callLogs.child(callId).setValue(mapOf("callerId" to uid, "calleeId" to otherUid, "type" to if (video) "video" else "voice", "status" to "ringing", "createdAt" to mapOf(".sv" to "timestamp")))
                val reference = FirebaseData.callRequests.child(otherUid).child(callId)
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        when (snapshot.child("status").getValue(String::class.java)) {
                            "accepted" -> openCall(callId, video, false)
                            "declined", "ended" -> {
                                reference.removeEventListener(this)
                                activeCallListener = null
                                Toast.makeText(this@ChatActivity, "Call was declined or ended", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
                }
                activeCallListener = listener
                reference.addValueEventListener(listener)
            }
            .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not place call", Toast.LENGTH_LONG).show() }
    }

    private fun listenForIncomingCalls() {
        val uid = currentUser?.uid ?: return
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) = showIncomingCall(snapshot)
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = showIncomingCall(snapshot)
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        callsListener = listener
        FirebaseData.callRequests.child(uid).addChildEventListener(listener)
    }

    private fun showIncomingCall(snapshot: DataSnapshot) {
        val uid = currentUser?.uid ?: return
        val callId = snapshot.key ?: return
        if (snapshot.child("calleeId").getValue(String::class.java) != uid || snapshot.child("callerId").getValue(String::class.java) != otherUid || snapshot.child("status").getValue(String::class.java) != "ringing") return
        if (isFinishing || isDestroyed) return
        val callerName = snapshot.child("callerName").getValue(String::class.java) ?: personName
        AlertDialog.Builder(this)
            .setTitle(if (snapshot.child("video").getValue(Boolean::class.java) == true) "Incoming video call" else "Incoming voice call")
            .setMessage("$callerName is calling")
            .setPositiveButton("Accept") { _, _ ->
                val video = snapshot.child("video").getValue(Boolean::class.java) == true
                FirebaseData.callRequests.child(uid).child(callId).child("status").setValue("accepted")
                FirebaseData.callLogs.child(callId).setValue(mapOf("callerId" to otherUid, "calleeId" to uid, "type" to if (video) "video" else "voice", "status" to "accepted", "createdAt" to mapOf(".sv" to "timestamp")))
                openCall(callId, video, true)
            }
            .setNegativeButton("Decline") { _, _ ->
                FirebaseData.callRequests.child(uid).child(callId).child("status").setValue("declined")
                FirebaseData.callLogs.child(callId).child("status").setValue("declined")
            }.show()
    }

    private fun openCall(callId: String, video: Boolean, incoming: Boolean) {
        if (isFinishing || isDestroyed) return
        val intent = Intent(this, VoiceCallActivity::class.java)
            .putExtra("call_id", callId)
            .putExtra("other_uid", otherUid)
            .putExtra("person_name", personName)
            .putExtra("person_initials", initials(personName))
            .putExtra("channel", "kinnect_assignment_demo")
            .putExtra("video", video)
            .putExtra("incoming", incoming)
        startActivity(intent)
    }

    private fun initials(name: String) = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")

    override fun onDestroy() {
        messageListener?.let { messagesQuery?.removeEventListener(it) }
        reactionListeners.values.forEach { (reference, listener) -> reference.removeEventListener(listener) }
        reactionListeners.clear()
        callsListener?.let { FirebaseData.callRequests.child(currentUser?.uid.orEmpty()).removeEventListener(it) }
        activeCallListener?.let { if (activeOutgoingCallId.isNotBlank()) FirebaseData.callRequests.child(otherUid).child(activeOutgoingCallId).removeEventListener(it) }
        presenceListener?.let { FirebaseData.database.getReference("presence").child(otherUid).removeEventListener(it) }
        if (Build.VERSION.SDK_INT >= 34) screenshotCallback?.let { unregisterScreenCaptureCallback(it) }
        super.onDestroy()
    }
}
