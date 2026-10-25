package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import android.net.Uri

class CameraActivity : AppCompatActivity() {
    private var captureUri: Uri? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.shutterButton).setOnClickListener {
            capturePhoto()
        }
        findViewById<View>(R.id.galleryButton).setOnClickListener {
            startActivityForResult(Intent(this, PhotoPickerActivity::class.java), 812)
        }
    }

    private fun capturePhoto() {
        val photoFile = try { File.createTempFile("kinnect_story_", ".jpg", cacheDir) } catch (error: Exception) {
            Toast.makeText(this, error.localizedMessage ?: "Could not create a photo file", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        captureUri = uri
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "No camera app is available", Toast.LENGTH_SHORT).show()
            return
        }
        startActivityForResult(intent, 813)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val mediaUri = when (requestCode) {
            812 -> if (resultCode == RESULT_OK) data?.getStringArrayListExtra("media_uris")?.firstOrNull() else null
            813 -> if (resultCode == RESULT_OK) captureUri?.toString() else null
            else -> null
        } ?: return
        if (intent.getBooleanExtra("for_post", false)) {
            setResult(RESULT_OK, Intent().putExtra("media_uri", mediaUri))
            finish()
        } else {
            startActivity(Intent(this, StoryEditorActivity::class.java).putExtra("media_uri", mediaUri))
        }
    }
}
