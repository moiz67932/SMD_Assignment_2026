package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth

/** Profile (15): the signed-in user's own profile, cover, details, friends and posts. */
class ProfileActivity : AppCompatActivity() {
    private val uid by lazy { FirebaseAuth.getInstance().currentUser?.uid }
    private val friendCardIds = listOf(R.id.profileFriend1, R.id.profileFriend2, R.id.profileFriend3)
    private val listeners = ListenerBag()
    private lateinit var postAdapter: PostAdapter

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
            onOpenPicker = { post -> startActivity(Intent(this, ReactionPickerActivity::class.java).putExtra("post_id", post.id)) },
            onComments = { post -> startActivity(Intent(this, CommentsActivity::class.java).putExtra("post_id", post.id)) },
            onAuthor = {}
        )
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
        // Private details (birthday, gender, join date) live in users/{uid}; photo and cover in profiles/{uid}.
        listeners.value(FirebaseData.users.child(userId)) { snapshot ->
            val name = snapshot.child("name").getValue(String::class.java).orEmpty()
            findViewById<TextView>(R.id.profileToolbarName).text = name
            findViewById<TextView>(R.id.profileDisplayName).text = name
            findViewById<TextView>(R.id.profileDisplayBio).text = snapshot.child("bio").getValue(String::class.java).orEmpty()
            val birthday = snapshot.child("birthday").getValue(String::class.java).orEmpty()
            val gender = snapshot.child("gender").getValue(String::class.java).orEmpty()
            val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
            findViewById<TextView>(R.id.profileBirthdayValue).text = birthday
            findViewById<View>(R.id.profileBirthdayRow).visibility = if (birthday.isBlank()) View.GONE else View.VISIBLE
            findViewById<TextView>(R.id.profileGenderValue).text = gender
            findViewById<View>(R.id.profileGenderRow).visibility = if (gender.isBlank()) View.GONE else View.VISIBLE
            findViewById<View>(R.id.profileLocationRow).visibility = View.GONE
            findViewById<TextView>(R.id.profileJoinedValue).text = if (createdAt > 0L) "Joined ${android.text.format.DateFormat.format("MMMM yyyy", createdAt)}" else ""
            findViewById<View>(R.id.profileJoinedRow).visibility = if (createdAt > 0L) View.VISIBLE else View.GONE
        }
        listeners.value(FirebaseData.profiles.child(userId)) { snapshot ->
            val profile = ProfileCache.from(snapshot)
            ProfileCache.put(profile)
            AvatarBinder.bind(findViewById(R.id.profileAvatar), profile.name, profile.photo)
            val cover = findViewById<ImageView>(R.id.profileCoverImage)
            if (profile.cover.isBlank()) cover.setImageDrawable(null) else MediaLoader.load(cover, profile.cover)
        }
        listeners.value(FirebaseData.friends.child(userId)) { snapshot ->
            val friends = snapshot.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNull { it.key }
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
        listeners.value(FirebaseData.posts.orderByChild("authorId").equalTo(userId)) { snapshot ->
            val posts = snapshot.children.map { FeedPost.from(it) }.sortedByDescending { it.createdAt }
            postAdapter.setPosts(posts)
            findViewById<TextView>(R.id.profilePostsEmpty).visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onStop() {
        postAdapter.pause()
        listeners.removeAll()
        super.onStop()
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener { startActivity(Intent(this, target)) }
    }
}
