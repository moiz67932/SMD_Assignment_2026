package com.shadowdevelopers.i230529_i230723

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class YourStoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_your_story)

        findViewById<View>(R.id.doneButton).setOnClickListener {
            finish()
        }
    }
}
