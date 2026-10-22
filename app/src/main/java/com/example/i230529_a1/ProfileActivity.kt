package com.example.i230529_a1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class ProfileActivity : AppCompatActivity() {
    private val uid by lazy { FirebaseAuth.getInstance().currentUser?.uid }
    private val friendCardIds = listOf(R.id.linaFriendButton, R.id.aishaFriendButton, R.id.omarFriendButton)
    private var profileListener: ValueEventListener? = null
    private var friendsListener: ValueEventListener? = null
    private var postsQuery: Query? = null
    private var postsListener: ValueEventListener? = null
    private val postCountListeners = mutableMapOf<String, MutableList<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>>()
    private lateinit var postAdapter: PostAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        loadProfile()
        loadPosts()

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }
        open(R.id.searchButton, SearchActivity::class.java)
        open(R.id.addStoryButton, CameraActivity::class.java)
        open(R.id.editProfileButton, EditProfileActivity::class.java)
        open(R.id.findFriendsFromProfile, SearchActivity::class.java)
    }

    private fun loadProfile() {
        val userId = uid ?: return
        val profileReference = FirebaseData.users.child(userId)
        val profileObserver = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.child("name").getValue(String::class.java).orEmpty()
                findViewById<TextView>(R.id.profileToolbarName).text = name
                findViewById<TextView>(R.id.profileDisplayName).text = name
                findViewById<TextView>(R.id.profileAvatar).text = initials(name)
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
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        profileListener = profileObserver
        profileReference.addValueEventListener(profileObserver)
        val friendsReference = FirebaseData.friends.child(userId)
        val friendsObserver = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.children.count { it.getValue(Boolean::class.java) == true }
                findViewById<TextView>(R.id.profileFriendCount).text = "$count friends"
                findViewById<TextView>(R.id.profileFriendsCountBottom).text = "$count friends"
                val friends = snapshot.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNull { it.key }
                friendCardIds.forEachIndexed { index, viewId ->
                    val card = findViewById<TextView>(viewId)
                    val friendUid = friends.getOrNull(index)
                    card.visibility = if (friendUid == null) View.INVISIBLE else View.VISIBLE
                    if (friendUid != null) {
                        card.setOnClickListener {
                            startActivity(Intent(this@ProfileActivity, OtherProfileActivity::class.java).putExtra("person_uid", friendUid))
                        }
                        FirebaseData.profiles.child(friendUid).get().addOnSuccessListener { profile ->
                            val name = profile.child("name").getValue(String::class.java).orEmpty()
                            card.text = initials(name)
                        }
                    }
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        friendsListener = friendsObserver
        friendsReference.addValueEventListener(friendsObserver)
    }

    private fun loadPosts() {
        val userId = uid ?: return
        postAdapter = PostAdapter(
            onReaction = { post -> startActivity(Intent(this, ReactionPickerActivity::class.java).putExtra("post_id", post.id)) },
            onComments = { post -> startActivity(Intent(this, CommentsActivity::class.java).putExtra("post_id", post.id)) },
            onAuthor = {}
        )
        findViewById<RecyclerView>(R.id.profilePostsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@ProfileActivity)
            isNestedScrollingEnabled = false
            adapter = postAdapter
        }
        postsQuery = FirebaseData.posts.orderByChild("createdAt").limitToLast(100)
        val observer = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val posts = snapshot.children.mapNotNull { item ->
                    if (item.child("authorId").getValue(String::class.java) != userId) return@mapNotNull null
                    FeedPost(
                        id = item.key.orEmpty(),
                        authorId = userId,
                        authorName = item.child("authorName").getValue(String::class.java).orEmpty(),
                        text = item.child("text").getValue(String::class.java).orEmpty(),
                        createdAt = item.child("createdAt").getValue(Long::class.java) ?: 0L,
                        reactionCount = item.child("reactionCount").getValue(Int::class.java) ?: 0,
                        commentCount = item.child("commentCount").getValue(Int::class.java) ?: 0
                    )
                }.sortedByDescending { it.createdAt }
                postAdapter.setPosts(posts)
                findViewById<TextView>(R.id.profilePostsEmpty).visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                posts.forEach { listenPostCounts(it.id) }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        postsListener = observer
        postsQuery?.addValueEventListener(observer)
    }

    private fun listenPostCounts(postId: String) {
        if (postId in postCountListeners) return
        val attached = mutableListOf<Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()
        val reactions = FirebaseData.postReactions.child(postId)
        val reactionsObserver = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { postAdapter.updateCounts(postId, reactionCount = snapshot.childrenCount.toInt()) }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        reactions.addValueEventListener(reactionsObserver)
        attached.add(reactions to reactionsObserver)
        val comments = FirebaseData.comments.child(postId)
        val commentsObserver = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { postAdapter.updateCounts(postId, commentCount = snapshot.childrenCount.toInt()) }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        comments.addValueEventListener(commentsObserver)
        attached.add(comments to commentsObserver)
        postCountListeners[postId] = attached
    }

    private fun initials(name: String) = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")

    override fun onDestroy() {
        uid?.let { userId ->
            profileListener?.let { FirebaseData.users.child(userId).removeEventListener(it) }
            friendsListener?.let { FirebaseData.friends.child(userId).removeEventListener(it) }
        }
        postsListener?.let { postsQuery?.removeEventListener(it) }
        postCountListeners.values.flatten().forEach { (reference, listener) -> reference.removeEventListener(listener) }
        postCountListeners.clear()
        super.onDestroy()
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener {
            startActivity(Intent(this, target))
        }
    }

}
