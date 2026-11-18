package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONObject
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Chat (21) with one person. chats/{chatId} holds members, messages, lastMessage, unread/{uid},
 * lastRead/{uid} (for the "seen" dot) and typing/{uid}. Hearts live in messageReactions/{chatId}/{messageId}/{uid}.
 * Edit and delete are offered only on my own messages for 5 minutes after sending (server-adjusted time);
 * the database rules enforce the same 5 minutes on the server.
 */
class ChatActivity : AppCompatActivity() {
    private val me by lazy { Backend.uid.orEmpty() }
    private var otherUid = ""
    private var personName = "Kinnect member"
    private var chatId = ""
    private var chatReady = false
    private var started = false
    private var visible = false
    private val listeners = ListenerBag()
    private var replyingTo: ChatMessage? = null
    private var messages: List<ChatMessage> = emptyList()
    private var hearts: Map<String, Set<String>> = emptyMap()
    private var otherLastRead = 0L
    private var otherTyping = false
    private var isFriend = false
    /** My messages / edits still queued offline ("Pending"). */
    private var pendingIds: Set<String> = emptySet()
    private val handler = Handler(Looper.getMainLooper())
    private val stopTyping = Runnable { setTyping(false) }
    private lateinit var replyLabel: TextView
    private lateinit var input: EditText
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var recycler: RecyclerView
    private val screenshotDetector by lazy { ScreenshotDetector(this) { reportScreenshot() } }
    private var recorder: android.media.MediaRecorder? = null
    private var voiceFile: java.io.File? = null
    private var voiceStartedAt = 0L
    private val voiceClock = object : Runnable {
        override fun run() {
            if (recorder == null) return
            val seconds = ((System.currentTimeMillis() - voiceStartedAt) / 1000).toInt()
            findViewById<TextView>(R.id.chatRecordTime).text = "● Recording ${TimeFormat.duration(seconds)} · tap the mic to send"
            handler.postDelayed(this, 500)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        personName = intent.getStringExtra("person_name") ?: "Kinnect member"
        otherUid = intent.getStringExtra("person_uid").orEmpty()
        replyLabel = findViewById(R.id.chatReplyLabel)
        input = findViewById(R.id.messageInput)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        if (otherUid.isBlank() || me.isBlank() || otherUid == me) {
            Toast.makeText(this, "Choose a real user profile to start a chat", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        findViewById<View>(R.id.callButton).setOnClickListener { startCall(false) }
        findViewById<View>(R.id.videoCallButton).setOnClickListener { startCall(true) }
        findViewById<View>(R.id.sendMessageButton).setOnClickListener { sendMessage() }
        findViewById<View>(R.id.chatReplyCancel).setOnClickListener { clearReply() }
        findViewById<View>(R.id.chatInfoButton).setOnClickListener {
            startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", otherUid))
        }
        // + and the gallery icon open the picker (photos and short videos); the camera icon takes a photo.
        findViewById<View>(R.id.chatImageButton).setOnClickListener {
            startActivityForResult(Intent(this, PhotoPickerActivity::class.java).putExtra("single", true), PICK_IMAGE)
        }
        // + : photo/video or any file (PDF, document...).
        findViewById<View>(R.id.chatAttachmentButton).setOnClickListener {
            AlertDialog.Builder(this).setItems(arrayOf("Photo or video", "File")) { _, which ->
                if (which == 0) startActivityForResult(Intent(this, PhotoPickerActivity::class.java).putExtra("single", true), PICK_IMAGE)
                else {
                    ExternalLaunch.mark()
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), PICK_FILE)
                }
            }.show()
        }
        findViewById<View>(R.id.chatCameraButton).setOnClickListener {
            startActivityForResult(Intent(this, CameraActivity::class.java).putExtra("for_post", true), TAKE_PHOTO)
        }
        // Voice message: tap the mic to start, tap it again to send (the x cancels).
        findViewById<View>(R.id.chatVoiceButton).setOnClickListener { if (recorder == null) startVoice() else sendVoice() }
        findViewById<View>(R.id.chatRecordCancel).setOnClickListener { cancelVoice() }
        findViewById<View>(R.id.chatEmojiButton).setOnClickListener {
            EmojiPicker.show(this) { emoji -> input.append(emoji) }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                // Empty input shows the thumbs-up (sends 👍), otherwise the send arrow.
                findViewById<ImageView>(R.id.sendMessageButton).setImageResource(if (s.isNullOrBlank()) R.drawable.ic_thumb_up else R.drawable.ic_send)
                if (!s.isNullOrBlank()) {
                    setTyping(true)
                    handler.removeCallbacks(stopTyping)
                    handler.postDelayed(stopTyping, 4000)
                }
            }
        })

