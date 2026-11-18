package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query

/**
 * Keeps Kinnect listening while the app is closed, so messages and calls still reach the phone WITHOUT any server.
 * It is a foreground service (Android shows a small quiet "Kinnect is running" notification), which stops the system
 * from freezing the app. While it runs:
 *   - CallManager's callRequests listener rings incoming calls (full-screen call notification),
 *   - this service shows a notification for every new row in notifications/{me} (messages, friend requests,
 *     reactions, comments, live...), including ones that arrived while the phone was offline.
 * FCM pushes from fcm-server still work and use the same notification ids, so nothing is shown twice.
 */
class KinnectBackgroundService : Service() {
    private var query: Query? = null
    private var listener: ChildEventListener? = null
    private var listeningUid: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uid = Backend.uid
        if (uid == null) {
            // Logged out (for example the system restarted this service after a logout).
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = runningNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
        CallManager.startListening(this)
        listenForNotifications(uid)
        return START_STICKY
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    private fun stopListening() {
        val current = query
        val currentListener = listener
        if (current != null && currentListener != null) current.removeEventListener(currentListener)
        query = null
        listener = null
        listeningUid = null
    }

    /** New rows of notifications/{me} since the last one this phone showed (a short catch-up on first run). */
    private fun listenForNotifications(uid: String) {
        if (listeningUid == uid) return
        stopListening()
        val prefs = getSharedPreferences("kinnect_push", MODE_PRIVATE)
        val since = prefs.getLong("last_$uid", System.currentTimeMillis() - 60_000L)
        val ref = FirebaseData.database.getReference("notifications").child(uid)
        val newer = ref.orderByChild("createdAt").startAt((since + 1).toDouble()).limitToLast(10)
        val childListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val created = snapshot.child("createdAt").getValue(Long::class.java) ?: return
                if (snapshot.child("read").getValue(Boolean::class.java) == true) return
                if (created > prefs.getLong("last_$uid", 0L)) prefs.edit().putLong("last_$uid", created).apply()
                present(applicationContext, snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) = Unit
        }
        newer.addChildEventListener(childListener)
        query = newer
        listener = childListener
        listeningUid = uid
    }

    /** Turns one notifications row into a system notification (unless the user is looking at it already). */
    private fun present(context: Context, row: DataSnapshot) {
        val type = row.child("type").getValue(String::class.java).orEmpty()
        val sender = row.child("senderId").getValue(String::class.java).orEmpty()
        val chatId = row.child("chatId").getValue(String::class.java)
        val body = row.child("body").getValue(String::class.java).orEmpty()
        if (sender.isBlank() || type == "call") return // calls ring through CallManager instead
        // Always post a system dropdown notification; the in-app list and badge update independently.
        ProfileCache.get(sender) { profile ->
            val name = profile.name.ifBlank { "Kinnect" }
            when (type) {
                "message" -> FirebaseNotifications.show(context, name, body.ifBlank { "Sent you a message" }, chatId, sender)
                "screenshot" -> FirebaseNotifications.show(context, name, "took a screenshot of your chat", chatId, sender)
                "activity" -> FirebaseNotifications.showActivity(context, name, body.ifBlank { "New activity on Kinnect" }, row.key.orEmpty())
                else -> FirebaseNotifications.showActivity(context, name, describe(type, body), row.key.orEmpty())
            }
        }
    }

    private fun describe(type: String, body: String) = when (type) {
        "friendRequest" -> "sent you a friend request"
        "friendAccepted" -> "accepted your friend request"
        "reaction" -> "reacted to your post"
        "comment" -> "commented: $body"
        "reply" -> "replied: $body"
        "live" -> "is live now"
        "tag" -> body.ifBlank { "tagged you" }
        else -> body.ifBlank { "New activity on Kinnect" }
    }

    companion object {
        private const val FOREGROUND_ID = 4021
        private const val CHANNEL_SERVICE = "kinnect_service"

        /** Starts (or keeps) the background listener. Call only while the user is signed in and Kinnect is on screen. */
        fun start(context: Context) {
            if (Backend.uid == null) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, KinnectBackgroundService::class.java))
            } catch (_: Exception) {
                // The system refused to start it right now; the next screen the user opens tries again.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KinnectBackgroundService::class.java))
        }

        private fun runningNotification(context: Context): Notification {
            if (Build.VERSION.SDK_INT >= 26) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(NotificationChannel(CHANNEL_SERVICE, "Kinnect is running", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Keeps Kinnect connected so messages and calls arrive when the app is closed"
                    setShowBadge(false)
                })
            }
            val open = PendingIntent.getActivity(context, 0, Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Builder(context, CHANNEL_SERVICE)
                .setSmallIcon(R.drawable.ic_chat)
                .setColor(ContextCompat.getColor(context, R.color.primary))
                .setContentTitle("Kinnect is running")
                .setContentText("Listening for messages and calls")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(open)
                .build()
        }
    }
}
