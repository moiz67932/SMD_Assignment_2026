package com.shadowdevelopers.i230529_i230723

import android.content.Context
import android.net.Uri
import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stories (09-12) from the PHP API. Metadata is in MySQL, the photo/video on the server's filesystem.
 * The server stops returning a story after 24 hours; this repository also drops expired stories from the SQLite
 * cache every time it reads it, so they disappear on the phone even offline.
 * Writes (share, view, react, delete) are queued in pending_actions and synced in order.
 */
object StoryRepo {
    /** Stories I may see (mine, friends' public ones, close-friends ones I am on), oldest first. */
    fun feed(fromServer: Boolean): List<Story> {
        val me = Backend.uid ?: return emptyList()
        if (fromServer) {
            val list = Backend.api.get("stories/feed.php").getJSONArray("stories")
            Backend.store.putEntity(me, "stories", "all", list.toString())
            for (i in 0 until list.length()) list.getJSONObject(i).let {
                ProfileCache.putJson(JSONObject().put("id", it.getString("owner_id")).put("display_name", it.optString("owner_name")).put("photo_media_id", it.opt("owner_photo_media_id")))
            }
        }
        val now = System.currentTimeMillis()
        val cached = Backend.store.entity(me, "stories", "all")?.let(::JSONArray) ?: JSONArray()
        val all = (0 until cached.length()).map { cached.getJSONObject(it) }
        val live = all.filter { TimeFormat.parse(it.optString("expires_at")) > now }
        // Expired stories are removed from SQLite too (not just hidden).
        if (live.size != all.size) Backend.store.putEntity(me, "stories", "all", JSONArray(live).toString())
        val viewed = Backend.store.entitiesWithState(me, "story_view").filter { it.second }.map { JSONObject(it.first).getString("id") }.toSet()
        val deleted = Backend.store.entitiesWithState(me, "story_deleted").filter { it.second }.map { JSONObject(it.first).getString("id") }.toSet()
        val reactions = Backend.store.entitiesWithState(me, "story_reaction").filter { it.second }.map { JSONObject(it.first) }.associateBy { it.getString("id") }
        val local = Backend.store.entitiesWithState(me, "story").filter { it.second }.map { JSONObject(it.first) }
            .filter { TimeFormat.parse(it.optString("expires_at")) > now }.map { parse(it).copy(pending = true) }
        val server = live.map(::parse).map { story ->
            var result = story
            if (story.id in viewed) result = result.copy(seen = true)
            reactions[story.id]?.let { r -> result = result.copy(myReaction = r.optString("reaction").takeUnless { r.isNull("reaction") }) }
            result
        }
        return (server + local.filter { mine -> server.none { it.id == mine.id } }).filter { it.id !in deleted }.sortedBy { it.createdAt }
    }

    private fun parse(json: JSONObject): Story {
        val extra = json.optJSONObject("extra") ?: JSONObject()
        val tags = extra.optJSONArray("tags") ?: JSONArray()
        val mime = json.optString("mime_type")
        return Story(
            id = json.getString("id"),
            ownerId = json.optString("owner_id"),
            mediaRef = json.optString("local_media").ifBlank { ProfileCache.mediaRef(json.optString("media_id")) },
            type = if (mime.startsWith("video") || json.optString("type") == MediaUploader.VIDEO) MediaUploader.VIDEO else MediaUploader.IMAGE,
            label = json.optString("label"),
            audience = if (json.optString("audience") == "close_friends") Stories.CLOSE_FRIENDS else Stories.PUBLIC,
            createdAt = TimeFormat.parse(json.optString("created_at")),
            expiresAt = TimeFormat.parse(json.optString("expires_at")),
            loop = extra.optBoolean("loop"),
            audioRef = json.optString("local_audio").ifBlank { ProfileCache.mediaRef(extra.optString("audio_media_id").takeIf(String::isNotBlank)) },
            audioTitle = extra.optString("audio_title"),
            tags = (0 until tags.length()).map { tags.getString(it) },
            seen = json.optInt("viewed_by_me") == 1 || json.optBoolean("viewed_by_me"),
            myReaction = json.optString("my_reaction").takeUnless { json.isNull("my_reaction") || it.isBlank() }
        )
    }

