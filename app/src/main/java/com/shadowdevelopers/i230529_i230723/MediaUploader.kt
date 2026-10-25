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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Uploads one image or video to Cloudinary (unsigned upload preset) and returns its https URL.
 * That URL is the "media ref" the app stores in the Realtime Database. Only this file knows
 * where media lives, so the host could be swapped later without touching the screens.
 */
object MediaUploader {
    const val IMAGE = "image"
    const val VIDEO = "video"
    private const val MAX_IMAGE_EDGE = 1280
    private const val MAX_VIDEO_MS = 31_000L          // 30 s plus a little tolerance
    private const val MAX_VIDEO_BYTES = 40L * 1024 * 1024

    private val worker = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun isConfigured() = BuildConfig.MEDIA_CLOUD_NAME.isNotBlank() && BuildConfig.MEDIA_UPLOAD_PRESET.isNotBlank()

    /** Runs on a background thread; every callback is delivered on the main thread. */
    fun upload(
        context: Context,
        uri: Uri,
        kind: String,
        onProgress: (Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isConfigured()) {
            onError("Media upload is not set up. Add media.properties and rebuild.")
            return
        }
        val appContext = context.applicationContext
        worker.execute {
            try {
                val url = if (kind == VIDEO) {
                    checkVideo(appContext, uri)
                    val size = fileSize(appContext, uri)
                    appContext.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open the video" }
                        send(VIDEO, input, size, "video.mp4", "video/mp4", onProgress)
                    }
                } else {
                    val jpeg = compressImage(appContext, uri)
                    send(IMAGE, jpeg.inputStream(), jpeg.size.toLong(), "image.jpg", "image/jpeg", onProgress)
                }
                main.post { onSuccess(url) }
            } catch (error: Exception) {
                main.post { onError(error.message ?: "Upload failed") }
            }
        }
    }

    /** Decodes a small enough bitmap, fixes the EXIF rotation, scales the long edge to 1280 px, JPEG q80. */
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
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
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
        require(fileSize(context, uri) <= MAX_VIDEO_BYTES) { "That video is too large (max 40 MB)" }
    }

    private fun fileSize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        }
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }

    /** Multipart POST to https://api.cloudinary.com/v1_1/{cloud}/{image|video}/upload. */
    private fun send(kind: String, data: InputStream, size: Long, fileName: String, mime: String, onProgress: (Int) -> Unit): String {
        val boundary = "kinnect" + System.currentTimeMillis()
        val head = ("--$boundary\r\nContent-Disposition: form-data; name=\"upload_preset\"\r\n\r\n${BuildConfig.MEDIA_UPLOAD_PRESET}\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\nContent-Type: $mime\r\n\r\n").toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()
        val connection = URL("https://api.cloudinary.com/v1_1/${BuildConfig.MEDIA_CLOUD_NAME}/$kind/upload").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            if (size > 0) connection.setFixedLengthStreamingMode(head.size + size + tail.size) else connection.setChunkedStreamingMode(64 * 1024)
            connection.outputStream.use { out ->
                out.write(head)
                val buffer = ByteArray(64 * 1024)
                var sent = 0L
                var lastPercent = -1
                while (true) {
                    val read = data.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    sent += read
                    val percent = if (size > 0) (sent * 100 / size).toInt() else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        main.post { onProgress(percent) }
                    }
                }
                out.write(tail)
            }
            val ok = connection.responseCode in 200..299
            val body = (if (ok) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!ok) {
                val message = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
                throw IllegalStateException(message ?: "Upload failed (${connection.responseCode})")
            }
            return JSONObject(body).getString("secure_url")
        } finally {
            connection.disconnect()
        }
    }
}
