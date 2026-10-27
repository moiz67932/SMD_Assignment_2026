package com.shadowdevelopers.i230529_i230723

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import java.io.File

/**
 * Camera (09). Kinnect uses the phone's own camera app (no camera library):
 * tap the shutter for a photo, hold it for a video (max 30 s), or pick from the gallery.
 * STORY sends the result to the Story editor (10); POST sends it to Create post (07).
 * When Create post opens this screen ("for_post"), the photo is simply returned to it.
 */
class CameraActivity : AppCompatActivity() {
    private var captureUri: Uri? = null
    private var captureIsVideo = false
    private val forPost by lazy { intent.getBooleanExtra("for_post", false) }
    private var postMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        postMode = forPost
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.shutterButton).apply {
            setOnClickListener { capture(video = false) }
            setOnLongClickListener {
                if (postMode) Toast.makeText(this@CameraActivity, "Posts can only have photos", Toast.LENGTH_SHORT).show()
                else capture(video = true)
                true
            }
        }
        findViewById<View>(R.id.galleryButton).setOnClickListener {
            startActivityForResult(Intent(this, PhotoPickerActivity::class.java)
                .putExtra("single", !postMode)
                .putExtra("images_only", postMode), REQUEST_GALLERY)
        }
        listOf(R.id.cameraSettingsButton, R.id.cameraFlashButton, R.id.cameraSwitchButton, R.id.cameraEffectsButton, R.id.modeText, R.id.modeLive, R.id.modeBoomerang).forEach { id ->
            findViewById<View>(id).setOnClickListener {
                Toast.makeText(this, "Use the camera app's own controls; this option is not part of this version", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.modeStory).setOnClickListener { if (!forPost) setMode(post = false) }
        findViewById<View>(R.id.modePost).setOnClickListener { setMode(post = true) }
        listOf(R.id.modeText, R.id.modeLive, R.id.modeBoomerang).forEach { findViewById<View>(it).alpha = 0.45f }
        setMode(postMode)
        showLatestPhoto()
    }

    private fun setMode(post: Boolean) {
        postMode = post
        // (label, underline, selected) for the two working modes.
        listOf(Triple(R.id.modeStoryLabel, R.id.modeStoryLine, !post), Triple(R.id.modePostLabel, R.id.modePostLine, post)).forEach { (labelId, lineId, selected) ->
            val label = findViewById<TextView>(labelId)
            label.setTextColor(if (selected) ContextCompat.getColor(this, R.color.white) else 0xCCFFFFFF.toInt())
            label.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            findViewById<View>(lineId).visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }
        findViewById<TextView>(R.id.cameraHint).text =
            if (post) "Tap the shutter to take a photo for your post" else "Tap the shutter for a photo\nHold it for a video (up to 30 s)"
    }

    /** The gallery button shows the newest photo on the phone, when gallery permission was already given. */
    private fun showLatestPhoto() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) return
        try {
            contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
                    val button = findViewById<ImageView>(R.id.galleryButton)
                    button.setPadding(0, 0, 0, 0)
                    button.imageTintList = null
                    Glide.with(this).load(uri).circleCrop().into(button)
                }
            }
        } catch (_: Exception) {
            // Keep the plain gallery icon.
        }
    }

    /** Photo: ACTION_IMAGE_CAPTURE. Video: ACTION_VIDEO_CAPTURE capped at 30 s. Both write to a cache file we own. */
    private fun capture(video: Boolean) {
        val file = try {
            File.createTempFile(if (video) "kinnect_video_" else "kinnect_photo_", if (video) ".mp4" else ".jpg", cacheDir)
        } catch (error: Exception) {
            Toast.makeText(this, error.localizedMessage ?: "Could not create a file", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        captureUri = uri
        captureIsVideo = video
        val intent = Intent(if (video) MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            if (video) {
                putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30)
                putExtra(MediaStore.EXTRA_SIZE_LIMIT, 38L * 1024 * 1024)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "No camera app is available", Toast.LENGTH_SHORT).show()
            return
        }
        ExternalLaunch.mark()
        startActivityForResult(intent, REQUEST_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val (uri, type) = when (requestCode) {
            REQUEST_CAPTURE -> (captureUri?.toString() ?: return) to (if (captureIsVideo) MediaUploader.VIDEO else MediaUploader.IMAGE)
            REQUEST_GALLERY -> {
                val uris = data?.getStringArrayListExtra("media_uris").orEmpty()
                if (uris.isEmpty()) return
                if (postMode) {
                    deliverToPost(uris)
                    return
                }
                uris.first() to (data?.getStringArrayListExtra("media_types")?.firstOrNull() ?: MediaUploader.IMAGE)
            }
            else -> return
        }
        if (postMode) deliverToPost(arrayListOf(uri))
        else startActivity(Intent(this, StoryEditorActivity::class.java).putExtra("media_uri", uri).putExtra("media_type", type))
    }

    private fun deliverToPost(uris: List<String>) {
        if (forPost) {
            setResult(RESULT_OK, Intent().putExtra("media_uri", uris.first()).putStringArrayListExtra("media_uris", ArrayList(uris)))
        } else {
            startActivity(Intent(this, CreatePostActivity::class.java).putStringArrayListExtra("media_uris", ArrayList(uris)))
        }
        finish()
    }

    companion object {
        private const val REQUEST_CAPTURE = 813
        private const val REQUEST_GALLERY = 812
    }
}
