package com.shadowdevelopers.i230529_i230723

import android.content.Context
import android.net.Uri
import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONArray
import org.json.JSONObject

/**
 * Posts, reactions and comments (screens 04, 05, 06, 07) from the PHP API, cached in SQLite.
 *
 * Reads: the SQLite copy is shown first (instant, works offline), then the server's answer replaces it.
 * Writes: the change is saved locally AND queued in pending_actions in one transaction, shown at once with a
 * "pending" look, and sent in order by the sync job. Each action has a client UUID, so retries never duplicate.
 * All functions that touch SQLite or the network are blocking: call them through Backend.call / Backend.background.
 */
object PostRepo {
    // ----- Reading -----

    /** Home feed (me + friends, newest first) or one person's posts (author != null). */
    fun feed(author: String? = null, fromServer: Boolean): List<FeedPost> {
        val me = Backend.uid ?: return emptyList()
        val key = author?.let { "author_$it" } ?: "home"
        if (fromServer) {
            val path = "posts/feed.php" + (author?.let { "?author=$it" } ?: "")
            val posts = Backend.api.get(path).getJSONArray("posts")
            Backend.store.putEntity(me, "feed", key, posts.toString())
            for (i in 0 until posts.length()) Backend.store.putEntity(me, "post", posts.getJSONObject(i).getString("id"), posts.getJSONObject(i).toString())
        }
        val server = Backend.store.entity(me, "feed", key)?.let(::JSONArray) ?: JSONArray()
        val list = (0 until server.length()).map { FeedPost.fromJson(server.getJSONObject(it)) }
        // My posts created offline (not on the server yet) go on top with the pending look.
        val local = Backend.store.entitiesWithState(me, "post").filter { it.second }.map { FeedPost.fromJson(JSONObject(it.first)).copy(pending = true) }
            .filter { author == null || it.authorId == author }
        return applyLocal(me, (local + list.filter { post -> local.none { it.id == post.id } }).sortedByDescending { it.createdAt })
    }

    /** One post (Comments header, reaction picker, a post shared in chat). */
    fun post(id: String, fromServer: Boolean): FeedPost? {
        val me = Backend.uid ?: return null
        if (fromServer) runCatching {
            val json = Backend.api.get("posts/get.php?id=$id").getJSONObject("post")
            Backend.store.putEntity(me, "post", id, json.toString())
        }
        return Backend.store.entity(me, "post", id)?.let { applyLocal(me, listOf(FeedPost.fromJson(JSONObject(it)))).firstOrNull() }
    }

    /** Pending reactions and deletions made offline are applied on top of the server copy. */
    private fun applyLocal(me: String, posts: List<FeedPost>): List<FeedPost> {
        val reactions = Backend.store.entitiesWithState(me, "reaction").filter { it.second }.map { JSONObject(it.first) }.associateBy { it.getString("post_id") }
        val deleted = Backend.store.entitiesWithState(me, "post_deleted").filter { it.second }.map { JSONObject(it.first).getString("id") }.toSet()
        return posts.filter { it.id !in deleted }.map { post ->
            val local = reactions[post.id] ?: return@map post
            val newReaction = local.optString("reaction").takeUnless { local.isNull("reaction") || it.isBlank() }
            val counts = post.reactionCounts.toMutableMap()
            post.myReaction?.let { old -> counts[old] = (counts[old] ?: 1) - 1 }
            newReaction?.let { counts[it] = (counts[it] ?: 0) + 1 }
            post.copy(myReaction = newReaction, reactionCounts = counts.filterValues { it > 0 }, pending = true)
        }
    }

    /** Reaction picker (05): who reacted with what. */
    fun reactions(postId: String): List<Pair<String, String>> {
        val list = Backend.api.get("posts/reactions.php?post_id=$postId").getJSONArray("reactions")
        return (0 until list.length()).map { i ->
            val row = list.getJSONObject(i)
            ProfileCache.putJson(JSONObject().put("id", row.getString("user_id")).put("display_name", row.optString("display_name")).put("photo_media_id", row.opt("photo_media_id")))
            row.getString("user_id") to row.getString("reaction")
        }
    }

    // ----- Writing (queued, works offline) -----

    /** Sets, changes or removes my reaction (null = remove). One reaction per user per post (server enforced). */
    fun react(postId: String, reaction: String?) {
        val entity = JSONObject().put("post_id", postId).put("reaction", reaction ?: JSONObject.NULL)
        Backend.enqueue("reaction", postId, entity, "post.react", JSONObject().put("post_id", postId).put("reaction", reaction ?: JSONObject.NULL))
    }

    /**
     * Create post (07): text and/or up to 10 photos plus the Assignment 2 extras. The photos are copied into app storage
     * first, so the queued multipart upload (posts/create.php, images[]) still works after a restart or offline.
     */
    fun create(context: Context, text: String, photos: List<Uri>, details: JSONObject): String {
        val me = Backend.uid ?: error("Please log in again")
        val id = Backend.newId()
        val files = photos.map { MediaUploader.prepare(context, me, it, MediaUploader.IMAGE) }
        val profile = Backend.store.entity(me, "user", me)?.let(::JSONObject)
        val local = JSONObject().put("id", id).put("author_id", me).put("author_name", profile?.optString("display_name") ?: "")
            .put("author_photo_media_id", profile?.opt("photo_media_id") ?: JSONObject.NULL)
            .put("body", text).put("created_at", Backend.nowUtc()).put("details", details)
            .put("media", JSONArray()).put("local_images", JSONArray(files.map { Uri.fromFile(it).toString() }))
            .put("reactions", JSONArray()).put("comment_count", 0)
        val payload = JSONObject().put("id", id).put("body", text).put("details", details.toString())
        if (files.isNotEmpty()) payload.put("local_files", JSONArray(files.map { JSONObject().put("field", "images[]").put("path", it.absolutePath).put("mime", "image/jpeg") }))
        Backend.enqueue("post", id, local, "post.create", payload)
        return id
    }

