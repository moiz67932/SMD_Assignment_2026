package com.shadowdevelopers.i230529_i230723

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.shadowdevelopers.i230529_i230723.data.Backend
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SignupActivity : AppCompatActivity() {
    private var birthday: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val password = findViewById<EditText>(R.id.signupPasswordInput)
        val birthdayButton = findViewById<View>(R.id.birthdayDayButton)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.loginLink).setOnClickListener { finish() }
        birthdayButton.setOnClickListener { chooseBirthday() }
        findViewById<View>(R.id.birthdayMonthButton).setOnClickListener { chooseBirthday() }
        findViewById<View>(R.id.birthdayYearButton).setOnClickListener { chooseBirthday() }

        // Password strength updates on every keystroke: 1 bar = Weak, 2 = Medium, 3 = Strong.
        password.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = showStrength(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })

        Forms.passwordToggle(password)
        // Done on the password keyboard = Create account (no need to reach for the button).
        Forms.onKeyboardAction(password) { createAccount() }
        findViewById<View>(R.id.createAccountButton).setOnClickListener { createAccount() }
    }

    /** Checks each field and shows the problem on that field, then creates the account. */
    private fun createAccount() {
        val firstName = findViewById<EditText>(R.id.firstNameInput)
        val lastName = findViewById<EditText>(R.id.lastNameInput)
        val email = findViewById<EditText>(R.id.signupEmailInput)
        val password = findViewById<EditText>(R.id.signupPasswordInput)
        val first = firstName.text.toString().trim()
        val last = lastName.text.toString().trim()
        val emailAddress = email.text.toString().trim()
        val secret = password.text.toString()
        when {
            first.isBlank() -> return run { Forms.error(firstName, "Enter your first name") }
            last.isBlank() -> return run { Forms.error(lastName, "Enter your last name") }
            birthday.isBlank() -> {
                findViewById<TextView>(R.id.birthdayDayButton).error = "Choose your birthday"
                Toast.makeText(this, "Choose your birthday", Toast.LENGTH_SHORT).show()
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(emailAddress).matches() -> return run { Forms.error(email, "Enter a valid email, like name@example.com") }
            secret.length < 8 -> return run { Forms.error(password, "Use at least 8 characters") }
            !findViewById<CheckBox>(R.id.termsCheckbox).isChecked -> {
                Toast.makeText(this, "Accept the terms to continue", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Forms.hideKeyboard(this)
        val gender = when (findViewById<RadioGroup>(R.id.genderGroup).checkedRadioButtonId) {
            R.id.genderFemale -> "Female"
            R.id.genderCustom -> "Custom"
            else -> "Male"
        }
        val button = findViewById<View>(R.id.createAccountButton)
        Forms.setBusy(button, true, "Creating account…")
        // POST auth/signup.php: the server stores password_hash(password) and signs the new user in (session token).
        Backend.call({ Backend.signup(emailAddress, secret, first, last, birthday, gender) }) { result ->
            result.onSuccess { user ->
                val uid = user.getString("id")
                Session.saveRecent(this, emailAddress, "$first $last", null)
                Session.setProfileComplete(this, uid, false)
                (application as KinnectApplication).onSignedIn()
                // First-time users choose a unique username and bio before Home (screen 16).
                Session.startFresh(this, EditProfileActivity::class.java)
            }.onFailure {
                Forms.setBusy(button, false)
                val error = it as? com.shadowdevelopers.i230529_i230723.data.ApiClient.ApiException
                if (error?.code == "email_taken") email.error = "Already registered"
                Toast.makeText(this, Forms.message(it as? Exception, "Account creation failed"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showStrength(value: String) {
        val score = listOf(value.length >= 8, value.any { it.isUpperCase() }, value.any { it.isDigit() }, value.any { !it.isLetterOrDigit() }).count { it }
        val (bars, label, color) = when {
            value.isEmpty() -> Triple(0, "Enter a password", R.color.text_secondary)
            score <= 1 -> Triple(1, "Weak", R.color.red)
            score == 2 -> Triple(2, "Medium", R.color.orange)
            else -> Triple(3, "Strong", R.color.primary)
        }
        val tint = ContextCompat.getColor(this, color)
        listOf(R.id.strengthBar1, R.id.strengthBar2, R.id.strengthBar3).forEachIndexed { index, id ->
            findViewById<View>(id).setBackgroundColor(if (index < bars) tint else ContextCompat.getColor(this, R.color.divider))
        }
        findViewById<TextView>(R.id.passwordStrengthLabel).apply { text = label; setTextColor(tint) }
    }

    private fun chooseBirthday() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)
            birthday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
            findViewById<TextView>(R.id.birthdayDayButton).text = day.toString()
            findViewById<TextView>(R.id.birthdayMonthButton).text = SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time)
            findViewById<TextView>(R.id.birthdayYearButton).text = year.toString()
            findViewById<TextView>(R.id.birthdayDayButton).error = null
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }
}
