package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class StoryEditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_editor)

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        val mediaUri = intent.getStringExtra("media_uri")
        if (!mediaUri.isNullOrBlank()) findViewById<ImageView>(R.id.storyPreview).setImageURI(Uri.parse(mediaUri))
        findViewById<View>(R.id.shareStoryButton).setOnClickListener { shareStory(false) }
        findViewById<View>(R.id.shareCloseFriendsButton).setOnClickListener { shareStory(true) }
        findViewById<View>(R.id.storyContinueButton).setOnClickListener { shareStory(false) }
    }

    private fun shareStory(closeFriends: Boolean) {
        val mediaUri = intent.getStringExtra("media_uri")
        if (mediaUri.isNullOrBlank()) {
            Toast.makeText(this, "Choose or capture a story image first", Toast.LENGTH_SHORT).show()
            return
        }
        val audience = if (closeFriends) "Close Friends" else "Your story"
        Toast.makeText(this, "$audience preview is local only. Cross-device publishing needs an external media host.", Toast.LENGTH_LONG).show()
    }
}
