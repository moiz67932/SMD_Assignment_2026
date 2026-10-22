package com.example.i230529_a1

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.widget.ImageView
import android.widget.EditText
import android.widget.Toast
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class CreatePostActivity : AppCompatActivity() {
    private var selectedMedia = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseData.users.child(uid).get().addOnSuccessListener { profile ->
                val name = profile.child("name").getValue(String::class.java).orEmpty()
                findViewById<TextView>(R.id.createPostName).text = name
                findViewById<TextView>(R.id.createPostAvatar).text = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            }
        }
        if (intent.getBooleanExtra("open_picker", false)) {
            startActivityForResult(Intent(this, PhotoPickerActivity::class.java), 811)
        }

        findViewById<View>(R.id.photoButton).setOnClickListener {
            startActivityForResult(Intent(this, PhotoPickerActivity::class.java), 811)
        }

        findViewById<View>(R.id.cameraButton).setOnClickListener {
            startActivityForResult(Intent(this, CameraActivity::class.java).putExtra("for_post", true), 814)
        }

        findViewById<View>(R.id.publishPostButton).setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            val text = findViewById<EditText>(R.id.postTextInput).text.toString().trim()
            if (user == null) {
                Toast.makeText(this, "Log in to create a post", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (text.isBlank()) {
                Toast.makeText(this, "Write something before posting", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedMedia) {
                Toast.makeText(this, "This image is only a local preview. Cross-device media sharing needs an external media host.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val postId = FirebaseData.posts.push().key ?: return@setOnClickListener
            FirebaseData.users.child(user.uid).get().addOnSuccessListener { profile ->
                val authorName = profile.child("name").getValue(String::class.java).orEmpty()
                val post = mapOf<String, Any>(
                    "postId" to postId,
                    "authorId" to user.uid,
                    "authorName" to authorName,
                    "text" to text,
                    "createdAt" to mapOf(".sv" to "timestamp"),
                    "reactionCount" to 0,
                    "commentCount" to 0
                )
                FirebaseData.posts.child(postId).setValue(post)
                    .addOnSuccessListener { finish() }
                    .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not publish post", Toast.LENGTH_LONG).show() }
            }.addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not load your profile", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val first = when (requestCode) {
            811 -> data.getStringArrayListExtra("media_uris")?.firstOrNull()?.let(Uri::parse)
            814 -> data.getStringExtra("media_uri")?.let(Uri::parse)
            else -> null
        } ?: return
        selectedMedia = true
        findViewById<ImageView>(R.id.postMediaPreview).apply { setImageURI(first); visibility = View.VISIBLE }
        findViewById<View>(R.id.postMediaNotice).visibility = View.VISIBLE
    }
}
