package com.example.i230529_a1

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

class EditProfileActivity : AppCompatActivity() {
    private val user by lazy { FirebaseAuth.getInstance().currentUser }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        val nameInput = findViewById<EditText>(R.id.profileNameInput)
        val usernameInput = findViewById<EditText>(R.id.profileUsernameInput)
        val bioInput = findViewById<EditText>(R.id.profileBioInput)
        val saveButton = findViewById<View>(R.id.saveButton)
        saveButton.isEnabled = false
        val uid = user?.uid
        if (uid == null) {
            Toast.makeText(this, "Please log in to edit your profile", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        var oldUsername = ""
        FirebaseData.users.child(uid).get().addOnSuccessListener { snapshot ->
            oldUsername = snapshot.child("username").getValue(String::class.java).orEmpty()
            nameInput.setText(snapshot.child("name").getValue(String::class.java).orEmpty())
            usernameInput.setText(oldUsername)
            bioInput.setText(snapshot.child("bio").getValue(String::class.java).orEmpty())
            findViewById<EditText>(R.id.profileEmailValue).setText(snapshot.child("email").getValue(String::class.java).orEmpty())
            findViewById<EditText>(R.id.profileGenderValue).setText(snapshot.child("gender").getValue(String::class.java).orEmpty())
            saveButton.isEnabled = true
        }.addOnFailureListener {
            Toast.makeText(this, it.localizedMessage ?: "Could not load profile", Toast.LENGTH_LONG).show()
        }

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val username = usernameInput.text.toString().trim().removePrefix("@").lowercase()
            val bio = bioInput.text.toString().trim()
            if (name.isBlank() || username.length < 3 || !username.matches(Regex("[a-z0-9_.]+")) || bio.isBlank()) {
                Toast.makeText(this, "Enter a name, a valid username, and a bio", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val save = findViewById<View>(R.id.saveButton)
            save.isEnabled = false
            val complete = { saveUserProfile(uid, name, username, bio, oldUsername, save) }
            if (username == oldUsername) {
                complete()
            } else {
                FirebaseData.usernames.child(username).runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        if (currentData.value != null) return Transaction.abort()
                        currentData.value = uid
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, currentData: com.google.firebase.database.DataSnapshot?) {
                        if (!committed || error != null) {
                            save.isEnabled = true
                            Toast.makeText(this@EditProfileActivity, "That username is already taken or could not be saved", Toast.LENGTH_LONG).show()
                        } else {
                            complete()
                        }
                    }
                })
            }
        }
    }

    private fun saveUserProfile(uid: String, name: String, username: String, bio: String, oldUsername: String, save: View) {
        val updates = mapOf<String, Any>(
            "name" to name,
            "username" to username,
            "usernameLower" to username,
            "bio" to bio,
            "profileSetupComplete" to true,
            "updatedAt" to mapOf(".sv" to "timestamp")
        )
        FirebaseData.users.child(uid).updateChildren(updates)
            .addOnSuccessListener {
                FirebaseData.profiles.child(uid).updateChildren(
                    mapOf("uid" to uid, "name" to name, "nameLower" to name.lowercase(), "username" to username, "usernameLower" to username, "bio" to bio, "profileSetupComplete" to true)
                ).addOnSuccessListener {
                    if (oldUsername.isNotBlank() && oldUsername != username) {
                        FirebaseData.usernames.child(oldUsername).get().addOnSuccessListener { snapshot ->
                            if (snapshot.getValue(String::class.java) == uid) FirebaseData.usernames.child(oldUsername).removeValue()
                        }
                    }
                    Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
                    finish()
                }.addOnFailureListener {
                    save.isEnabled = true
                    Toast.makeText(this, it.localizedMessage ?: "Could not publish profile", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                save.isEnabled = true
                if (oldUsername != username) FirebaseData.usernames.child(username).removeValue()
                Toast.makeText(this, it.localizedMessage ?: "Could not save profile", Toast.LENGTH_LONG).show()
            }
    }
}
