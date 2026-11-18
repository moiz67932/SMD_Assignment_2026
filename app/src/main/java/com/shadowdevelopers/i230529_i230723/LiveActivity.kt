package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas

/**
 * Live video (new screen). Extras: "host" = true to broadcast, otherwise "host_uid" = whose live video to watch.
 *   lives/{hostUid} = { channel, hostId, hostName, startedAt }   (exists only while the host is live)
 *   lives/{hostUid}/viewers/{viewerUid} = true                    (for the "N watching" count)
 * Uses Agora in live-broadcasting mode with the same App ID and null token as calls (testing-mode project).
 * Friends are told with a notification when someone goes live; their Home row shows a LIVE tile.
 */
class LiveActivity : AppCompatActivity() {
    private val me by lazy { Backend.uid.orEmpty() }
    private val listeners = ListenerBag()
    private val handler = Handler(Looper.getMainLooper())
    private var host = false
    private var hostUid = ""
    private var channel = ""
    private var engine: RtcEngine? = null
    private var started = false
    private var ended = false

    private val events = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                if (host) {
                    findViewById<TextView>(R.id.liveStatus).visibility = View.GONE
                    publishLive()
                } else findViewById<TextView>(R.id.liveStatus).text = "Waiting for the host…"
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            // Viewer: the host's picture arrives; attach it once we know the host's Agora uid.
            if (host) return
            runOnUiThread {
                findViewById<TextView>(R.id.liveStatus).visibility = View.GONE
                val remote = SurfaceView(this@LiveActivity)
                findViewById<FrameLayout>(R.id.liveVideoContainer).apply { removeAllViews(); addView(remote) }
                engine?.setupRemoteVideo(VideoCanvas(remote, VideoCanvas.RENDER_MODE_HIDDEN, uid))
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            if (!host) runOnUiThread { finishLive("The live video ended") }
        }

        override fun onError(err: Int) {
            val message = when (err) {
                101 -> "Agora App ID is not valid. Check appId in agora.properties."
                109, 110 -> "Agora rejected the call token. Check appId and appCertificate in agora.properties, and that this phone's date and time are correct."
                else -> null
            }
            if (message != null) runOnUiThread { Toast.makeText(this@LiveActivity, message, Toast.LENGTH_LONG).show() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)
        host = intent.getBooleanExtra("host", false)
        hostUid = if (host) me else intent.getStringExtra("host_uid").orEmpty()
        if (me.isBlank() || hostUid.isBlank()) {
            finish()
            return
        }
        if (BuildConfig.AGORA_APP_ID.isBlank()) {
            Toast.makeText(this, "Add your Agora App ID to agora.properties to enable live video", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        ProfileCache.get(hostUid) { findViewById<TextView>(R.id.liveHostName).text = if (host) "You" else it.name }
        findViewById<View>(R.id.liveClose).setOnClickListener { leave() }
        findViewById<Button>(R.id.liveEndButton).apply {
            text = if (host) "End live video" else "Leave"
            setOnClickListener { leave() }
        }
        findViewById<View>(R.id.liveSwitchCamera).apply {
            visibility = if (host) View.VISIBLE else View.GONE
            setOnClickListener { engine?.switchCamera() }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        findViewById<TextView>(R.id.liveStatus).text = if (host) "Starting your live video…" else "Joining…"
        if (!host || hasPermissions()) begin()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), REQUEST_PERMISSIONS)
    }

    private fun hasPermissions() = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        .all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        if (hasPermissions()) begin()
        else finishLive("Live video needs the camera and microphone")
    }

    private fun begin() {
        if (started) return
        started = true
        if (host) {
            channel = "live_$me"
            joinChannel()
            watchViewers()
            return
        }
        // Viewer: GET lives/get.php (friends only) gives the channel; polling it counts me as watching and notices the end.
        Backend.call({ Backend.api.get("lives/get.php?host=$hostUid&join=1").optJSONObject("live") }) { result ->
            val live = result.getOrNull()
            if (live == null) {
                finishLive(if (result.isFailure) Forms.message(result.exceptionOrNull() as? Exception, "Could not join the live video") else "This live video has ended")
                return@call
            }
            channel = live.getString("channel")
            joinChannel()
            watchViewers()
        }
    }

    private fun joinChannel() {
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = BuildConfig.AGORA_APP_ID
                mEventHandler = events
            }
            val rtc = RtcEngine.create(config)
            engine = rtc
            rtc.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
            rtc.enableVideo()
            rtc.setClientRole(if (host) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE)
            if (host) {
                val local = SurfaceView(this)
                findViewById<FrameLayout>(R.id.liveVideoContainer).apply { removeAllViews(); addView(local) }
                rtc.setupLocalVideo(VideoCanvas(local, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                rtc.startPreview()
            }
            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                clientRoleType = if (host) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE
                publishMicrophoneTrack = host
                publishCameraTrack = host
                autoSubscribeAudio = !host
                autoSubscribeVideo = !host
            }
            rtc.joinChannel(AgoraToken.forChannel(BuildConfig.AGORA_APP_ID, BuildConfig.AGORA_APP_CERTIFICATE, channel), channel, 0, options)
        } catch (error: Exception) {
            finishLive(error.localizedMessage ?: "Could not start the live video")
        }
    }

    /**
     * Host: POST lives/start.php (friends get a "live" notification on the first call), repeated every 10 s as a
     * heartbeat; if the host's phone disappears, the server ends the live after 30 s.
     */
    private fun publishLive() {
        listeners.poll(10_000) { Backend.background { Backend.api.post("lives/start.php", org.json.JSONObject().put("channel", channel)) } }
    }

    /** Both sides: viewer count, and for viewers, the end of the broadcast. */
    private fun watchViewers() {
        listeners.poll(5_000) {
            Backend.call({ Backend.api.get("lives/get.php?host=$hostUid" + if (host) "" else "&join=1").optJSONObject("live") }) { result ->
                if (result.isFailure) return@call
                val live = result.getOrNull()
                if (live == null) { if (!host && !ended) finishLive("The live video ended"); return@call }
                findViewById<TextView>(R.id.liveViewers).text = "${live.optInt("viewers")} watching"
            }
        }
    }

    private fun leave() {
        if (ended) return
        ended = true
        if (host) Backend.background { Backend.api.post("lives/end.php", org.json.JSONObject()) }
        finish()
    }

    /** Shows why the live video stopped, then closes. */
    private fun finishLive(message: String) {
        if (ended) return
        findViewById<TextView>(R.id.liveStatus).apply { visibility = View.VISIBLE; text = message }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        handler.postDelayed({ leave() }, 1500)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        listeners.removeAll()
        if (!ended) {
            ended = true
            if (host) Backend.background { Backend.api.post("lives/end.php", org.json.JSONObject()) }
        }
        engine?.leaveChannel()
        if (engine != null) RtcEngine.destroy()
        engine = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 818
    }
}
