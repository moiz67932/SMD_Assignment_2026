package com.shadowdevelopers.i230529_i230723

import android.os.Handler
import android.os.Looper
import com.shadowdevelopers.i230529_i230723.data.Backend

/**
 * What a screen keeps running while it is visible: polling of the PHP API (so lists stay fresh without leaving the
 * screen) and observers of shared repositories. Convention in this app: start in onStart, call removeAll() in onStop.
 */
class ListenerBag {
    private val handler = Handler(Looper.getMainLooper())
    private val cleanups = mutableListOf<() -> Unit>()

    /**
     * Runs [block] now and then every [intervalMs] while the screen is visible (until removeAll() in onStop),
     * and again whenever queued offline actions reach the server.
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
        Backend.onSynced(synced)
        cleanups.add {
            handler.removeCallbacks(tick)
            Backend.removeOnSynced(synced)
        }
    }

    /** Anything else to undo in removeAll() (for example an observer of a shared repository). */
    fun onRemove(cleanup: () -> Unit) {
        cleanups.add(cleanup)
    }

    fun removeAll() {
        cleanups.toList().forEach { it() }
        cleanups.clear()
    }
}
