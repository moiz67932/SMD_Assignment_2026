package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
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

/**
 * Story editor (10): preview the photo/video, type a label, choose Your story or Close friends, then share.
 * The media is uploaded, then stories/{me}/{public|closeFriends}/{storyId} is written with expiresAt = now + 24 h.
 */
class StoryEditorActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_AUDIO = 819
    }

    private val me by lazy { Backend.uid.orEmpty() }
    private var mediaUri: Uri? = null
    private var mediaType = MediaUploader.IMAGE
    private var audience = Stories.PUBLIC
    private var sharing = false
    private var boomerang = false
    private var effect = Effects.NORMAL
    private var audioUri: Uri? = null
    private var audioTitle = ""
    private val tagged = linkedSetOf<String>()
    private lateinit var label: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_editor)
        label = findViewById(R.id.storyLabelInput)
        mediaUri = intent.getStringExtra("media_uri")?.let(Uri::parse)
        mediaType = intent.getStringExtra("media_type") ?: MediaUploader.IMAGE
        boomerang = intent.getBooleanExtra("boomerang", false)
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
        findViewById<View>(R.id.storyToolTag).setOnClickListener { chooseTags() }
        findViewById<View>(R.id.storyToolSticker).setOnClickListener {
            EmojiPicker.show(this) { emoji ->
                label.setText(label.text.toString() + emoji)
                label.setSelection(label.text.length)
            }
        }
        findViewById<View>(R.id.storyToolMusic).setOnClickListener { chooseMusic() }
        findViewById<View>(R.id.storyToolEffects).setOnClickListener { chooseEffect() }
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

    // ----- Tools: tag a friend, music, effects -----

    /** Tag: choose friends; their names are added to the label and they are notified when the story is shared. */
    private fun chooseTags() {
        FirebaseData.friends.child(me).get().addOnSuccessListener { snapshot ->
            val friends = snapshot.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNull { it.key }
            if (friends.isEmpty()) {
                Toast.makeText(this, "Add friends first to tag them", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val names = arrayOfNulls<String>(friends.size)
            var left = friends.size
            friends.forEachIndexed { index, uid ->
                ProfileCache.get(uid) { profile ->
                    names[index] = profile.name
                    left--
                    if (left == 0 && !isFinishing) showTagDialog(friends, names.map { it.orEmpty() })
                }
            }
        }.addOnFailureListener { Toast.makeText(this, Forms.message(it, "Could not load your friends"), Toast.LENGTH_LONG).show() }
    }

    private fun showTagDialog(friends: List<String>, names: List<String>) {
        val checked = BooleanArray(friends.size) { friends[it] in tagged }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Tag friends")
            .setMultiChoiceItems(names.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Done") { _, _ ->
                friends.forEachIndexed { index, uid ->
                    if (checked[index] && tagged.add(uid)) label.setText((label.text.toString() + " @" + names[index]).trim())
                    if (!checked[index]) tagged.remove(uid)
                }
                label.setSelection(label.text.length)
                markTool(R.id.storyToolTag, tagged.isNotEmpty())
            }
            .setNegativeButton("Cancel", null).show()
    }

    /** Music: pick a short audio file (up to 2 MB) from the phone; it plays while the story is watched. */
    private fun chooseMusic() {
        val options = if (audioUri == null) arrayOf("Choose a song from your phone") else arrayOf("Change music", "Remove music")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(if (audioUri == null) "Add music" else "♪ $audioTitle")
            .setItems(options) { _, which ->
                if (audioUri != null && which == 1) {
                    audioUri = null
                    audioTitle = ""
                    markTool(R.id.storyToolMusic, false)
                } else {
                    ExternalLaunch.mark()
                    startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("audio/*"), REQUEST_AUDIO)
                }
            }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != REQUEST_AUDIO || resultCode != RESULT_OK) return
        var name = "Music"
        var size = -1L
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0)?.substringBeforeLast('.') ?: name
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
        if (size > 2L * 1024 * 1024) {
            Toast.makeText(this, "That song is too big (max 2 MB). Choose a shorter clip.", Toast.LENGTH_LONG).show()
            return
        }
        audioUri = uri
        audioTitle = name.take(40)
        markTool(R.id.storyToolMusic, true)
        Toast.makeText(this, "♪ $audioTitle added", Toast.LENGTH_SHORT).show()
    }

    /** Effects: a filter on the picture (shown now, saved into the photo when shared). */
    private fun chooseEffect() {
        if (mediaType != MediaUploader.IMAGE) {
            Toast.makeText(this, "Filters are for photos. Your video stays as recorded.", Toast.LENGTH_SHORT).show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Filter")
            .setItems(Effects.NAMES.toTypedArray()) { _, which ->
                effect = Effects.NAMES[which]
                findViewById<ImageView>(R.id.storyPreview).colorFilter = Effects.colorFilter(effect)
                markTool(R.id.storyToolEffects, effect != Effects.NORMAL)
            }.show()
    }

    /** A tool button turns white when it has something applied. */
    private fun markTool(id: Int, on: Boolean) {
        findViewById<ImageView>(id).apply {
            setBackgroundResource(if (on) R.drawable.circle_white else R.drawable.circle_dark)
            setColorFilter(if (on) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }

    // ----- Sharing: filter -> music -> picture/video -> story -----

    private fun share() {
        val source = mediaUri ?: return
        if (sharing) return
        setSharing(true)
        if (mediaType == MediaUploader.IMAGE && effect != Effects.NORMAL) {
            findViewById<TextView>(R.id.storyUploadStatus).text = "Applying filter…"
            Thread {
                val baked = Effects.bake(applicationContext, source, effect, cacheDir)
                runOnUiThread { uploadAudioThen(baked?.let { Uri.fromFile(it) } ?: source) }
            }.start()
        } else uploadAudioThen(source)
    }

    private fun uploadAudioThen(picture: Uri) {
        val status = findViewById<TextView>(R.id.storyUploadStatus)
        val music = audioUri
        if (music == null) {
            uploadMedia(picture, "")
            return
        }
        MediaUploader.upload(this, music, MediaUploader.AUDIO,
            onProgress = { status.text = "Saving music $it%" },
            onSuccess = { audioRef -> uploadMedia(picture, audioRef) },
            onError = { message -> setSharing(false); Toast.makeText(this, message, Toast.LENGTH_LONG).show() })
    }

    private fun uploadMedia(picture: Uri, audioRef: String) {
        val status = findViewById<TextView>(R.id.storyUploadStatus)
        MediaUploader.upload(this, picture, mediaType,
            onProgress = { status.text = "Uploading $it%" },
            onSuccess = { ref -> writeStory(ref, audioRef) },
            onError = { message ->
                setSharing(false)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            })
    }

    private fun writeStory(mediaRef: String, audioRef: String) {
        val node = Stories.stories.child(me).child(audience)
        val storyId = node.push().key ?: return setSharing(false)
        val story = mutableMapOf<String, Any>(
            "mediaRef" to mediaRef,
            "type" to mediaType,
            "label" to label.text.toString().trim().take(80),
            "audience" to audience,
            "createdAt" to mapOf(".sv" to "timestamp"),
            "expiresAt" to FirebaseData.now() + Stories.LIFETIME_MS
        )
        if (boomerang) story["loop"] = true
        if (audioRef.isNotBlank()) {
            story["audioRef"] = audioRef
            story["audioTitle"] = audioTitle
        }
        if (tagged.isNotEmpty()) story["tags"] = tagged.toList()
        node.child(storyId).setValue(story)
            .addOnSuccessListener {
                notifyTagged()
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

    /** Tagged friends get a notification. */
    private fun notifyTagged() {
        if (tagged.isEmpty()) return
        val updates = mutableMapOf<String, Any>()
        tagged.forEach { friend ->
            val id = FirebaseData.database.getReference("notifications").child(friend).push().key ?: return@forEach
            updates["notifications/$friend/$id"] = mapOf("senderId" to me, "type" to "tag", "body" to "tagged you in a story", "createdAt" to mapOf(".sv" to "timestamp"), "read" to false)
        }
        FirebaseData.database.reference.updateChildren(updates)
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
