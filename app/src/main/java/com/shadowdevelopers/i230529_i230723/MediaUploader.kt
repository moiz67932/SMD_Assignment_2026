package com.shadowdevelopers.i230529_i230723

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.shadowdevelopers.i230529_i230723.data.ApiClient
import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Uploads photos, videos and audio to the Kinnect PHP server (Assignment 3). The bytes are stored on the server's
 * filesystem and their metadata in MySQL; posts, stories, messages and profiles keep only the short ref "srv:<media id>".
 * Photos are shrunk first (long edge 1024 px, JPEG q70 or lower). Videos: up to 30 s / 24 MB (server limit 25 MB).
 */
object MediaUploader {
    const val IMAGE = "image"
    const val VIDEO = "video"
    /** Voice messages and story music (up to 10 MB). */
    const val AUDIO = "audio"
    private const val MAX_IMAGE_EDGE = 1024
    private const val MAX_IMAGE_BYTES = 900 * 1024
    private const val MAX_VIDEO_BYTES = 24L * 1024 * 1024
    private const val MAX_VIDEO_MS = 31_000L
    private const val MAX_AUDIO_BYTES = 10L * 1024 * 1024

    private val worker = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    /** Always true now: media is stored in Firebase itself, so there is nothing to configure. */
    fun isConfigured() = true

    /** Encodes on a background thread; every callback is delivered on the main thread. */
    /**
     * Prepares the file (photos shrunk to 1024 px JPEG; videos/audio copied) in app storage, then uploads it to
     * POST media/upload.php (multipart: id, action_uuid, file). The server stores the bytes on its filesystem and the
     * metadata in MySQL, and returns the media id; [onSuccess] receives the ref "srv:<id>".
     * The id and action UUID are generated once, so a retried upload is recognised by the server, not stored twice.
     */
    fun upload(
        context: Context,
        uri: Uri,
        kind: String,
        onProgress: (Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val account = Backend.uid
        if (account == null) {
            onError("Please log in again")
            return
        }
        val appContext = context.applicationContext
        worker.execute {
            try {
                val file = prepare(appContext, account, uri, kind)
                main.post { onProgress(30) }
                val ref = send(file, kind)
                main.post { onProgress(100); onSuccess(ref) }
            } catch (error: OutOfMemoryError) {
                main.post { onError("That file is too large to upload") }
            } catch (error: Exception) {
                main.post { onError(Forms.message(error, "Upload failed. Check your connection and try again.")) }
            }
        }
    }

    /** Copies/compresses the picked media into durable app storage (survives restarts, so offline actions can upload later). */
    fun prepare(context: Context, account: String, uri: Uri, kind: String): File {
        val folder = File(context.filesDir, "pending_media/$account").also { it.mkdirs() }
        val file = File(folder, Backend.newId() + extension(kind))
        when (kind) {
            VIDEO -> { checkVideo(context, uri); copy(context, uri, file, MAX_VIDEO_BYTES) }
            AUDIO -> copy(context, uri, file, MAX_AUDIO_BYTES)
            else -> file.writeBytes(compressImage(context, uri))
        }
        return file
    }

    /** Blocking multipart upload of a prepared file; returns "srv:<media id>". Call off the main thread. */
    fun send(file: File, kind: String): String {
        val id = Backend.newId()
        val fields = JSONObject().put("id", id).put("action_uuid", Backend.newId())
        val response = Backend.api.multipart("media/upload.php", fields, listOf(ApiClient.Upload("file", file, mime(kind))))
        val mediaId = response.optJSONObject("media")?.optString("id")?.takeIf { it.isNotBlank() } ?: id
        // Keep a copy under its server id so it shows instantly (and offline) without downloading it again.
        MediaLoader.remember(file, mediaId)
        return ProfileCache.mediaRef(mediaId)
    }

    fun mime(kind: String) = when (kind) {
        VIDEO -> "video/mp4"
        AUDIO -> "audio/mp4"
        else -> "image/jpeg"
    }

    private fun extension(kind: String) = when (kind) {
        VIDEO -> ".mp4"
        AUDIO -> ".m4a"
        else -> ".jpg"
    }

    private fun copy(context: Context, uri: Uri, target: File, limit: Long) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= limit) { "That file is too big to send (max ${limit / (1024 * 1024)} MB)" }
                    output.write(buffer, 0, read)
                }
            }
        } ?: throw IllegalArgumentException("Could not read the file")
    }

    /** Decodes a small enough bitmap, fixes the EXIF rotation, scales the long edge to 1024 px, then JPEG. */
    private fun compressImage(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file is not an image" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_EDGE) sample *= 2
        var bitmap = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Could not read the image")

        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val scale = minOf(1f, MAX_IMAGE_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height))
        if (rotation != 0 || scale < 1f) {
            val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        // Lower the quality step by step if a photo is still too big (the database is not meant for big files).
        var quality = 70
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            quality -= 15
        } while (bytes.size > MAX_IMAGE_BYTES && quality >= 25)
        return bytes
    }

    private fun checkVideo(context: Context, uri: Uri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            require(duration <= MAX_VIDEO_MS) { "Videos can be at most 30 seconds long" }
        } finally {
            retriever.release()
        }
        val size = fileSize(context, uri)
        require(size <= MAX_VIDEO_BYTES) { "That video is too big to send (max 24 MB). Choose a shorter clip." }
    }




    private fun fileSize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        }
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }
}
