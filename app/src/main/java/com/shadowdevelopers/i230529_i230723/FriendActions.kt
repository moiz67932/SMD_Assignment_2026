package com.shadowdevelopers.i230529_i230723

import android.content.Context
import android.widget.Toast

/**
 * Every friend-request change in one place. Each action is a single multi-path update, so both
 * sides of a friendship (and the request indexes) change together or not at all.
 *
 *   friendRequests/{receiver}/{sender} = {senderId, receiverId, createdAt}   incoming requests
 *   sentRequests/{sender}/{receiver}   = createdAt                            outgoing requests ("Requested")
 *   friends/{a}/{b} = true and friends/{b}/{a} = true                         a confirmed friendship
 */
object FriendActions {
    private val root get() = FirebaseData.database.reference
    private val now = mapOf(".sv" to "timestamp")

    fun sendRequest(context: Context, me: String, other: String) = run(context, "Could not send the request", mapOf(
        "friendRequests/$other/$me" to mapOf("senderId" to me, "receiverId" to other, "createdAt" to now),
        "sentRequests/$me/$other" to now,
        notification(other, me, "friendRequest", "sent you a friend request")
    ))

    fun cancelRequest(context: Context, me: String, other: String) = run(context, "Could not cancel the request", mapOf(
        "friendRequests/$other/$me" to null,
        "sentRequests/$me/$other" to null
    ))

    fun confirm(context: Context, me: String, other: String) = run(context, "Could not confirm the request", mapOf(
        "friends/$me/$other" to true,
        "friends/$other/$me" to true,
        "friendRequests/$me/$other" to null,
        "sentRequests/$other/$me" to null,
        notification(other, me, "friendAccepted", "accepted your friend request")
    ))

    fun deleteIncoming(context: Context, me: String, other: String) = run(context, "Could not delete the request", mapOf(
        "friendRequests/$me/$other" to null,
        "sentRequests/$other/$me" to null
    ))

    fun unfriend(context: Context, me: String, other: String) = run(context, "Could not remove this friend", mapOf(
        "friends/$me/$other" to null,
        "friends/$other/$me" to null
    ))

    private fun notification(to: String, from: String, type: String, body: String): Pair<String, Any> {
        val id = FirebaseData.database.getReference("notifications").child(to).push().key.orEmpty()
        return "notifications/$to/$id" to mapOf("senderId" to from, "type" to type, "body" to body, "createdAt" to now, "read" to false)
    }

    private fun run(context: Context, error: String, updates: Map<String, Any?>) {
        root.updateChildren(updates).addOnFailureListener {
            Toast.makeText(context, it.localizedMessage ?: error, Toast.LENGTH_LONG).show()
        }
    }
}
