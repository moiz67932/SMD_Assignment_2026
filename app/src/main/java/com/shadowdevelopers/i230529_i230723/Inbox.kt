package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONArray
import org.json.JSONObject

/**
 * Notifications (screen 18) and the red bell count, from GET notifications/list.php (cached in SQLite).
 * Mark-as-read and remove are queued (pending_actions), so they work offline and sync later.
 */
object Inbox {
    /** Server notification types -> the names the Assignment 2 screens use. */
    private fun type(server: String) = when (server) {
        "friend_request" -> "friendRequest"
        "friend_accepted" -> "friendAccepted"
        else -> server
    }

    private fun parse(json: JSONObject) = KinnectNotification(
        id = json.optString("id"),
        senderId = json.optString("actor_id"),
        type = type(json.optString("type")),
        body = json.optString("body"),
        chatId = "",
        postId = if (json.optString("type") in setOf("reaction", "comment", "reply")) json.optString("resource_id") else "",
        createdAt = TimeFormat.parse(json.optString("created_at")),
        read = !json.isNull("read_at") && json.optString("read_at").isNotBlank()
    )

    /** SQLite copy first (so the screen fills instantly and offline), then the server's list. */
    fun load(onCached: (List<KinnectNotification>) -> Unit, onFresh: (Result<List<KinnectNotification>>) -> Unit) {
        val me = Backend.uid ?: return
        Backend.call({ cached(me) }) { it.getOrNull()?.let(onCached) }
        Backend.call({
            val response = Backend.api.get("notifications/list.php")
            Backend.store.putEntity(me, "notifications", "all", response.toString())
            cached(me)
        }, onFresh)
    }

    /** The cached list with my offline changes (read / removed) applied on top. */
    private fun cached(me: String): List<KinnectNotification> {
        val list = Backend.store.entity(me, "notifications", "all")?.let { JSONObject(it).optJSONArray("notifications") } ?: JSONArray()
        val local = Backend.store.entitiesWithState(me, "notification_state").filter { it.second }.map { JSONObject(it.first) }
        val removed = local.filter { it.optBoolean("removed") }.map { it.getString("id") }.toSet()
        val read = local.filter { it.optBoolean("read") }.map { it.getString("id") }.toSet()
        return (0 until list.length()).map { parse(list.getJSONObject(it)) }
            .filter { it.id !in removed && it.senderId.isNotBlank() }
            .map { if (it.id in read) it.copy(read = true) else it }
    }

    fun unreadCount(onCount: (Int) -> Unit) {
        val me = Backend.uid ?: return
        Backend.call({
            runCatching { Backend.store.putEntity(me, "notifications", "all", Backend.api.get("notifications/list.php").toString()) }
            cached(me).count { !it.read }
        }) { it.getOrNull()?.let(onCount) }
    }

    fun markRead(item: KinnectNotification) {
        if (item.read) return
        queue(item.id, JSONObject().put("id", item.id).put("read", true), "notification.read")
    }

    fun remove(item: KinnectNotification) {
        queue(item.id, JSONObject().put("id", item.id).put("removed", true), "notification.delete")
    }

    private fun queue(id: String, state: JSONObject, operation: String) {
        Backend.background { Backend.enqueue("notification_state", id, state, operation, JSONObject().put("id", id)) }
    }
}
