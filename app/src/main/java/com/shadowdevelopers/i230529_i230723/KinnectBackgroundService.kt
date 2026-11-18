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
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var listeningUid: String? = null
    private val poll = object : Runnable {
        override fun run() {
            val uid = listeningUid ?: return
            if (Backend.uid != uid) { stopListening(); return }
            checkNotifications(uid)
            Backend.requestSync() // also retries offline actions while the app is closed
            handler.postDelayed(this, POLL_MS)
        }
    }

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
        handler.removeCallbacks(poll)
        listeningUid = null
    }

    /** Polls GET notifications/list.php (every 8 s while the service runs, so also when the app is closed). */
    private fun listenForNotifications(uid: String) {
        if (listeningUid == uid) return
        stopListening()
        listeningUid = uid
        handler.post(poll)
    }

    /** Shows every unread notification newer than the last one this phone showed (catch-up after being offline). */
    private fun checkNotifications(uid: String) {
        val prefs = getSharedPreferences("kinnect_push", MODE_PRIVATE)
        Backend.call({ Backend.api.get("notifications/list.php").getJSONArray("notifications") }) { result ->
            val list = result.getOrNull() ?: return@call
            val since = prefs.getLong("last_$uid", System.currentTimeMillis() - 60_000L)
            var newest = since
            (0 until list.length()).map { list.getJSONObject(it) }.sortedBy { TimeFormat.parse(it.optString("created_at")) }.forEach { row ->
                val created = TimeFormat.parse(row.optString("created_at"))
                if (created <= since || !(row.isNull("read_at") || row.optString("read_at").isBlank())) return@forEach
                newest = maxOf(newest, created)
                present(applicationContext, row)
            }
            if (newest > since) prefs.edit().putLong("last_$uid", newest).apply()
        }
    }

    /** Turns one notifications row into a system notification (unless the user is looking at it already). */
    private fun present(context: Context, row: org.json.JSONObject) {
        val type = when (row.optString("type")) { "friend_request" -> "friendRequest"; "friend_accepted" -> "friendAccepted"; else -> row.optString("type") }
        val sender = row.optString("actor_id")
        val chatId: String? = null
        val body = row.optString("body")
        if (sender.isBlank() || type == "call") return // calls ring through CallManager instead
        // Always post a system dropdown notification; the in-app list and badge update independently.
        ProfileCache.get(sender) { profile ->
            val name = profile.name.ifBlank { "Kinnect" }
            when (type) {
                "message" -> FirebaseNotifications.show(context, name, body.ifBlank { "Sent you a message" }, chatId, sender)
                "screenshot" -> FirebaseNotifications.show(context, name, "took a screenshot of your chat", chatId, sender)
                "activity" -> FirebaseNotifications.showActivity(context, name, body.ifBlank { "New activity on Kinnect" }, row.optString("id"))
                else -> FirebaseNotifications.showActivity(context, name, describe(type, body), row.optString("id"))
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
        private const val POLL_MS = 8_000L
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
