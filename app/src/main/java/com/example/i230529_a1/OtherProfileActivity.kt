package com.example.i230529_a1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ValueEventListener

class OtherProfileActivity : AppCompatActivity() {
    private val currentUid by lazy { FirebaseAuth.getInstance().currentUser?.uid }
    private lateinit var personUid: String
    private lateinit var personName: String
    private lateinit var actionLabel: TextView
    private var profileListener: ValueEventListener? = null
    private var friendsListener: ValueEventListener? = null
    private var otherFriendsListener: ValueEventListener? = null
    private var friendStatusListener: ValueEventListener? = null
    private var incomingRequestListener: ValueEventListener? = null
    private var outgoingRequestListener: ValueEventListener? = null
    private val mutualCardIds = listOf(R.id.aishaMutualFriendButton, R.id.linaMutualFriendButton, R.id.zainMutualFriendButton)
    private var isAlreadyFriend = false
    private var hasIncomingRequest = false
    private var hasOutgoingRequest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other_profile)
        personUid = intent.getStringExtra("person_uid").orEmpty()
        personName = intent.getStringExtra("person_name") ?: "Kinnect member"
        actionLabel = findViewById(R.id.friendActionLabel)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.searchButton).setOnClickListener { startActivity(Intent(this, SearchActivity::class.java)) }
        findViewById<TextView>(R.id.otherProfileWork).text = ""
        findViewById<TextView>(R.id.otherProfileLocation).text = ""
        findViewById<TextView>(R.id.otherProfileJoined).text = ""
        mutualCardIds.forEach { findViewById<View>(it).visibility = View.GONE }

        if (personUid.isNotBlank()) loadProfile()
        findViewById<View>(R.id.friendActionButton).setOnClickListener { performFriendAction() }
        findViewById<View>(R.id.messageButton).setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("person_uid", personUid)
            intent.putExtra("person_name", personName)
            startActivity(intent)
        }
    }

    private fun loadProfile() {
        val profileReference = FirebaseData.profiles.child(personUid)
        val profileObserver = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                personName = snapshot.child("name").getValue(String::class.java) ?: "Kinnect member"
                findViewById<TextView>(R.id.otherToolbarName).text = personName
                findViewById<TextView>(R.id.otherProfileName).text = personName
                findViewById<TextView>(R.id.otherProfileAvatar).text = initials(personName)
                findViewById<TextView>(R.id.otherProfileBio).text = snapshot.child("bio").getValue(String::class.java).orEmpty()
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        profileListener = profileObserver
        profileReference.addValueEventListener(profileObserver)

        val uid = currentUid ?: return
        val otherFriends = mutableSetOf<String>()
        val currentFriends = mutableSetOf<String>()
        val mutualUpdater = {
            val mutualIds = otherFriends.intersect(currentFriends).toList()
            val count = mutualIds.size
            findViewById<TextView>(R.id.otherProfileStats).text = "${otherFriends.size} friends · $count mutual"
            findViewById<TextView>(R.id.otherMutualFriendNames).text = if (count == 0) "No mutual friends" else "$count mutual friends"
            mutualCardIds.forEachIndexed { index, viewId ->
                val card = findViewById<TextView>(viewId)
                val friendUid = mutualIds.getOrNull(index)
                card.visibility = if (friendUid == null) View.GONE else View.VISIBLE
                if (friendUid != null) {
                    card.setOnClickListener {
                        startActivity(Intent(this@OtherProfileActivity, OtherProfileActivity::class.java).putExtra("person_uid", friendUid))
                    }
                    FirebaseData.profiles.child(friendUid).get().addOnSuccessListener { profile ->
                        val name = profile.child("name").getValue(String::class.java).orEmpty()
                        card.text = initials(name)
                    }
                }
            }
        }
        val otherObserver = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                otherFriends.clear()
                snapshot.children.forEach { if (it.getValue(Boolean::class.java) == true) otherFriends.add(it.key.orEmpty()) }
                mutualUpdater()
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        otherFriendsListener = otherObserver
        FirebaseData.friends.child(personUid).addValueEventListener(otherObserver)
        val currentObserver = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentFriends.clear()
                snapshot.children.forEach { if (it.getValue(Boolean::class.java) == true) currentFriends.add(it.key.orEmpty()) }
                mutualUpdater()
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        FirebaseData.friends.child(uid).addValueEventListener(currentObserver)
        friendsListener = currentObserver

        friendStatusListener = observeStatus(FirebaseData.friends.child(uid).child(personUid)) { isAlreadyFriend = it }
        incomingRequestListener = observeStatus(FirebaseData.friendRequests.child(uid).child(personUid)) { hasIncomingRequest = it }
        outgoingRequestListener = observeStatus(FirebaseData.friendRequests.child(personUid).child(uid)) { hasOutgoingRequest = it }
    }

    private fun observeStatus(reference: com.google.firebase.database.DatabaseReference, update: (Boolean) -> Unit): ValueEventListener {
        val observer = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val value = snapshot.value
                update(snapshot.exists() && (value !is Boolean || value))
                updateFriendActionLabel()
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        reference.addValueEventListener(observer)
        return observer
    }

    private fun updateFriendActionLabel() {
        actionLabel.text = when {
            isAlreadyFriend -> "Friends"
            hasIncomingRequest -> "Confirm"
            hasOutgoingRequest -> "Requested"
            else -> "Add friend"
        }
    }

    private fun performFriendAction() {
        val uid = currentUid ?: return
        if (personUid.isBlank() || uid == personUid) return
        when (actionLabel.text.toString()) {
            "Add friend" -> {
                val request = mapOf<String, Any>("senderId" to uid, "receiverId" to personUid, "createdAt" to mapOf(".sv" to "timestamp"))
                FirebaseData.friendRequests.child(personUid).child(uid).setValue(request)
                    .addOnSuccessListener {
                        val notification = FirebaseData.database.getReference("notifications").child(personUid).push()
                        notification.setValue(mapOf("senderId" to uid, "type" to "friendRequest", "body" to "sent you a friend request", "createdAt" to mapOf(".sv" to "timestamp"), "read" to false))
                    }.addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not send request", Toast.LENGTH_LONG).show() }
            }
            "Confirm" -> {
                val notificationId = FirebaseData.database.getReference("notifications").child(personUid).push().key ?: return
                val updates = mapOf<String, Any?>(
                    "friends/$uid/$personUid" to true,
                    "friends/$personUid/$uid" to true,
                    "friendRequests/$uid/$personUid" to null,
                    "notifications/$personUid/$notificationId" to mapOf("senderId" to uid, "type" to "friendAccepted", "body" to "accepted your friend request", "createdAt" to mapOf(".sv" to "timestamp"), "read" to false)
                )
                FirebaseData.database.reference.updateChildren(updates)
                    .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not confirm request", Toast.LENGTH_LONG).show() }
            }
            "Requested" -> FirebaseData.friendRequests.child(personUid).child(uid).removeValue()
        }
    }

    private fun initials(name: String) = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")

    override fun onDestroy() {
        profileListener?.let { FirebaseData.profiles.child(personUid).removeEventListener(it) }
        val uid = currentUid
        if (uid != null) {
            friendsListener?.let { FirebaseData.friends.child(uid).removeEventListener(it) }
            friendStatusListener?.let { FirebaseData.friends.child(uid).child(personUid).removeEventListener(it) }
            incomingRequestListener?.let { FirebaseData.friendRequests.child(uid).child(personUid).removeEventListener(it) }
            outgoingRequestListener?.let { FirebaseData.friendRequests.child(personUid).child(uid).removeEventListener(it) }
        }
        otherFriendsListener?.let { FirebaseData.friends.child(personUid).removeEventListener(it) }
        super.onDestroy()
    }
}
