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

    // ----- PHP backend (Assignment 3): polling while the screen is visible -----

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val cleanups = mutableListOf<() -> Unit>()

    /**
     * Runs [block] now and then every [intervalMs] while the screen is visible (until removeAll() in onStop),
     * and again whenever queued offline actions reach the server. This keeps lists fresh without leaving the screen.
     */
    fun poll(intervalMs: Long, block: () -> Unit) {
        val tick = object : Runnable {
            override fun run() {
                block()
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.post(tick)
        val synced: () -> Unit = { block() }
        com.shadowdevelopers.i230529_i230723.data.Backend.onSynced(synced)
        cleanups.add {
            handler.removeCallbacks(tick)
            com.shadowdevelopers.i230529_i230723.data.Backend.removeOnSynced(synced)
        }
    }

    /** Anything else to undo in removeAll() (for example an observer of a shared repository). */
    fun onRemove(cleanup: () -> Unit) {
        cleanups.add(cleanup)
    }

    fun removeAll() {
        cleanups.forEach { it() }
        cleanups.clear()
        values.forEach { (query, listener) -> query.removeEventListener(listener) }
        children.forEach { (query, listener) -> query.removeEventListener(listener) }
        values.clear()
        children.clear()
    }
}
