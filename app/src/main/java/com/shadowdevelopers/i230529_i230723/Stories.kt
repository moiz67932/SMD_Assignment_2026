package com.shadowdevelopers.i230529_i230723

import com.google.firebase.database.DataSnapshot

/** One story item. */
data class Story(
    val id: String,
    val ownerId: String,
    val mediaRef: String,
    val type: String,
    val label: String,
    val audience: String,
    val createdAt: Long,
    val expiresAt: Long
)

/**
 * Stories in the Realtime Database:
 *   stories/{owner}/public/{storyId}        seen by the owner's friends
 *   stories/{owner}/closeFriends/{storyId}  seen only by people in closeFriends/{owner}
 *   storyViews/{owner}/{storyId}/{viewer} = time the viewer opened it
 *   storyReactions/{owner}/{storyId}/{viewer} = quick reaction sent from the viewer
 * Each story has expiresAt = server time + 24 h. Readers ignore expired ones; the owner's app deletes them.
 */
object Stories {
    const val PUBLIC = "public"
    const val CLOSE_FRIENDS = "closeFriends"
    const val LIFETIME_MS = 24L * 60 * 60 * 1000

    val stories get() = FirebaseData.database.getReference("stories")
    val views get() = FirebaseData.database.getReference("storyViews")
    val closeFriends get() = FirebaseData.database.getReference("closeFriends")
    /** storyReactions/{owner}/{storyId}/{viewer} = "Like" | "Love" | "Laugh" (quick reactions from the viewer) */
    val reactions get() = FirebaseData.database.getReference("storyReactions")

    fun from(snapshot: DataSnapshot, ownerId: String) = Story(
        id = snapshot.key.orEmpty(),
        ownerId = ownerId,
        mediaRef = snapshot.child("mediaRef").getValue(String::class.java).orEmpty(),
        type = snapshot.child("type").getValue(String::class.java) ?: MediaUploader.IMAGE,
        label = snapshot.child("label").getValue(String::class.java).orEmpty(),
        audience = snapshot.child("audience").getValue(String::class.java) ?: PUBLIC,
        createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
        expiresAt = snapshot.child("expiresAt").getValue(Long::class.java) ?: 0L
    )

    /** Active (not expired) stories under one audience node, oldest first. */
    fun active(snapshot: DataSnapshot, ownerId: String): List<Story> =
        snapshot.children.map { from(it, ownerId) }.filter { it.expiresAt > FirebaseData.now() && it.mediaRef.isNotBlank() }.sortedBy { it.createdAt }

    /** Deletes my own expired stories (and who viewed them). Called when Home opens. */
    fun removeExpired(me: String) {
        listOf(PUBLIC, CLOSE_FRIENDS).forEach { audience ->
            stories.child(me).child(audience).orderByChild("expiresAt").endAt(FirebaseData.now().toDouble()).get()
                .addOnSuccessListener { snapshot ->
                    val updates = mutableMapOf<String, Any?>()
                    snapshot.children.forEach { story ->
                        updates["stories/$me/$audience/${story.key}"] = null
                        updates["storyViews/$me/${story.key}"] = null
                        updates["storyReactions/$me/${story.key}"] = null
                    }
                    if (updates.isNotEmpty()) FirebaseData.database.reference.updateChildren(updates)
                }
        }
    }
}
