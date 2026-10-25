package com.shadowdevelopers.i230529_i230723

import com.google.firebase.database.DataSnapshot

/** Public profile fields shown next to people's names (from profiles/{uid}). */
data class UserProfile(
    val uid: String,
    val name: String,
    val username: String = "",
    val bio: String = "",
    val photo: String = "",
    val cover: String = ""
)

/**
 * Remembers profiles already loaded during this app session, so lists (friends, notifications,
 * comments, chats) don't read the same profiles/{uid} again for every row they draw.
 * All calls happen on the main thread (Firebase delivers callbacks there).
 */
object ProfileCache {
    private val cache = mutableMapOf<String, UserProfile>()
    private val waiting = mutableMapOf<String, MutableList<(UserProfile) -> Unit>>()

    fun from(snapshot: DataSnapshot) = UserProfile(
        uid = snapshot.key.orEmpty(),
        name = snapshot.child("name").getValue(String::class.java) ?: "Kinnect member",
        username = snapshot.child("username").getValue(String::class.java).orEmpty(),
        bio = snapshot.child("bio").getValue(String::class.java).orEmpty(),
        photo = snapshot.child("photo").getValue(String::class.java).orEmpty(),
        cover = snapshot.child("cover").getValue(String::class.java).orEmpty()
    )

    fun put(profile: UserProfile) {
        cache[profile.uid] = profile
    }

    fun get(uid: String, onReady: (UserProfile) -> Unit) {
        cache[uid]?.let { onReady(it); return }
        val callbacks = waiting.getOrPut(uid) { mutableListOf() }
        callbacks.add(onReady)
        if (callbacks.size > 1) return // a read for this uid is already running
        FirebaseData.profiles.child(uid).get()
            .addOnSuccessListener { snapshot ->
                val profile = from(snapshot)
                put(profile)
                waiting.remove(uid)?.forEach { it(profile) }
            }
            .addOnFailureListener {
                waiting.remove(uid)?.forEach { it(UserProfile(uid, "Kinnect member")) }
            }
    }
}
