package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Query

class SearchActivity : AppCompatActivity() {
    private val results = linkedMapOf<String, SearchUser>()
    private var usernameQuery: Query? = null
    private var nameQuery: Query? = null
    private var usernameListener: com.google.firebase.database.ValueEventListener? = null
    private var nameListener: com.google.firebase.database.ValueEventListener? = null
    private lateinit var adapter: UserSearchAdapter
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    private lateinit var state: FriendState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        state = FriendState(me) { adapter.notifyDataSetChanged() }
        adapter = UserSearchAdapter(
            relationOf = { state.relation(it) },
            onOpen = { user -> startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", user.uid)) },
            onAction = { user, relation -> friendAction(user, relation) }
        )
        findViewById<RecyclerView>(R.id.searchResults).apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = this@SearchActivity.adapter
        }
        findViewById<EditText>(R.id.searchInput).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = search(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun search(text: String) {
        usernameListener?.let { usernameQuery?.removeEventListener(it) }
        nameListener?.let { nameQuery?.removeEventListener(it) }
        results.clear()
        adapter.setUsers(emptyList())
        val term = text.trim().lowercase()
        val currentUser = FirebaseAuth.getInstance().currentUser?.uid
        if (term.isBlank() || currentUser == null) return
        usernameQuery = FirebaseData.profiles.orderByChild("usernameLower").startAt(term).endAt("$term\uf8ff").limitToFirst(20)
        nameQuery = FirebaseData.profiles.orderByChild("nameLower").startAt(term).endAt("$term\uf8ff").limitToFirst(20)
        usernameListener = userResults { snapshot -> addResults(snapshot, currentUser) }
        nameListener = userResults { snapshot -> addResults(snapshot, currentUser) }
        usernameQuery?.addValueEventListener(usernameListener!!)
        nameQuery?.addValueEventListener(nameListener!!)
    }

    private fun userResults(onData: (DataSnapshot) -> Unit) = object : com.google.firebase.database.ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)
        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
            Toast.makeText(this@SearchActivity, error.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun addResults(snapshot: DataSnapshot, currentUser: String) {
        snapshot.children.forEach { profile ->
            val uid = profile.child("uid").getValue(String::class.java) ?: profile.key ?: return@forEach
            if (uid == currentUser || profile.child("profileSetupComplete").getValue(Boolean::class.java) == false) return@forEach
            results[uid] = SearchUser(
                uid,
                profile.child("name").getValue(String::class.java).orEmpty(),
                profile.child("username").getValue(String::class.java).orEmpty(),
                profile.child("bio").getValue(String::class.java).orEmpty(),
                profile.child("photo").getValue(String::class.java).orEmpty()
            )
        }
        adapter.setUsers(results.values.toList())
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

    override fun onStart() {
        super.onStart()
        if (me.isNotBlank()) state.attach(listeners)
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    override fun onDestroy() {
        usernameListener?.let { usernameQuery?.removeEventListener(it) }
        nameListener?.let { nameQuery?.removeEventListener(it) }
        super.onDestroy()
    }
}
