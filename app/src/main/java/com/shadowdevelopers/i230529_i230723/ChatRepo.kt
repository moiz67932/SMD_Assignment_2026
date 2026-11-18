package com.shadowdevelopers.i230529_i230723

import android.content.Context
import android.net.Uri
import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONArray
import org.json.JSONObject

/**
 * Chats (20) and Chat (21) from the PHP API, cached in SQLite and keyed by the other person's id, so a conversation
 * opens instantly and stays readable offline. Every change (send, edit, delete, heart, screenshot alert) is saved
 * locally and queued in pending_actions; the sync job sends them in order with their UUIDs (no duplicates).
 * New messages appear without leaving the screen: Chat polls every 3 s while it is open.
 * Blocking functions: call them through Backend.call / Backend.background.
 */
object ChatRepo {
    /** Conversation state for the Chat screen. */
    data class Conversation(
        val messages: List<ChatMessage>,
        val pendingIds: Set<String>,
        val hearts: Map<String, Set<String>>,
        val peerLastRead: Long,
        val peerTyping: Boolean
    )

    /** One row of Chats (20). */
    data class Summary(val peer: String, val chatId: String, val preview: String, val time: Long, val unread: Int, val fromMe: Boolean, val pending: Boolean)

    // ----- Chat ids -----

    fun chatIdFor(peer: String): String? {
        val me = Backend.uid ?: return null
        return Backend.store.entity(me, "chat_of", peer)?.let { JSONObject(it).optString("chat_id").takeIf(String::isNotBlank) }
    }

    /** POST chats/ensure.php once per person (then cached). Needs the network; offline, messages carry the peer id instead. */
    private fun ensureChat(peer: String): String {
        chatIdFor(peer)?.let { return it }
        val me = Backend.uid ?: error("Please log in again")
        val id = Backend.api.post("chats/ensure.php", JSONObject().put("action_uuid", Backend.newId()).put("other_id", peer)).getString("chat_id")
        Backend.store.putEntity(me, "chat_of", peer, JSONObject().put("chat_id", id).toString())
        return id
    }

    // ----- Reading -----

    fun conversation(peer: String, fromServer: Boolean): Conversation {
        val me = Backend.uid ?: return Conversation(emptyList(), emptySet(), emptyMap(), 0L, false)
        if (fromServer) {
            val chatId = ensureChat(peer)
            val response = Backend.api.get("messages/list.php?chat_id=$chatId")
            Backend.store.putEntity(me, "messages", peer, response.toString())
        }
        val cached = Backend.store.entity(me, "messages", peer)?.let(::JSONObject) ?: JSONObject()
        val server = cached.optJSONArray("messages") ?: JSONArray()
        val hearts = mutableMapOf<String, MutableSet<String>>()
        val list = (0 until server.length()).map { server.getJSONObject(it) }.mapNotNull { json ->
            val heartList = json.optJSONArray("hearts") ?: JSONArray()
            hearts[json.getString("id")] = (0 until heartList.length()).map { heartList.getString(it) }.toMutableSet()
            parse(json)
        }
        // Offline changes on top: unsent messages, edits, deletions and hearts that are still queued.
        val local = Backend.store.entitiesWithState(me, "message").filter { it.second }.map { JSONObject(it.first) }.filter { it.optString("peer") == peer }
        val edits = Backend.store.entitiesWithState(me, "message_edit").filter { it.second }.map { JSONObject(it.first) }.associateBy { it.getString("id") }
        val deleted = Backend.store.entitiesWithState(me, "message_delete").filter { it.second }.map { JSONObject(it.first).getString("id") }.toSet()
        Backend.store.entitiesWithState(me, "message_heart").filter { it.second }.map { JSONObject(it.first) }.forEach {
            val set = hearts.getOrPut(it.getString("id")) { mutableSetOf() }
            if (it.getBoolean("hearted")) set.add(me) else set.remove(me)
        }
        val serverIds = list.map { it.id }.toSet()
        val unsent = local.mapNotNull(::parse).filter { it.id !in serverIds }
        val pending = (unsent.map { it.id } + edits.keys + deleted).toSet()
        val messages = (list + unsent).filter { it.id !in deleted }.map { message ->
            edits[message.id]?.let { message.copy(text = it.getString("body"), edited = true) } ?: message
        }.sortedBy { it.timestamp }
        return Conversation(messages, pending, hearts, TimeFormat.parse(cached.optString("peer_last_read_at")), cached.optBoolean("peer_typing"))
    }

