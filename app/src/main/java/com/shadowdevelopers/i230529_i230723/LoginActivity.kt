package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/** Log in (02). Email + password sign in, Recent login card, Forgot password. */
class LoginActivity : AppCompatActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        showRecentAccount()

        findViewById<View>(R.id.loginButton).setOnClickListener { logIn() }
        findViewById<View>(R.id.forgotPasswordButton).setOnClickListener { resetPassword() }
        findViewById<View>(R.id.signupButton).setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    /** The last account that signed in on this phone; tapping it pre-fills the email. */
    private fun showRecentAccount() {
        val email = Session.recentEmail(this)
        val card = findViewById<View>(R.id.recentLoginCard)
        if (email.isNullOrBlank()) {
            card.visibility = View.GONE
            return
        }
        val name = Session.recentName(this).takeUnless { it.isNullOrBlank() } ?: email
        card.visibility = View.VISIBLE
        findViewById<TextView>(R.id.recentLoginName).text = name
        AvatarBinder.bind(findViewById(R.id.recentLoginAvatar), name, Session.recentPhoto(this))
        card.setOnClickListener {
            emailInput.setText(email)
            passwordInput.requestFocus()
        }
        findViewById<View>(R.id.recentLoginMore).setOnClickListener {
            Session.clearRecent(this)
            card.visibility = View.GONE
        }
    }

    private fun logIn() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Enter your email and password", Toast.LENGTH_SHORT).show()
            return
        }
        val button = findViewById<View>(R.id.loginButton)
        button.isEnabled = false
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            val user = auth.currentUser
            if (!task.isSuccessful || user == null) {
                button.isEnabled = true
                Toast.makeText(this, task.exception?.localizedMessage ?: "Login failed", Toast.LENGTH_LONG).show()
                return@addOnCompleteListener
            }
            FirebaseData.users.child(user.uid).get()
                .addOnSuccessListener { snapshot ->
                    val complete = snapshot.child("profileSetupComplete").getValue(Boolean::class.java) == true
                    Session.setProfileComplete(this, user.uid, complete)
                    Session.saveRecent(this, email, snapshot.child("name").getValue(String::class.java) ?: email, snapshot.child("photo").getValue(String::class.java))
                    Session.startFresh(this, if (complete) HomeActivity::class.java else EditProfileActivity::class.java)
                }
                .addOnFailureListener {
                    button.isEnabled = true
                    Toast.makeText(this, it.localizedMessage ?: "Could not load your profile", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun resetPassword() {
        val email = emailInput.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Type your account email above first", Toast.LENGTH_SHORT).show()
            emailInput.requestFocus()
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_LONG).show() }
            .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not send the reset email", Toast.LENGTH_LONG).show() }
    }
}
