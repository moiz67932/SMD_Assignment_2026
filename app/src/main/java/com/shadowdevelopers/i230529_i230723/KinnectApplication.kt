package com.shadowdevelopers.i230529_i230723

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class KinnectApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        com.shadowdevelopers.i230529_i230723.data.Backend.init(this)
        MediaLoader.init(this)
        FirebaseNotifications.init(this)
        registerActivityLifecycleCallbacks(this)
        FirebaseData.database.getReference(".info/serverTimeOffset").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                FirebaseData.serverTimeOffset = snapshot.getValue(Long::class.java) ?: 0L
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        })
    }

    override fun onActivityStarted(activity: Activity) {
        Insets.apply(activity)
        TouchFeedback.install(activity)
        startedActivities++
        inForeground = true
        ExternalLaunch.clear()
        handler.removeCallbacks(markOffline)
        if (com.shadowdevelopers.i230529_i230723.data.Backend.uid != null) {
            connectPresence()
            CallManager.startListening(this) // incoming calls ring anywhere in the app while signed in
            KinnectBackgroundService.start(this) // keeps messages and calls coming after the app is closed
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities <= 0) {
            startedActivities = 0
            inForeground = false
            handler.postDelayed(markOffline, OFFLINE_GRACE_MS)
        }
    }

    // Grace period before going offline; skipped entirely while a camera/gallery/picker is open for us.
    private val markOffline: Runnable = object : Runnable {
        override fun run() {
            if (startedActivities > 0) return
            if (ExternalLaunch.active()) handler.postDelayed(this, 30_000) // check again once the picker is likely closed
            else setOffline()
        }
    }

    /**
     * Online status (green dot, "Active now"): while any Kinnect screen is open, POST presence/heartbeat.php every
     * 20 s. The server counts the user online until 45 s after the last heartbeat. When the app goes to the
     * background, presence/leave.php ends it at once. Pending offline actions are retried at the same time.
     */
    private val heartbeat: Runnable = object : Runnable {
        override fun run() {
            if (com.shadowdevelopers.i230529_i230723.data.Backend.uid == null) return
            com.shadowdevelopers.i230529_i230723.data.Backend.background {
                com.shadowdevelopers.i230529_i230723.data.Backend.api.post("presence/heartbeat.php", org.json.JSONObject())
            }
            com.shadowdevelopers.i230529_i230723.data.Backend.requestSync()
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private fun connectPresence() {
        handler.removeCallbacks(heartbeat)
        handler.post(heartbeat)
    }

    fun setOffline(onComplete: () -> Unit = {}) {
        handler.removeCallbacks(heartbeat)
        if (com.shadowdevelopers.i230529_i230723.data.Backend.uid == null) return onComplete()
        com.shadowdevelopers.i230529_i230723.data.Backend.call({
            com.shadowdevelopers.i230529_i230723.data.Backend.api.post("presence/leave.php", org.json.JSONObject())
        }) { onComplete() }
    }

    /** Right after log in / sign up: register this phone for pushes, start presence and the listeners. */
    fun onSignedIn() {
        FirebaseNotifications.saveToken()
        connectPresence()
    }

    /** Stops every listener that belongs to the signed-in user (called on logout). */
    fun stopUserListeners() {
        handler.removeCallbacks(markOffline)
        handler.removeCallbacks(heartbeat)
        CallManager.stopListening()
        KinnectBackgroundService.stop(this)
    }

    companion object {
        /** True while any Kinnect screen is visible (an incoming call can then open its screen directly). */
        @Volatile var inForeground = false

        /** How long the app may be in the background before the user is shown as offline. */
        private const val OFFLINE_GRACE_MS = 5_000L
        private const val HEARTBEAT_MS = 20_000L

        /** "Active now", "Active 5m ago", "Active yesterday", "Active 12 Oct" (or "Offline" if never seen). */
        fun presenceLabel(online: Boolean, lastSeen: Long): String = when {
            online -> "Active now"
            lastSeen <= 0L -> "Offline"
            else -> when (val ago = TimeFormat.ago(lastSeen)) {
                "now" -> "Active just now"
                "Yesterday" -> "Active yesterday"
                else -> if (ago.matches(Regex("\\d+[mh]"))) "Active $ago ago" else "Active $ago" // "5m" -> "Active 5m ago", "12 Oct" -> "Active 12 Oct"
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Insets.prepare(activity)
        Nav.installBackToHome(activity)
    }
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
