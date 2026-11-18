package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Call signalling through the PHP API (the audio/video itself goes through Agora, channel = call id):
 *   POST calls/create.php  -> MySQL calls row (status ringing) + "incoming call" FCM push to the callee
 *   POST calls/status.php  -> accepted / declined / cancelled / missed / ended (ended stores the duration = call log)
 *   GET  calls/get.php     -> both phones follow the status while the call screen is open
 *   GET  calls/incoming.php-> calls ringing for me (polled while Kinnect runs; FCM rings it when closed)
 */
object CallManager {
    const val RINGING = "ringing"
    const val ACCEPTED = "accepted"
    const val DECLINED = "declined"
    const val MISSED = "missed"
    const val CANCELLED = "cancelled"
    const val ENDED = "ended"
    const val RING_TIMEOUT_MS = 30_000L

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Starts a call; [onCreated] gets the call id (also the Agora channel name). Needs the internet, like the call itself. */
    fun create(me: String, callee: String, video: Boolean, onCreated: (String) -> Unit, onError: (String) -> Unit) {
        val id = Backend.newId()
        Backend.call({
            Backend.api.post("calls/create.php", org.json.JSONObject().put("action_uuid", Backend.newId()).put("id", id).put("callee_id", callee).put("video", video))
        }) { result ->
            result.onSuccess { onCreated(id) }.onFailure { onError(Forms.message(it as? Exception, "Could not start the call")) }
        }
    }

    /** Status changes are queued (pending_actions), so a hang-up while the connection drops still reaches the call log. */
    fun setStatus(calleeUid: String, callId: String, status: String, durationSeconds: Int? = null) {
        if (callId.isBlank()) return
        Backend.background {
            Backend.enqueue("call_status", Backend.newId(), org.json.JSONObject().put("id", callId).put("status", status), "call.status",
                org.json.JSONObject().put("id", callId).put("status", status))
        }
    }

    /** Polls the call's status every 1.5 s while the call screen is visible (removed with the screen's ListenerBag). */
    fun watch(bag: ListenerBag, callId: String, onStatus: (String) -> Unit) {
        bag.poll(1_500) {
            Backend.call({ Backend.api.get("calls/get.php?id=$callId").getJSONObject("call").getString("status") }) { result ->
                result.onSuccess(onStatus)
            }
        }
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

    // ----- "Someone is calling me": checked every 3 s while a user is signed in and Kinnect runs -----

    private var listeningUid: String? = null
    private val ringing = mutableSetOf<String>()
    private lateinit var appContext: Context
    private val poll = object : Runnable {
        override fun run() {
            val uid = listeningUid ?: return
            if (Backend.uid != uid) return stopListening()
            Backend.call({ Backend.api.get("calls/incoming.php").getJSONArray("calls") }) { result ->
                result.onSuccess { list ->
                    val now = (0 until list.length()).map { list.getJSONObject(it) }
                    val ids = now.map { it.getString("id") }.toSet()
                    (ringing - ids).forEach { CallNotifier.cancel(appContext, it) }
                    ringing.retainAll(ids)
                    now.filter { it.getString("id") !in ringing }.forEach { call ->
                        ringing.add(call.getString("id"))
                        CallNotifier.ring(appContext, call.getString("id"), call.getString("caller_id"), call.optString("caller_name").ifBlank { "Kinnect member" },
                            call.optInt("video") == 1)
                    }
                }
            }
            main.postDelayed(this, 3_000)
        }
    }

    fun startListening(context: Context) {
        val uid = Backend.uid ?: return
        if (listeningUid == uid) return
        stopListening()
        appContext = context.applicationContext
        listeningUid = uid
        main.post(poll)
    }

    fun stopListening() {
        listeningUid = null
        main.removeCallbacks(poll)
        ringing.clear()
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