    /** A server (or locally queued) message -> the Assignment 2 ChatMessage the adapter draws. Deleted messages are hidden. */
    private fun parse(json: JSONObject): ChatMessage? {
        if (!json.isNull("deleted_at") && json.optString("deleted_at").isNotBlank()) return null
        val type = json.optString("type").ifBlank { "text" }
        val extra = json.optJSONObject("extra") ?: JSONObject()
        val attachments = json.optJSONArray("attachments") ?: JSONArray()
        val media = if (attachments.length() > 0) ProfileCache.mediaRef(attachments.getJSONObject(0).getString("id")) else json.optString("local_media")
        val fileName = if (attachments.length() > 0) attachments.getJSONObject(0).optString("original_name") else extra.optString("file_name")
        return ChatMessage(
            id = json.getString("id"),
            senderId = json.optString("sender_id"),
            text = if (type == "file") fileName.ifBlank { json.optString("body") } else json.optString("body"),
            timestamp = TimeFormat.parse(json.optString("created_at")),
            edited = !json.isNull("edited_at") && json.optString("edited_at").isNotBlank(),
            replyToText = extra.optString("reply_to_text"),
            system = type == "system",
            type = type,
            imageRef = if (type == "image") media else "",
            sharedPostId = extra.optString("post_id"),
            sharedAuthorName = extra.optString("post_author"),
            sharedSnippet = extra.optString("post_snippet"),
            sharedImage = extra.optString("post_image"),
            replyToSenderId = extra.optString("reply_to_sender"),
            videoRef = if (type == "video") media else "",
            audioRef = if (type == "audio") media else "",
            durationSec = extra.optInt("duration_sec"),
            fileRef = if (type == "file") media else ""
        )
    }

    /** Chats (20): the server list (cached), with my unsent messages shown as the newest preview. */
    fun summaries(fromServer: Boolean): List<Summary> {
        val me = Backend.uid ?: return emptyList()
        if (fromServer) {
            val chats = Backend.api.get("chats/list.php").getJSONArray("chats")
            Backend.store.putEntity(me, "chats", "all", chats.toString())
            for (i in 0 until chats.length()) chats.getJSONObject(i).let {
                Backend.store.putEntity(me, "chat_of", it.getString("peer_id"), JSONObject().put("chat_id", it.getString("id")).toString())
                ProfileCache.putJson(JSONObject().put("id", it.getString("peer_id")).put("display_name", it.optString("peer_name")).put("photo_media_id", it.opt("peer_photo_media_id")))
            }
        }
        val chats = Backend.store.entity(me, "chats", "all")?.let(::JSONArray) ?: JSONArray()
        val rows = (0 until chats.length()).map { chats.getJSONObject(it) }.map { c ->
            val last = c.getJSONObject("last_message")
            Summary(c.getString("peer_id"), c.getString("id"), preview(last.optString("type"), last.optString("body")),
                TimeFormat.parse(last.optString("created_at")), c.optInt("unread"), last.optString("sender_id") == me, false)
        }.associateBy { it.peer }.toMutableMap()
        Backend.store.entitiesWithState(me, "message").filter { it.second }.map { JSONObject(it.first) }.forEach { local ->
            val peer = local.optString("peer")
            val time = TimeFormat.parse(local.optString("created_at"))
            if ((rows[peer]?.time ?: 0L) <= time) rows[peer] = Summary(peer, rows[peer]?.chatId.orEmpty(), preview(local.optString("type"), local.optString("body")), time, rows[peer]?.unread ?: 0, true, true)
        }
        return rows.values.sortedByDescending { it.time }
    }

    fun preview(type: String, body: String) = when (type) {
        "image" -> "Photo"
        "video" -> "Video"
        "audio" -> "Voice message"
        "file" -> "File"
        "post" -> "Shared a post"
        else -> body
    }

