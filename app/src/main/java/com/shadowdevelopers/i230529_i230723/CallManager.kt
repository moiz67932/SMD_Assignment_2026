package com.shadowdevelopers.i230529_i230723

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

/**
 * Call signalling through the Realtime Database (the audio/video itself goes through Agora):
 *   callRequests/{calleeUid}/{callId} = {callerId, calleeId, callerName, video, status, createdAt}
 *   status: ringing -> accepted -> ended, or declined / missed / cancelled
 * The Agora channel name is the callId, and the token is null (Agora project in testing mode).
 */
object CallManager {
    const val RINGING = "ringing"
    const val ACCEPTED = "accepted"
    const val DECLINED = "declined"
    const val MISSED = "missed"
    const val CANCELLED = "cancelled"
    const val ENDED = "ended"
    const val RING_TIMEOUT_MS = 30_000L

    fun request(calleeUid: String, callId: String) = FirebaseData.callRequests.child(calleeUid).child(callId)

    /** Creates the ringing request, the call log and the push for the callee in one update. */
    fun create(me: String, callee: String, video: Boolean, onCreated: (String) -> Unit, onError: (String) -> Unit) {
        val callId = FirebaseData.callRequests.child(callee).push().key ?: return onError("Could not start the call")
        ProfileCache.get(me) { profile ->
            val now = mapOf(".sv" to "timestamp")
            val updates = mutableMapOf<String, Any>(
                "callRequests/$callee/$callId" to mapOf(
                    "callerId" to me, "calleeId" to callee, "callerName" to profile.name,
                    "video" to video, "status" to RINGING, "createdAt" to now
                ),
                "callLogs/$callId" to mapOf("callerId" to me, "calleeId" to callee, "type" to if (video) "video" else "voice", "status" to RINGING, "createdAt" to now)
            )
            updates += PushQueue.entry(callee, me, "call", profile.name, if (video) "Incoming video call" else "Incoming voice call",
                mapOf("callId" to callId, "video" to video))
            FirebaseData.database.reference.updateChildren(updates)
                .addOnSuccessListener { onCreated(callId) }
                .addOnFailureListener { onError(it.localizedMessage ?: "Could not start the call") }
        }
    }

    fun setStatus(calleeUid: String, callId: String, status: String, durationSeconds: Int? = null) {
        val updates = mutableMapOf<String, Any>("callRequests/$calleeUid/$callId/status" to status, "callLogs/$callId/status" to status)
        if (durationSeconds != null) {
            updates["callLogs/$callId/durationSeconds"] = durationSeconds
            updates["callLogs/$callId/endedAt"] = mapOf(".sv" to "timestamp")
        }
        FirebaseData.database.reference.updateChildren(updates)
    }

    /** The line written into the chat when a call finishes ("Voice call · 2:31", "Missed video call"). */
    fun summary(video: Boolean, status: String, seconds: Int): String {
        val kind = if (video) "video call" else "voice call"
        return when {
            status == DECLINED -> "${kind.replaceFirstChar { it.uppercase() }} declined"
            status == MISSED || status == CANCELLED || seconds <= 0 -> "Missed $kind"
            else -> "${kind.replaceFirstChar { it.uppercase() }} · ${TimeFormat.duration(seconds)}"
        }
    }

    // ----- Global "someone is calling me" listener, active while a user is signed in -----

    private var listeningUid: String? = null
    private var listener: ChildEventListener? = null

    fun startListening(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (listeningUid == uid) return
        stopListening()
        val app = context.applicationContext
        val childListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) = handle(app, snapshot)
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = handle(app, snapshot)
            override fun onChildRemoved(snapshot: DataSnapshot) = CallNotifier.cancel(app, snapshot.key.orEmpty())
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) = Unit
        }
        FirebaseData.callRequests.child(uid).addChildEventListener(childListener)
        listener = childListener
        listeningUid = uid
    }

    fun stopListening() {
        val uid = listeningUid ?: return
        listener?.let { FirebaseData.callRequests.child(uid).removeEventListener(it) }
        listener = null
        listeningUid = null
    }

    private fun handle(context: Context, snapshot: DataSnapshot) {
        val callId = snapshot.key ?: return
        val status = snapshot.child("status").getValue(String::class.java)
        val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
        if (status == RINGING && FirebaseData.now() - createdAt < RING_TIMEOUT_MS) {
            CallNotifier.ring(
                context, callId,
                callerId = snapshot.child("callerId").getValue(String::class.java).orEmpty(),
                callerName = snapshot.child("callerName").getValue(String::class.java) ?: "Kinnect member",
                video = snapshot.child("video").getValue(Boolean::class.java) == true
            )
        } else {
            CallNotifier.cancel(context, callId)
        }
    }
}

/** Shows the ringing screen: straight away when Kinnect is open, otherwise as a full-screen call notification. */
object CallNotifier {
    fun ring(context: Context, callId: String, callerId: String, callerName: String, video: Boolean) {
        if (callId.isBlank() || callerId.isBlank() || IncomingCallActivity.showingCallId == callId) return
        val intent = Intent(context, IncomingCallActivity::class.java)
            .putExtra("call_id", callId).putExtra("caller_id", callerId).putExtra("caller_name", callerName).putExtra("video", video)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (KinnectApplication.inForeground) {
            context.startActivity(intent)
            return
        }
        if (!FirebaseNotifications.canNotify(context)) return
        FirebaseNotifications.createChannels(context)
        val pending = PendingIntent.getActivity(context, callId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, FirebaseNotifications.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_phone)
            .setColor(context.getColor(R.color.primary))
            .setContentTitle(callerName)
            .setContentText(if (video) "Incoming video call" else "Incoming voice call")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setTimeoutAfter(CallManager.RING_TIMEOUT_MS)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()
        NotificationManagerCompat.from(context).notify(callId.hashCode(), notification)
    }

    fun cancel(context: Context, callId: String) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(callId.hashCode())
    }
}
