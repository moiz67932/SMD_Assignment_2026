package com.shadowdevelopers.i230529_i230723

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot

/**
 * Search (13). Finds people by username, full name, first name or last name while typing.
 * Each lowercase field in profiles/{uid} is indexed, so a prefix query is
 *   profiles.orderByChild(field).startAt(term).endAt(term + "")
 * The four results are merged without duplicates. Typing is debounced by 250 ms, and a numbered
 * "generation" makes sure an older, slower query can never overwrite the results of a newer one.
 */
class SearchActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    private lateinit var state: FriendState
    private lateinit var adapter: UserSearchAdapter
    private lateinit var marketAdapter: MarketResultAdapter
    private lateinit var input: EditText
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var chip = CHIP_ALL
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
        findViewById<View>(R.id.searchClearButton).setOnClickListener { input.text.clear() }
        mapOf(R.id.chipAll to CHIP_ALL, R.id.chipPeople to CHIP_PEOPLE, R.id.chipPosts to CHIP_POSTS, R.id.chipGroups to CHIP_GROUPS, R.id.chipMarketplace to CHIP_MARKET)
            .forEach { (id, value) -> findViewById<View>(id).setOnClickListener { chip = value; styleChips(); search(input.text.toString()) } }
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
            .forEach { (id, value) ->
                findViewById<TextView>(id).apply {
                    setBackgroundResource(if (chip == value) R.drawable.rounded_teal_soft else R.drawable.rounded_soft)
                    setTextColor(ContextCompat.getColor(this@SearchActivity, if (chip == value) R.color.primary else R.color.text_primary))
                }
            }
    }

    private fun search(text: String) {
        val term = text.trim().lowercase()
        val run = ++generation
        val title = findViewById<TextView>(R.id.searchSectionTitle)
        val recycler = findViewById<RecyclerView>(R.id.searchResults)
        if (term.isBlank()) {
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
                adapter.setUsers(emptyList())
                showEmpty(if (chip == CHIP_POSTS) "Searching posts is not part of this version. Try People." else "Groups are not part of this version.")
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

    private fun searchPeople(term: String, run: Int) {
        showEmpty("Searching…")
        val found = linkedMapOf<String, SearchUser>()
        var pending = FIELDS.size
        FIELDS.forEach { field ->
            FirebaseData.profiles.orderByChild(field).startAt(term).endAt(term + "").limitToFirst(20).get()
                .addOnCompleteListener { task ->
                    if (run != generation) return@addOnCompleteListener // a newer search started: ignore this result
                    if (task.isSuccessful) {
                        task.result.children.forEach { profile -> toUser(profile)?.let { found[it.uid] = it } }
                    }
                    pending--
                    if (pending == 0) {
                        // Usernames that start with the term first, then names.
                        val users = found.values.sortedWith(compareBy({ !it.username.startsWith(term) }, { it.name.lowercase() }))
                        adapter.setUsers(users)
                        showEmpty(if (users.isEmpty()) "No people found for \"$term\"" else null)
                        if (!task.isSuccessful && users.isEmpty()) showEmpty(task.exception?.localizedMessage ?: "Search failed")
                    }
                }
        }
    }

    private fun toUser(profile: DataSnapshot): SearchUser? {
        val uid = profile.child("uid").getValue(String::class.java) ?: profile.key ?: return null
        if (uid == me || profile.child("profileSetupComplete").getValue(Boolean::class.java) != true) return null
        return SearchUser(
            uid,
            profile.child("name").getValue(String::class.java).orEmpty(),
            profile.child("username").getValue(String::class.java).orEmpty(),
            profile.child("bio").getValue(String::class.java).orEmpty(),
            profile.child("photo").getValue(String::class.java).orEmpty()
        )
    }

    /** Marketplace: the newest 100 items are read once and filtered on the phone by title. */
    private fun searchMarketplace(term: String, run: Int) {
        val cached = marketItems
        if (cached != null) {
            val matches = cached.filter { it.title.lowercase().contains(term) }
            marketAdapter.setItems(matches)
            showEmpty(if (matches.isEmpty()) "No items found for \"$term\"" else null)
            return
        }
        showEmpty("Searching…")
        FirebaseData.marketplaceItems.orderByChild("createdAt").limitToLast(100).get().addOnCompleteListener { task ->
            marketItems = if (task.isSuccessful) task.result.children.mapNotNull { item ->
                val title = item.child("title").getValue(String::class.java) ?: return@mapNotNull null
                MarketItem(item.key.orEmpty(), title, item.child("price").getValue(String::class.java).orEmpty(),
                    item.child("ownerName").getValue(String::class.java).orEmpty(), item.child("createdAt").getValue(Long::class.java) ?: 0L)
            } else emptyList()
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
            Relation.INCOMING -> FriendActions.confirm(this, me, user.uid)
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
        private val FIELDS = listOf("usernameLower", "nameLower", "firstNameLower", "lastNameLower")
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
