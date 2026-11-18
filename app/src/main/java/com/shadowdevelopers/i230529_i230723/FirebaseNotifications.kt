package com.shadowdevelopers.i230529_i230723

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shadowdevelopers.i230529_i230723.data.Backend
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

object FirebaseNotifications {
    /** A random id for this installation; the server keeps one FCM token per (user, device). */
    private fun deviceKey(context: android.content.Context): String {
        val prefs = context.getSharedPreferences("kinnect_device", android.content.Context.MODE_PRIVATE)
        prefs.getString("key", null)?.let { return it }
        val key = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("key", key).apply()
        return key
    }

    private lateinit var appContext: android.content.Context
    fun init(context: android.content.Context) { appContext = context.applicationContext }

    /**
     * Sends this phone's FCM token to POST devices/register.php. The PHP backend stores it in MySQL and its push
     * worker sends the notifications (new message, friend request, screenshot) from the server.
     */
    fun saveToken() {
        if (Backend.uid == null) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val body = org.json.JSONObject().put("action_uuid", Backend.newId()).put("device_key", deviceKey(appContext)).put("fcm_token", token)
            Backend.background { Backend.api.post("devices/register.php", body) }
        }
    }

    /** Blocking (call off the main thread, before the token is cleared): stop pushes for the account that logs out. */
    fun unregisterDevice() {
        if (Backend.uid == null) return
        runCatching {
            Backend.api.post("devices/unregister.php", org.json.JSONObject().put("action_uuid", Backend.newId()).put("device_key", deviceKey(appContext)))
        }
    }

    /**
     * The same notification can arrive twice: as an FCM push from the PHP worker and from the background poll of
     * notifications/list.php. Both check this list of shown notification ids first, so it is displayed only once.
     */
    @Synchronized fun firstTime(context: android.content.Context, notificationId: String?): Boolean {
        if (notificationId.isNullOrBlank()) return true
        val prefs = context.getSharedPreferences("kinnect_shown", android.content.Context.MODE_PRIVATE)
        val shown = prefs.getString("ids", "").orEmpty().split(",").filter { it.isNotBlank() }
        if (notificationId in shown) return false
        prefs.edit().putString("ids", (shown.takeLast(199) + notificationId).joinToString(",")).apply()
        return true
    }

    const val CHANNEL_MESSAGES = "kinnect_messages"
    const val CHANNEL_CALLS = "kinnect_calls"

    /** Notification channels: "Messages" (high importance) and "Calls" (high importance, used by incoming calls). */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH))
    }

    fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Friend requests, reactions, comments...: tapping opens the Notifications screen. */
    fun showActivity(context: Context, title: String, body: String, key: String) {
        if (!canNotify(context)) return
        createChannels(context)
        val intent = Intent(context, NotificationsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notifications)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(title.ifBlank { "Kinnect" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
    }

    /** A message/screenshot notification; tapping it opens the chat with that person. */
    fun show(context: Context, title: String, body: String, chatId: String?, senderId: String?) {
        if (!canNotify(context)) return
        createChannels(context)
        val intent = Intent(context, ChatActivity::class.java)
        if (!senderId.isNullOrBlank()) intent.putExtra("person_uid", senderId)
        intent.putExtra("person_name", title)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pending = PendingIntent.getActivity(context, (chatId ?: senderId ?: "chat").hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_chat)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(title.ifBlank { "Kinnect" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify((chatId ?: senderId ?: body).hashCode(), notification)
    }
}

/**
 * Receives the data-only pushes sent by fcm-server. Builds the notification itself so it can stay quiet
 * when the user is already looking at that conversation.
 */
class KinnectMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FirebaseNotifications.saveToken()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title.orEmpty()
        val body = data["body"] ?: message.notification?.body.orEmpty()
        val from = data["fromUid"]
        when (data["type"]) {
            // Incoming call while Kinnect is closed or in the background: full-screen ringing notification.
            "call" -> CallNotifier.ring(this, data["callId"].orEmpty(), from.orEmpty(), title, data["video"] == "true")
            else -> {
                if (!FirebaseNotifications.firstTime(this, data["notification_id"])) return // already shown by the background poll
                if (from != null && from == ChatActivity.openChatWith) return // that chat is open on screen right now
                if (body.isBlank()) return
                when (data["type"]) {
                    "message", "screenshot" -> FirebaseNotifications.show(this, title, body, data["chatId"], from)
                    else -> FirebaseNotifications.showActivity(this, title, body, data["notification_id"] ?: body)
                }
            }
        }
    }
}
