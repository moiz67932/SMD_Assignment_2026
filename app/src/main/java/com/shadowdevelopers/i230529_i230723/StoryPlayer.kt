package com.shadowdevelopers.i230529_i230723

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import com.bumptech.glide.Glide

/**
 * Plays a list of stories in the Story viewer (11) and Your story (12) screens: one progress segment per story,
 * 5 seconds per photo, videos until they end. Tap the left third to go back, anywhere else to go forward,
 * hold to pause. Both screens use the same ids (storyImage, storyVideo, storyTouch, storySegments, storyLabel).
 */
class StoryPlayer(
    private val activity: Activity,
    private val onShow: (Story, Int) -> Unit,
    private val onFinished: () -> Unit,
    private val onBeforeStart: () -> Unit = {}
) {
    private val image = activity.findViewById<ImageView>(R.id.storyImage)
    private val video = activity.findViewById<VideoView>(R.id.storyVideo)
    private val segments = activity.findViewById<LinearLayout>(R.id.storySegments)
    private val label = activity.findViewById<TextView>(R.id.storyLabel)
    private val handler = Handler(Looper.getMainLooper())
    private val fills = mutableListOf<View>()
    private var stories: List<Story> = emptyList()
    var index = 0
        private set
    private var durationMs = IMAGE_MS
    private var elapsedMs = 0L
    private var lastTick = 0L
    private var running = false
    private var holding = false
    private var released = false

    private val tick = object : Runnable {
        override fun run() {
            if (released) return
            val now = SystemClock.uptimeMillis()
            if (running && !holding) {
                elapsedMs = if (video.visibility == View.VISIBLE && video.isPlaying) video.currentPosition.toLong() else elapsedMs + (now - lastTick)
            }
            lastTick = now
            fills.getOrNull(index)?.scaleX = (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
            if (running && elapsedMs >= durationMs) next() else handler.postDelayed(this, 50)
        }
    }

    init {
        attachTouch()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouch() {
        var downAt = 0L
        activity.findViewById<View>(R.id.storyTouch).setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downAt = SystemClock.uptimeMillis(); setHolding(true) }
                MotionEvent.ACTION_UP -> {
                    setHolding(false)
                    if (SystemClock.uptimeMillis() - downAt < 250) {
                        if (event.x < view.width / 3f) previous() else next()
                    }
                    view.performClick()
                }
                MotionEvent.ACTION_CANCEL -> setHolding(false)
            }
            true
        }
    }

    fun play(items: List<Story>, startAt: Int = 0) {
        stories = items
        segments.removeAllViews()
        fills.clear()
        val density = activity.resources.displayMetrics.density
        items.forEach { _ ->
            // A grey track with a white fill that grows from left to right (scaleX 0 -> 1).
            val track = FrameLayout(activity).apply {
                setBackgroundColor(0x66FFFFFF)
                layoutParams = LinearLayout.LayoutParams(0, (3 * density).toInt(), 1f).apply { marginEnd = (4 * density).toInt() }
            }
            val fill = View(activity).apply {
                setBackgroundColor(0xFFFFFFFF.toInt())
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                pivotX = 0f
                scaleX = 0f
            }
            track.addView(fill)
            segments.addView(track)
            fills.add(fill)
        }
        if (items.isEmpty()) return
        handler.removeCallbacks(tick)
        handler.post(tick)
        show(startAt.coerceIn(0, items.size - 1))
    }

    private fun show(position: Int) {
        onBeforeStart()
        index = position
        fills.forEachIndexed { i, fill -> fill.scaleX = if (i < position) 1f else 0f }
        val story = stories[position]
        elapsedMs = 0
        lastTick = SystemClock.uptimeMillis()
        label.text = story.label
        label.visibility = if (story.label.isBlank()) View.GONE else View.VISIBLE
        video.stopPlayback()
        if (story.type == MediaUploader.VIDEO) {
            running = false
            image.setImageDrawable(null)
            video.visibility = View.VISIBLE
            video.setVideoURI(Uri.parse(story.mediaRef))
            video.setOnPreparedListener { player ->
                durationMs = player.duration.toLong().coerceIn(1_000L, 31_000L)
                running = true
                if (!holding) player.start()
            }
            video.setOnErrorListener { _, _, _ -> durationMs = IMAGE_MS; running = true; true }
        } else {
            video.visibility = View.GONE
            durationMs = IMAGE_MS
            running = true
            Glide.with(activity).load(story.mediaRef).error(R.drawable.media_error).centerCrop().into(image)
        }
        onShow(story, position)
    }

    fun next() {
        if (index + 1 < stories.size) show(index + 1)
        else {
            running = false
            onFinished()
        }
    }

    fun previous() = show((index - 1).coerceAtLeast(0))

    fun setHolding(value: Boolean) {
        holding = value
        if (video.visibility == View.VISIBLE) {
            if (value) video.pause() else if (running) video.start()
        }
    }

    fun current(): Story? = stories.getOrNull(index)

    fun release() {
        released = true
        handler.removeCallbacks(tick)
        video.stopPlayback()
    }

    companion object {
        private const val IMAGE_MS = 5_000L
    }
}
