package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONArray
import org.json.JSONObject

/** How the signed-in user is related to someone else. Drives the Add friend -> Requested -> Friends button. */
enum class Relation { SELF, FRIENDS, INCOMING, OUTGOING, NONE }

/**
 * My friends, incoming requests and outgoing requests (Assignment 3: from the PHP API, cached in SQLite).
 *
 * Read path: SQLite first (instant, offline), then GET friends/list.php, saved back to SQLite.
 * Write path (FriendActions): the change is shown at once and queued in pending_actions; it stays "pending"
 * until the server confirms it, so the button never jumps back while offline.
 */
object Friends {
    val friends = linkedSetOf<String>()
    /** sender uid -> time the request was sent */
    val incoming = linkedMapOf<String, Long>()
    val outgoing = mutableSetOf<String>()
    /** People whose relation was changed offline and is waiting for the server. */
    val pending = mutableSetOf<String>()
    private val observers = mutableListOf<() -> Unit>()
    private var loadedFor: String? = null
    private var refreshing = false

    fun observe(observer: () -> Unit) { observers.add(observer) }
    fun unobserve(observer: () -> Unit) { observers.remove(observer) }
    private fun changed() = observers.toList().forEach { it() }

    /** Loads SQLite (first time) and the server; observers are told after each step. */
    fun refresh() {
        val me = Backend.uid ?: return
        if (loadedFor != me) {
            clearMemory()
            loadedFor = me
            Backend.call({ Backend.store.entity(me, "friends", "all") }) { cached -> cached.getOrNull()?.let { apply(me, JSONObject(it)) } }
        }
        if (refreshing) return
        refreshing = true
        Backend.call({
            val response = Backend.api.get("friends/list.php")
            Backend.store.putEntity(me, "friends", "all", response.toString())
            response
        }) { result ->
            refreshing = false
            result.onSuccess { apply(me, it) }
        }
    }

    private fun apply(me: String, response: JSONObject) {
        if (Backend.uid != me) return
        friends.clear(); incoming.clear(); outgoing.clear()
        val list = response.optJSONArray("friends") ?: JSONArray()
        for (i in 0 until list.length()) list.getJSONObject(i).let { friends.add(it.getString("id")); ProfileCache.putJson(it) }
        val requests = response.optJSONArray("requests") ?: JSONArray()
        for (i in 0 until requests.length()) {
            val request = requests.getJSONObject(i)
            if (request.optString("receiver_id") == me) incoming[request.optString("sender_id")] = TimeFormat.parse(request.optString("created_at"))
            else outgoing.add(request.optString("receiver_id"))
        }
        // Offline changes not yet confirmed by the server win over the (older) server copy.
        Backend.background {
            val local = Backend.store.entitiesWithState(me, "relation").filter { it.second }.map { JSONObject(it.first) }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                pending.clear()
                local.forEach { applyLocal(it.getString("other"), it.getString("state")); pending.add(it.getString("other")) }
                changed()
            }
        }
    }

    /** Shows a friend change immediately (before the server answers). */
    fun applyLocal(other: String, state: String) {
        friends.remove(other); incoming.remove(other); outgoing.remove(other)
        when (state) {
            "friends" -> friends.add(other)
            "requested" -> outgoing.add(other)
            "incoming" -> incoming[other] = System.currentTimeMillis()
        }
    }

    fun markPending(other: String) { pending.add(other); changed() }

    fun clearMemory() {
        friends.clear(); incoming.clear(); outgoing.clear(); pending.clear(); loadedFor = null
    }
}

/**
 * A screen's view of [Friends]: attach it in onStart (through the screen's ListenerBag) and onChange() runs whenever
 * the lists change, so every friend button stays correct. It also refreshes every 10 s while the screen is open.
 */
class FriendState(private val me: String, private val onChange: () -> Unit) {
    val friends get() = Friends.friends
    val incoming get() = Friends.incoming
    val outgoing get() = Friends.outgoing

    fun attach(bag: ListenerBag) {
        val observer: () -> Unit = { onChange() }
        Friends.observe(observer)
        bag.onRemove { Friends.unobserve(observer) }
        bag.poll(10_000) { Friends.refresh() }
        onChange()
    }

    fun relation(uid: String) = when {
        uid == me -> Relation.SELF
        uid in friends -> Relation.FRIENDS
        uid in incoming -> Relation.INCOMING
        uid in outgoing -> Relation.OUTGOING
        else -> Relation.NONE
    }

    /** "Requested · pending" while the request is queued offline. */
    fun labelFor(uid: String): String = label(relation(uid)) + if (uid in Friends.pending) " · pending" else ""

    companion object {
        fun label(relation: Relation) = when (relation) {
            Relation.FRIENDS -> "Friends"
            Relation.INCOMING -> "Confirm"
            Relation.OUTGOING -> "Requested"
            else -> "Add friend"
        }
    }
}
