package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth

/** What a row in Friends (14) is: an incoming request, a suggestion, or an existing friend. */
enum class FriendRowKind { REQUEST, SUGGESTION, FRIEND }

data class FriendRow(val uid: String, val kind: FriendRowKind, val relation: Relation, val time: Long = 0L, val mutual: List<String> = emptyList())

/**
 * Friends (14). "Suggestions" shows pending requests (live count) and People you may know
 * (friends of friends, most mutual friends first). "Your friends" shows the real friends list.
 */
class FriendsActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    private lateinit var state: FriendState
    private lateinit var requestsAdapter: FriendAdapter
    private lateinit var listAdapter: FriendAdapter
    private var showingFriends = false
    private var showAllRequests = false
    /** Friend lists of other people, read once per visit (used for mutual counts and suggestions). */
    private val friendsOf = mutableMapOf<String, Set<String>>()
    private var suggestions: List<FriendRow>? = null
    private var suggestionsFor: Set<String>? = null
    private val dismissed = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)
        if (me.isBlank()) {
            finish()
            return
        }
        open(R.id.searchButton, SearchActivity::class.java)
        open(R.id.chatsButton, ChatsActivity::class.java)
        Nav.tab(this, R.id.homeTab, HomeActivity::class.java)
        Nav.tab(this, R.id.marketTab, MarketplaceActivity::class.java)
        Nav.tab(this, R.id.notificationsTab, NotificationsActivity::class.java)
        Nav.tab(this, R.id.menuTab, MenuActivity::class.java)

        state = FriendState(me) { render() }
        requestsAdapter = FriendAdapter(::openProfile, ::primaryAction, ::secondaryAction)
        listAdapter = FriendAdapter(::openProfile, ::primaryAction, ::secondaryAction)
        findViewById<RecyclerView>(R.id.requestsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@FriendsActivity)
            adapter = requestsAdapter
        }
        findViewById<RecyclerView>(R.id.friendsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@FriendsActivity)
            adapter = listAdapter
        }
        findViewById<View>(R.id.suggestionsTab).setOnClickListener { showingFriends = false; render() }
        findViewById<View>(R.id.yourFriendsTab).setOnClickListener { showingFriends = true; render() }
        findViewById<View>(R.id.seeAllFriends).setOnClickListener { showAllRequests = true; render() }
    }

    override fun onStart() {
        super.onStart()
        if (me.isNotBlank()) state.attach(listeners)
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    private fun render() {
        styleChip(R.id.suggestionsTab, !showingFriends)
        styleChip(R.id.yourFriendsTab, showingFriends)

        // Friend requests N (the number in red, as in the design).
        val count = state.incoming.size
        val header = SpannableString("Friend requests $count")
        header.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)), header.length - count.toString().length, header.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        findViewById<TextView>(R.id.friendRequestCount).text = header
        val requests = state.incoming.entries.sortedByDescending { it.value }
            .map { FriendRow(it.key, FriendRowKind.REQUEST, Relation.INCOMING, it.value, mutualWith(it.key)) }
        requestsAdapter.setRows(if (showAllRequests) requests else requests.take(4))
        findViewById<View>(R.id.seeAllFriends).visibility = if (!showAllRequests && requests.size > 4) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.requestsEmpty).apply {
            text = "No friend requests"
            visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
        }
        loadFriendLists(state.incoming.keys)
        findViewById<View>(R.id.requestsSection).visibility = if (showingFriends) View.GONE else View.VISIBLE

        val title = findViewById<TextView>(R.id.friendsSectionTitle)
        val empty = findViewById<TextView>(R.id.friendsEmpty)
        if (showingFriends) {
            title.text = if (state.friends.size == 1) "Your friends · 1" else "Your friends · ${state.friends.size}"
            val rows = state.friends.map { FriendRow(it, FriendRowKind.FRIEND, Relation.FRIENDS) }
            listAdapter.setRows(rows)
            empty.text = "You have no friends yet. Use Search to find people."
            empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        } else {
            title.text = "People you may know"
            refreshSuggestions()
            val rows = suggestions?.filter { it.uid !in dismissed && state.relation(it.uid).let { r -> r == Relation.NONE || r == Relation.OUTGOING } }
                ?.map { it.copy(relation = state.relation(it.uid)) }
            listAdapter.setRows(rows.orEmpty())
            empty.text = if (rows == null) "Loading…" else "No suggestions right now"
            empty.visibility = if (rows.isNullOrEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun styleChip(id: Int, selected: Boolean) {
        findViewById<TextView>(id).apply {
            setBackgroundResource(if (selected) R.drawable.rounded_teal_soft else R.drawable.rounded_soft)
            setTextColor(ContextCompat.getColor(this@FriendsActivity, if (selected) R.color.primary else R.color.text_primary))
        }
    }

    private fun mutualWith(uid: String) = friendsOf[uid]?.filter { it in state.friends && it != me }.orEmpty()

    /** Reads friends/{uid} once for each person we need mutual counts for, then re-renders. */
    private fun loadFriendLists(uids: Collection<String>, onDone: (() -> Unit)? = null) {
        val missing = uids.filter { it !in friendsOf }
        if (missing.isEmpty()) {
            onDone?.invoke()
            return
        }
        var left = missing.size
        missing.forEach { uid ->
            friendsOf[uid] = emptySet()
            FirebaseData.friends.child(uid).get().addOnCompleteListener { task ->
                if (task.isSuccessful) friendsOf[uid] = task.result.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNull { it.key }.toSet()
                left--
                if (left == 0) {
                    if (onDone != null) onDone() else render()
                }
            }
        }
    }

    /**
     * People you may know: friends of my friends (up to 40 friends scanned), ranked by mutual friends.
     * A new user with no friends gets a few other Kinnect members instead.
     */
    private fun refreshSuggestions() {
        val myFriends = state.friends.toSet()
        if (suggestionsFor == myFriends) return
        suggestionsFor = myFriends
        loadFriendLists(myFriends.take(40)) {
            val counts = mutableMapOf<String, MutableList<String>>()
            myFriends.take(40).forEach { friend ->
                friendsOf[friend].orEmpty().forEach { candidate ->
                    if (candidate != me && candidate !in myFriends) counts.getOrPut(candidate) { mutableListOf() }.add(friend)
                }
            }
            if (counts.isNotEmpty()) {
                suggestions = counts.entries.sortedByDescending { it.value.size }.take(20)
                    .map { FriendRow(it.key, FriendRowKind.SUGGESTION, Relation.NONE, mutual = it.value) }
                render()
            } else {
                FirebaseData.profiles.limitToFirst(40).get().addOnCompleteListener { task ->
                    suggestions = if (!task.isSuccessful) emptyList() else task.result.children
                        .filter { it.key != me && it.child("profileSetupComplete").getValue(Boolean::class.java) == true }
                        .map { FriendRow(it.key.orEmpty(), FriendRowKind.SUGGESTION, Relation.NONE) }.take(20)
                    render()
                }
            }
        }
    }

    private fun primaryAction(row: FriendRow) {
        when (row.kind) {
            FriendRowKind.REQUEST -> FriendActions.confirm(this, me, row.uid)
            FriendRowKind.SUGGESTION ->
                if (state.relation(row.uid) == Relation.OUTGOING) FriendActions.cancelRequest(this, me, row.uid)
                else FriendActions.sendRequest(this, me, row.uid)
            FriendRowKind.FRIEND -> openProfile(row.uid)
        }
    }

    private fun secondaryAction(row: FriendRow) {
        when (row.kind) {
            FriendRowKind.REQUEST -> FriendActions.deleteIncoming(this, me, row.uid)
            FriendRowKind.SUGGESTION -> { dismissed.add(row.uid); render() }
            FriendRowKind.FRIEND -> Unit
        }
    }

    private fun openProfile(uid: String) {
        startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", uid))
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener { startActivity(Intent(this, target)) }
    }
}

class FriendAdapter(
    private val onOpen: (String) -> Unit,
    private val onPrimary: (FriendRow) -> Unit,
    private val onSecondary: (FriendRow) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {
    private val rows = mutableListOf<FriendRow>()

    fun setRows(items: List<FriendRow>) {
        rows.clear()
        rows.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FriendViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false))

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) = holder.bind(rows[position])
    override fun getItemCount() = rows.size

    inner class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar = view.findViewById<TextView>(R.id.friendAvatar)
        private val name = view.findViewById<TextView>(R.id.friendName)
        private val time = view.findViewById<TextView>(R.id.friendTime)
        private val subtitle = view.findViewById<TextView>(R.id.friendSubtitle)
        private val mutualViews = listOf(view.findViewById<TextView>(R.id.friendMutual1), view.findViewById<TextView>(R.id.friendMutual2))
        private val actions = view.findViewById<View>(R.id.friendRequestActions)
        private val primary = view.findViewById<Button>(R.id.confirmFriendRequest)
        private val secondary = view.findViewById<Button>(R.id.deleteFriendRequest)

        fun bind(row: FriendRow) {
            itemView.tag = row.uid
            time.text = if (row.time > 0) TimeFormat.ago(row.time) else ""
            subtitle.text = when (row.mutual.size) {
                0 -> ""
                1 -> "1 mutual friend"
                else -> "${row.mutual.size} mutual friends"
            }
            name.text = ""
            avatar.text = ""
            ProfileCache.get(row.uid) { profile ->
                if (itemView.tag != row.uid) return@get
                name.text = profile.name
                AvatarBinder.bind(avatar, profile.name, profile.photo)
                if (row.mutual.isEmpty()) subtitle.text = if (profile.username.isBlank()) "" else "@${profile.username}"
            }
            mutualViews.forEachIndexed { index, view ->
                val friend = row.mutual.getOrNull(index)
                view.visibility = if (friend == null) View.GONE else View.VISIBLE
                if (friend != null) ProfileCache.get(friend) { if (itemView.tag == row.uid) AvatarBinder.bind(view, it.name, it.photo) }
            }
            actions.visibility = if (row.kind == FriendRowKind.FRIEND) View.GONE else View.VISIBLE
            when (row.kind) {
                FriendRowKind.REQUEST -> { primary.text = "Confirm"; secondary.text = "Delete" }
                FriendRowKind.SUGGESTION -> { primary.text = FriendState.label(row.relation); secondary.text = "Remove" }
                FriendRowKind.FRIEND -> Unit
            }
            itemView.setOnClickListener { onOpen(row.uid) }
            primary.setOnClickListener { onPrimary(row) }
            secondary.setOnClickListener { onSecondary(row) }
        }
    }
}
