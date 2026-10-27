package com.shadowdevelopers.i230529_i230723

import android.content.Context
import android.widget.Toast
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

/**
 * Sends a chat message from any screen (Chat, story replies, shared posts).
 * chats/{chatId} where chatId = the two uids sorted and joined with "_":
 *   members/{uid} = true, messages/{messageId}, lastMessage, unread/{uid}
 * userChats/{uid}/{chatId} = the other person's uid (the Chats list index).
 */
object ChatSender {
    fun chatId(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    /** Creates the chat (members + both userChats entries) the first time two people talk. */
    fun ensureChat(me: String, other: String, onReady: (String) -> Unit, onError: (String) -> Unit) {
        val id = chatId(me, other)
        val members = FirebaseData.database.getReference("chats").child(id).child("members")
        val finish = {
            FirebaseData.database.reference.updateChildren(mapOf("userChats/$me/$id" to other, "userChats/$other/$id" to me))
                .addOnSuccessListener { onReady(id) }
                .addOnFailureListener { onError(it.localizedMessage ?: "Could not open the chat") }
        }
        members.get().addOnSuccessListener { snapshot ->
            when {
                !snapshot.exists() -> members.setValue(mapOf(me to true, other to true))
                    .addOnSuccessListener { finish() }
                    .addOnFailureListener { onError(it.localizedMessage ?: "Could not create the chat") }
                snapshot.child(me).getValue(Boolean::class.java) == true && snapshot.child(other).getValue(Boolean::class.java) == true -> finish()
                else -> onError("This conversation is not available to your account")
            }
        }.addOnFailureListener { onError(it.localizedMessage ?: "Could not open the chat") }
    }

    /**
     * Writes one message plus the list preview ("Photo", "Shared a post", ...), bumps the other person's
     * unread counter and leaves them a notification record. `fields` holds the message-type specific data.
     */
    fun send(context: Context, me: String, other: String, type: String, text: String, preview: String, fields: Map<String, Any> = emptyMap(), onSent: () -> Unit = {}, onFailed: () -> Unit = {}) {
        ensureChat(me, other, onReady = { id ->
            val chat = FirebaseData.database.getReference("chats").child(id)
            val messageId = chat.child("messages").push().key ?: return@ensureChat onFailed()
            ProfileCache.get(me) { profile ->
                val now = mapOf(".sv" to "timestamp")
                val message = mutableMapOf<String, Any>(
                    "messageId" to messageId, "senderId" to me, "senderName" to profile.name,
                    "text" to text, "type" to type, "timestamp" to now, "edited" to false
                )
                message.putAll(fields)
                chat.child("messages").child(messageId).setValue(message)
                    .addOnSuccessListener {
                        chat.child("lastMessage").setValue(mapOf("text" to preview, "senderId" to me, "timestamp" to now, "messageId" to messageId))
                        chat.child("unread").child(other).runTransaction(object : Transaction.Handler {
                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                currentData.value = (currentData.getValue(Int::class.java) ?: 0) + 1
                                return Transaction.success(currentData)
                            }

                            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) = Unit
                        })
                        FirebaseData.database.getReference("notifications").child(other).push().setValue(mapOf(
                            "senderId" to me, "type" to "message", "chatId" to id, "body" to preview.take(120), "createdAt" to now, "read" to false
                        ))
                        onSent()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, it.localizedMessage ?: "Could not send", Toast.LENGTH_LONG).show()
                        onFailed()
                    }
            }
        }, onError = { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onFailed()
        })
    }
}
