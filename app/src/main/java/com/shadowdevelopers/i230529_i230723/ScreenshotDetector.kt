package com.shadowdevelopers.i230529_i230723

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/**
 * Tells a screen when the user takes a screenshot while it is visible.
 * - Android 14+ (API 34): the official Activity.ScreenCaptureCallback (needs DETECT_SCREEN_CAPTURE, a normal permission).
 * - Older Android: best effort. Watches new images in MediaStore and treats one as a screenshot when its name or folder
 *   contains "screenshot" and it was added in the last few seconds. This needs gallery (media read) permission.
 * Repeated events within 10 seconds count as one screenshot.
 */
class ScreenshotDetector(private val activity: Activity, private val onScreenshot: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var callback: Any? = null
    private var observer: ContentObserver? = null
    private var lastReport = 0L

    fun start() {
        if (Build.VERSION.SDK_INT >= 34) {
            val screenCallback = Activity.ScreenCaptureCallback { report() }
            activity.registerScreenCaptureCallback(activity.mainExecutor, screenCallback)
            callback = screenCallback
        } else if (hasMediaPermission()) {
            val contentObserver = object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    if (looksLikeNewScreenshot()) report()
                }
            }
            activity.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, contentObserver)
            observer = contentObserver
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= 34) (callback as? Activity.ScreenCaptureCallback)?.let { activity.unregisterScreenCaptureCallback(it) }
        callback = null
        observer?.let { activity.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    /** Only needed below Android 14. */
    fun needsPermission() = Build.VERSION.SDK_INT < 34 && !hasMediaPermission()

    fun permission() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasMediaPermission() = ContextCompat.checkSelfPermission(activity, permission()) == PackageManager.PERMISSION_GRANTED

    private fun report() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastReport < 10_000) return
        lastReport = now
        main.post(onScreenshot)
    }

    /** The newest image, if it was added in the last 15 s and its name/path mentions "screenshot". */
    private fun looksLikeNewScreenshot(): Boolean = try {
        val columns = mutableListOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED)
        if (Build.VERSION.SDK_INT >= 29) columns.add(MediaStore.Images.Media.RELATIVE_PATH) else @Suppress("DEPRECATION") columns.add(MediaStore.Images.Media.DATA)
        activity.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns.toTypedArray(), null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            if (!cursor.moveToFirst()) return false
            val name = cursor.getString(0).orEmpty()
            val addedSeconds = cursor.getLong(1)
            val path = cursor.getString(2).orEmpty()
            val recent = System.currentTimeMillis() / 1000 - addedSeconds <= 15
            recent && (name.contains("screenshot", ignoreCase = true) || path.contains("screenshot", ignoreCase = true))
        } ?: false
    } catch (_: Exception) {
        false
    }
}
