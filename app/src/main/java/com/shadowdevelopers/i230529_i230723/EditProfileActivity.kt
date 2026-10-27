package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

/**
 * Edit profile (16). Also the first-time setup step: a new account must pick a unique username and
 * a bio here before reaching Home. Photo and cover are uploaded as soon as they are picked; Save then
 * writes every field to users/{uid} (private) and profiles/{uid} (public, used by search and lists).
 */
class EditProfileActivity : AppCompatActivity() {
    private val user by lazy { FirebaseAuth.getInstance().currentUser }
    /** True for a brand-new account: after saving, go to Home instead of back. */
    private var firstSetup = false
    private var oldUsername = ""
    private var photoRef = ""
    private var coverRef = ""
    private var uploading = 0
    private lateinit var nameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var websiteInput: EditText
    private lateinit var bioInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var saveButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)
        val uid = user?.uid
        if (uid == null) {
            Toast.makeText(this, "Please log in to edit your profile", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        nameInput = findViewById(R.id.profileNameInput)
        usernameInput = findViewById(R.id.profileUsernameInput)
        websiteInput = findViewById(R.id.profileWebsiteInput)
        bioInput = findViewById(R.id.profileBioInput)
        phoneInput = findViewById(R.id.profilePhoneValue)
        saveButton = findViewById(R.id.saveButton)
        saveButton.isEnabled = false

        FirebaseData.users.child(uid).get().addOnSuccessListener { snapshot ->
            firstSetup = snapshot.child("profileSetupComplete").getValue(Boolean::class.java) != true
            oldUsername = text(snapshot, "username")
            photoRef = text(snapshot, "photo")
            coverRef = text(snapshot, "cover")
            nameInput.setText(text(snapshot, "name"))
            usernameInput.setText(oldUsername)
            websiteInput.setText(text(snapshot, "website"))
            bioInput.setText(text(snapshot, "bio"))
            phoneInput.setText(text(snapshot, "phone"))
            findViewById<EditText>(R.id.profileEmailValue).setText(text(snapshot, "email"))
            findViewById<EditText>(R.id.profileGenderValue).setText(text(snapshot, "gender"))
            showPhoto()
            MediaLoader.load(findViewById(R.id.editCoverImage), coverRef.ifBlank { null })
            if (coverRef.isBlank()) findViewById<ImageView>(R.id.editCoverImage).setImageDrawable(null)
            // A first-time user cannot leave setup without saving.
            findViewById<TextView>(R.id.backButton).visibility = if (firstSetup) View.INVISIBLE else View.VISIBLE
            saveButton.isEnabled = true
        }.addOnFailureListener {
            Toast.makeText(this, it.localizedMessage ?: "Could not load profile", Toast.LENGTH_LONG).show()
        }

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        listOf(R.id.editPhotoButton, R.id.editPhotoCamera, R.id.editProfileAvatar).forEach { id ->
            findViewById<View>(id).setOnClickListener { pickImage(PICK_PHOTO) }
        }
        listOf(R.id.editCoverButton, R.id.editCoverCamera, R.id.editCoverImage).forEach { id ->
            findViewById<View>(id).setOnClickListener { pickImage(PICK_COVER) }
        }
        saveButton.setOnClickListener { save(uid) }
    }

    private fun text(snapshot: DataSnapshot, key: String) = snapshot.child(key).getValue(String::class.java).orEmpty()

    private fun showPhoto() {
        AvatarBinder.bind(findViewById(R.id.editProfileAvatar), nameInput.text.toString().ifBlank { "?" }, photoRef)
    }

    private fun pickImage(requestCode: Int) {
        if (uploading > 0) return
        ExternalLaunch.mark()
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (resultCode != RESULT_OK || (requestCode != PICK_PHOTO && requestCode != PICK_COVER)) return
        val label = findViewById<TextView>(if (requestCode == PICK_PHOTO) R.id.editPhotoButton else R.id.editCoverButton)
        uploading++
        saveButton.isEnabled = false
        MediaUploader.upload(this, uri, MediaUploader.IMAGE,
            onProgress = { label.text = "$it%" },
            onSuccess = { ref ->
                uploading--
                label.text = "Edit"
                saveButton.isEnabled = uploading == 0
                if (requestCode == PICK_PHOTO) {
                    photoRef = ref
                    showPhoto()
                } else {
                    coverRef = ref
                    MediaLoader.load(findViewById(R.id.editCoverImage), ref)
                }
            },
            onError = { message ->
                uploading--
                label.text = "Edit"
                saveButton.isEnabled = uploading == 0
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            })
    }

    private fun save(uid: String) {
        val name = nameInput.text.toString().trim()
        val username = usernameInput.text.toString().trim().removePrefix("@").lowercase()
        val bio = bioInput.text.toString().trim()
        // Same limits as database.rules.json: name 1-60, username 3-30 of a-z 0-9 _ ., bio 1-150.
        if (name.isBlank() || name.length > 60 || username.length !in 3..30 || !username.matches(Regex("[a-z0-9_.]+")) || bio.isBlank() || bio.length > 150) {
            Toast.makeText(this, "Enter a name, a username (3-30 letters, digits, _ or .) and a bio", Toast.LENGTH_LONG).show()
            return
        }
        saveButton.isEnabled = false
        if (username == oldUsername) {
            writeProfile(uid, name, username, bio)
            return
        }
        // Username uniqueness: usernames/{name} can only be claimed if nobody owns it yet (transaction + rules).
        FirebaseData.usernames.child(username).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.value != null && currentData.value != uid) return Transaction.abort()
                currentData.value = uid
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (!committed || error != null) {
                    saveButton.isEnabled = true
                    Toast.makeText(this@EditProfileActivity, "That username is already taken", Toast.LENGTH_LONG).show()
                } else {
                    writeProfile(uid, name, username, bio)
                }
            }
        })
    }

    private fun writeProfile(uid: String, name: String, username: String, bio: String) {
        val website = websiteInput.text.toString().trim()
        val parts = name.lowercase().split(" ").filter { it.isNotBlank() }
        val private = mapOf<String, Any>(
            "name" to name, "username" to username, "usernameLower" to username, "bio" to bio,
            "website" to website, "phone" to phoneInput.text.toString().trim(),
            "photo" to photoRef, "cover" to coverRef,
            "profileSetupComplete" to true, "updatedAt" to mapOf(".sv" to "timestamp")
        )
        // Public copy: the lower-case fields power Search (prefix queries on first, last and full name).
        val public = mapOf<String, Any>(
            "uid" to uid, "name" to name, "nameLower" to name.lowercase(),
            "firstNameLower" to parts.firstOrNull().orEmpty(), "lastNameLower" to parts.drop(1).lastOrNull().orEmpty(),
            "username" to username, "usernameLower" to username, "bio" to bio, "website" to website,
            "photo" to photoRef, "cover" to coverRef, "profileSetupComplete" to true
        )
        FirebaseData.users.child(uid).updateChildren(private)
            .addOnSuccessListener {
                FirebaseData.profiles.child(uid).updateChildren(public).addOnSuccessListener {
                    if (oldUsername.isNotBlank() && oldUsername != username) FirebaseData.usernames.child(oldUsername).removeValue()
                    ProfileCache.put(UserProfile(uid, name, username, bio, photoRef, coverRef))
                    Session.setProfileComplete(this, uid, true)
                    user?.email?.let { Session.saveRecent(this, it, name, photoRef) }
                    Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
                    if (firstSetup) Session.startFresh(this, HomeActivity::class.java) else finish()
                }.addOnFailureListener {
                    saveButton.isEnabled = true
                    Toast.makeText(this, it.localizedMessage ?: "Could not publish profile", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                saveButton.isEnabled = true
                if (oldUsername != username) FirebaseData.usernames.child(username).removeValue()
                Toast.makeText(this, it.localizedMessage ?: "Could not save profile", Toast.LENGTH_LONG).show()
            }
    }

    companion object {
        private const val PICK_PHOTO = 901
        private const val PICK_COVER = 902
    }
}
