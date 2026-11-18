package com.shadowdevelopers.i230529_i230723

import android.content.Context
import android.widget.Toast
import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONObject

/**
 * Sends a chat message from any screen (Chat, story replies, shared posts, call summaries) through ChatRepo:
 * saved on the phone, queued, and sent to POST messages/send.php (works offline, no duplicates).
 * `fields` keeps the Assignment 2 names; they are mapped to the server's message `extra`.
 */
object ChatSender {
    fun send(context: Context, me: String, other: String, type: String, text: String, preview: String, fields: Map<String, Any> = emptyMap(), onSent: () -> Unit = {}, onFailed: () -> Unit = {}) {
        val extra = JSONObject()
        fun copy(from: String, to: String) { (fields[from] as? String)?.takeIf { it.isNotBlank() }?.let { extra.put(to, it) } }
        copy("replyToMessageId", "reply_to_id"); copy("replyToSenderId", "reply_to_sender"); copy("replyToText", "reply_to_text")
        copy("sharedPostId", "post_id"); copy("sharedAuthorName", "post_author"); copy("sharedSnippet", "post_snippet"); copy("sharedImage", "post_image")
        copy("callId", "call_id")
        (fields["durationSec"] as? Int)?.let { extra.put("duration_sec", it) }
        // Media already on the server (a story or post photo being shared) is referenced by id, not uploaded again.
        val ref = listOf("imageRef", "videoRef", "audioRef").firstNotNullOfOrNull { (fields[it] as? String)?.takeIf(String::isNotBlank) }
        val mediaId = ref?.takeIf { MediaLoader.isServer(it) }?.removePrefix("srv:")
        Backend.call({ ChatRepo.send(context, other, type, if (type == "text" || type == "call") text else text, extra, uploadedMediaId = mediaId) }) { result ->
            result.onSuccess { onSent() }.onFailure {
                Toast.makeText(context, Forms.message(it as? Exception, "Could not send"), Toast.LENGTH_LONG).show()
                onFailed()
            }
        }
    }
}
