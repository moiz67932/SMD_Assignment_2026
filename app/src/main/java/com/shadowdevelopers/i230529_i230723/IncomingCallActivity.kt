package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Incoming call (new screen, Voice call style). Rings and vibrates for up to 30 s.
 * Accept -> status "accepted" and the call screen joins the Agora channel; Decline -> "declined";
 * no answer -> "missed". Closes by itself if the caller hangs up first.
 */
class IncomingCallActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var answered = false
    private var callId = ""
    private var callerId = ""
    private var video = false
    private val missed = Runnable { finishWith(CallManager.MISSED) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over the lock screen and wake the display, like a phone call.
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        setContentView(R.layout.activity_incoming_call)
        callId = intent.getStringExtra("call_id").orEmpty()
        callerId = intent.getStringExtra("caller_id").orEmpty()
        video = intent.getBooleanExtra("video", false)
        if (callId.isBlank() || callerId.isBlank() || me.isBlank()) {
            finish()
            return
        }
        showingCallId = callId
        CallNotifier.cancel(this, callId)
        findViewById<TextView>(R.id.incomingType).text = if (video) "KINNECT VIDEO CALL" else "KINNECT VOICE CALL"
        findViewById<TextView>(R.id.incomingStatus).text = if (video) "Incoming video call" else "Incoming voice call"
        findViewById<TextView>(R.id.incomingName).text = intent.getStringExtra("caller_name").orEmpty()
        ProfileCache.get(callerId) { profile ->
            findViewById<TextView>(R.id.incomingName).text = profile.name
            AvatarBinder.bind(findViewById(R.id.incomingAvatar), profile.name, profile.photo, cornerRadiusDp = 28f)
        }
        findViewById<View>(R.id.acceptCallButton).setOnClickListener { accept() }
        findViewById<View>(R.id.declineCallButton).setOnClickListener { finishWith(CallManager.DECLINED) }
        startRinging()
        handler.postDelayed(missed, CallManager.RING_TIMEOUT_MS)
    }

    override fun onStart() {
        super.onStart()
        if (callId.isBlank()) return
        // If the caller cancels (or another device answers), stop ringing.
        listeners.value(CallManager.request(me, callId)) { snapshot ->
            val status = snapshot.child("status").getValue(String::class.java)
            if (!answered && status != CallManager.RINGING) {
                answered = true
                finish()
            }
        }
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    private fun accept() {
        if (answered) return
        answered = true
        stopRinging()
        CallManager.setStatus(me, callId, CallManager.ACCEPTED)
        startActivity(Intent(this, VoiceCallActivity::class.java)
            .putExtra("call_id", callId).putExtra("other_uid", callerId).putExtra("video", video).putExtra("incoming", true))
        finish()
    }

    private fun finishWith(status: String) {
        if (answered) return
        answered = true
        CallManager.setStatus(me, callId, status)
        finish()
    }

    private fun startRinging() {
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also { it.play() }
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val pattern = longArrayOf(0, 800, 800)
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        else @Suppress("DEPRECATION") vibrator.vibrate(pattern, 0)
    }

    private fun stopRinging() {
        ringtone?.stop()
        getSystemService(Vibrator::class.java)?.cancel()
    }

    override fun onDestroy() {
        handler.removeCallbacks(missed)
        stopRinging()
        if (showingCallId == callId) showingCallId = null
        super.onDestroy()
    }

    companion object {
        /** The call this screen is ringing for, so a second signal for the same call is ignored. */
        @Volatile var showingCallId: String? = null
    }
}