    fun delete(postId: String) {
        Backend.enqueue("post_deleted", postId, JSONObject().put("id", postId), "post.delete", JSONObject().put("id", postId))
    }

    // ----- Comments (06) -----

    data class Comment(
        val id: String, val authorId: String, val authorName: String, val text: String, val imageRef: String,
        val parentId: String, val createdAt: Long, val likes: Int, val likedByMe: Boolean, val pending: Boolean
    )

    fun comments(postId: String, fromServer: Boolean): List<Comment> {
        val me = Backend.uid ?: return emptyList()
        if (fromServer) {
            val list = Backend.api.get("comments/list.php?post_id=$postId").getJSONArray("comments")
            Backend.store.putEntity(me, "comments", postId, list.toString())
        }
        val server = Backend.store.entity(me, "comments", postId)?.let(::JSONArray) ?: JSONArray()
        val likes = Backend.store.entitiesWithState(me, "comment_like").filter { it.second }.map { JSONObject(it.first) }.associateBy { it.getString("comment_id") }
        val deleted = Backend.store.entitiesWithState(me, "comment_deleted").filter { it.second }.map { JSONObject(it.first).getString("id") }.toSet()
        val local = Backend.store.entitiesWithState(me, "comment").filter { it.second }.map { JSONObject(it.first) }.filter { it.getString("post_id") == postId }
        val all = (0 until server.length()).map { server.getJSONObject(it) }.map { toComment(it, false) } +
            local.map { toComment(it, true) }.filter { mine -> (0 until server.length()).none { server.getJSONObject(it).getString("id") == mine.id } }
        return all.filter { it.id !in deleted }.map { comment ->
            val like = likes[comment.id] ?: return@map comment
            val liked = like.getBoolean("liked")
            if (liked == comment.likedByMe) comment
            else comment.copy(likedByMe = liked, likes = comment.likes + if (liked) 1 else -1, pending = true)
        }.sortedBy { it.createdAt }
    }

    private fun toComment(json: JSONObject, pending: Boolean): Comment {
        ProfileCache.putJson(JSONObject().put("id", json.getString("author_id")).put("display_name", json.optString("author_name")).put("photo_media_id", json.opt("author_photo_media_id")))
        val media = json.optString("media_id").takeUnless { json.isNull("media_id") || it.isBlank() }
        return Comment(
            id = json.getString("id"), authorId = json.getString("author_id"), authorName = json.optString("author_name"),
            text = json.optString("body"), imageRef = json.optString("local_image").ifBlank { ProfileCache.mediaRef(media) },
            parentId = json.optString("parent_id").takeUnless { json.isNull("parent_id") }.orEmpty(),
            createdAt = TimeFormat.parse(json.optString("created_at")), likes = json.optInt("likes"),
            likedByMe = json.optInt("liked_by_me") == 1 || json.optBoolean("liked_by_me"), pending = pending
        )
    }

    /**
     * A comment or reply, optionally with one photo. The photo is uploaded first (media.upload) and the comment refers
     * to its media id; the queue keeps that order, so this also works fully offline.
     */
    fun comment(context: Context, postId: String, text: String, parentId: String?, photo: Uri?) {
        val me = Backend.uid ?: error("Please log in again")
        val id = Backend.newId()
        var mediaId: String? = null
        var localImage = ""
        if (photo != null) {
            val file = MediaUploader.prepare(context, me, photo, MediaUploader.IMAGE)
            mediaId = Backend.newId()
            localImage = Uri.fromFile(file).toString()
            Backend.enqueue("media", mediaId, JSONObject().put("id", mediaId), "media.upload",
                JSONObject().put("id", mediaId).put("local_files", JSONArray().put(JSONObject().put("field", "file").put("path", file.absolutePath).put("mime", "image/jpeg"))))
        }
        val profile = Backend.store.entity(me, "user", me)?.let(::JSONObject)
        val local = JSONObject().put("id", id).put("post_id", postId).put("author_id", me).put("author_name", profile?.optString("display_name") ?: "")
            .put("body", text).put("parent_id", parentId ?: JSONObject.NULL).put("media_id", JSONObject.NULL).put("local_image", localImage)
            .put("created_at", Backend.nowUtc()).put("likes", 0).put("liked_by_me", 0)
        val payload = JSONObject().put("id", id).put("post_id", postId).put("body", text).put("parent_id", parentId ?: JSONObject.NULL).put("media_id", mediaId ?: JSONObject.NULL)
        Backend.enqueue("comment", id, local, "comment.create", payload)
    }

    fun likeComment(commentId: String, liked: Boolean) {
        Backend.enqueue("comment_like", commentId, JSONObject().put("comment_id", commentId).put("liked", liked), "comment.like",
            JSONObject().put("comment_id", commentId).put("liked", liked))
    }

    fun deleteComment(commentId: String) {
        Backend.enqueue("comment_deleted", commentId, JSONObject().put("id", commentId), "comment.delete", JSONObject().put("id", commentId))
    }
}
