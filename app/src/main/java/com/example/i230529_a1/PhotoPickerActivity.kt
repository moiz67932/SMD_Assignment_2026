package com.example.i230529_a1

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PhotoPickerActivity : AppCompatActivity() {
    private val selectedUris = mutableListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_picker)

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.selectMultipleButton).setOnClickListener { openSystemPicker() }
        findViewById<View>(R.id.photoPickerPreview).setOnClickListener { openSystemPicker() }
        findViewById<View>(R.id.photoPickerNext).setOnClickListener {
            if (selectedUris.isEmpty()) {
                Toast.makeText(this, "Select at least one image or video", Toast.LENGTH_SHORT).show()
                openSystemPicker()
            } else {
                val result = Intent().putStringArrayListExtra("media_uris", ArrayList(selectedUris.map(Uri::toString)))
                setResult(RESULT_OK, result)
                finish()
            }
        }
    }

    private fun openSystemPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 810)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 810 || resultCode != RESULT_OK || data == null) return
        selectedUris.clear()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) selectedUris.add(clip.getItemAt(index).uri)
        } ?: data.data?.let { selectedUris.add(it) }
        selectedUris.firstOrNull()?.let { uri ->
            findViewById<ImageView>(R.id.photoPickerPreview).setImageURI(uri)
            findViewById<TextView>(R.id.photoPickerNext).text = "Next (${selectedUris.size})"
        }
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        selectedUris.forEach { uri ->
            try { contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
        }
    }
}
