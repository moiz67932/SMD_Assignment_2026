package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/** Other profile (17): another user's cover, bio, friend count, mutual friends, photos and the friend button. */
class OtherProfileActivity : AppCompatActivity() {
    private val currentUid by lazy { FirebaseAuth.getInstance().currentUser?.uid }
    private val listeners = ListenerBag()
    private val mutualAvatarIds = listOf(R.id.mutualAvatar1, R.id.mutualAvatar2, R.id.mutualAvatar3)
    private val photoIds = listOf(R.id.otherPhoto1, R.id.otherPhoto2, R.id.otherPhoto3)
    private var personUid = ""
    private var personName = ""
    private val otherFriends = mutableSetOf<String>()
    private val myFriends = mutableSetOf<String>()
    private lateinit var actionLabel: TextView
    private var isAlreadyFriend = false
    private var hasIncomingRequest = false
    private var hasOutgoingRequest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other_profile)
        personUid = intent.getStringExtra("person_uid").orEmpty()
        personName = intent.getStringExtra("person_name").orEmpty()
        actionLabel = findViewById(R.id.friendActionLabel)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.searchButton).setOnClickListener { startActivity(Intent(this, SearchActivity::class.java)) }
        mutualAvatarIds.forEach { findViewById<View>(it).visibility = View.GONE }
        listOf(R.id.otherProfileWork, R.id.otherProfileLocation, R.id.otherProfileJoined).forEach { (findViewById<View>(it).parent as View).visibility = View.GONE }
        if (personUid.isBlank()) {
            Toast.makeText(this, "This profile is not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (personUid == currentUid) {
            // Opening yourself from a list goes to your own Profile (15).
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
            return
        }
        findViewById<View>(R.id.friendActionButton).setOnClickListener { performFriendAction() }
        findViewById<View>(R.id.messageButton).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java).putExtra("person_uid", personUid).putExtra("person_name", personName))
        }
    }

    override fun onStart() {
        super.onStart()
        val uid = currentUid ?: return
        if (personUid.isBlank() || personUid == uid) return
        listeners.value(FirebaseData.profiles.child(personUid)) { snapshot ->
            val profile = ProfileCache.from(snapshot)
            ProfileCache.put(profile)
            personName = profile.name
            findViewById<TextView>(R.id.otherToolbarName).text = profile.name
            findViewById<TextView>(R.id.otherProfileName).text = profile.name
            findViewById<TextView>(R.id.otherProfileBio).apply {
                text = profile.bio
                visibility = if (profile.bio.isBlank()) View.GONE else View.VISIBLE
            }
            AvatarBinder.bind(findViewById(R.id.otherProfileAvatar), profile.name, profile.photo)
            val cover = findViewById<ImageView>(R.id.otherCoverImage)
            if (profile.cover.isBlank()) cover.setImageDrawable(null) else MediaLoader.load(cover, profile.cover)
        }
        listeners.value(FirebaseData.friends.child(personUid)) { snapshot ->
            otherFriends.clear()
            snapshot.children.forEach { if (it.getValue(Boolean::class.java) == true) otherFriends.add(it.key.orEmpty()) }
            showMutualFriends()
        }
        listeners.value(FirebaseData.friends.child(uid)) { snapshot ->
            myFriends.clear()
            snapshot.children.forEach { if (it.getValue(Boolean::class.java) == true) myFriends.add(it.key.orEmpty()) }
            showMutualFriends()
        }
        listeners.value(FirebaseData.friends.child(uid).child(personUid)) { isAlreadyFriend = it.getValue(Boolean::class.java) == true; updateFriendActionLabel() }
        listeners.value(FirebaseData.friendRequests.child(uid).child(personUid)) { hasIncomingRequest = it.exists(); updateFriendActionLabel() }
        listeners.value(FirebaseData.friendRequests.child(personUid).child(uid)) { hasOutgoingRequest = it.exists(); updateFriendActionLabel() }
        // Photos: the images from this person's latest posts (posts are readable by any signed-in user).
        listeners.value(FirebaseData.posts.orderByChild("authorId").equalTo(personUid).limitToLast(30)) { snapshot ->
            val images = snapshot.children.sortedByDescending { it.child("createdAt").getValue(Long::class.java) ?: 0L }
                .flatMap { post -> post.child("images").children.mapNotNull { it.getValue(String::class.java) } }
            photoIds.forEachIndexed { index, id ->
                val view = findViewById<ImageView>(id)
                val ref = images.getOrNull(index)
                view.visibility = if (ref == null) View.INVISIBLE else View.VISIBLE
                if (ref != null) MediaLoader.load(view, ref)
            }
            findViewById<View>(R.id.otherPhotosGrid).visibility = if (images.isEmpty()) View.GONE else View.VISIBLE
            findViewById<View>(R.id.otherPhotosEmpty).visibility = if (images.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    /** Friend count, mutual count (set intersection) and up to three mutual friends' avatars and names. */
    private fun showMutualFriends() {
        val mutual = otherFriends.intersect(myFriends).toList()
        val friendsLabel = if (otherFriends.size == 1) "1 friend" else "${otherFriends.size} friends"
        findViewById<TextView>(R.id.otherProfileStats).text = "$friendsLabel · ${mutual.size} mutual"
        val namesView = findViewById<TextView>(R.id.otherMutualFriendNames)
        if (mutual.isEmpty()) namesView.text = "No mutual friends"
        val names = arrayOfNulls<String>(minOf(2, mutual.size))
        mutualAvatarIds.forEachIndexed { index, viewId ->
            val avatar = findViewById<TextView>(viewId)
            val friendUid = mutual.getOrNull(index)
            avatar.visibility = if (friendUid == null) View.GONE else View.VISIBLE
            if (friendUid == null) return@forEachIndexed
            avatar.setOnClickListener { startActivity(Intent(this, OtherProfileActivity::class.java).putExtra("person_uid", friendUid)) }
            ProfileCache.get(friendUid) { profile ->
                AvatarBinder.bind(avatar, profile.name, profile.photo)
                if (index < names.size) {
                    names[index] = profile.name
                    if (names.all { it != null }) namesView.text = mutualSentence(names.filterNotNull(), mutual.size)
                }
            }
        }
    }

    /** "Friends with Aisha Khan", "Friends with A and B", "Friends with A, B and 36 others". */
    private fun mutualSentence(names: List<String>, total: Int): String {
        val others = total - names.size
        return when {
            others > 0 -> "Friends with ${names.joinToString(", ")} and $others ${if (others == 1) "other" else "others"}"
            names.size == 2 -> "Friends with ${names[0]} and ${names[1]}"
            else -> "Friends with ${names.first()}"
        }
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
        when (actionLabel.text.toString()) {
            "Add friend" -> {
                val request = mapOf<String, Any>("senderId" to uid, "receiverId" to personUid, "createdAt" to mapOf(".sv" to "timestamp"))
                FirebaseData.friendRequests.child(personUid).child(uid).setValue(request)
                    .addOnSuccessListener {
                        FirebaseData.database.getReference("notifications").child(personUid).push()
                            .setValue(mapOf("senderId" to uid, "type" to "friendRequest", "body" to "sent you a friend request", "createdAt" to mapOf(".sv" to "timestamp"), "read" to false))
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
}
