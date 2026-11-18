package com.shadowdevelopers.i230529_i230723

import com.google.firebase.database.DataSnapshot
import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONObject

/** Public profile fields shown next to people's names. Photo and cover are media refs ("srv:<media id>"). */
data class UserProfile(
    val uid: String,
    val name: String,
    val username: String = "",
    val bio: String = "",
    val photo: String = "",
    val cover: String = ""
)

/**
 * Profiles for names and avatars in every list (friends, notifications, comments, chats).
 * Order: memory -> SQLite (instant, works offline) -> GET profiles/get.php (then saved to SQLite for next time).
 * All callbacks run on the main thread.
 */
object ProfileCache {
    private val cache = mutableMapOf<String, UserProfile>()
    private val waiting = mutableMapOf<String, MutableList<(UserProfile) -> Unit>>()

    /** Assignment 2 (Firebase) shape; still used by screens that are not migrated yet. */
    fun from(snapshot: DataSnapshot) = UserProfile(
        uid = snapshot.key.orEmpty(),
        name = snapshot.child("name").getValue(String::class.java) ?: "Kinnect member",
        username = snapshot.child("username").getValue(String::class.java).orEmpty(),
        bio = snapshot.child("bio").getValue(String::class.java).orEmpty(),
        photo = snapshot.child("photo").getValue(String::class.java).orEmpty(),
        cover = snapshot.child("cover").getValue(String::class.java).orEmpty()
    )

    /** A `user` object from the PHP API (profiles/get.php, search, friends...). */
    fun fromJson(json: JSONObject) = UserProfile(
        uid = json.optString("id"),
        name = json.optString("display_name").ifBlank { "Kinnect member" },
        username = json.optString("username").takeUnless { it == "null" }.orEmpty(),
        bio = json.optString("bio").takeUnless { it == "null" }.orEmpty(),
        photo = mediaRef(json.optString("photo_media_id")),
        cover = mediaRef(json.optString("cover_media_id"))
    )

    /** A server media id as the ref MediaLoader/AvatarBinder understand. */
    fun mediaRef(id: String?): String = if (id.isNullOrBlank() || id == "null") "" else "srv:$id"

    fun put(profile: UserProfile) {
        cache[profile.uid] = profile
    }

    /** Saves a server user object to memory and SQLite, so names and photos show offline. */
    fun putJson(json: JSONObject) {
        val profile = fromJson(json)
        if (profile.uid.isBlank()) return
        put(profile)
        val account = Backend.uid ?: return
        Backend.background { Backend.store.putEntity(account, "user", profile.uid, json.toString()) }
    }

    /** The cached name, or "" if this profile has not been loaded yet (used for local filtering). */
    fun peekName(uid: String) = cache[uid]?.name.orEmpty()

    /** Forget a profile (after editing my own) so the next get() reloads it. */
    fun invalidate(uid: String) {
        cache.remove(uid)
    }

    fun get(uid: String, onReady: (UserProfile) -> Unit) {
        cache[uid]?.let { onReady(it); return }
        val callbacks = waiting.getOrPut(uid) { mutableListOf() }
        callbacks.add(onReady)
        if (callbacks.size > 1) return // a load for this uid is already running
        val account = Backend.uid
        Backend.call({
            // 1) SQLite copy (instant and offline), 2) the server (fresh), saved back to SQLite.
            val cached = account?.let { Backend.store.entity(it, "user", uid) }?.let { JSONObject(it) }
            val fresh = runCatching { Backend.api.get("profiles/get.php?id=$uid").getJSONObject("user") }.getOrNull()
            if (fresh != null && account != null) Backend.store.putEntity(account, "user", uid, fresh.toString())
            fresh ?: cached
        }) { result ->
            val profile = result.getOrNull()?.let(::fromJson)?.takeIf { it.uid.isNotBlank() } ?: UserProfile(uid, "Kinnect member")
            if (result.getOrNull() != null) put(profile)
            waiting.remove(uid)?.forEach { it(profile) }
        }
    }
}
