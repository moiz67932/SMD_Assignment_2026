package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Profile (15): the signed-in user's own profile, cover, details, friends and posts. */
class ProfileActivity : AppCompatActivity() {
    private val uid by lazy { Backend.uid }
    private val friendCardIds = listOf(R.id.profileFriend1, R.id.profileFriend2, R.id.profileFriend3)
    private val listeners = ListenerBag()
    private lateinit var postAdapter: PostAdapter
    private lateinit var rowAdapter: TextRowAdapter
    private var allPosts: List<FeedPost> = emptyList()
    private var tab = "Posts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        if (uid == null) {
            finish()
            return
        }
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        open(R.id.searchButton, SearchActivity::class.java)
        open(R.id.addStoryButton, CameraActivity::class.java)
        open(R.id.editProfileButton, EditProfileActivity::class.java)
        open(R.id.profileCoverCamera, EditProfileActivity::class.java)
        open(R.id.profileAvatarCamera, EditProfileActivity::class.java)
        open(R.id.findFriendsFromProfile, SearchActivity::class.java)

        postAdapter = PostAdapter(
            me = uid.orEmpty(),
            onComments = { post -> startActivity(Intent(this, CommentsActivity::class.java).putExtra("post_id", post.id)) },
            onAuthor = {},
            onShare = { post -> startActivity(Intent(this, SharePostActivity::class.java).putExtra("post_id", post.id)) },
            onChanged = { uid?.let { loadPosts(it, fromServer = false) } }
        )
        rowAdapter = TextRowAdapter { row ->
            when {
                row.extra.startsWith("group:") -> startActivity(Intent(this, GroupChatActivity::class.java).putExtra("group_id", row.id).putExtra("group_name", row.title))
                else -> startActivity(Intent(this, MediaViewerActivity::class.java)
                    .putStringArrayListExtra("media_refs", arrayListOf(row.extra)).putStringArrayListExtra("media_types", arrayListOf(MediaUploader.VIDEO)))
            }
        }
        mapOf(R.id.profileTabPosts to "Posts", R.id.profileTabPhotos to "Photos", R.id.profileTabVideos to "Videos", R.id.profileTabGroups to "Groups").forEach { (id, name) ->
            findViewById<View>(id).setOnClickListener { selectTab(name) }
        }
        findViewById<View>(R.id.profileMoreButton).setOnClickListener { anchor ->
            android.widget.PopupMenu(this, anchor).apply {
                menu.add(0, 1, 0, "Edit profile")
                menu.add(0, 2, 1, "Close friends")
                menu.add(0, 3, 2, "Saved posts")
                setOnMenuItemClickListener { item ->
                    startActivity(when (item.itemId) {
                        1 -> Intent(this@ProfileActivity, EditProfileActivity::class.java)
                        2 -> Intent(this@ProfileActivity, CloseFriendsActivity::class.java)
                        else -> Intent(this@ProfileActivity, SimpleListActivity::class.java).putExtra("mode", "saved")
                    })
                    true
                }
            }.show()
        }
        findViewById<RecyclerView>(R.id.profilePostsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@ProfileActivity)
            isNestedScrollingEnabled = false
            adapter = postAdapter
        }
    }

    override fun onStart() {
        super.onStart()
        val userId = uid ?: return
        postAdapter.resume()
        // My profile (GET profiles/get.php: birthday, gender and join date are returned only to me) and my friends
        // (GET friends/of.php), both shown from SQLite first so the screen works offline.
        listeners.poll(15_000) { loadProfile(userId) }
        // My posts: SQLite first, then GET posts/feed.php?author=me.
        loadPosts(userId, fromServer = false)
        listeners.poll(20_000) { loadPosts(userId, fromServer = true) }
    }

    /** Posts = everything; Photos = posts with pictures; Videos = my highlighted video stories; Groups = my groups. */
    private fun selectTab(name: String) {
        tab = name
        val ids = mapOf("Posts" to R.id.profileTabPosts, "Photos" to R.id.profileTabPhotos, "Videos" to R.id.profileTabVideos, "Groups" to R.id.profileTabGroups)
        ids.forEach { (label, id) ->
            findViewById<TextView>(id).apply {
                setBackgroundResource(if (label == name) R.drawable.rounded_teal_soft else 0)
                setTextColor(getColor(if (label == name) R.color.primary else R.color.text_primary))
            }
        }
        val list = findViewById<RecyclerView>(R.id.profilePostsRecyclerView)
        val empty = findViewById<TextView>(R.id.profilePostsEmpty)
        val userId = uid.orEmpty()
        fun showRows(rows: List<TextRow>, message: String) {
            if (tab != name) return
            list.adapter = rowAdapter
            rowAdapter.set(rows)
            empty.text = message
            empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
        when (name) {
            // Video highlights (GET highlights/list.php) and my groups (GET groups/list.php).
            "Videos" -> Backend.call({ Backend.api.get("highlights/list.php?user=$userId").getJSONArray("highlights") }) { result ->
                result.onSuccess { list ->
                    showRows((0 until list.length()).map { list.getJSONObject(it) }.filter { it.optString("mime_type").startsWith("video") }.map {
                        TextRow(it.getString("story_id"), it.optString("label").ifBlank { "Video highlight" }, "Tap to play", ProfileCache.mediaRef(it.getString("media_id")))
                    }, "No videos yet. Highlight a video story from Your story.")
                }.onFailure { showRows(emptyList(), Forms.message(it as? Exception, "Could not load videos")) }
            }
            "Groups" -> Backend.call({ Backend.api.get("groups/list.php").getJSONArray("groups") }) { result ->
                result.onSuccess { list ->
                    showRows((0 until list.length()).map { list.getJSONObject(it) }.map { TextRow(it.getString("id"), it.optString("name").ifBlank { "Group" }, "Tap to open the chat", "group:") },
                        "No groups yet. Create one from Menu → Groups.")
                }.onFailure { showRows(emptyList(), Forms.message(it as? Exception, "Could not load groups")) }
            }
            else -> {
                val posts = if (name == "Photos") allPosts.filter { it.images.isNotEmpty() } else allPosts
                list.adapter = postAdapter
                postAdapter.setPosts(posts)
                empty.text = if (name == "Photos") "No photos yet" else "No posts yet"
                empty.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun loadPosts(userId: String, fromServer: Boolean) {
        Backend.call({ PostRepo.feed(author = userId, fromServer = fromServer) }) { result ->
            result.onSuccess { allPosts = it; selectTab(tab) }
        }
    }

    private fun loadProfile(userId: String) {
        Backend.call({
            val user = runCatching { Backend.api.get("profiles/get.php").getJSONObject("user") }
                .onSuccess { Backend.store.putEntity(userId, "user", userId, it.toString()) }
                .getOrElse { Backend.store.entity(userId, "user", userId)?.let { org.json.JSONObject(it) } }
            val friends = runCatching { Backend.api.get("friends/of.php?id=$userId").getJSONArray("friends") }
                .onSuccess { Backend.store.putEntity(userId, "friends_of", userId, it.toString()) }
                .getOrElse { Backend.store.entity(userId, "friends_of", userId)?.let { org.json.JSONArray(it) } }
            user to friends
        }) { result ->
            val (user, friends) = result.getOrNull() ?: return@call
            if (user != null) showDetails(user)
            if (friends != null) showFriends((0 until friends.length()).map { friends.getJSONObject(it).also(ProfileCache::putJson).getString("id") })
        }
    }

    private fun showDetails(user: org.json.JSONObject) {
        fun text(key: String) = user.optString(key).takeUnless { it == "null" }.orEmpty()
        val profile = ProfileCache.fromJson(user)
        ProfileCache.put(profile)
        findViewById<TextView>(R.id.profileToolbarName).text = profile.name
        findViewById<TextView>(R.id.profileDisplayName).text = profile.name
        findViewById<TextView>(R.id.profileDisplayBio).text = profile.bio
        val birthday = text("birthday")
        val gender = text("gender")
        val createdAt = TimeFormat.parse(text("created_at"))
        findViewById<TextView>(R.id.profileBirthdayValue).text = birthday
        findViewById<View>(R.id.profileBirthdayRow).visibility = if (birthday.isBlank()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.profileGenderValue).text = gender
        findViewById<View>(R.id.profileGenderRow).visibility = if (gender.isBlank()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.profileLocationRow).visibility = View.GONE
        findViewById<TextView>(R.id.profileJoinedValue).text = if (createdAt > 0L) "Joined ${android.text.format.DateFormat.format("MMMM yyyy", createdAt)}" else ""
        findViewById<View>(R.id.profileJoinedRow).visibility = if (createdAt > 0L) View.VISIBLE else View.GONE
        AvatarBinder.bind(findViewById(R.id.profileAvatar), profile.name, profile.photo)
        val cover = findViewById<ImageView>(R.id.profileCoverImage)
        if (profile.cover.isBlank()) cover.setImageDrawable(null) else MediaLoader.load(cover, profile.cover)
    }

    private fun showFriends(friends: List<String>) {
        val label = if (friends.size == 1) "1 friend" else "${friends.size} friends"
        findViewById<TextView>(R.id.profileFriendCount).text = label
        findViewById<TextView>(R.id.profileFriendsCountBottom).text = label
        friendCardIds.forEachIndexed { index, viewId ->
            val card = findViewById<TextView>(viewId)
            val friendUid = friends.getOrNull(index)
            card.visibility = if (friendUid == null) View.INVISIBLE else View.VISIBLE
            if (friendUid != null) {
                card.setOnClickListener { startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", friendUid)) }
                ProfileCache.get(friendUid) { AvatarBinder.bind(card, it.name, it.photo, cornerRadiusDp = 10f) }
            }
        }
    }

    override fun onStop() {
        postAdapter.pause()
        listeners.removeAll()
        super.onStop()
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener { Nav.goTab(this, target) }
    }
}
