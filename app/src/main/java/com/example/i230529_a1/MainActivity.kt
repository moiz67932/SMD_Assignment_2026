package com.example.i230529_a1

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Handler(Looper.getMainLooper()).postDelayed(
            {
                if (!navigated) {
                    navigated = true
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null) {
                        open(LoginActivity::class.java)
                    } else {
                        FirebaseData.users.child(user.uid).get()
                            .addOnSuccessListener { snapshot ->
                                open(if (snapshot.child("profileSetupComplete").getValue(Boolean::class.java) == true) HomeActivity::class.java else EditProfileActivity::class.java)
                            }
                            .addOnFailureListener { open(EditProfileActivity::class.java) }
                    }
                }
            },
            5000
        )
    }

    private fun open(target: Class<*>) {
        val intent = Intent(this, target)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (!navigated) super.onBackPressed()
    }
}
