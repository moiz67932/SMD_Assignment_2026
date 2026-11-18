package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
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
    private val tagged = linkedMapOf<String, String>() // friend uid -> name
    private var nameShown = ""
    private var feeling = ""
    private var location = ""
    private var event = ""
    private lateinit var textInput: EditText
    private lateinit var mediaAdapter: SelectedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)
        textInput = findViewById(R.id.postTextInput)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        Backend.uid?.let { uid ->
            ProfileCache.get(uid) { profile ->
                nameShown = profile.name
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
        findViewById<View>(R.id.tagPeopleButton).setOnClickListener {
            FriendPicker.multi(this, "Tag friends", tagged.keys) { picked ->
                tagged.clear()
                picked.forEach { tagged[it.first] = it.second }
                showDetails()
            }
        }
        findViewById<View>(R.id.feelingButton).setOnClickListener { chooseFeeling() }
        findViewById<View>(R.id.checkInButton).setOnClickListener { askText("Check in", "Where are you?", location) { location = it } }
        findViewById<View>(R.id.createEventButton).setOnClickListener { askText("Create event", "Event name and date, e.g. Birthday party, 5 Dec", event) { event = it } }
        // Live video: go live now (friends see a LIVE tile on Home).
        findViewById<View>(R.id.liveVideoButton).setOnClickListener {
            startActivity(Intent(this, LiveActivity::class.java).putExtra("host", true))
        }
        setupColours()
        findViewById<View>(R.id.publishPostButton).setOnClickListener { publish() }
        showSelection()
    }

    /** The name line of the card says what was added (for example "with Ali · at Lahore"). */
    private fun showDetails() {
        val parts = listOfNotNull(
            feeling.takeIf { it.isNotBlank() }?.let { "feeling $it" },
            location.takeIf { it.isNotBlank() }?.let { "at $it" },
            tagged.values.takeIf { it.isNotEmpty() }?.let { "with ${it.joinToString(", ")}" },
            event.takeIf { it.isNotBlank() }?.let { "event: $it" }
        )
        findViewById<TextView>(R.id.createPostName).text = (nameShown + if (parts.isEmpty()) "" else " — " + parts.joinToString(" · "))
    }

    private fun chooseFeeling() {
        val feelings = arrayOf("happy 😊", "excited 🤩", "loved ❤️", "blessed 🙏", "grateful 🤗", "cool 😎", "tired 😴", "sad 😢", "angry 😡", "silly 🤪", "None")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("How are you feeling?")
            .setItems(feelings) { _, which ->
                feeling = if (which == feelings.lastIndex) "" else feelings[which]
                showDetails()
            }.show()
    }

    /** One-line text question in a dialog (check-in place, event name). An empty answer clears it. */
    private fun askText(title: String, hint: String, current: String, onAnswer: (String) -> Unit) {
        val field = EditText(this).apply {
            this.hint = hint
            setText(current)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(60))
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setView(field)
            .setPositiveButton("Done") { _, _ -> onAnswer(field.text.toString().trim()); showDetails() }
            .setNegativeButton("Cancel", null).show()
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

    /**
     * Saves the post on the phone and queues it (posts/create.php, multipart with images[]). It shows on Home straight
     * away ("Pending" while offline) and is uploaded in order when the internet is available. The client post UUID
     * means a retried upload never creates two posts.
     */
    private fun publish() {
        if (publishing) return
        if (Backend.uid == null) {
            Toast.makeText(this, "Log in to create a post", Toast.LENGTH_SHORT).show()
            return
        }
        val text = textInput.text.toString().trim()
        if (text.isBlank() && selected.isEmpty()) {
            Toast.makeText(this, "Write something or add a photo", Toast.LENGTH_SHORT).show()
            return
        }
        setPublishing(true)
        findViewById<TextView>(R.id.postUploadStatus).text = if (selected.isEmpty()) "Posting…" else "Preparing ${selected.size} photo${if (selected.size == 1) "" else "s"}…"
        val details = org.json.JSONObject()
        if (selected.isEmpty() && background.isNotBlank()) details.put("background", background)
        if (feeling.isNotBlank()) details.put("feeling", feeling)
        if (location.isNotBlank()) details.put("location", location)
        if (event.isNotBlank()) details.put("event", event)
        if (tagged.isNotEmpty()) details.put("tags", org.json.JSONObject(tagged.toMap()))
        val photos = selected.toList()
        Backend.call({ PostRepo.create(this, text, photos, details) }) { result ->
            result.onSuccess {
                if (!Backend.online()) Toast.makeText(this, "You're offline. Your post will upload when you reconnect.", Toast.LENGTH_LONG).show()
                finish()
            }.onFailure {
                setPublishing(false)
                Toast.makeText(this, Forms.message(it as? Exception, "Could not publish post"), Toast.LENGTH_LONG).show()
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
