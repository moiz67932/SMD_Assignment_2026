package com.shadowdevelopers.i230529_i230723

/** How the signed-in user is related to someone else. Drives the Add friend -> Requested -> Friends button. */
enum class Relation { SELF, FRIENDS, INCOMING, OUTGOING, NONE }

/**
 * Live view of the signed-in user's friends, incoming requests and outgoing requests. A screen attaches it
 * in onStart (through its ListenerBag) and gets onChange() whenever any of the three lists changes,
 * so every friend button stays correct without reading the database once per row.
 */
class FriendState(private val me: String, private val onChange: () -> Unit) {
    val friends = linkedSetOf<String>()
    /** sender uid -> time the request was sent */
    val incoming = linkedMapOf<String, Long>()
    val outgoing = mutableSetOf<String>()

    fun attach(bag: ListenerBag) {
        bag.value(FirebaseData.friends.child(me)) { snapshot ->
            friends.clear()
            snapshot.children.forEach { if (it.getValue(Boolean::class.java) == true) friends.add(it.key.orEmpty()) }
            onChange()
        }
        bag.value(FirebaseData.friendRequests.child(me)) { snapshot ->
            incoming.clear()
            snapshot.children.forEach { incoming[it.key.orEmpty()] = it.child("createdAt").getValue(Long::class.java) ?: 0L }
            onChange()
        }
        bag.value(FirebaseData.sentRequests.child(me)) { snapshot ->
            outgoing.clear()
            snapshot.children.forEach { outgoing.add(it.key.orEmpty()) }
            onChange()
        }
    }

    fun relation(uid: String) = when {
        uid == me -> Relation.SELF
        uid in friends -> Relation.FRIENDS
        uid in incoming -> Relation.INCOMING
        uid in outgoing -> Relation.OUTGOING
        else -> Relation.NONE
    }

    companion object {
        fun label(relation: Relation) = when (relation) {
            Relation.FRIENDS -> "Friends"
            Relation.INCOMING -> "Confirm"
            Relation.OUTGOING -> "Requested"
            else -> "Add friend"
        }
    }
}
