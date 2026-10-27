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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

object FirebaseNotifications {
    /** Each phone's token is stored under a short id derived from the token itself. */
    private fun tokenId(token: String) = token.hashCode().toUInt().toString(16)

    fun saveToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            FirebaseData.database.getReference("fcmTokens").child(uid).child(tokenId(token)).setValue(token)
        }
    }

    /** Called on logout so this phone stops receiving pushes meant for the account that left. */
    fun removeToken(onDone: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return onDone()
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                FirebaseData.database.getReference("fcmTokens").child(uid).child(tokenId(token)).removeValue()
                    .addOnCompleteListener { onDone() }
            }
            .addOnFailureListener { onDone() }
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
                if (from != null && from == ChatActivity.openChatWith) return // that chat is open on screen right now
                if (body.isNotBlank()) FirebaseNotifications.show(this, title, body, data["chatId"], from)
            }
        }
    }
}