    /**
     * Share a story (Story editor 10): the photo/video (and optional music) are copied into app storage and uploaded
     * first, then stories/create.php is called with their media ids. All queued, so this works offline as well.
     */
    fun create(context: Context, media: Uri, type: String, label: String, audience: String, loop: Boolean, music: Uri?, musicTitle: String, tags: List<String>) {
        val me = Backend.uid ?: error("Please log in again")
        val id = Backend.newId()
        val file = MediaUploader.prepare(context, me, media, type)
        val mediaId = Backend.newId()
        queueUpload(mediaId, file, MediaUploader.mime(type))
        val extra = JSONObject()
        if (loop) extra.put("loop", true)
        if (tags.isNotEmpty()) extra.put("tags", JSONArray(tags))
        var localAudio = ""
        if (music != null) {
            val audio = MediaUploader.prepare(context, me, music, MediaUploader.AUDIO)
            val audioId = Backend.newId()
            queueUpload(audioId, audio, MediaUploader.mime(MediaUploader.AUDIO))
            extra.put("audio_media_id", audioId).put("audio_title", musicTitle)
            localAudio = Uri.fromFile(audio).toString()
        }
        val serverAudience = if (audience == Stories.CLOSE_FRIENDS) "close_friends" else "public"
        val created = Backend.nowUtc()
        val expires = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(System.currentTimeMillis() + Stories.LIFETIME_MS))
        val local = JSONObject().put("id", id).put("owner_id", me).put("label", label).put("audience", serverAudience)
            .put("type", type).put("local_media", Uri.fromFile(file).toString()).put("local_audio", localAudio)
            .put("extra", extra).put("created_at", created).put("expires_at", expires)
        Backend.enqueue("story", id, local, "story.create",
            JSONObject().put("id", id).put("media_id", mediaId).put("label", label).put("audience", serverAudience).put("extra", if (extra.length() == 0) JSONObject.NULL else extra))
    }

    private fun queueUpload(mediaId: String, file: java.io.File, mime: String) {
        Backend.enqueue("media", mediaId, JSONObject().put("id", mediaId), "media.upload",
            JSONObject().put("id", mediaId).put("local_files", JSONArray().put(JSONObject().put("field", "file").put("path", file.absolutePath).put("mime", mime))))
    }

    fun markViewed(story: Story) {
        if (story.seen || story.ownerId == Backend.uid || story.pending) return
        Backend.enqueue("story_view", story.id, JSONObject().put("id", story.id), "story.view", JSONObject().put("id", story.id))
    }

    fun react(story: Story, reaction: String?) {
        Backend.enqueue("story_reaction", story.id, JSONObject().put("id", story.id).put("reaction", reaction ?: JSONObject.NULL), "story.react",
            JSONObject().put("id", story.id).put("reaction", reaction ?: JSONObject.NULL))
    }

    fun delete(story: Story) {
        Backend.enqueue("story_deleted", story.id, JSONObject().put("id", story.id), "story.delete", JSONObject().put("id", story.id))
    }

    /** Your story (12): who viewed this story and how they reacted. Needs the network. */
    data class Viewer(val uid: String, val name: String, val time: Long, val reaction: String?)

    fun viewers(story: Story): List<Viewer> {
        val list = Backend.api.get("stories/viewers.php?id=${story.id}").getJSONArray("viewers")
        return (0 until list.length()).map { list.getJSONObject(it) }.map {
            ProfileCache.putJson(JSONObject().put("id", it.getString("viewer_id")).put("display_name", it.optString("display_name")).put("photo_media_id", it.opt("photo_media_id")))
            Viewer(it.getString("viewer_id"), it.optString("display_name"), TimeFormat.parse(it.optString("viewed_at")), it.optString("reaction").takeUnless { r -> it.isNull("reaction") || r.isBlank() })
        }
    }

    // ----- Close friends (who sees "Close friends" stories) -----

    fun closeFriends(fromServer: Boolean): Set<String> {
        val me = Backend.uid ?: return emptySet()
        if (fromServer) Backend.store.putEntity(me, "close_friends", "all", Backend.api.get("close-friends/list.php").getJSONArray("members").toString())
        val list = Backend.store.entity(me, "close_friends", "all")?.let(::JSONArray) ?: JSONArray()
        val ids = (0 until list.length()).map { list.getJSONObject(it).getString("id") }.toMutableSet()
        Backend.store.entitiesWithState(me, "close_friend").filter { it.second }.map { JSONObject(it.first) }.forEach {
            if (it.getBoolean("enabled")) ids.add(it.getString("id")) else ids.remove(it.getString("id"))
        }
        return ids
    }

    fun setCloseFriend(uid: String, enabled: Boolean) {
        Backend.enqueue("close_friend", uid, JSONObject().put("id", uid).put("enabled", enabled), "close_friend.set",
            JSONObject().put("member_id", uid).put("enabled", enabled))
    }
}
