package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONObject
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Other profile (17): another user's cover, bio, friend count, mutual friends, photos and the friend button. */
class OtherProfileActivity : AppCompatActivity() {
    private val currentUid by lazy { Backend.uid }
    private val listeners = ListenerBag()
    private val mutualAvatarIds = listOf(R.id.mutualAvatar1, R.id.mutualAvatar2, R.id.mutualAvatar3)
    private val photoIds = listOf(R.id.otherPhoto1, R.id.otherPhoto2, R.id.otherPhoto3)
    private var personUid = ""
    private var personName = ""
    private val otherFriends = mutableSetOf<String>()
    private lateinit var actionLabel: TextView
    private var state: FriendState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other_profile)
        personUid = intent.getStringExtra("person_uid").orEmpty()
        personName = intent.getStringExtra("person_name").orEmpty()
        actionLabel = findViewById(R.id.friendActionLabel)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.searchButton).setOnClickListener { startActivity(Intent(this, SearchActivity::class.java)) }
        mutualAvatarIds.forEach { findViewById<View>(it).visibility = View.GONE }
        listOf(R.id.otherProfileWork, R.id.otherProfileLocation, R.id.otherProfileJoined).forEach { (findViewById<View>(it).parent as View).visibility = View.GONE }
        if (personUid.isBlank()) {
            Toast.makeText(this, "This profile is not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (personUid == currentUid) {
            // Opening yourself from a list goes to your own Profile (15).
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
            return
        }
        state = FriendState(currentUid.orEmpty()) { updateFriendActionLabel(); showMutualFriends() }
        findViewById<View>(R.id.friendActionButton).setOnClickListener { performFriendAction() }
        findViewById<View>(R.id.otherMoreButton).setOnClickListener { showMoreMenu(it) }
        findViewById<View>(R.id.messageButton).setOnClickListener {
            // CLEAR_TOP: if a chat is already open underneath (Chat -> Profile -> Message) go back to it instead of stacking another.
            startActivity(Intent(this, ChatActivity::class.java).putExtra("person_uid", personUid).putExtra("person_name", personName)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    override fun onStart() {
        super.onStart()
        val uid = currentUid ?: return
        if (personUid.isBlank() || personUid == uid) return
        // My friends + incoming/outgoing requests (server, polled): drives the friend button and the mutual count.
        state?.attach(listeners)
        // Profile, friend count and photos from the PHP API; shown from SQLite first so it also works offline.
        listeners.poll(15_000) { loadProfile() }
    }

    private fun loadProfile() {
        val me = currentUid ?: return
        Backend.call({
            val user = runCatching { Backend.api.get("profiles/get.php?id=$personUid").getJSONObject("user") }
                .onSuccess { Backend.store.putEntity(me, "user", personUid, it.toString()) }
                .getOrElse { Backend.store.entity(me, "user", personUid)?.let(::JSONObject) }
            val friends = runCatching {
                val list = Backend.api.get("friends/of.php?id=$personUid").getJSONArray("friends")
                (0 until list.length()).map { list.getJSONObject(it).also(ProfileCache::putJson).getString("id") }
            }.getOrNull()
            val images = runCatching {
                val posts = Backend.api.get("posts/feed.php?author=$personUid").getJSONArray("posts")
                (0 until posts.length()).flatMap { i ->
                    val media = posts.getJSONObject(i).optJSONArray("media") ?: org.json.JSONArray()
                    (0 until media.length()).map { ProfileCache.mediaRef(media.getJSONObject(it).getString("id")) }
                }
            }.getOrNull()
            Triple(user, friends, images)
        }) { result ->
            val (user, friends, images) = result.getOrNull() ?: return@call
            if (user != null) {
                val profile = ProfileCache.fromJson(user)
                ProfileCache.put(profile)
                personName = profile.name
                findViewById<TextView>(R.id.otherToolbarName).text = profile.name
                findViewById<TextView>(R.id.otherProfileName).text = profile.name
                findViewById<TextView>(R.id.otherProfileBio).apply {
                    text = profile.bio
                    visibility = if (profile.bio.isBlank()) View.GONE else View.VISIBLE
                }
                AvatarBinder.bind(findViewById(R.id.otherProfileAvatar), profile.name, profile.photo)
                val cover = findViewById<ImageView>(R.id.otherCoverImage)
                if (profile.cover.isBlank()) cover.setImageDrawable(null) else MediaLoader.load(cover, profile.cover)
            }
            if (friends != null) {
                otherFriends.clear()
                otherFriends.addAll(friends)
                showMutualFriends()
            }
            if (images != null) {
                photoIds.forEachIndexed { index, id ->
                    val view = findViewById<ImageView>(id)
                    val ref = images.getOrNull(index)
                    view.visibility = if (ref == null) View.INVISIBLE else View.VISIBLE
                    if (ref != null) MediaLoader.load(view, ref)
                }
                findViewById<View>(R.id.otherPhotosGrid).visibility = if (images.isEmpty()) View.GONE else View.VISIBLE
                findViewById<View>(R.id.otherPhotosEmpty).visibility = if (images.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    /** Friend count, mutual count (set intersection) and up to three mutual friends' avatars and names. */
    private fun showMutualFriends() {
        val mutual = otherFriends.intersect(state?.friends.orEmpty()).toList()
        val friendsLabel = if (otherFriends.size == 1) "1 friend" else "${otherFriends.size} friends"
        findViewById<TextView>(R.id.otherProfileStats).text = "$friendsLabel · ${mutual.size} mutual"
        val namesView = findViewById<TextView>(R.id.otherMutualFriendNames)
        if (mutual.isEmpty()) namesView.text = "No mutual friends"
        val names = arrayOfNulls<String>(minOf(2, mutual.size))
        mutualAvatarIds.forEachIndexed { index, viewId ->
            val avatar = findViewById<TextView>(viewId)
            val friendUid = mutual.getOrNull(index)
            avatar.visibility = if (friendUid == null) View.GONE else View.VISIBLE
            if (friendUid == null) return@forEachIndexed
            avatar.setOnClickListener {
                // A friend's friend replaces this profile, so a chain of profiles does not need a Back per person.
                startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", friendUid))
                finish()
            }
            ProfileCache.get(friendUid) { profile ->
                AvatarBinder.bind(avatar, profile.name, profile.photo)
                if (index < names.size) {
                    names[index] = profile.name
                    if (names.all { it != null }) namesView.text = mutualSentence(names.filterNotNull(), mutual.size)
                }
            }
        }
    }

    /** "Friends with Aisha Khan", "Friends with A and B", "Friends with A, B and 36 others". */
    private fun mutualSentence(names: List<String>, total: Int): String {
        val others = total - names.size
        return when {
            others > 0 -> "Friends with ${names.joinToString(", ")} and $others ${if (others == 1) "other" else "others"}"
            names.size == 2 -> "Friends with ${names[0]} and ${names[1]}"
            else -> "Friends with ${names.first()}"
        }
    }

    private fun relation() = state?.relation(personUid) ?: Relation.NONE

    /** Add friend -> Requested -> Friends, live. An incoming request shows Confirm (Delete is in the ... menu). */
    private fun updateFriendActionLabel() {
        actionLabel.text = state?.labelFor(personUid) ?: FriendState.label(relation())
    }

    private fun performFriendAction() {
        val uid = currentUid ?: return
        when (relation()) {
            Relation.NONE -> FriendActions.sendRequest(this, uid, personUid)
            Relation.OUTGOING -> FriendActions.cancelRequest(this, uid, personUid)
            Relation.INCOMING -> FriendActions.confirm(this, uid, personUid)
            Relation.FRIENDS -> showMoreMenu(findViewById(R.id.friendActionButton))
            Relation.SELF -> Unit
        }
    }

    private fun showMoreMenu(anchor: View) {
        val uid = currentUid ?: return
        val menu = PopupMenu(this, anchor)
        when (relation()) {
            Relation.INCOMING -> menu.menu.add(0, 1, 0, "Delete friend request")
            Relation.OUTGOING -> menu.menu.add(0, 2, 0, "Cancel friend request")
            Relation.FRIENDS -> menu.menu.add(0, 3, 0, "Unfriend")
            else -> menu.menu.add(0, 4, 0, "Add friend")
        }
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> FriendActions.deleteIncoming(this, uid, personUid)
                2 -> FriendActions.cancelRequest(this, uid, personUid)
                3 -> AlertDialog.Builder(this).setMessage("Remove $personName from your friends?")
                    .setPositiveButton("Unfriend") { _, _ -> FriendActions.unfriend(this, uid, personUid) }
                    .setNegativeButton("Cancel", null).show()
                4 -> FriendActions.sendRequest(this, uid, personUid)
            }
            true
        }
        menu.show()
    }
}
