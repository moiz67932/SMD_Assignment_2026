package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth

/**
 * Create post (07). Text and/or one or more photos (Photo picker 08 or Camera 09). Photos are uploaded one
 * after another with progress, then posts/{postId} is written with the ordered list of image URLs.
 * A text-only post can have a colour background (the Aa / colour row in the design).
 */
class CreatePostActivity : AppCompatActivity() {
    private val selected = mutableListOf<Uri>()
    private var background = ""
    private var publishing = false
    private lateinit var textInput: EditText
    private lateinit var mediaAdapter: SelectedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)
        textInput = findViewById(R.id.postTextInput)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            ProfileCache.get(uid) { profile ->
                findViewById<TextView>(R.id.createPostName).text = profile.name
                AvatarBinder.bind(findViewById(R.id.createPostAvatar), profile.name, profile.photo)
            }
        }
        mediaAdapter = SelectedAdapter()
        findViewById<RecyclerView>(R.id.postMediaList).apply {
            layoutManager = LinearLayoutManager(this@CreatePostActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = mediaAdapter
        }
        if (intent.getBooleanExtra("open_picker", false) && savedInstanceState == null) openPicker()
        // Photos handed over by the Camera screen in POST mode.
        intent.getStringArrayListExtra("media_uris")?.forEach { if (selected.size < MAX_IMAGES) selected.add(Uri.parse(it)) }
        findViewById<View>(R.id.photoButton).setOnClickListener { openPicker() }
        findViewById<View>(R.id.cameraButton).setOnClickListener {
            startActivityForResult(Intent(this, CameraActivity::class.java).putExtra("for_post", true), REQUEST_CAMERA)
        }
        listOf(R.id.tagPeopleButton, R.id.feelingButton, R.id.checkInButton, R.id.liveVideoButton, R.id.createEventButton).forEach { id ->
            findViewById<View>(id).setOnClickListener { Toast.makeText(this, "This option is not part of this version", Toast.LENGTH_SHORT).show() }
        }
        setupColours()
        findViewById<View>(R.id.publishPostButton).setOnClickListener { publish() }
        showSelection()
    }

    private fun openPicker() {
        startActivityForResult(Intent(this, PhotoPickerActivity::class.java).putExtra("images_only", true), REQUEST_PICKER)
    }

    /** Aa = no colour; the seven swatches set the card colour of a text-only post. */
    private fun setupColours() {
        val swatches = mapOf(
            R.id.postColor0 to "", R.id.postColor1 to "#0B5F63", R.id.postColor2 to "#C44C29", R.id.postColor3 to "#4E5898",
            R.id.postColor4 to "#2F7359", R.id.postColor5 to "#D18D08", R.id.postColor6 to "#765184", R.id.postColor7 to "#202124"
        )
        swatches.forEach { (id, colour) ->
            findViewById<View>(id).setOnClickListener {
                if (selected.isNotEmpty() && colour.isNotEmpty()) {
                    Toast.makeText(this, "Colour backgrounds are for text-only posts", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                background = colour
                swatches.keys.forEach { other -> findViewById<View>(other).alpha = if (other == id) 1f else 0.45f }
                showBackground()
            }
        }
    }

    private fun showBackground() {
        if (background.isBlank()) {
            textInput.setBackgroundColor(Color.WHITE)
            textInput.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            textInput.gravity = Gravity.TOP or Gravity.START
        } else {
            textInput.setBackgroundColor(Color.parseColor(background))
            textInput.setTextColor(Color.WHITE)
            textInput.gravity = Gravity.CENTER
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val uris = when (requestCode) {
            REQUEST_PICKER -> data.getStringArrayListExtra("media_uris").orEmpty().map(Uri::parse)
            REQUEST_CAMERA -> listOfNotNull(data.getStringExtra("media_uri")?.let(Uri::parse))
            else -> emptyList()
        }
        uris.forEach { if (it !in selected && selected.size < MAX_IMAGES) selected.add(it) }
        if (selected.isNotEmpty() && background.isNotBlank()) {
            background = ""
            showBackground()
        }
        showSelection()
    }

    private fun showSelection() {
        findViewById<View>(R.id.postMediaList).visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        mediaAdapter.notifyDataSetChanged()
    }

    private fun publish() {
        if (publishing) return
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Toast.makeText(this, "Log in to create a post", Toast.LENGTH_SHORT).show()
            return
        }
        val text = textInput.text.toString().trim()
        if (text.isBlank() && selected.isEmpty()) {
            Toast.makeText(this, "Write something or add a photo", Toast.LENGTH_SHORT).show()
            return
        }
        setPublishing(true)
        uploadAll(0, mutableListOf()) { refs -> writePost(user.uid, text, refs) }
    }

    /** Uploads the photos one at a time so the progress line can say "Uploading photo 2 of 5 · 40%". */
    private fun uploadAll(index: Int, refs: MutableList<String>, onDone: (List<String>) -> Unit) {
        if (index >= selected.size) {
            onDone(refs)
            return
        }
        val status = findViewById<TextView>(R.id.postUploadStatus)
        MediaUploader.upload(this, selected[index], MediaUploader.IMAGE,
            onProgress = { status.text = "Uploading photo ${index + 1} of ${selected.size} · $it%" },
            onSuccess = { ref ->
                refs.add(ref)
                uploadAll(index + 1, refs, onDone)
            },
            onError = { message ->
                setPublishing(false)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            })
    }

    private fun writePost(uid: String, text: String, images: List<String>) {
        val postId = FirebaseData.posts.push().key ?: return setPublishing(false)
        ProfileCache.get(uid) { profile ->
            val post = mapOf<String, Any>(
                "postId" to postId,
                "authorId" to uid,
                "authorName" to profile.name,
                "text" to text,
                "images" to images,
                "imageCount" to images.size,
                "background" to if (images.isEmpty()) background else "",
                "createdAt" to mapOf(".sv" to "timestamp")
            )
            FirebaseData.posts.child(postId).setValue(post)
                .addOnSuccessListener { finish() }
                .addOnFailureListener {
                    setPublishing(false)
                    Toast.makeText(this, it.localizedMessage ?: "Could not publish post", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun setPublishing(active: Boolean) {
        publishing = active
        findViewById<View>(R.id.publishPostButton).apply { isEnabled = !active; alpha = if (active) 0.5f else 1f }
        findViewById<TextView>(R.id.postUploadStatus).apply {
            visibility = if (active) View.VISIBLE else View.GONE
            if (active) text = if (selected.isEmpty()) "Posting…" else "Uploading photo 1 of ${selected.size}"
        }
    }

    private inner class SelectedAdapter : RecyclerView.Adapter<SelectedAdapter.Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_selected_media, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(selected[position])
        override fun getItemCount() = selected.size

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(uri: Uri) {
                Glide.with(itemView).load(uri).centerCrop().into(itemView.findViewById<ImageView>(R.id.selectedMediaImage))
                itemView.findViewById<View>(R.id.selectedMediaRemove).setOnClickListener {
                    if (publishing) return@setOnClickListener
                    selected.remove(uri)
                    showSelection()
                }
            }
        }
    }

    companion object {
        private const val REQUEST_PICKER = 811
        private const val REQUEST_CAMERA = 814
        private const val MAX_IMAGES = 10
    }
}