        messageAdapter = MessageAdapter(me, onLongPress = ::showMessageOptions, onDoubleTap = ::toggleHeart)
        recycler = findViewById(R.id.messagesRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = messageAdapter
        messageAdapter.other = UserProfile(otherUid, personName)
        render()

        // Opens instantly from SQLite (also offline); the server chat is created on first use (chats/ensure or with the first message).
        chatReady = true
        askScreenshotPermissionOnce()
    }

    override fun onStart() {
        super.onStart()
        started = true
        if (chatReady) attach()
        screenshotDetector.start()
    }

    override fun onResume() {
        super.onResume()
        visible = true
        openChatWith = otherUid
        markRead()
    }

    override fun onPause() {
        visible = false
        if (openChatWith == otherUid) openChatWith = null
        super.onPause()
    }

    override fun onStop() {
        cancelVoice()
        AudioPlayer.stop()
        started = false
        setTyping(false)
        listeners.removeAll()
        screenshotDetector.stop()
        super.onStop()
    }

    /**
     * The conversation: SQLite first (instant, offline), then GET messages/list.php every 3 s while the screen is open,
     * so new messages, "Seen", typing and hearts appear without leaving the screen. Also refreshed when queued
     * actions reach the server.
     */
    private fun attach() {
        listeners.removeAll()
        ProfileCache.get(otherUid) { profile ->
            personName = profile.name
            findViewById<TextView>(R.id.chatToolbarName).text = profile.name
            AvatarBinder.bind(findViewById(R.id.chatToolbarAvatar), profile.name, profile.photo)
            messageAdapter.other = profile
            render()
        }
        val friendsChanged: () -> Unit = { isFriend = otherUid in Friends.friends; render() }
        Friends.observe(friendsChanged)
        listeners.onRemove { Friends.unobserve(friendsChanged) }
        Friends.refresh()
        load(fromServer = false)
        listeners.poll(3_000) { load(fromServer = true) }
        listeners.poll(15_000) { loadPresence() }
    }

    private fun load(fromServer: Boolean) {
        Backend.call({ runCatching { ChatRepo.conversation(otherUid, fromServer) }.getOrElse { ChatRepo.conversation(otherUid, false) } }) { result ->
            val conversation = result.getOrNull() ?: return@call
            messages = conversation.messages
            hearts = conversation.hearts
            pendingIds = conversation.pendingIds
            otherLastRead = conversation.peerLastRead
            otherTyping = conversation.peerTyping
            messageAdapter.pendingIds = pendingIds
            render()
            if (fromServer) markRead()
        }
    }

