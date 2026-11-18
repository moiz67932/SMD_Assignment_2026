package com.shadowdevelopers.i230529_i230723


/** One story item. */
data class Story(
    val id: String,
    val ownerId: String,
    val mediaRef: String,
    val type: String,
    val label: String,
    val audience: String,
    val createdAt: Long,
    val expiresAt: Long,
    /** Boomerang: a short clip that loops for the whole story time. */
    val loop: Boolean = false,
    /** Music chosen in the editor (a small audio clip saved in media/{id}) and its name. */
    val audioRef: String = "",
    val audioTitle: String = "",
    /** Friends tagged in the story. */
    val tags: List<String> = emptyList(),
    /** I have already opened it (grey ring instead of teal). */
    val seen: Boolean = false,
    /** My quick reaction to someone else's story. */
    val myReaction: String? = null,
    /** Still queued offline (not on the server yet). */
    val pending: Boolean = false
)

/**
 * Story constants. Stories live in MySQL (stories table) with the photo/video on the server's filesystem;
 * see StoryRepo. The server stops returning a story 24 hours after it was shared.
 */
object Stories {
    const val PUBLIC = "public"
    const val CLOSE_FRIENDS = "closeFriends"
    const val LIFETIME_MS = 24L * 60 * 60 * 1000
}
