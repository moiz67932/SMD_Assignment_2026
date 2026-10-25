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
    fun saveToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val tokenId = token.hashCode().toUInt().toString(16)
            FirebaseData.database.getReference("fcmTokens").child(uid).child(tokenId).setValue(token)
        }
    }

    fun show(context: Context, title: String, body: String, chatId: String?, senderId: String?) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("kinnect_messages", "Kinnect messages", NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = Intent(context, ChatActivity::class.java)
        if (!chatId.isNullOrBlank()) intent.putExtra("chat_id", chatId)
        if (!senderId.isNullOrBlank()) intent.putExtra("person_uid", senderId)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pending = PendingIntent.getActivity(context, (chatId ?: senderId ?: "chat").hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "kinnect_messages")
            .setSmallIcon(R.drawable.ic_chat)
            .setContentTitle(title.ifBlank { "Kinnect" })
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify((chatId ?: senderId ?: body).hashCode(), notification)
    }
}

class KinnectMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FirebaseNotifications.saveToken()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"].orEmpty()
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        if (body.isNotBlank()) {
            FirebaseNotifications.show(this, title, body, message.data["chatId"], message.data["senderId"])
        }
    }
}