    /** Green dot and "Active now" / "Active 5m ago" (GET presence/get.php). */
    private fun loadPresence() {
        Backend.call({ Backend.api.get("presence/get.php?id=$otherUid").getJSONObject("presence") }) { result ->
            val presence = result.getOrNull() ?: return@call
            val online = presence.optInt("online") == 1
            findViewById<View>(R.id.chatToolbarOnline).visibility = if (online) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.chatToolbarStatus).text = KinnectApplication.presenceLabel(online, TimeFormat.parse(presence.optString("last_seen")))
        }
    }

    /** Clears my unread count and tells the other person I have seen their messages (only while this chat is visible). */
    private fun markRead() {
        if (!visible || !chatReady) return
        Backend.background { ChatRepo.markRead(otherUid) }
    }

    private fun setTyping(typing: Boolean) {
        if (!chatReady) return
        if (!typing) handler.removeCallbacks(stopTyping)
        Backend.background { ChatRepo.typing(otherUid, typing) }
    }

    /** Builds the list: intro card, messages with date breaks / grouping / hearts / seen dot, typing bubble. */
    private fun render() {
        val rows = mutableListOf<ChatRow>(ChatRow.Intro)
        val myLastSeen = messages.lastOrNull { it.senderId == me && it.timestamp in 1..otherLastRead && !it.system }?.id
        messages.forEachIndexed { index, message ->
            val previous = messages.getOrNull(index - 1)
            val next = messages.getOrNull(index + 1)
            val newDay = previous == null || !sameDay(previous.timestamp, message.timestamp) || message.timestamp - previous.timestamp > 60 * 60 * 1000
            val nextBreaks = next == null || next.senderId != message.senderId || next.system || !sameDay(next.timestamp, message.timestamp) ||
                next.timestamp - message.timestamp > 60 * 60 * 1000
            val prevBreaks = previous == null || previous.senderId != message.senderId || previous.system || newDay
            val replyHeader = when {
                message.replyToText.isBlank() -> ""
                message.senderId == me -> "You replied to ${if (message.replyToSenderId == me) "yourself" else personName.substringBefore(" ")}"
                else -> "${personName.substringBefore(" ")} replied to ${if (message.replyToSenderId == otherUid) "themself" else "you"}"
            }
            val reacted = hearts[message.id].orEmpty()
            rows.add(ChatRow.Message(
                message = message,
                dateLabel = if (newDay) dateLabel(message.timestamp) else null,
                showAvatar = message.senderId != me && nextBreaks,
                replyHeader = replyHeader,
                heartMine = me in reacted,
                heartOther = otherUid in reacted,
                seen = message.id == myLastSeen,
                showTime = next == null,
                firstInRun = prevBreaks,
                lastInRun = nextBreaks
            ))
        }
        if (otherTyping) rows.add(ChatRow.Typing)
        messageAdapter.introSubtitle = if (isFriend) "You're friends on Kinnect" else "You're not friends on Kinnect yet"
        val atBottom = !recycler.canScrollVertically(1)
        messageAdapter.setRows(rows)
        if (atBottom || rows.size <= 2) recycler.scrollToPosition(rows.lastIndex)
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val x = Calendar.getInstance().apply { timeInMillis = a }
        val y = Calendar.getInstance().apply { timeInMillis = b }
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
    }

    /** "TODAY 4:49 PM", "YESTERDAY 9:02 AM", "12 OCT 4:49 PM" */
    private fun dateLabel(time: Long): String {
        if (time <= 0) return ""
        val now = System.currentTimeMillis()
        val day = when {
            sameDay(time, now) -> "Today"
            sameDay(time, now - 86_400_000) -> "Yesterday"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time))
        }
        return "$day ${TimeFormat.clock(time)}"
    }

    private fun sendMessage() {
        val text = input.text.toString().trim().ifBlank { "👍" }
        val reply = replyingTo
        ChatSender.send(this, me, otherUid, type = "text", text = text, preview = text, fields = replyFields(reply), onSent = { load(fromServer = false) })
        input.text.clear()
        clearReply()
        setTyping(false)
    }

    private fun replyFields(reply: ChatMessage?) = mapOf<String, Any>(
        "replyToMessageId" to (reply?.id ?: ""),
        "replyToSenderId" to (reply?.senderId ?: ""),
        "replyToText" to (reply?.let { previewOf(it) } ?: "")
    )

    /** How a message reads in the Chats list and in reply quotes. */
    private fun previewOf(message: ChatMessage) = when (message.type) {
        "image" -> "Photo"
        "video" -> "Video"
        "audio" -> "Voice message"
        "post" -> "Shared a post"
        else -> message.text
    }

    private fun startReply(message: ChatMessage) {
        replyingTo = message
        findViewById<View>(R.id.chatReplyBar).visibility = View.VISIBLE
        val who = if (message.senderId == me) "yourself" else personName.substringBefore(" ")
        replyLabel.text = "Replying to $who: ${previewOf(message).take(48)}"
        input.requestFocus()
    }

    private fun clearReply() {
        replyingTo = null
        findViewById<View>(R.id.chatReplyBar).visibility = View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            PICK_IMAGE -> {
                val uri = data.getStringArrayListExtra("media_uris")?.firstOrNull() ?: return
                val kind = data.getStringArrayListExtra("media_types")?.firstOrNull() ?: MediaUploader.IMAGE
                sendMedia(android.net.Uri.parse(uri), kind)
            }
            TAKE_PHOTO -> data.getStringExtra("media_uri")?.let { sendMedia(android.net.Uri.parse(it), MediaUploader.IMAGE) }
            PICK_FILE -> data.data?.let(::sendFile)
        }
    }

    /** Photo or video: copied into app storage and queued (upload, then the message). Shows at once; offline it waits. */
    private fun sendMedia(uri: android.net.Uri, kind: String) {
        val video = kind == MediaUploader.VIDEO
        sendAttachment(if (video) "video" else "image", uri, kind, JSONObject())
    }

    /** Any document (PDF, ZIP, text...) from the phone's files. */
    private fun sendFile(uri: android.net.Uri) {
        var name = "file"
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        sendAttachment("file", uri, "file", JSONObject().put("file_name", name), mime)
    }

    private fun sendAttachment(type: String, uri: android.net.Uri, kind: String, extra: JSONObject, mime: String? = null) {
        val status = findViewById<TextView>(R.id.chatUploadStatus)
        status.visibility = View.VISIBLE
        status.text = "Preparing…"
        replyingTo?.let { reply -> replyFields(reply).forEach { (key, value) -> extra.put(mapReplyKey(key), value) } }
        Backend.call({ ChatRepo.send(this, otherUid, type, "", extra, attachment = uri, attachmentKind = kind, fileMime = mime) }) { result ->
            status.visibility = View.GONE
            result.onSuccess { clearReply(); load(fromServer = false) }
                .onFailure { Toast.makeText(this, Forms.message(it as? Exception, "Could not send"), Toast.LENGTH_LONG).show() }
        }
    }

    private fun mapReplyKey(key: String) = when (key) {
        "replyToMessageId" -> "reply_to_id"
        "replyToSenderId" -> "reply_to_sender"
        else -> "reply_to_text"
    }

    // ----- Voice messages -----

    private fun startVoice() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            return
        }
        val file = java.io.File.createTempFile("kinnect_voice_", ".m4a", cacheDir)
        val rec = if (Build.VERSION.SDK_INT >= 31) android.media.MediaRecorder(this) else @Suppress("DEPRECATION") android.media.MediaRecorder()
        try {
            rec.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(48_000)
            rec.setAudioSamplingRate(32_000)
            rec.setMaxDuration(60_000) // a minute fits easily in the database (about 360 KB)
            rec.setOutputFile(file.absolutePath)
            rec.setOnInfoListener { _, what, _ ->
                if (what == android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) runOnUiThread { sendVoice() }
            }
            rec.prepare()
            rec.start()
        } catch (error: Exception) {
            runCatching { rec.release() }
            Toast.makeText(this, "Could not start recording. Is another app using the microphone?", Toast.LENGTH_LONG).show()
            return
        }
        recorder = rec
        voiceFile = file
        voiceStartedAt = System.currentTimeMillis()
        findViewById<View>(R.id.chatRecordBar).visibility = View.VISIBLE
        findViewById<ImageView>(R.id.chatVoiceButton).setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.red))
        handler.post(voiceClock)
    }

    /** Stops the recorder; returns the recording file, or null if it failed or was too short. */
    private fun stopVoice(): java.io.File? {
        val rec = recorder ?: return null
        recorder = null
        handler.removeCallbacks(voiceClock)
        findViewById<View>(R.id.chatRecordBar).visibility = View.GONE
        findViewById<ImageView>(R.id.chatVoiceButton).setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.primary))
        val file = voiceFile
        voiceFile = null
        val stopped = runCatching { rec.stop() }.isSuccess // stop() fails when the recording is far too short
        runCatching { rec.release() }
        return if (stopped && file != null && file.length() > 0) file else null
    }

    private fun cancelVoice() {
        stopVoice()?.delete()
    }

    private fun sendVoice() {
        val seconds = ((System.currentTimeMillis() - voiceStartedAt) / 1000).toInt()
        val file = stopVoice()
        if (file == null || seconds < 1) {
            file?.delete()
            Toast.makeText(this, "Recording was too short. Hold on a little longer.", Toast.LENGTH_SHORT).show()
            return
        }
        val extra = JSONObject().put("duration_sec", seconds)
        replyingTo?.let { reply -> replyFields(reply).forEach { (key, value) -> extra.put(mapReplyKey(key), value) } }
        Backend.call({ ChatRepo.send(this, otherUid, "audio", "", extra, attachment = android.net.Uri.fromFile(file), attachmentKind = MediaUploader.AUDIO) }) { result ->
            result.onSuccess { clearReply(); load(fromServer = false) }
                .onFailure { Toast.makeText(this, Forms.message(it as? Exception, "Could not send the voice message"), Toast.LENGTH_LONG).show() }
        }
    }

    private fun toggleHeart(message: ChatMessage) {
        if (message.system) return
        val hearted = me !in hearts[message.id].orEmpty()
        Backend.call({ ChatRepo.heart(message.id, hearted) }) { load(fromServer = false) }
    }

    /** The 5-minute window is checked here for the menu and enforced again by the server. */
    private fun canChange(message: ChatMessage) = message.senderId == me && !message.system && System.currentTimeMillis() - message.timestamp < EDIT_WINDOW_MS

    /** Reply / heart for any message; Edit (text only) and Delete only for my own message within 5 minutes. */
    private fun showMessageOptions(message: ChatMessage) {
        if (message.system) return
        val options = mutableListOf("Reply", if (me in hearts[message.id].orEmpty()) "Remove heart" else "Heart")
        if (canChange(message)) {
            if (message.type == "text") options.add("Edit")
            options.add("Delete")
        }
        AlertDialog.Builder(this).setItems(options.toTypedArray()) { _, picked ->
            when (options[picked]) {
                "Reply" -> startReply(message)
                "Heart", "Remove heart" -> toggleHeart(message)
                "Edit" -> if (canChange(message)) editMessage(message) else tooLate()
                "Delete" -> if (canChange(message)) deleteMessage(message) else tooLate()
            }
        }.show()
    }

    private fun tooLate() = Toast.makeText(this, "Messages can only be changed within 5 minutes", Toast.LENGTH_SHORT).show()

    private fun editMessage(message: ChatMessage) {
        val editor = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(message.text)
            setSelection(text.length)
        }
        AlertDialog.Builder(this).setTitle("Edit message").setView(editor)
            .setPositiveButton("Save") { _, _ ->
                val text = editor.text.toString().trim()
                if (text.isBlank() || !canChange(message)) return@setPositiveButton tooLate()
                Backend.call({ ChatRepo.edit(message.id, text) }) { load(fromServer = false) }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteMessage(message: ChatMessage) {
        AlertDialog.Builder(this).setMessage("Delete this message?")
            .setPositiveButton("Delete") { _, _ ->
                Backend.call({ ChatRepo.delete(message.id) }) { load(fromServer = false) }
            }
            .setNegativeButton("Cancel", null).show()
    }

    /**
     * Screenshot detected: the server adds the system message "{Name} took a screenshot" to this chat and notifies the
     * other person (notification + FCM push). Queued, so it is not lost offline.
     */
    private fun reportScreenshot() {
        if (!chatReady) return
        Backend.call({ ChatRepo.screenshot(otherUid) }) { load(fromServer = false) }
    }

    /** Below Android 14 the detector needs gallery access; ask once, with a reason. */
    private fun askScreenshotPermissionOnce() {
        if (!screenshotDetector.needsPermission()) return
        val prefs = getSharedPreferences("kinnect", MODE_PRIVATE)
        if (prefs.getBoolean("asked_screenshot_permission", false)) return
        prefs.edit().putBoolean("asked_screenshot_permission", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Screenshot alerts")
            .setMessage("Kinnect tells people when someone takes a screenshot of their chat. On this Android version that needs access to your photos.")
            .setPositiveButton("Allow") { _, _ -> requestPermissions(arrayOf(screenshotDetector.permission()), REQUEST_MEDIA) }
            .setNegativeButton("Not now", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) startVoice()
        else if (requestCode == REQUEST_MIC) Toast.makeText(this, "Allow the microphone to send voice messages", Toast.LENGTH_LONG).show()
        if (requestCode == REQUEST_MEDIA && started) {
            screenshotDetector.stop()
            screenshotDetector.start()
        }
    }

    /** Phone / video icons: the call screen asks for permissions, then rings the other person. */
    private fun startCall(video: Boolean) {
        startActivity(Intent(this, VoiceCallActivity::class.java).putExtra("other_uid", otherUid).putExtra("video", video))
    }

    companion object {
        /** The person whose chat is on screen right now (no push notification is shown for them). */
        @Volatile var openChatWith: String? = null
        private const val PICK_IMAGE = 840
        private const val TAKE_PHOTO = 841
        private const val REQUEST_MEDIA = 842
        private const val REQUEST_MIC = 843
        private const val PICK_FILE = 844
        private const val EDIT_WINDOW_MS = 5 * 60 * 1000L
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopTyping)
        super.onDestroy()
    }
}
