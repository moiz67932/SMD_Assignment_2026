package com.example.i230529_a1

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SignupActivity : AppCompatActivity() {
    private var birthday: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val firstName = findViewById<EditText>(R.id.firstNameInput)
        val lastName = findViewById<EditText>(R.id.lastNameInput)
        val email = findViewById<EditText>(R.id.signupEmailInput)
        val password = findViewById<EditText>(R.id.signupPasswordInput)
        val strength = findViewById<TextView>(R.id.passwordStrengthLabel)
        val birthdayButton = findViewById<View>(R.id.birthdayDayButton)
        val terms = findViewById<CheckBox>(R.id.termsCheckbox)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.loginLink).setOnClickListener { finish() }
        birthdayButton.setOnClickListener { chooseBirthday() }
        findViewById<View>(R.id.birthdayMonthButton).setOnClickListener { chooseBirthday() }
        findViewById<View>(R.id.birthdayYearButton).setOnClickListener { chooseBirthday() }

        password.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s.orEmpty().toString()
                val score = listOf(value.length >= 8, value.any { it.isUpperCase() }, value.any { it.isDigit() }, value.any { !it.isLetterOrDigit() }).count { it }
                strength.text = when {
                    value.isEmpty() -> "Enter a password"
                    score <= 1 -> "Weak"
                    score <= 2 -> "Medium"
                    else -> "Strong"
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        findViewById<View>(R.id.createAccountButton).setOnClickListener {
            val first = firstName.text.toString().trim()
            val last = lastName.text.toString().trim()
            val emailAddress = email.text.toString().trim()
            val secret = password.text.toString()
            if (first.isBlank() || last.isBlank() || birthday.isBlank() || emailAddress.isBlank() || secret.length < 6) {
                Toast.makeText(this, "Complete your name, birthday, email and a password of at least 6 characters", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!terms.isChecked) {
                Toast.makeText(this, "Accept the terms to continue", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val gender = when (findViewById<RadioGroup>(R.id.genderGroup).checkedRadioButtonId) {
                R.id.genderFemale -> "Female"
                R.id.genderCustom -> "Custom"
                else -> "Male"
            }
            val button = findViewById<View>(R.id.createAccountButton)
            button.isEnabled = false
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(emailAddress, secret)
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        button.isEnabled = true
                        Toast.makeText(this, task.exception?.localizedMessage ?: "Account creation failed", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null) {
                        button.isEnabled = true
                        Toast.makeText(this, "Account created, but sign in again to continue", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }
                    val profile = mapOf(
                        "uid" to user.uid,
                        "firstName" to first,
                        "lastName" to last,
                        "name" to "$first $last",
                        "email" to emailAddress,
                        "birthday" to birthday,
                        "gender" to gender,
                        "username" to "",
                        "usernameLower" to "",
                        "bio" to "",
                        "profileSetupComplete" to false,
                        "createdAt" to mapOf(".sv" to "timestamp")
                    )
                    FirebaseData.users.child(user.uid).setValue(profile)
                        .addOnSuccessListener {
                            FirebaseData.profiles.child(user.uid).setValue(
                                mapOf("uid" to user.uid, "name" to "$first $last", "nameLower" to "$first $last".lowercase(), "username" to "", "usernameLower" to "", "bio" to "", "profileSetupComplete" to false)
                            ).addOnSuccessListener {
                                getSharedPreferences("kinnect", MODE_PRIVATE).edit().putString("recent_email", emailAddress).putString("recent_name", "$first $last").apply()
                                val intent = Intent(this, EditProfileActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }.addOnFailureListener {
                                button.isEnabled = true
                                Toast.makeText(this, it.localizedMessage ?: "Could not publish your searchable profile", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener {
                            button.isEnabled = true
                            Toast.makeText(this, it.localizedMessage ?: "Could not save your profile", Toast.LENGTH_LONG).show()
                        }
                }
        }
    }

    private fun chooseBirthday() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)
            birthday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
            findViewById<TextView>(R.id.birthdayDayButton).text = day.toString()
            findViewById<TextView>(R.id.birthdayMonthButton).text = SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time)
            findViewById<TextView>(R.id.birthdayYearButton).text = year.toString()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }
}
