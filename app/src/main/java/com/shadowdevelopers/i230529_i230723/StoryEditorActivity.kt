package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth

/**
 * Story editor (10): preview the photo/video, type a label, choose Your story or Close friends, then share.
 * The media is uploaded, then stories/{me}/{public|closeFriends}/{storyId} is written with expiresAt = now + 24 h.
 */
class StoryEditorActivity : AppCompatActivity() {
    private val me by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    private var mediaUri: Uri? = null
    private var mediaType = MediaUploader.IMAGE
    private var audience = Stories.PUBLIC
    private var sharing = false
    private lateinit var label: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_editor)
        label = findViewById(R.id.storyLabelInput)
        mediaUri = intent.getStringExtra("media_uri")?.let(Uri::parse)
        mediaType = intent.getStringExtra("media_type") ?: MediaUploader.IMAGE
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        if (mediaUri == null || me.isBlank()) {
            Toast.makeText(this, "Choose or capture a photo or video first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        showPreview()
        ProfileCache.get(me) { AvatarBinder.bind(findViewById(R.id.storyEditorAvatar), it.name, it.photo) }

        findViewById<View>(R.id.storyTextButton).setOnClickListener {
            label.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(label, InputMethodManager.SHOW_IMPLICIT)
        }
        listOf(R.id.storyToolTag, R.id.storyToolSticker, R.id.storyToolMusic, R.id.storyToolEffects).forEach { id ->
            findViewById<View>(id).setOnClickListener { Toast.makeText(this, "Not part of this version; use the label (Aa)", Toast.LENGTH_SHORT).show() }
        }
        findViewById<View>(R.id.shareStoryButton).setOnClickListener { selectAudience(Stories.PUBLIC) }
        findViewById<View>(R.id.shareCloseFriendsButton).apply {
            // First tap selects Close friends; tapping it again (or a long press) opens the list.
            setOnClickListener {
                if (audience == Stories.CLOSE_FRIENDS) openCloseFriends() else selectAudience(Stories.CLOSE_FRIENDS)
            }
            setOnLongClickListener { openCloseFriends(); true }
        }
        findViewById<View>(R.id.storyContinueButton).setOnClickListener { share() }
        selectAudience(Stories.PUBLIC)
    }

    private fun showPreview() {
        val uri = mediaUri ?: return
        val image = findViewById<ImageView>(R.id.storyPreview)
        val video = findViewById<VideoView>(R.id.storyVideoPreview)
        if (mediaType == MediaUploader.VIDEO) {
            video.visibility = View.VISIBLE
            video.setVideoURI(uri)
            video.setOnPreparedListener { player -> player.isLooping = true; player.setVolume(0f, 0f); player.start() }
        } else {
            Glide.with(this).load(uri).centerCrop().into(image)
        }
    }

    private fun openCloseFriends() {
        startActivity(Intent(this, CloseFriendsActivity::class.java))
    }

    private fun selectAudience(value: String) {
        audience = value
        findViewById<View>(R.id.shareStoryButton).setBackgroundResource(if (value == Stories.PUBLIC) R.drawable.rounded_teal else R.drawable.rounded_dark)
        findViewById<View>(R.id.shareCloseFriendsButton).setBackgroundResource(if (value == Stories.CLOSE_FRIENDS) R.drawable.rounded_teal else R.drawable.rounded_dark)
        if (value == Stories.CLOSE_FRIENDS) {
            Stories.closeFriends.child(me).get().addOnSuccessListener { snapshot ->
                if (!snapshot.hasChildren()) {
                    Toast.makeText(this, "Your close friends list is empty, add people first", Toast.LENGTH_LONG).show()
                    openCloseFriends()
                } else {
                    Toast.makeText(this, "Sharing with ${snapshot.childrenCount} close friends. Tap again to edit the list.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun share() {
        val uri = mediaUri ?: return
        if (sharing) return
        setSharing(true)
        val status = findViewById<TextView>(R.id.storyUploadStatus)
        MediaUploader.upload(this, uri, mediaType,
            onProgress = { status.text = "Uploading $it%" },
            onSuccess = { ref -> writeStory(ref) },
            onError = { message ->
                setSharing(false)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            })
    }

    private fun writeStory(mediaRef: String) {
        val node = Stories.stories.child(me).child(audience)
        val storyId = node.push().key ?: return setSharing(false)
        val story = mapOf(
            "mediaRef" to mediaRef,
            "type" to mediaType,
            "label" to label.text.toString().trim().take(80),
            "audience" to audience,
            "createdAt" to mapOf(".sv" to "timestamp"),
            "expiresAt" to FirebaseData.now() + Stories.LIFETIME_MS
        )
        node.child(storyId).setValue(story)
            .addOnSuccessListener {
                Toast.makeText(this, if (audience == Stories.PUBLIC) "Shared to your story" else "Shared to Close friends", Toast.LENGTH_SHORT).show()
                // Back to Home (clears Camera and this editor from the back stack).
                startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            }
            .addOnFailureListener {
                setSharing(false)
                Toast.makeText(this, it.localizedMessage ?: "Could not share the story", Toast.LENGTH_LONG).show()
            }
    }

    private fun setSharing(active: Boolean) {
        sharing = active
        listOf(R.id.storyContinueButton, R.id.shareStoryButton, R.id.shareCloseFriendsButton).forEach { findViewById<View>(it).isEnabled = !active }
        findViewById<View>(R.id.storyContinueButton).alpha = if (active) 0.5f else 1f
        findViewById<TextView>(R.id.storyUploadStatus).apply {
            visibility = if (active) View.VISIBLE else View.GONE
            if (active) text = "Uploading…"
        }
    }
}
