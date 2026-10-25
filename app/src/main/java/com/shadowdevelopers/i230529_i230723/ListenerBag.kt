package com.shadowdevelopers.i230529_i230723

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Keeps track of the Firebase listeners a screen attached, so they can all be removed in one call.
 * Convention in this app: attach in onStart, call removeAll() in onStop.
 */
class ListenerBag {
    private val values = mutableListOf<Pair<Query, ValueEventListener>>()
    private val children = mutableListOf<Pair<Query, ChildEventListener>>()

    /** Attaches a value listener. onError is optional; permission-denied reads can be ignored by passing nothing. */
    fun value(query: Query, onError: ((DatabaseError) -> Unit)? = null, onData: (DataSnapshot) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)
            override fun onCancelled(error: DatabaseError) { onError?.invoke(error) }
        }
        query.addValueEventListener(listener)
        values.add(query to listener)
        return listener
    }

    fun child(query: Query, listener: ChildEventListener) {
        query.addChildEventListener(listener)
        children.add(query to listener)
    }

    /** Removes a single value listener that was added through this bag. */
    fun remove(query: Query, listener: ValueEventListener) {
        query.removeEventListener(listener)
        values.remove(query to listener)
    }

    fun removeAll() {
        values.forEach { (query, listener) -> query.removeEventListener(listener) }
        children.forEach { (query, listener) -> query.removeEventListener(listener) }
        values.clear()
        children.clear()
    }
}
