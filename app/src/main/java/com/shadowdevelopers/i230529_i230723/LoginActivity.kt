package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shadowdevelopers.i230529_i230723.data.Backend

/** Log in (02). Email + password sign in, Recent login card, Forgot password. */
class LoginActivity : AppCompatActivity() {
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        showRecentAccount()

        Forms.passwordToggle(passwordInput)
        Forms.onKeyboardAction(passwordInput) { logIn() } // Done on the keyboard = Log in
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
        when {
            email.isBlank() -> return run { Forms.error(emailInput, "Enter your email") }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> return run { Forms.error(emailInput, "Enter a valid email, like name@example.com") }
            password.isBlank() -> return run { Forms.error(passwordInput, "Enter your password") }
        }
        Forms.hideKeyboard(this)
        val button = findViewById<View>(R.id.loginButton)
        Forms.setBusy(button, true, "Logging in…")
        // POST auth/login.php: the server verifies the password hash and returns a session token (stored encrypted).
        Backend.call({ Backend.login(email, password) }) { result ->
            result.onSuccess { user ->
                val uid = user.getString("id")
                val complete = user.optInt("profile_setup_complete") == 1 || user.optBoolean("profile_setup_complete")
                Session.setProfileComplete(this, uid, complete)
                Session.saveRecent(this, email, user.optString("display_name").ifBlank { email }, null)
                ProfileCache.invalidate(uid)
                (application as KinnectApplication).onSignedIn()
                Session.startFresh(this, if (complete) HomeActivity::class.java else EditProfileActivity::class.java)
            }.onFailure {
                Forms.setBusy(button, false)
                Toast.makeText(this, Forms.message(it as? Exception, "Login failed"), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Password reset needs an email service on the server, which this coursework backend does not run. */
    private fun resetPassword() {
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Forgot password?")
            .setMessage("Ask your Kinnect administrator to reset it, or create a new account. (Password reset emails are not part of this server.)")
            .setPositiveButton("OK", null).show()
    }
}
