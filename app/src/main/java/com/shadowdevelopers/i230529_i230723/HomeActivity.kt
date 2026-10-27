package com.shadowdevelopers.i230529_i230723

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
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Home feed (04): posts from me and my friends, newest first.
 * Instead of downloading everyone's posts, Home listens to each author separately:
 *   posts.orderByChild("authorId").equalTo(uid).limitToLast(20)
 * for me + up to 40 friends, merges the results and sorts them by createdAt.
 */
class HomeActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    /** One live query per author currently in the feed. */
    private val authorQueries = mutableMapOf<String, Pair<Query, ValueEventListener>>()
    /** authorId -> that author's latest posts */
    private val postsByAuthor = mutableMapOf<String, List<FeedPost>>()
    private val hidden = mutableSetOf<String>()
    private var feedError: String? = null
    private lateinit var feedAdapter: PostAdapter
    private lateinit var storyAdapter: StoryRowAdapter
    /** Stories row: owner -> audience ("public"/"closeFriends") -> active stories, plus which ones I have seen. */
    private val storyNodes = mutableMapOf<String, Pair<Query, ValueEventListener>>()
    private val storiesByOwner = mutableMapOf<String, MutableMap<String, List<Story>>>()
    private val seenStories = mutableSetOf<String>()
    private val seenQueries = mutableMapOf<String, Pair<Query, ValueEventListener>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        if (me.isBlank()) {
            Session.startFresh(this, LoginActivity::class.java)
            return
        }

        FirebaseNotifications.saveToken()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 301)
        }

        findViewById<View>(R.id.samplePostCard).visibility = View.GONE
        ProfileCache.get(me) { profile ->
            AvatarBinder.bind(findViewById(R.id.profileButton), profile.name, profile.photo)
            findViewById<TextView>(R.id.createPostButton).text = "What’s on your mind, ${profile.name.substringBefore(" ")}?"
        }
        val feed = findViewById<RecyclerView>(R.id.feedRecyclerView)
        feed.layoutManager = LinearLayoutManager(this)
        feed.isNestedScrollingEnabled = false
        feedAdapter = PostAdapter(
            me = me,
            onOpenPicker = { post -> startActivity(Intent(this, ReactionPickerActivity::class.java).putExtra("post_id", post.id)) },
            onComments = { post -> startActivity(Intent(this, CommentsActivity::class.java).putExtra("post_id", post.id)) },
            onAuthor = { post ->
                val target = if (post.authorId == me) ProfileActivity::class.java else OtherProfileActivity::class.java
                startActivity(Intent(this, target).putExtra("person_uid", post.authorId))
            },
            onShare = { post -> startActivity(Intent(this, SharePostActivity::class.java).putExtra("post_id", post.id)) },
            onHide = { post ->
                hidden.add(post.id)
                showFeed()
            }
        )
        feed.adapter = feedAdapter

        storyAdapter = StoryRowAdapter(
            me = me,
            onCreate = { startActivity(Intent(this, CameraActivity::class.java)) },
            onOpen = ::openStories
        )
        findViewById<RecyclerView>(R.id.storiesRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = storyAdapter
        }

        open(R.id.searchButton, SearchActivity::class.java)
        open(R.id.chatsButton, ChatsActivity::class.java)
        open(R.id.createPostButton, CreatePostActivity::class.java)
        findViewById<View>(R.id.photoShortcutButton).setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java).putExtra("open_picker", true))
        }
        open(R.id.commentsButton, CommentsActivity::class.java)
        open(R.id.reactionButton, ReactionPickerActivity::class.java)
        open(R.id.profileButton, ProfileActivity::class.java)
        Nav.tab(this, R.id.friendsTab, FriendsActivity::class.java)
        Nav.tab(this, R.id.marketTab, MarketplaceActivity::class.java)
        Nav.tab(this, R.id.notificationsTab, NotificationsActivity::class.java)
        Nav.tab(this, R.id.menuTab, MenuActivity::class.java)
    }

    override fun onStart() {
        super.onStart()
        if (me.isBlank()) return
        feedAdapter.resume()
        Stories.removeExpired(me)
        listeners.value(FirebaseData.friends.child(me), onError = { feedError = it.message; showFeed() }) { snapshot ->
            val friends = snapshot.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNull { it.key }
            setAuthors(listOf(me) + friends.take(MAX_FRIEND_AUTHORS))
            setStoryOwners(listOf(me) + friends.take(MAX_FRIEND_AUTHORS))
        }
    }

    override fun onStop() {
        feedAdapter.pause()
        listeners.removeAll()
        authorQueries.values.forEach { (query, listener) -> query.removeEventListener(listener) }
        authorQueries.clear()
        (storyNodes.values + seenQueries.values).forEach { (query, listener) -> query.removeEventListener(listener) }
        storyNodes.clear()
        seenQueries.clear()
        super.onStop()
    }

    /** Starts listening to new authors and stops listening to people who are no longer friends. */
    private fun setAuthors(authors: List<String>) {
        authorQueries.keys.filter { it !in authors }.forEach { uid ->
            authorQueries.remove(uid)?.let { (query, listener) -> query.removeEventListener(listener) }
            postsByAuthor.remove(uid)
        }
        authors.filter { it !in authorQueries }.forEach { uid ->
            val query = FirebaseData.posts.orderByChild("authorId").equalTo(uid).limitToLast(POSTS_PER_AUTHOR)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    postsByAuthor[uid] = snapshot.children.map { FeedPost.from(it) }
                    feedError = null
                    showFeed()
                }

                override fun onCancelled(error: DatabaseError) {
                    feedError = error.message
                    showFeed()
                }
            }
            query.addValueEventListener(listener)
            authorQueries[uid] = query to listener
        }
        showFeed()
    }

    /**
     * Listens to stories/{uid}/public and stories/{uid}/closeFriends for me and each friend.
     * A close-friends node I am not allowed to read just fails quietly (onCancelled) and is skipped.
     */
    private fun setStoryOwners(owners: List<String>) {
        storyNodes.keys.filter { it.substringBefore('/') !in owners }.forEach { key ->
            storyNodes.remove(key)?.let { (query, listener) -> query.removeEventListener(listener) }
            storiesByOwner.remove(key.substringBefore('/'))
        }
        owners.forEach { uid ->
            listOf(Stories.PUBLIC, Stories.CLOSE_FRIENDS).forEach { audience ->
                val key = "$uid/$audience"
                if (key in storyNodes) return@forEach
                val query = Stories.stories.child(uid).child(audience)
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val active = Stories.active(snapshot, uid)
                        storiesByOwner.getOrPut(uid) { mutableMapOf() }[audience] = active
                        active.forEach { watchSeen(it) }
                        showStories()
                    }

                    override fun onCancelled(error: DatabaseError) = Unit // permission denied = not on their close friends list
                }
                query.addValueEventListener(listener)
                storyNodes[key] = query to listener
            }
        }
        showStories()
    }

    /** storyViews/{owner}/{storyId}/{me} exists once I have opened that story (grey ring instead of teal). */
    private fun watchSeen(story: Story) {
        if (story.ownerId == me || story.id in seenQueries) return
        val query = Stories.views.child(story.ownerId).child(story.id).child(me)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) seenStories.add(story.id) else seenStories.remove(story.id)
                showStories()
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        query.addValueEventListener(listener)
        seenQueries[story.id] = query to listener
    }

    /** Order: my own story, then friends with unseen stories, then the rest; newest first inside each group. */
    private fun showStories() {
        val owners = storiesByOwner.mapNotNull { (uid, byAudience) ->
            val stories = byAudience.values.flatten().filter { it.expiresAt > FirebaseData.now() }.sortedBy { it.createdAt }
            if (stories.isEmpty()) null else StoryOwner(uid, stories, stories.all { it.id in seenStories })
        }.sortedWith(compareBy<StoryOwner>({ it.uid != me }, { it.allSeen }).thenByDescending { it.latest?.createdAt ?: 0L })
        storyAdapter.setOwners(owners)
        currentStoryOwners = owners
    }

    private var currentStoryOwners: List<StoryOwner> = emptyList()

    /** My tile opens Your story (12); a friend's tile opens the Story viewer (11), which then continues to the next friends. */
    private fun openStories(owner: StoryOwner) {
        if (owner.uid == me) {
            startActivity(Intent(this, YourStoryActivity::class.java))
            return
        }
        val queue = currentStoryOwners.filter { it.uid != me }.map { it.uid }
        startActivity(Intent(this, StoryViewerActivity::class.java)
            .putStringArrayListExtra("owner_ids", ArrayList(queue))
            .putExtra("index", queue.indexOf(owner.uid).coerceAtLeast(0)))
    }

    private fun showFeed() {
        val items = postsByAuthor.values.flatten().filter { it.id !in hidden }.sortedByDescending { it.createdAt }
        feedAdapter.setPosts(items)
        val loaded = authorQueries.keys.all { it in postsByAuthor }
        findViewById<TextView>(R.id.feedEmpty).apply {
            visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            text = when {
                feedError != null -> "Could not load the feed: $feedError"
                !loaded -> "Loading your feed…"
                else -> "No posts yet. Share something, or add friends to see their posts."
            }
        }
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener { startActivity(Intent(this, target)) }
    }

    companion object {
        private const val POSTS_PER_AUTHOR = 20
        private const val MAX_FRIEND_AUTHORS = 40
    }
}
