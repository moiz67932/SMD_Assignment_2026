package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val auth = FirebaseAuth.getInstance()
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val preferences = getSharedPreferences("kinnect", MODE_PRIVATE)
        val recentEmail = preferences.getString("recent_email", null)
        val recentCard = findViewById<View>(R.id.recentLoginCard)
        recentCard.visibility = if (recentEmail.isNullOrBlank()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.recentLoginName).text = preferences.getString("recent_name", recentEmail ?: "Recent account")
        recentCard.setOnClickListener {
            emailInput.setText(recentEmail.orEmpty())
            passwordInput.requestFocus()
        }

        findViewById<View>(R.id.loginButton).setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Enter your email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            findViewById<View>(R.id.loginButton).isEnabled = false
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    findViewById<View>(R.id.loginButton).isEnabled = true
                    if (task.isSuccessful) {
                        preferences.edit().putString("recent_email", email).apply()
                        val user = auth.currentUser ?: return@addOnCompleteListener
                        FirebaseData.users.child(user.uid).get()
                            .addOnSuccessListener { snapshot ->
                                preferences.edit().putString("recent_name", snapshot.child("name").getValue(String::class.java) ?: email).apply()
                                open(if (snapshot.child("profileSetupComplete").getValue(Boolean::class.java) == true) HomeActivity::class.java else EditProfileActivity::class.java)
                            }
                            .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not load your profile", Toast.LENGTH_LONG).show() }
                    } else {
                        Toast.makeText(this, task.exception?.localizedMessage ?: "Login failed", Toast.LENGTH_LONG).show()
                    }
                }
        }

        findViewById<View>(R.id.signupButton).setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun open(target: Class<*>) {
        val intent = Intent(this, target)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
