package com.shadowdevelopers.i230529_i230723

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class KinnectApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0
    private val handler = Handler(Looper.getMainLooper())
    private var presenceReference: DatabaseReference? = null
    private var connectionListener: com.google.firebase.database.ValueEventListener? = null

    override fun onCreate() {
        super.onCreate()
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
        startedActivities++
        handler.removeCallbacks(markOffline)
        if (FirebaseAuth.getInstance().currentUser != null) connectPresence()
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities <= 0) {
            startedActivities = 0
            handler.postDelayed(markOffline, 1200)
        }
    }

    private val markOffline = Runnable { setOffline() }

    private fun connectPresence() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val database = FirebaseDatabase.getInstance().reference
        presenceReference = database.child("presence").child(uid)
        connectionListener?.let { database.child(".info/connected").removeEventListener(it) }
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) != true) return
                val reference = presenceReference ?: return
                reference.onDisconnect().setValue(mapOf("online" to false, "lastSeen" to mapOf(".sv" to "timestamp")))
                    .addOnSuccessListener { reference.setValue(mapOf("online" to true, "lastSeen" to mapOf(".sv" to "timestamp"))) }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
        }
        connectionListener = listener
        database.child(".info/connected").addValueEventListener(listener)
    }

    fun setOffline(onComplete: () -> Unit = {}) {
        val database = FirebaseDatabase.getInstance().reference
        connectionListener?.let { database.child(".info/connected").removeEventListener(it) }
        connectionListener = null
        val reference = presenceReference
        presenceReference = null
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null || reference == null) {
            onComplete()
            return
        }
        reference.setValue(mapOf("online" to false, "lastSeen" to mapOf(".sv" to "timestamp"))).addOnCompleteListener { onComplete() }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Insets.prepare(activity)
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
