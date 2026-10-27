package com.shadowdevelopers.i230529_i230723

import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

/**
 * Full-screen viewer for post photos, chat photos and picker previews (a new screen in the Kinnect style).
 * Extras: "media_refs" (URLs or content URIs), optional "media_types" (image/video) and "index".
 * Tap the right half for the next item, the left half for the previous one.
 */
class MediaViewerActivity : AppCompatActivity() {
    private lateinit var refs: List<String>
    private lateinit var types: List<String>
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_viewer)
        refs = intent.getStringArrayListExtra("media_refs").orEmpty()
        types = intent.getStringArrayListExtra("media_types").orEmpty()
        index = intent.getIntExtra("index", 0).coerceIn(0, (refs.size - 1).coerceAtLeast(0))
        if (refs.isEmpty()) {
            finish()
            return
        }
        findViewById<View>(R.id.viewerClose).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.viewerImage).setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (event.x > view.width / 2f) show(index + 1) else show(index - 1)
                view.performClick()
            }
            true
        }
        show(index)
    }

    private fun show(position: Int) {
        if (position !in refs.indices) return
        index = position
        val image = findViewById<ImageView>(R.id.viewerImage)
        val video = findViewById<VideoView>(R.id.viewerVideo)
        val isVideo = types.getOrNull(index) == MediaUploader.VIDEO
        findViewById<TextView>(R.id.viewerCounter).apply {
            text = "${index + 1} / ${refs.size}"
            visibility = if (refs.size > 1) View.VISIBLE else View.GONE
        }
        video.stopPlayback()
        if (isVideo) {
            image.setImageDrawable(null)
            video.visibility = View.VISIBLE
            video.setMediaController(MediaController(this).also { it.setAnchorView(video) })
            video.setVideoURI(Uri.parse(refs[index]))
            video.setOnPreparedListener { it.start() }
        } else {
            video.visibility = View.GONE
            Glide.with(this).load(refs[index]).error(R.drawable.media_error).fitCenter().into(image)
        }
    }

    override fun onStop() {
        findViewById<VideoView>(R.id.viewerVideo).pause()
        super.onStop()
    }
}
