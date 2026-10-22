package com.example.i230529_a1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Query

class HomeActivity : AppCompatActivity() {
    private var feedQuery: Query? = null
    private var friendsListener: com.google.firebase.database.ValueEventListener? = null
    private val allowedAuthors = mutableSetOf<String>()
    private val postCountListeners = mutableMapOf<String, MutableList<Pair<com.google.firebase.database.DatabaseReference, com.google.firebase.database.ValueEventListener>>>()
    private lateinit var feedAdapter: PostAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        FirebaseNotifications.saveToken()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 301)
        }

        findViewById<View>(R.id.samplePostCard).visibility = View.GONE
        findViewById<View>(R.id.storyButton).visibility = View.GONE
        findViewById<View>(R.id.sampleStoryTwo).visibility = View.GONE
        findViewById<View>(R.id.sampleStoryThree).visibility = View.GONE
        FirebaseAuth.getInstance().currentUser?.uid?.let { userId ->
            FirebaseData.users.child(userId).get().addOnSuccessListener { profile ->
                val name = profile.child("name").getValue(String::class.java).orEmpty()
                val initials = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
                findViewById<TextView>(R.id.profileButton).text = initials
                findViewById<TextView>(R.id.createStoryAvatar).text = initials
                findViewById<TextView>(R.id.createPostButton).text = "What’s on your mind, ${name.substringBefore(" ")}?"
            }
        }
        val feed = findViewById<RecyclerView>(R.id.feedRecyclerView)
        feed.layoutManager = LinearLayoutManager(this)
        feed.isNestedScrollingEnabled = false
        feedAdapter = PostAdapter(
            onReaction = { post ->
                startActivity(Intent(this, ReactionPickerActivity::class.java).putExtra("post_id", post.id))
            },
            onComments = { post ->
                startActivity(Intent(this, CommentsActivity::class.java).putExtra("post_id", post.id))
            },
            onAuthor = { post ->
                val target = if (post.authorId == FirebaseAuth.getInstance().currentUser?.uid) ProfileActivity::class.java else OtherProfileActivity::class.java
                startActivity(Intent(this, target).putExtra("person_uid", post.authorId))
            }
        )
        feed.adapter = feedAdapter
        loadFeed()

        open(R.id.searchButton, SearchActivity::class.java)
        open(R.id.chatsButton, ChatsActivity::class.java)
        open(R.id.createPostButton, CreatePostActivity::class.java)
        findViewById<View>(R.id.photoShortcutButton).setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java).putExtra("open_picker", true))
        }
        open(R.id.commentsButton, CommentsActivity::class.java)
        open(R.id.reactionButton, ReactionPickerActivity::class.java)
        open(R.id.storyButton, StoryViewerActivity::class.java)
        open(R.id.profileButton, ProfileActivity::class.java)
        open(R.id.createStoryButton, CameraActivity::class.java)
        open(R.id.friendsTab, FriendsActivity::class.java)
        open(R.id.marketTab, MarketplaceActivity::class.java)
        open(R.id.notificationsTab, NotificationsActivity::class.java)
        open(R.id.menuTab, MenuActivity::class.java)
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener {
            startActivity(Intent(this, target))
        }
    }

    private fun loadFeed() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val friendReference = FirebaseData.friends.child(userId)
        val friendObserver = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allowedAuthors.clear()
                allowedAuthors.add(userId)
                snapshot.children.forEach { child ->
                    if (child.getValue(Boolean::class.java) == true) allowedAuthors.add(child.key.orEmpty())
                }
                feedQuery?.removeEventListener(postsObserver)
                feedQuery = FirebaseData.posts.orderByChild("createdAt").limitToLast(100)
                feedQuery?.addValueEventListener(postsObserver)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        friendsListener = friendObserver
        friendReference.addValueEventListener(friendObserver)
    }

    private val postsObserver = object : com.google.firebase.database.ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
                val items = snapshot.children.mapNotNull { post ->
                val authorId = post.child("authorId").getValue(String::class.java) ?: return@mapNotNull null
                if (authorId !in allowedAuthors) return@mapNotNull null
                val createdAt = post.child("createdAt").getValue(Long::class.java) ?: 0L
                FeedPost(
                    id = post.key.orEmpty(),
                    authorId = authorId,
                    authorName = post.child("authorName").getValue(String::class.java) ?: "Kinnect member",
                    text = post.child("text").getValue(String::class.java).orEmpty(),
                    createdAt = createdAt,
                    reactionCount = post.child("reactionCount").getValue(Int::class.java) ?: 0,
                    commentCount = post.child("commentCount").getValue(Int::class.java) ?: 0
                )
            }.sortedByDescending { it.createdAt }
                feedAdapter.setPosts(items)
                items.forEach { listenPostCounts(it.id) }
        }

        override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
    }

    private fun listenPostCounts(postId: String) {
        if (postId in postCountListeners) return
        val listeners = mutableListOf<Pair<com.google.firebase.database.DatabaseReference, com.google.firebase.database.ValueEventListener>>()
        val reactions = FirebaseData.postReactions.child(postId)
        val reactionListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { feedAdapter.updateCounts(postId, reactionCount = snapshot.childrenCount.toInt()) }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        reactions.addValueEventListener(reactionListener)
        listeners.add(reactions to reactionListener)
        val comments = FirebaseData.comments.child(postId)
        val commentListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { feedAdapter.updateCounts(postId, commentCount = snapshot.childrenCount.toInt()) }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        comments.addValueEventListener(commentListener)
        listeners.add(comments to commentListener)
        postCountListeners[postId] = listeners
    }

    override fun onDestroy() {
        super.onDestroy()
        feedQuery?.removeEventListener(postsObserver)
        postCountListeners.values.flatten().forEach { (reference, listener) -> reference.removeEventListener(listener) }
        postCountListeners.clear()
        friendsListener?.let { FirebaseData.friends.child(FirebaseAuth.getInstance().currentUser?.uid.orEmpty()).removeEventListener(it) }
    }
}
