package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
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

/**
 * Home feed (04): posts from me and my friends, newest first.
 * Instead of downloading everyone's posts, Home listens to each author separately:
 *   posts.orderByChild("authorId").equalTo(uid).limitToLast(20)
 * for me + up to 40 friends, merges the results and sorts them by createdAt.
 */
class HomeActivity : AppCompatActivity() {
    private val badges by lazy { TabBadges(this) }
    private val me by lazy { Backend.uid.orEmpty() }
    private val listeners = ListenerBag()
    /** One live query per author currently in the feed. */
    /** authorId -> that author's latest posts */
    /** The feed as last shown (SQLite first, then the server). */
    private var feedPosts: List<FeedPost> = emptyList()
    private var feedLoaded = false
    private val hidden = mutableSetOf<String>()
    private var feedError: String? = null
    private lateinit var feedAdapter: PostAdapter
    private lateinit var storyAdapter: StoryRowAdapter
    /** Stories row: owner -> audience ("public"/"closeFriends") -> active stories, plus which ones I have seen. */

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

        ProfileCache.get(me) { profile ->
            AvatarBinder.bind(findViewById(R.id.profileButton), profile.name, profile.photo)
            findViewById<TextView>(R.id.createPostButton).text = "What’s on your mind, ${profile.name.substringBefore(" ")}?"
        }
        val feed = findViewById<RecyclerView>(R.id.feedRecyclerView)
        feed.layoutManager = LinearLayoutManager(this)
        feed.isNestedScrollingEnabled = false
        feedAdapter = PostAdapter(
            me = me,
            onComments = { post -> startActivity(Intent(this, CommentsActivity::class.java).putExtra("post_id", post.id)) },
            onAuthor = { post ->
                val target = if (post.authorId == me) ProfileActivity::class.java else OtherProfileActivity::class.java
                startActivity(Intent(this, target).putExtra("person_uid", post.authorId))
            },
            onShare = { post -> startActivity(Intent(this, SharePostActivity::class.java).putExtra("post_id", post.id)) },
            onHide = { post ->
                hidden.add(post.id)
                showFeed()
            },
            onChanged = { loadFeed(fromServer = false) }
        )
        feed.adapter = feedAdapter

        storyAdapter = StoryRowAdapter(
            me = me,
            onCreate = { startActivity(Intent(this, CameraActivity::class.java)) },
            onOpen = ::openStories,
            onLive = { host -> startActivity(Intent(this, LiveActivity::class.java).putExtra("host_uid", host)) }
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
        open(R.id.profileButton, ProfileActivity::class.java)
        Nav.tab(this, R.id.friendsTab, FriendsActivity::class.java)
        Nav.tab(this, R.id.marketTab, MarketplaceActivity::class.java)
        Nav.tab(this, R.id.notificationsTab, NotificationsActivity::class.java)
        Nav.tab(this, R.id.menuTab, MenuActivity::class.java)
    }

    override fun onStart() {
        super.onStart()
        badges.start()
        if (me.isBlank()) return
        feedAdapter.resume()
        // Feed (04): SQLite first (instant, offline), then GET posts/feed.php every 15 s and after offline actions sync.
        loadFeed(fromServer = false)
        listeners.poll(15_000) { loadFeed(fromServer = true); loadStories(fromServer = true) }
        // Red LIVE tiles for friends broadcasting right now (GET lives/list.php).
        listeners.poll(15_000) {
            Backend.call({ Backend.api.get("lives/list.php").getJSONArray("hosts").let { list -> (0 until list.length()).map { list.getString(it) } } }) { result ->
                result.onSuccess { storyAdapter.setLive(it) }
            }
        }
        // Story row owners = me + my friends.
        val friendsChanged: () -> Unit = { setStoryOwners(listOf(me) + Friends.friends.take(MAX_FRIEND_AUTHORS)) }
        Friends.observe(friendsChanged)
        listeners.onRemove { Friends.unobserve(friendsChanged) }
        listeners.poll(30_000) { Friends.refresh() }
        friendsChanged()
    }

    private fun loadFeed(fromServer: Boolean) {
        Backend.call({ PostRepo.feed(fromServer = fromServer) }) { result ->
            result.onSuccess { posts ->
                feedPosts = posts
                feedLoaded = true
                feedError = null
                showFeed()
            }.onFailure {
                // Offline or server down: keep showing the SQLite copy; only explain when there is nothing to show.
                if (fromServer) {
                    feedLoaded = true
                    if (feedPosts.isEmpty()) feedError = Forms.message(it as? Exception, "Could not load your feed")
                    showFeed()
                }
            }
        }
    }

    override fun onStop() {
        badges.stop()
        feedAdapter.pause()
        listeners.removeAll()
        super.onStop()
    }

    /**
     * Listens to stories/{uid}/public and stories/{uid}/closeFriends for me and each friend.
     * A close-friends node I am not allowed to read just fails quietly (onCancelled) and is skipped.
     */
    /**
     * Stories row: GET stories/feed.php (my stories, friends' public stories and close-friends stories I am allowed to see),
     * shown from SQLite first; expired stories are dropped by StoryRepo. "Create story" is always the first tile.
     */
    private fun setStoryOwners(owners: List<String>) {
        loadStories(fromServer = false)
    }

    private fun loadStories(fromServer: Boolean) {
        Backend.call({ runCatching { StoryRepo.feed(fromServer) }.getOrElse { StoryRepo.feed(false) } }) { result ->
            val stories = result.getOrNull() ?: return@call
            showStories(stories)
        }
    }

    /** Order: my own story, then friends with unseen stories, then the rest; newest first inside each group. */
    private fun showStories(all: List<Story>) {
        val owners = all.groupBy { it.ownerId }.map { (uid, stories) ->
            val sorted = stories.sortedBy { it.createdAt }
            StoryOwner(uid, sorted, sorted.all { it.seen })
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
        val items = feedPosts.filter { it.id !in hidden }
        feedAdapter.setPosts(items)
        val loaded = feedLoaded
        findViewById<TextView>(R.id.feedEmpty).apply {
            visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            text = when {
                feedError != null -> feedError.orEmpty()
                !loaded -> "Loading your feed…"
                else -> "No posts yet. Share something, or add friends to see their posts."
            }
        }
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener { Nav.goTab(this, target) }
    }

    companion object {
        private const val MAX_FRIEND_AUTHORS = 40
    }
}
