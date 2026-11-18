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
import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONObject

/**
 * Edit profile (16). Also the first-time setup step: a new account must pick a unique username and
 * a bio here before reaching Home. Photo and cover are uploaded as soon as they are picked; Save then
 * writes every field to users/{uid} (private) and profiles/{uid} (public, used by search and lists).
 */
class EditProfileActivity : AppCompatActivity() {
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
        val uid = Backend.uid
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

        // GET profiles/get.php (my own profile also returns email, phone and birthday). Offline: the SQLite copy.
        Backend.call({
            runCatching { Backend.api.get("profiles/get.php").getJSONObject("user") }
                .onSuccess { Backend.store.putEntity(uid, "user", uid, it.toString()) }
                .getOrElse { error -> Backend.store.entity(uid, "user", uid)?.let { JSONObject(it) } ?: throw error }
        }) { result ->
            result.onSuccess { user ->
                fun text(key: String) = user.optString(key).takeUnless { it == "null" }.orEmpty()
                firstSetup = !(user.optInt("profile_setup_complete") == 1 || user.optBoolean("profile_setup_complete"))
                oldUsername = text("username")
                photoRef = ProfileCache.mediaRef(text("photo_media_id"))
                coverRef = ProfileCache.mediaRef(text("cover_media_id"))
                nameInput.setText(text("display_name"))
                usernameInput.setText(oldUsername)
                websiteInput.setText(text("website"))
                bioInput.setText(text("bio"))
                phoneInput.setText(text("phone"))
                findViewById<EditText>(R.id.profileEmailValue).setText(text("email"))
                findViewById<EditText>(R.id.profileGenderValue).setText(text("gender"))
                showPhoto()
                MediaLoader.load(findViewById(R.id.editCoverImage), coverRef.ifBlank { null })
                if (coverRef.isBlank()) findViewById<ImageView>(R.id.editCoverImage).setImageDrawable(null)
                // A first-time user cannot leave setup without saving.
                findViewById<TextView>(R.id.backButton).visibility = if (firstSetup) View.INVISIBLE else View.VISIBLE
                saveButton.isEnabled = true
            }.onFailure {
                Toast.makeText(this, Forms.message(it as? Exception, "Could not load profile"), Toast.LENGTH_LONG).show()
            }
        }

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        listOf(R.id.editPhotoButton, R.id.editPhotoCamera, R.id.editProfileAvatar).forEach { id ->
            findViewById<View>(id).setOnClickListener { pickImage(PICK_PHOTO) }
        }
        listOf(R.id.editCoverButton, R.id.editCoverCamera, R.id.editCoverImage).forEach { id ->
            findViewById<View>(id).setOnClickListener { pickImage(PICK_COVER) }
        }
        saveButton.setOnClickListener { save(uid) }
        Forms.onKeyboardAction(phoneInput) { save(uid) } // Done on the last field = Save
    }

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
        when {
            name.isBlank() -> return run { Forms.error(nameInput, "Enter your name") }
            username.length !in 3..30 || !username.matches(Regex("[a-z0-9_.]+")) ->
                return run { Forms.error(usernameInput, "3–30 characters: lowercase letters, numbers, _ or .") }
            bio.isBlank() -> return run { Forms.error(bioInput, "Write a short bio") }
        }
        Forms.hideKeyboard(this)
        Forms.setBusy(saveButton, true, "Saving…")
        // POST profiles/update.php. The server checks the username is unique (409 username_taken) and saves everything
        // in one transaction; the action UUID makes a retried save safe.
        val body = JSONObject().put("action_uuid", Backend.newId()).put("name", name).put("username", username).put("bio", bio)
            .put("website", websiteInput.text.toString().trim()).put("phone", phoneInput.text.toString().trim())
            .put("photo_media_id", photoRef.removePrefix("srv:").ifBlank { JSONObject.NULL })
            .put("cover_media_id", coverRef.removePrefix("srv:").ifBlank { JSONObject.NULL })
        Backend.call({ Backend.api.post("profiles/update.php", body).getJSONObject("user") }) { result ->
            result.onSuccess { user ->
                ProfileCache.putJson(user)
                Session.setProfileComplete(this, uid, true)
                Session.recentEmail(this)?.let { Session.saveRecent(this, it, name, photoRef) }
                Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
                if (firstSetup) Session.startFresh(this, HomeActivity::class.java) else finish()
            }.onFailure {
                Forms.setBusy(saveButton, false)
                val error = it as? com.shadowdevelopers.i230529_i230723.data.ApiClient.ApiException
                if (error?.code == "username_taken") Forms.error(usernameInput, "That username is already taken")
                else Toast.makeText(this, Forms.message(it as? Exception, "Could not save profile"), Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val PICK_PHOTO = 901
        private const val PICK_COVER = 902
    }
}
