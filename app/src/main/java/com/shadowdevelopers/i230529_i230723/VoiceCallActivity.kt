package com.shadowdevelopers.i230529_i230723

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas

/**
 * Voice call (22), also used for video calls.
 * Outgoing ("other_uid", "video"): asks for mic/camera, creates the ringing request, waits for an answer.
 * Incoming ("call_id", "other_uid", "video", "incoming" = true): the call was just accepted, so it joins at once.
 * Both phones join the Agora channel named after the callId with a null token (Agora testing mode).
 * When either side hangs up, the request status becomes "ended" and the other phone closes too.
 * The caller writes the call line into the chat ("Voice call · 2:31", "Missed video call").
 */
class VoiceCallActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private val listeners = ListenerBag()
    private val handler = Handler(Looper.getMainLooper())
    private var engine: RtcEngine? = null
    private var callId = ""
    private var peerUid = ""
    private var videoCall = false
    private var incoming = false
    private var joined = false
    private var connectedAt = 0L
    private var finished = false
    private var historyWritten = false
    private var muted = false
    private var speaker = true
    private var cameraOn = true
    private lateinit var statusText: TextView
    private lateinit var durationText: TextView

    private val calleeUid get() = if (incoming) me else peerUid

    private val timer = object : Runnable {
        override fun run() {
            if (connectedAt > 0 && !finished) {
                durationText.text = TimeFormat.duration(seconds())
                handler.postDelayed(this, 1000)
            }
        }
    }

    /** Caller side: nobody answered in time. */
    private val noAnswer = Runnable { if (!joinedRemote()) finishCall(CallManager.MISSED, "No answer") }

    private val engineEvents = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread { if (connectedAt == 0L) statusText.text = "Connecting…" }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                connectedAt = System.currentTimeMillis()
                statusText.text = if (videoCall) "" else "Connected"
                handler.removeCallbacks(noAnswer)
                handler.post(timer)
                // The remote picture can only be attached once we know the other person's Agora uid.
                if (videoCall) {
                    val remote = SurfaceView(this@VoiceCallActivity)
                    findViewById<FrameLayout>(R.id.remoteVideoContainer).apply { removeAllViews(); addView(remote) }
                    engine?.setupRemoteVideo(VideoCanvas(remote, VideoCanvas.RENDER_MODE_HIDDEN, uid))
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread { finishCall(CallManager.ENDED, "Call ended") }
        }

        override fun onError(err: Int) {
            runOnUiThread { statusText.text = "Call error ($err)" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_call)
        callId = intent.getStringExtra("call_id").orEmpty()
        peerUid = intent.getStringExtra("other_uid").orEmpty()
        videoCall = intent.getBooleanExtra("video", false)
        incoming = intent.getBooleanExtra("incoming", false)
        statusText = findViewById(R.id.callStatusText)
        durationText = findViewById(R.id.callDuration)
        findViewById<TextView>(R.id.callModeLabel).text = if (videoCall) "Kinnect video call" else "Kinnect voice call"
        if (peerUid.isBlank() || me.isBlank() || (incoming && callId.isBlank())) {
            finish()
            return
        }
        if (BuildConfig.AGORA_APP_ID.isBlank()) {
            Toast.makeText(this, "Add your Agora App ID to agora.properties to enable calls", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        ProfileCache.get(peerUid) { profile ->
            findViewById<TextView>(R.id.callName).text = profile.name
            AvatarBinder.bind(findViewById(R.id.callAvatar), profile.name, profile.photo, cornerRadiusDp = 28f)
        }
        findViewById<View>(R.id.endCallButton).setOnClickListener { hangUp() }
        findViewById<View>(R.id.muteButton).setOnClickListener { toggleMute() }
        findViewById<View>(R.id.speakerButton).setOnClickListener { toggleSpeaker() }
        findViewById<View>(R.id.switchCameraButton).apply {
            visibility = if (videoCall) View.VISIBLE else View.GONE
            setOnClickListener { engine?.switchCamera() }
        }
        findViewById<ImageView>(R.id.cameraToggleButton).apply {
            if (videoCall) {
                setImageResource(R.drawable.ic_video)
                contentDescription = "Turn camera off"
                setOnClickListener { toggleCamera() }
            } else {
                setOnClickListener { Toast.makeText(this@VoiceCallActivity, "The keypad is not part of this version", Toast.LENGTH_SHORT).show() }
            }
        }
        statusText.text = if (incoming) "Connecting…" else "Calling…"
        // Back = hang up, so a call never keeps running on a screen the user has left.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = hangUp()
        })
        // Microphone (and camera for video) must be granted before anything is sent or joined.
        val required = permissions()
        if (required.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) begin()
        else requestPermissions(required, REQUEST_PERMISSIONS)
    }

    private fun permissions() = if (videoCall) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) begin()
        else {
            Toast.makeText(this, if (videoCall) "Calls need the microphone and camera" else "Calls need the microphone", Toast.LENGTH_LONG).show()
            if (incoming) CallManager.setStatus(me, callId, CallManager.DECLINED)
            finish()
        }
    }

    private fun begin() {
        if (incoming) {
            watchStatus()
            join()
            return
        }
        CallManager.create(me, peerUid, videoCall, onCreated = { id ->
            callId = id
            watchStatus()
            handler.postDelayed(noAnswer, CallManager.RING_TIMEOUT_MS + 5_000)
        }, onError = { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            finish()
        })
    }

    /** Follows callRequests/{callee}/{callId}/status on both phones. */
    private fun watchStatus() {
        listeners.value(CallManager.request(calleeUid, callId).child("status")) { snapshot ->
            when (snapshot.getValue(String::class.java)) {
                CallManager.ACCEPTED -> if (!incoming) {
                    statusText.text = "Connecting…"
                    join()
                }
                CallManager.DECLINED -> finishCall(CallManager.DECLINED, "Declined")
                CallManager.MISSED -> finishCall(CallManager.MISSED, "No answer")
                CallManager.CANCELLED -> finishCall(CallManager.CANCELLED, "Call cancelled")
                CallManager.ENDED -> finishCall(CallManager.ENDED, "Call ended")
            }
        }
    }

    private fun join() {
        if (joined || finished) return
        joined = true
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = BuildConfig.AGORA_APP_ID
                mEventHandler = engineEvents
            }
            val rtc = RtcEngine.create(config)
            engine = rtc
            rtc.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            if (videoCall) {
                rtc.enableVideo()
                findViewById<View>(R.id.callVideoContainer).visibility = View.VISIBLE
                val local = SurfaceView(this).apply { setZOrderMediaOverlay(true) }
                findViewById<FrameLayout>(R.id.localVideoContainer).apply { removeAllViews(); addView(local) }
                rtc.setupLocalVideo(VideoCanvas(local, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                rtc.startPreview()
            }
            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishMicrophoneTrack = true
                publishCameraTrack = videoCall
                autoSubscribeAudio = true
                autoSubscribeVideo = videoCall
            }
            rtc.setDefaultAudioRoutetoSpeakerphone(speaker)
            rtc.joinChannel(null, callId, 0, options)
        } catch (error: Exception) {
            Toast.makeText(this, error.localizedMessage ?: "Could not start the call", Toast.LENGTH_LONG).show()
            hangUp()
        }
    }

    private fun joinedRemote() = connectedAt > 0

    private fun seconds() = if (connectedAt == 0L) 0 else ((System.currentTimeMillis() - connectedAt) / 1000).toInt()

    /** My red button. Before anyone answered it cancels the call; afterwards it ends it for both. */
    private fun hangUp() {
        if (callId.isBlank()) {
            finish()
            return
        }
        val status = if (!incoming && !joinedRemote()) CallManager.CANCELLED else CallManager.ENDED
        finishCall(status, "Call ended")
    }

    private fun finishCall(status: String, label: String) {
        if (finished) return
        finished = true
        handler.removeCallbacks(timer)
        handler.removeCallbacks(noAnswer)
        val duration = seconds()
        statusText.text = label
        if (callId.isNotBlank()) CallManager.setStatus(calleeUid, callId, status, durationSeconds = duration)
        engine?.leaveChannel()
        writeHistory(status, duration)
        handler.postDelayed({ finish() }, if (status == CallManager.ENDED) 300 else 1200)
    }

    /** Only the caller writes the chat line, so it appears exactly once. */
    private fun writeHistory(status: String, duration: Int) {
        if (incoming || historyWritten || callId.isBlank()) return
        historyWritten = true
        val text = CallManager.summary(videoCall, status, duration)
        ChatSender.send(this, me, peerUid, type = "call", text = text, preview = text, fields = mapOf("callId" to callId))
    }

    private fun toggleMute() {
        muted = !muted
        engine?.muteLocalAudioStream(muted)
        findViewById<ImageView>(R.id.muteButton).apply {
            setBackgroundResource(if (muted) R.drawable.circle_white else R.drawable.circle_teal_mid)
            setColorFilter(ContextCompat.getColor(context, if (muted) R.color.primary else R.color.white))
            contentDescription = if (muted) "Unmute" else "Mute"
        }
    }

    private fun toggleSpeaker() {
        speaker = !speaker
        engine?.setEnableSpeakerphone(speaker)
        findViewById<ImageView>(R.id.speakerButton).apply {
            setBackgroundResource(if (speaker) R.drawable.circle_teal_mid else R.drawable.circle_white)
            setColorFilter(ContextCompat.getColor(context, if (speaker) R.color.white else R.color.primary))
            contentDescription = if (speaker) "Speaker on" else "Speaker off"
        }
    }

    private fun toggleCamera() {
        cameraOn = !cameraOn
        engine?.muteLocalVideoStream(!cameraOn)
        findViewById<View>(R.id.localVideoContainer).visibility = if (cameraOn) View.VISIBLE else View.INVISIBLE
        findViewById<ImageView>(R.id.cameraToggleButton).apply {
            setBackgroundResource(if (cameraOn) R.drawable.circle_teal_mid else R.drawable.circle_white)
            setColorFilter(ContextCompat.getColor(context, if (cameraOn) R.color.white else R.color.primary))
            contentDescription = if (cameraOn) "Turn camera off" else "Turn camera on"
        }
    }

    override fun onDestroy() {
        if (!finished && callId.isNotBlank()) hangUp()
        listeners.removeAll()
        handler.removeCallbacksAndMessages(null)
        engine?.leaveChannel()
        if (engine != null) RtcEngine.destroy()
        engine = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 415
    }
}
