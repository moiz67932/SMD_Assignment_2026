package com.shadowdevelopers.i230529_i230723

import android.os.SystemClock

/**
 * Opening the system camera, gallery or a file picker puts Kinnect in the background for a moment.
 * Screens call mark() right before such a launch, so KinnectApplication does not flip the user to
 * offline while they are only choosing a photo. The flag expires on its own after two minutes.
 */
object ExternalLaunch {
    private const val WINDOW_MS = 2 * 60 * 1000L
    @Volatile private var until = 0L

    fun mark() {
        until = SystemClock.elapsedRealtime() + WINDOW_MS
    }

    fun clear() {
        until = 0L
    }

    fun active() = SystemClock.elapsedRealtime() < until
}
