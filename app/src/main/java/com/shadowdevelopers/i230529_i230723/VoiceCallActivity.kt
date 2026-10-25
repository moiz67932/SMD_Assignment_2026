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

class VoiceCallActivity : AppCompatActivity() {
    private var engine: RtcEngine? = null
    private val handler = Handler(Looper.getMainLooper())
    private var connectedAt = 0L
    private var callEnded = false
    private var muted = false
    private var speaker = true
    private var cameraEnabled = true
    private var remoteUserJoined = false
    private lateinit var statusText: TextView
    private lateinit var durationText: TextView
    private lateinit var callId: String
    private lateinit var peerUid: String
    private lateinit var channel: String
    private var videoCall = false
    private var incoming = false

    private val timer = object : Runnable {
        override fun run() {
            if (connectedAt > 0 && !callEnded) {
                val seconds = ((System.currentTimeMillis() - connectedAt) / 1000).toInt()
                durationText.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val engineEvents = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread { statusText.text = "Waiting for the other person…" }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                remoteUserJoined = true
                connectedAt = System.currentTimeMillis()
                statusText.text = "Connected"
                handler.post(timer)
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                if (remoteUserJoined && !callEnded) {
                    statusText.text = "The other person left"
                    endCall()
                }
            }
        }

        override fun onError(err: Int) {
            runOnUiThread {
                statusText.text = "Call error ($err)"
                Toast.makeText(this@VoiceCallActivity, "Agora call failed: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_call)
        callId = intent.getStringExtra("call_id").orEmpty()
        peerUid = intent.getStringExtra("other_uid").orEmpty()
        channel = intent.getStringExtra("channel") ?: "kinnect_assignment_demo"
        videoCall = intent.getBooleanExtra("video", false)
        incoming = intent.getBooleanExtra("incoming", false)
        val name = intent.getStringExtra("person_name") ?: "Kinnect member"
        findViewById<TextView>(R.id.callName).text = name
        findViewById<TextView>(R.id.callAvatar).text = intent.getStringExtra("person_initials") ?: ""
        statusText = findViewById(R.id.callStatusText)
        durationText = findViewById(R.id.callDuration)
        val cameraButton = findViewById<ImageView>(R.id.cameraToggleButton)
        cameraButton.visibility = if (videoCall) View.VISIBLE else View.GONE
        findViewById<View>(R.id.endCallButton).setOnClickListener { endCall() }
        findViewById<View>(R.id.muteButton).setOnClickListener { toggleMute() }
        findViewById<View>(R.id.speakerButton).setOnClickListener { toggleSpeaker() }
        cameraButton.setOnClickListener { toggleCamera() }
        if (BuildConfig.AGORA_APP_ID.isBlank() || BuildConfig.AGORA_TEMP_TOKEN.isBlank() || callId.isBlank()) {
            statusText.text = "Call setup is incomplete"
            Toast.makeText(this, "Add Agora App ID and a valid temporary token for channel $channel to agora.properties", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val required = if (videoCall) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
        if (required.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) startRtc()
        else requestPermissions(required, 415)
    }

    private fun startRtc() {
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = BuildConfig.AGORA_APP_ID
                mEventHandler = engineEvents
            }
            engine = RtcEngine.create(config)
            val rtc = engine ?: return
            rtc.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtc.setEnableSpeakerphone(true)
            if (videoCall) {
                rtc.enableVideo()
                findViewById<FrameLayout>(R.id.callVideoContainer).visibility = View.VISIBLE
                val local = SurfaceView(this)
                val remote = SurfaceView(this)
                findViewById<FrameLayout>(R.id.localVideoContainer).addView(local)
                findViewById<FrameLayout>(R.id.remoteVideoContainer).addView(remote)
                rtc.setupLocalVideo(VideoCanvas(local, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                rtc.setupRemoteVideo(VideoCanvas(remote, VideoCanvas.RENDER_MODE_HIDDEN, 0))
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
            rtc.joinChannel(BuildConfig.AGORA_TEMP_TOKEN, channel, 0, options)
        } catch (error: Exception) {
            statusText.text = "Could not start call"
            Toast.makeText(this, error.localizedMessage ?: "Agora initialization failed", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun toggleMute() {
        muted = !muted
        engine?.muteLocalAudioStream(muted)
        findViewById<ImageView>(R.id.muteButton).setImageResource(if (muted) R.drawable.ic_mic else R.drawable.ic_mic)
        findViewById<ImageView>(R.id.muteButton).contentDescription = if (muted) "Unmute" else "Mute"
    }

    private fun toggleSpeaker() {
        speaker = !speaker
        engine?.setEnableSpeakerphone(speaker)
        findViewById<ImageView>(R.id.speakerButton).contentDescription = if (speaker) "Speaker on" else "Speaker off"
    }

    private fun toggleCamera() {
        if (!videoCall) return
        cameraEnabled = !cameraEnabled
        engine?.muteLocalVideoStream(!cameraEnabled)
        findViewById<ImageView>(R.id.cameraToggleButton).contentDescription = if (cameraEnabled) "Turn camera off" else "Turn camera on"
    }

    private fun endCall() {
        if (callEnded) return
        callEnded = true
        val durationSeconds = if (connectedAt == 0L) 0 else ((System.currentTimeMillis() - connectedAt) / 1000).toInt()
        statusText.text = "Call ended"
        engine?.leaveChannel()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val updates = mapOf<String, Any>(
            "status" to "ended",
            "durationSeconds" to durationSeconds,
            "endedAt" to mapOf(".sv" to "timestamp")
        )
        FirebaseData.callLogs.child(callId).updateChildren(updates)
        val callOwner = if (incoming) uid else peerUid
        if (callOwner != null && callOwner.isNotBlank()) FirebaseData.callRequests.child(callOwner).child(callId).child("status").setValue("ended")
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 415 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startRtc()
        else if (requestCode == 415) {
            Toast.makeText(this, "Microphone and camera permissions are required for this call", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timer)
        engine?.leaveChannel()
        engine?.let { RtcEngine.destroy() }
        engine = null
        super.onDestroy()
    }
}
