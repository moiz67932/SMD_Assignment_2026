package com.shadowdevelopers.i230529_i230723

import com.google.firebase.database.FirebaseDatabase

object FirebaseData {
    val database = FirebaseDatabase.getInstance()
    val users = database.getReference("users")
    val profiles = database.getReference("profiles")
    val usernames = database.getReference("usernames")
    val friends = database.getReference("friends")
    val friendRequests = database.getReference("friendRequests")
    val posts = database.getReference("posts")
    val postReactions = database.getReference("postReactions")
    val comments = database.getReference("comments")
    val commentLikes = database.getReference("commentLikes")
    val userChats = database.getReference("userChats")
    val messageReactions = database.getReference("messageReactions")
    val callRequests = database.getReference("callRequests")
    val callLogs = database.getReference("callLogs")
    val marketplaceItems = database.getReference("marketplaceItems")

    /** Difference between the Firebase server clock and this phone's clock (kept up to date by KinnectApplication). */
    @Volatile var serverTimeOffset = 0L

    /** Server-adjusted "now", so the 24 h story expiry and the 5 minute edit window don't trust the phone clock. */
    fun now() = System.currentTimeMillis() + serverTimeOffset
}
