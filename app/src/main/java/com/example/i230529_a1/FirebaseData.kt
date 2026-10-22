package com.example.i230529_a1

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
}
