package com.shadowdevelopers.i230529_i230723

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.util.concurrent.Executors

/** One photo or video on the phone. */
data class GalleryItem(val uri: Uri, val isVideo: Boolean, val durationMs: Long)

/**
 * Photo picker (08). Shows the phone's real gallery (MediaStore) newest first. Tap a tile to preview it;
 * with SELECT MULTIPLE on, taps add numbered selections. Next returns the chosen URIs to the caller:
 *   extras in:  "images_only" (posts allow photos only), "single" (stories, chat, comments: one item)
 *   extras out: "media_uris" (ArrayList<String>) and "media_types" ("image" / "video", same order)
 * If gallery permission is refused, the system file picker is used instead.
 */
class PhotoPickerActivity : AppCompatActivity() {
    private val items = mutableListOf<GalleryItem>()
    private val selected = mutableListOf<GalleryItem>()
    private var multiple = false
    private var filter = FILTER_ALL
    private var preview: GalleryItem? = null
    private val imagesOnly by lazy { intent.getBooleanExtra("images_only", false) }
    private val singleOnly by lazy { intent.getBooleanExtra("single", false) }
    private lateinit var adapter: TileAdapter
    private val loader = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_picker)
        multiple = !singleOnly && intent.getBooleanExtra("start_multiple", true)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.photoPickerNext).setOnClickListener { finishWithSelection() }
        findViewById<View>(R.id.photoPickerBrowse).setOnClickListener { openSystemPicker() }
        findViewById<View>(R.id.photoPickerFullscreen).setOnClickListener {
            val item = preview ?: return@setOnClickListener
            startActivity(Intent(this, MediaViewerActivity::class.java)
                .putStringArrayListExtra("media_refs", arrayListOf(item.uri.toString()))
                .putStringArrayListExtra("media_types", arrayListOf(if (item.isVideo) MediaUploader.VIDEO else MediaUploader.IMAGE)))
        }
        val selectMultiple = findViewById<View>(R.id.selectMultipleButton)
        selectMultiple.visibility = if (singleOnly) View.GONE else View.VISIBLE
        selectMultiple.setOnClickListener {
            multiple = !multiple
            if (!multiple && selected.size > 1) selected.retainAll(listOf(selected.last()))
            refresh()
        }
        findViewById<View>(R.id.pickerTabAll).setOnClickListener { setFilter(FILTER_ALL) }
        findViewById<View>(R.id.pickerTabPhoto).setOnClickListener { setFilter(FILTER_PHOTO) }
        findViewById<View>(R.id.pickerTabVideo).setOnClickListener { setFilter(FILTER_VIDEO) }
        if (imagesOnly) findViewById<View>(R.id.pickerTabVideo).visibility = View.INVISIBLE

        adapter = TileAdapter()
        findViewById<RecyclerView>(R.id.photoPickerGrid).apply {
            layoutManager = GridLayoutManager(this@PhotoPickerActivity, 4)
            adapter = this@PhotoPickerActivity.adapter
        }
        refresh()
        if (hasGalleryPermission()) loadGallery() else requestPermissions(galleryPermissions(), REQUEST_PERMISSION)
    }

    private fun galleryPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** On Android 14 "Allow limited access" grants only READ_MEDIA_VISUAL_USER_SELECTED; that still counts. */
    private fun hasGalleryPermission() = galleryPermissions().any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSION) return
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) loadGallery()
        else {
            showMessage("Kinnect needs access to your photos to show them here.", showBrowse = true)
            openSystemPicker()
        }
    }

    /** Reads the newest 300 photos/videos on a background thread. */
    private fun loadGallery() {
        showMessage("Loading your photos…", showBrowse = false)
        loader.execute {
            val found = mutableListOf<GalleryItem>()
            val projection = mutableListOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
            if (Build.VERSION.SDK_INT >= 29) projection.add(MediaStore.MediaColumns.DURATION)
            val selection = if (imagesOnly) "${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
            else "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE},${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            try {
                contentResolver.query(MediaStore.Files.getContentUri("external"), projection.toTypedArray(), selection, null,
                    "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { cursor ->
                    while (cursor.moveToNext() && found.size < 300) {
                        val id = cursor.getLong(0)
                        val isVideo = cursor.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        val duration = if (Build.VERSION.SDK_INT >= 29 && isVideo) cursor.getLong(2) else 0L
                        found.add(GalleryItem(ContentUris.withAppendedId(base, id), isVideo, duration))
                    }
                }
            } catch (error: Exception) {
                main.post { showMessage(error.localizedMessage ?: "Could not read your gallery", showBrowse = true) }
                return@execute
            }
            main.post {
                if (isDestroyed) return@post
                items.clear()
                items.addAll(found)
                if (preview == null) preview = items.firstOrNull()
                refresh()
            }
        }
    }

    private fun setFilter(value: Int) {
        filter = value
        refresh()
    }

    private fun visibleItems() = items.filter {
        when (filter) {
            FILTER_PHOTO -> !it.isVideo
            FILTER_VIDEO -> it.isVideo
            else -> true
        }
    }

    private fun refresh() {
        val visible = visibleItems()
        adapter.setItems(visible)
        if (items.isEmpty()) {
            if (findViewById<View>(R.id.photoPickerMessage).visibility != View.VISIBLE) showMessage("No photos yet", showBrowse = true)
        } else if (visible.isEmpty()) {
            showMessage(if (filter == FILTER_VIDEO) "No videos" else "No photos", showBrowse = false)
        } else {
            findViewById<View>(R.id.photoPickerMessage).visibility = View.GONE
        }
        findViewById<TextView>(R.id.photoPickerNext).text = if (selected.isEmpty()) "Next" else "Next (${selected.size})"
        findViewById<TextView>(R.id.selectMultipleLabel).text = if (multiple) "SELECT ONE" else "SELECT MULTIPLE"
        val previewView = findViewById<ImageView>(R.id.photoPickerPreview)
        preview?.let { Glide.with(this).load(it.uri).centerCrop().into(previewView) }
        findViewById<TextView>(R.id.pickerTabAllLabel).setTextColor(ContextCompat.getColor(this, if (filter == FILTER_ALL) R.color.primary else R.color.text_secondary))
        findViewById<View>(R.id.pickerTabAllLine).visibility = if (filter == FILTER_ALL) View.VISIBLE else View.INVISIBLE
        findViewById<TextView>(R.id.pickerTabPhoto).setTextColor(ContextCompat.getColor(this, if (filter == FILTER_PHOTO) R.color.primary else R.color.text_secondary))
        findViewById<TextView>(R.id.pickerTabVideo).setTextColor(ContextCompat.getColor(this, if (filter == FILTER_VIDEO) R.color.primary else R.color.text_secondary))
    }

    private fun showMessage(text: String, showBrowse: Boolean) {
        findViewById<View>(R.id.photoPickerMessage).visibility = View.VISIBLE
        findViewById<TextView>(R.id.photoPickerMessageText).text = text
        findViewById<View>(R.id.photoPickerBrowse).visibility = if (showBrowse) View.VISIBLE else View.GONE
    }

    private fun toggle(item: GalleryItem) {
        preview = item
        when {
            !multiple -> { selected.clear(); selected.add(item) }
            item in selected -> selected.remove(item)
            selected.size >= MAX_ITEMS -> Toast.makeText(this, "You can choose up to $MAX_ITEMS", Toast.LENGTH_SHORT).show()
            else -> selected.add(item)
        }
        refresh()
    }

    private fun finishWithSelection() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select at least one ${if (imagesOnly) "photo" else "photo or video"}", Toast.LENGTH_SHORT).show()
            return
        }
        setResult(RESULT_OK, Intent()
            .putStringArrayListExtra("media_uris", ArrayList(selected.map { it.uri.toString() }))
            .putStringArrayListExtra("media_types", ArrayList(selected.map { if (it.isVideo) MediaUploader.VIDEO else MediaUploader.IMAGE })))
        finish()
    }

    /** Fallback when gallery permission is refused: the system document picker (no permission needed). */
    private fun openSystemPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (imagesOnly) "image/*" else "*/*"
            if (!imagesOnly) putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, !singleOnly)
        }
        ExternalLaunch.mark()
        startActivityForResult(intent, REQUEST_DOCUMENTS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_DOCUMENTS || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip -> for (index in 0 until clip.itemCount) uris.add(clip.getItemAt(index).uri) } ?: data.data?.let { uris.add(it) }
        selected.clear()
        uris.take(if (singleOnly) 1 else MAX_ITEMS).forEach { uri ->
            val isVideo = contentResolver.getType(uri)?.startsWith("video") == true
            selected.add(GalleryItem(uri, isVideo, 0L))
        }
        if (selected.isNotEmpty()) finishWithSelection()
    }

    override fun onDestroy() {
        loader.shutdownNow()
        super.onDestroy()
    }

    private inner class TileAdapter : RecyclerView.Adapter<TileAdapter.TileHolder>() {
        private val tiles = mutableListOf<GalleryItem>()

        fun setItems(list: List<GalleryItem>) {
            tiles.clear()
            tiles.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_tile, parent, false)
            val width = if (parent.width > 0) parent.width else parent.resources.displayMetrics.widthPixels
            view.layoutParams.height = width / 4 // square tiles
            return TileHolder(view)
        }

        override fun onBindViewHolder(holder: TileHolder, position: Int) = holder.bind(tiles[position])
        override fun getItemCount() = tiles.size

        inner class TileHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val image = view.findViewById<ImageView>(R.id.mediaTileImage)
            private val number = view.findViewById<TextView>(R.id.mediaTileNumber)
            private val duration = view.findViewById<TextView>(R.id.mediaTileDuration)

            fun bind(item: GalleryItem) {
                Glide.with(image).load(item.uri).centerCrop().placeholder(R.drawable.media_placeholder).into(image)
                val order = selected.indexOf(item)
                itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, if (order >= 0) R.color.primary else R.color.white))
                number.setBackgroundResource(if (order >= 0) R.drawable.selection_number else R.drawable.selection_outline)
                number.text = if (order >= 0 && multiple) (order + 1).toString() else ""
                duration.visibility = if (item.isVideo) View.VISIBLE else View.GONE
                duration.text = TimeFormat.duration((item.durationMs / 1000).toInt())
                itemView.setOnClickListener { toggle(item) }
            }
        }
    }

    companion object {
        private const val REQUEST_PERMISSION = 820
        private const val REQUEST_DOCUMENTS = 821
        private const val MAX_ITEMS = 10
        private const val FILTER_ALL = 0
        private const val FILTER_PHOTO = 1
        private const val FILTER_VIDEO = 2
    }
}
