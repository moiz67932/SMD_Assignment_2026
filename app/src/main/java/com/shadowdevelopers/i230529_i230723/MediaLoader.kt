package com.shadowdevelopers.i230529_i230723

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.shadowdevelopers.i230529_i230723.data.Backend
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Shows stored media. A media ref is either
 *   "srv:<media id>" -> a file on the Kinnect server: GET media/get.php?id=… with the session token (the server checks
 *                       that this user may see it). It is downloaded ONCE into app storage, so it still shows offline.
 *   anything else    -> a local file/content URI or URL (for example a photo still waiting to upload), given to Glide.
 */
object MediaLoader {
    private const val PREFIX = "srv:"
    private val main = Handler(Looper.getMainLooper())
    private val downloader = Executors.newFixedThreadPool(3)
    /** Callers waiting for a download that is already running (main thread only). */
    private val waiting = mutableMapOf<String, MutableList<(File?) -> Unit>>()

    fun isServer(ref: String) = ref.startsWith(PREFIX)
    private fun idOf(ref: String) = ref.removePrefix(PREFIX)

    private fun folder(): File = File(appContext.filesDir, "media_cache").also { it.mkdirs() }
    private lateinit var appContext: Context
    fun init(context: Context) { appContext = context.applicationContext }

    /** The cached file for a server media id (it may not exist yet). */
    private fun cached(id: String) = File(folder(), id.replace(Regex("[^A-Za-z0-9-]"), ""))

    /** After an upload, keep the local copy under its server id so it is not downloaded again. */
    fun remember(file: File, mediaId: String) {
        runCatching { file.copyTo(cached(mediaId), overwrite = true) }
    }

    fun load(view: ImageView, ref: String?) = show(view, ref)

    /** Story tile / chat bubble for a video: Glide draws a frame of the (downloaded) video file. */
    fun loadVideoThumbnail(view: ImageView, ref: String?) = show(view, ref)

    private fun show(view: ImageView, ref: String?) {
        view.setTag(R.id.mediaRefTag, ref) // lets a recycled row ignore an answer meant for its previous content
        if (ref.isNullOrBlank()) {
            runCatching { Glide.with(view).clear(view) }
            view.setImageResource(R.drawable.media_placeholder)
            return
        }
        if (!isServer(ref)) {
            runCatching {
                Glide.with(view).load(ref).placeholder(R.drawable.media_placeholder).error(R.drawable.media_error).centerCrop().into(view)
            }
            return
        }
        runCatching { Glide.with(view).clear(view) }
        view.setImageResource(R.drawable.media_placeholder)
        file(ref) { file ->
            if (view.getTag(R.id.mediaRefTag) != ref) return@file
            if (file == null) {
                view.setImageResource(R.drawable.media_error)
                return@file
            }
            runCatching {
                Glide.with(view).load(file).placeholder(R.drawable.media_placeholder).error(R.drawable.media_error).centerCrop().into(view)
            }
        }
    }

    /** Gives Glide something it can load: the downloaded file for server media, otherwise the ref itself. */
    fun resolveModel(ref: String, field: String = "data", onReady: (Any?) -> Unit) {
        if (!isServer(ref)) onReady(ref) else file(ref) { onReady(it) }
    }

    /** A playable address for a video (a local file). */
    fun videoUri(context: Context, ref: String, onReady: (Uri?) -> Unit) = playable(ref, onReady)

    /** Same for audio (voice messages, story music). */
    fun audioUri(context: Context, ref: String, onReady: (Uri?) -> Unit) = playable(ref, onReady)

    private fun playable(ref: String, onReady: (Uri?) -> Unit) {
        if (!isServer(ref)) {
            onReady(Uri.parse(ref))
            return
        }
        file(ref) { onReady(it?.let(Uri::fromFile)) }
    }

    /** Downloads server media once (authenticated) and answers with the local file, on the main thread. */
    private fun file(ref: String, onDone: (File?) -> Unit) {
        val id = idOf(ref)
        val target = cached(id)
        if (target.exists() && target.length() > 0) {
            onDone(target)
            return
        }
        val callbacks = waiting.getOrPut(id) { mutableListOf() }
        callbacks.add(onDone)
        if (callbacks.size > 1) return // the same download is already running
        downloader.execute {
            val result = runCatching { download(id, target) }.getOrNull()
            main.post { waiting.remove(id)?.forEach { it(result) } }
        }
    }

    private fun download(id: String, target: File): File? {
        val connection = URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/media/get.php?id=" + id).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            Backend.token()?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (connection.responseCode != 200) return null
            val part = File(target.parentFile, target.name + ".part")
            connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            return if (part.length() > 0 && part.renameTo(target)) target else null
        } finally {
            connection.disconnect()
        }
    }

    /** Logout: cached media of the previous account is removed with the rest of the local cache. */
    fun clear() {
        runCatching { folder().deleteRecursively() }
    }
}
