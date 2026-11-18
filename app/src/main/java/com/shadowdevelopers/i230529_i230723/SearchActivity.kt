package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Search (13). Finds people by username, full name, first name or last name while typing.
 * Each lowercase field in profiles/{uid} is indexed, so a prefix query is
 *   profiles.orderByChild(field).startAt(term).endAt(term + "")
 * The four results are merged without duplicates. Typing is debounced by 250 ms, and a numbered
 * "generation" makes sure an older, slower query can never overwrite the results of a newer one.
 */
class SearchActivity : AppCompatActivity() {
    private val me by lazy { Backend.uid.orEmpty() }
    private val listeners = ListenerBag()
    private lateinit var state: FriendState
    private lateinit var adapter: UserSearchAdapter
    private lateinit var marketAdapter: MarketResultAdapter
    private lateinit var input: EditText
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var chip = CHIP_ALL
    /** Search among everyone, only my friends, or only people with a pending request (Assignment 3 filter). */
    private var relationFilter = "everyone"
    private var marketItems: List<MarketItem>? = null
    private val runSearch = Runnable { search(input.text.toString()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        if (me.isBlank()) {
            finish()
            return
        }
        input = findViewById(R.id.searchInput)
        state = FriendState(me) { adapter.notifyDataSetChanged() }
        adapter = UserSearchAdapter(
            relationOf = { state.relation(it) },
            onOpen = { user ->
                remember(input.text.toString())
                startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", user.uid))
            },
            onAction = { user, relation -> friendAction(user, relation) }
        )
        marketAdapter = MarketResultAdapter()
        findViewById<RecyclerView>(R.id.searchResults).apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = this@SearchActivity.adapter
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                findViewById<View>(R.id.searchClearButton).visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                handler.removeCallbacks(runSearch)
                handler.postDelayed(runSearch, 250)
            }
        })
        findViewById<View>(R.id.searchClearButton).setOnClickListener { input.text.clear(); input.requestFocus() }
        // Search key: results are already live, so it just closes the keyboard to show them.
        Forms.onKeyboardAction(input) { Forms.hideKeyboard(this) }
        mapOf(R.id.chipAll to CHIP_ALL, R.id.chipPeople to CHIP_PEOPLE, R.id.chipPosts to CHIP_POSTS, R.id.chipGroups to CHIP_GROUPS, R.id.chipMarketplace to CHIP_MARKET)
            .forEach { (id, value) -> findViewById<View>(id).setOnClickListener { chip = value; styleChips(); search(input.text.toString()) } }
        mapOf(R.id.filterEveryone to "everyone", R.id.filterFriends to "friends", R.id.filterRequests to "requests")
            .forEach { (id, value) -> findViewById<View>(id).setOnClickListener { relationFilter = value; styleChips(); search(input.text.toString()) } }
        styleChips()
        search("")
    }

    override fun onStart() {
        super.onStart()
        if (me.isNotBlank()) state.attach(listeners)
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(runSearch)
        super.onDestroy()
    }

    private fun styleChips() {
        mapOf(R.id.chipAll to CHIP_ALL, R.id.chipPeople to CHIP_PEOPLE, R.id.chipPosts to CHIP_POSTS, R.id.chipGroups to CHIP_GROUPS, R.id.chipMarketplace to CHIP_MARKET)
            .forEach { (id, value) -> styleChip(id, chip == value) }
        mapOf(R.id.filterEveryone to "everyone", R.id.filterFriends to "friends", R.id.filterRequests to "requests")
            .forEach { (id, value) -> styleChip(id, relationFilter == value) }
        findViewById<View>(R.id.searchRelationFilter).visibility = if (chip == CHIP_ALL || chip == CHIP_PEOPLE) View.VISIBLE else View.GONE
    }

    private fun styleChip(id: Int, selected: Boolean) {
        findViewById<TextView>(id).apply {
            setBackgroundResource(if (selected) R.drawable.rounded_teal_soft else R.drawable.rounded_soft)
            setTextColor(ContextCompat.getColor(this@SearchActivity, if (selected) R.color.primary else R.color.text_primary))
        }
    }

    /** Plain rows (a title and a line of text) for post and group results. */
    private val rowAdapter by lazy {
        TextRowAdapter { row ->
            if (chip == CHIP_GROUPS) startActivity(Intent(this, GroupChatActivity::class.java).putExtra("group_id", row.id).putExtra("group_name", row.title))
            else startActivity(Intent(this, CommentsActivity::class.java).putExtra("post_id", row.id))
        }
    }

    /** Groups: only groups I am in (userGroups/{me}), by name. */
    private fun searchGroups(term: String, run: Int) {
        val me = Backend.uid ?: return
        // My groups (GET groups/list.php), matched by name.
        Backend.call({
            val list = runCatching { Backend.api.get("groups/list.php").getJSONArray("groups") }.getOrNull()
                ?: Backend.store.entity(me, "groups", "all")?.let { org.json.JSONArray(it) } ?: org.json.JSONArray()
            (0 until list.length()).map { list.getJSONObject(it) }
        }) { result ->
            if (run != generation) return@call
            val rows = result.getOrDefault(emptyList()).map { TextRow(it.getString("id"), it.optString("name").ifBlank { "Group" }, "Tap to open the chat") }
                .filter { it.title.lowercase().contains(term) }
            rowAdapter.set(rows)
            showEmpty(if (rows.isEmpty()) "No group of yours matches \"$term\"" else null)
        }
    }

    /** Posts: text of my posts and my friends' posts (the feed; SQLite copy when offline). */
    private fun searchPosts(term: String, run: Int) {
        Backend.call({ runCatching { PostRepo.feed(fromServer = true) }.getOrElse { PostRepo.feed(fromServer = false) } }) { result ->
            if (run != generation) return@call
            val found = result.getOrDefault(emptyList()).filter { it.text.lowercase().contains(term) }
            rowAdapter.set(found.map { TextRow(it.id, it.authorName, listOf(TimeFormat.ago(it.createdAt), it.text).joinToString(" · ")) })
            showEmpty(if (found.isEmpty()) "No posts match \"$term\"" else null)
        }
    }

    private fun search(text: String) {
        val term = text.trim().lowercase()
        val run = ++generation
        val title = findViewById<TextView>(R.id.searchSectionTitle)
        val recycler = findViewById<RecyclerView>(R.id.searchResults)
        if (term.isBlank() && (relationFilter == "everyone" || chip !in listOf(CHIP_ALL, CHIP_PEOPLE))) {
            title.text = "Recent searches"
            adapter.setUsers(emptyList())
            showRecent()
            showEmpty(if (recentSearches().isEmpty()) "Search for people by name or username" else null)
            return
        }
        findViewById<View>(R.id.recentSearchesList).visibility = View.GONE
        when (chip) {
            CHIP_POSTS, CHIP_GROUPS -> {
                title.text = if (chip == CHIP_POSTS) "Posts" else "Groups"
                recycler.adapter = rowAdapter
                if (chip == CHIP_POSTS) searchPosts(term, run) else searchGroups(term, run)
            }
            CHIP_MARKET -> {
                title.text = "Marketplace"
                recycler.adapter = marketAdapter
                searchMarketplace(term, run)
            }
            else -> {
                title.text = "People"
                recycler.adapter = adapter
                searchPeople(term, run)
            }
        }
    }

    /**
     * People search as you type: GET search/users.php?q=…&filter=everyone|friends|requests (name or username).
     * Results are saved to SQLite, so searching offline still finds people seen before.
     */
    private fun searchPeople(term: String, run: Int) {
        showEmpty("Searching…")
        val filter = relationFilter
        Backend.call({
            val response = Backend.api.get("search/users.php?filter=$filter&q=" + java.net.URLEncoder.encode(term, "UTF-8"))
            val list = response.getJSONArray("users")
            (0 until list.length()).map { list.getJSONObject(it) }
        }) { result ->
            if (run != generation) return@call // a newer search started: ignore this result
            result.onSuccess { rows ->
                rows.forEach { ProfileCache.putJson(it) }
                val users = rows.map(::toUser).sortedWith(compareBy({ !it.username.startsWith(term) }, { it.name.lowercase() }))
                adapter.setUsers(users)
                showEmpty(if (users.isEmpty()) "No people found for \"$term\"" else null)
            }.onFailure { searchOffline(term, run) }
        }
    }

    /** Offline: filter the profiles cached in SQLite (and my friend/request lists) on the phone. */
    private fun searchOffline(term: String, run: Int) {
        val filter = relationFilter
        Backend.call({ Backend.store.entities(me, "user").map { org.json.JSONObject(it) } }) { result ->
            if (run != generation) return@call
            val users = result.getOrDefault(emptyList()).map(::toUser).filter { user ->
                user.uid != me && (user.name.lowercase().contains(term) || user.username.lowercase().contains(term)) && when (filter) {
                    "friends" -> state.relation(user.uid) == Relation.FRIENDS
                    "requests" -> state.relation(user.uid).let { it == Relation.INCOMING || it == Relation.OUTGOING }
                    else -> true
                }
            }
            adapter.setUsers(users)
            showEmpty(if (users.isEmpty()) "You're offline. No saved people match \"$term\"" else "You're offline: showing people saved on this phone")
        }
    }

    private fun toUser(json: org.json.JSONObject): SearchUser {
        val profile = ProfileCache.fromJson(json)
        return SearchUser(profile.uid, profile.name, profile.username, profile.bio, profile.photo)
    }

    private fun searchMarketplace(term: String, run: Int) {
        val cached = marketItems
        if (cached != null) {
            val matches = cached.filter { it.title.lowercase().contains(term) }
            marketAdapter.setItems(matches)
            showEmpty(if (matches.isEmpty()) "No items found for \"$term\"" else null)
            return
        }
        showEmpty("Searching…")
        // GET market/list.php (SQLite copy when offline).
        Backend.call({
            runCatching { Backend.api.get("market/list.php").getJSONArray("items") }.getOrNull()
                ?: Backend.store.entity(me, "market", "all")?.let { org.json.JSONArray(it) } ?: org.json.JSONArray()
        }) { result ->
            val list = result.getOrDefault(org.json.JSONArray())
            marketItems = (0 until list.length()).map { list.getJSONObject(it) }.map {
                MarketItem(it.getString("id"), it.optString("title"), it.optString("price"), it.optString("seller_name"), TimeFormat.parse(it.optString("created_at")))
            }
            if (run == generation) searchMarketplace(term, run)
        }
    }

    private fun showEmpty(text: String?) {
        findViewById<TextView>(R.id.searchEmpty).apply {
            visibility = if (text == null) View.GONE else View.VISIBLE
            this.text = text.orEmpty()
        }
    }

    /** Add friend -> Requested (tap again to cancel) ; Confirm accepts an incoming request ; Friends opens the profile. */
    private fun friendAction(user: SearchUser, relation: Relation) {
        when (relation) {
            Relation.NONE -> FriendActions.sendRequest(this, me, user.uid)
            Relation.OUTGOING -> FriendActions.cancelRequest(this, me, user.uid)
            // An incoming request can be confirmed or deleted right here (brief: send / confirm / delete from Search).
            Relation.INCOMING -> androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("${user.name} sent you a friend request")
                .setPositiveButton("Confirm") { _, _ -> FriendActions.confirm(this, me, user.uid) }
                .setNegativeButton("Delete") { _, _ -> FriendActions.deleteIncoming(this, me, user.uid) }
                .setNeutralButton("Cancel", null)
                .show()
            else -> startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", user.uid))
        }
    }

    // ----- Recent searches (kept only on this phone, newest first, max 5) -----

    private fun prefs() = getSharedPreferences("kinnect", Context.MODE_PRIVATE)
    private fun recentSearches() = prefs().getString("recent_searches", "").orEmpty().split("\n").filter { it.isNotBlank() }

    private fun remember(text: String) {
        val term = text.trim()
        if (term.isBlank()) return
        val list = (listOf(term) + recentSearches().filter { !it.equals(term, ignoreCase = true) }).take(5)
        prefs().edit().putString("recent_searches", list.joinToString("\n")).apply()
    }

    private fun forget(term: String) {
        prefs().edit().putString("recent_searches", recentSearches().filter { it != term }.joinToString("\n")).apply()
        showRecent()
    }

    private fun showRecent() {
        val container = findViewById<LinearLayout>(R.id.recentSearchesList)
        container.removeAllViews()
        val items = recentSearches()
        container.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.searchSectionTitle).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        items.forEach { term ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_recent_search, container, false)
            row.findViewById<TextView>(R.id.recentSearchText).text = term
            row.setOnClickListener { input.setText(term); input.setSelection(term.length) }
            row.findViewById<View>(R.id.recentSearchRemove).setOnClickListener { forget(term) }
            container.addView(row)
        }
    }

    companion object {
        private const val CHIP_ALL = 0
        private const val CHIP_PEOPLE = 1
        private const val CHIP_POSTS = 2
        private const val CHIP_GROUPS = 3
        private const val CHIP_MARKET = 4
    }
}

/** Marketplace items in Search results (re-uses the person row: initials circle, title, price · seller). */
class MarketResultAdapter : RecyclerView.Adapter<MarketResultAdapter.Holder>() {
    private val items = mutableListOf<MarketItem>()

    fun setItems(list: List<MarketItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: MarketItem) {
            itemView.findViewById<TextView>(R.id.userAvatar).text = AvatarBinder.initials(item.title)
            itemView.findViewById<TextView>(R.id.userName).text = item.title
            itemView.findViewById<TextView>(R.id.userSubtitle).text = listOf(item.price, item.seller).filter { it.isNotBlank() }.joinToString(" · ")
            itemView.findViewById<View>(R.id.userFriendAction).visibility = View.GONE
            itemView.setOnClickListener { itemView.context.startActivity(Intent(itemView.context, MarketplaceActivity::class.java)) }
        }
    }
}