    /** How many conversations have unread messages (red badge on the chat icon). */
    fun unreadConversations(): Int = runCatching { summaries(fromServer = true).count { it.unread > 0 } }
        .getOrElse { summaries(fromServer = false).count { it.unread > 0 } }

    // ----- Writing (queued, works offline) -----

    /**
     * Sends a message. [attachment] (photo, video, voice note or any file) is copied into app storage and uploaded first
     * (media.upload); the message refers to it by media id. Both are queued, so this works offline too.
     */
    fun send(context: Context, peer: String, type: String, body: String, extra: JSONObject = JSONObject(), attachment: Uri? = null,
             attachmentKind: String = type, fileMime: String? = null, uploadedMediaId: String? = null) {
        val me = Backend.uid ?: error("Please log in again")
        val id = Backend.newId()
        var mediaId: String? = uploadedMediaId
        var localMedia = ""
        if (attachment != null) {
            val file = if (type == "file") copyFile(context, me, attachment, extra.optString("file_name")) else MediaUploader.prepare(context, me, attachment, attachmentKind)
            mediaId = Backend.newId()
            localMedia = Uri.fromFile(file).toString()
            val mime = fileMime ?: MediaUploader.mime(attachmentKind)
            Backend.enqueue("media", mediaId, JSONObject().put("id", mediaId), "media.upload",
                JSONObject().put("id", mediaId).put("local_files", JSONArray().put(JSONObject().put("field", "file").put("path", file.absolutePath).put("mime", mime))))
        }
        val local = JSONObject().put("id", id).put("peer", peer).put("sender_id", me).put("body", body).put("type", type)
            .put("extra", extra).put("created_at", Backend.nowUtc()).put("local_media", localMedia)
        val payload = JSONObject().put("id", id).put("peer_id", peer).put("body", body).put("type", type)
            .put("media_id", mediaId ?: JSONObject.NULL).put("extra", if (extra.length() == 0) JSONObject.NULL else extra)
        chatIdFor(peer)?.let { payload.put("chat_id", it) }
        Backend.enqueue("message", id, local, "message.send", payload)
    }

    /** Files keep their real type (PDF, ZIP, text...) and name. */
    private fun copyFile(context: Context, account: String, uri: Uri, name: String): java.io.File {
        val folder = java.io.File(context.filesDir, "pending_media/$account").also { it.mkdirs() }
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file" }
        val target = java.io.File(folder, Backend.newId() + "_" + safe)
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: error("Could not read the file")
        require(target.length() <= 24L * 1024 * 1024) { "That file is too big to send (max 24 MB)" }
        return target
    }

    /** Edit my message (text). The server refuses after 5 minutes; the app then shows the original again. */
    fun edit(messageId: String, body: String) {
        Backend.enqueue("message_edit", messageId, JSONObject().put("id", messageId).put("body", body), "message.edit", JSONObject().put("id", messageId).put("body", body))
    }

    fun delete(messageId: String) {
        Backend.enqueue("message_delete", messageId, JSONObject().put("id", messageId), "message.delete", JSONObject().put("id", messageId))
    }

    fun heart(messageId: String, hearted: Boolean) {
        Backend.enqueue("message_heart", messageId, JSONObject().put("id", messageId).put("hearted", hearted), "message.heart",
            JSONObject().put("message_id", messageId).put("hearted", hearted))
    }

    /** "Aisha took a screenshot": a system message in the chat and a notification + push for the other person (server side). */
    fun screenshot(peer: String) {
        val payload = JSONObject().put("peer_id", peer)
        chatIdFor(peer)?.let { payload.put("chat_id", it) }
        Backend.enqueue("screenshot", Backend.newId(), JSONObject(), "chat.screenshot", payload)
    }

    /** Not queued (they only matter right now): read receipt and typing flag. */
    fun markRead(peer: String) {
        val chatId = chatIdFor(peer) ?: return
        runCatching { Backend.api.post("chats/read.php", JSONObject().put("chat_id", chatId)) }
    }

    fun typing(peer: String, typing: Boolean) {
        val chatId = chatIdFor(peer) ?: return
        runCatching { Backend.api.post("chats/typing.php", JSONObject().put("chat_id", chatId).put("typing", typing)) }
    }
}
