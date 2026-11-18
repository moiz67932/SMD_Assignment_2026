package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class MenuActivity : AppCompatActivity() {
    private val badges by lazy { TabBadges(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        Backend.uid?.let { uid ->
            ProfileCache.get(uid) { profile ->
                findViewById<TextView>(R.id.menuProfileName).text = profile.name
                AvatarBinder.bind(findViewById(R.id.menuProfileAvatar), profile.name, profile.photo)
            }
        }

        fun open(id: Int, target: Class<*>) {
            findViewById<View>(id).setOnClickListener { Nav.goTab(this, target) }
        }

        open(R.id.searchButton, SearchActivity::class.java)
        open(R.id.chatsButton, ChatsActivity::class.java)
        open(R.id.profileCard, ProfileActivity::class.java)
        open(R.id.marketplaceShortcut, MarketplaceActivity::class.java)
        open(R.id.friendsShortcut, FriendsActivity::class.java)
        Nav.tab(this, R.id.homeTab, HomeActivity::class.java)
        Nav.tab(this, R.id.friendsTab, FriendsActivity::class.java)
        Nav.tab(this, R.id.marketTab, MarketplaceActivity::class.java)
        Nav.tab(this, R.id.notificationsTab, NotificationsActivity::class.java)

        // "See more" holds the story shortcuts; the other tiles are design-only in this version.
        findViewById<View>(R.id.seeMoreButton).setOnClickListener { anchor ->
            android.widget.PopupMenu(this, anchor).apply {
                menu.add(0, 1, 0, "Your story")
                menu.add(0, 2, 1, "Close friends")
                setOnMenuItemClickListener { item ->
                    startActivity(Intent(this@MenuActivity, if (item.itemId == 1) YourStoryActivity::class.java else CloseFriendsActivity::class.java))
                    true
                }
            }.show()
        }
        mapOf(R.id.memoriesShortcut to "memories", R.id.savedShortcut to "saved", R.id.groupsShortcut to "groups", R.id.eventsShortcut to "events").forEach { (id, mode) ->
            findViewById<View>(id).setOnClickListener { startActivity(Intent(this, SimpleListActivity::class.java).putExtra("mode", mode)) }
        }
        findViewById<View>(R.id.helpRow).setOnClickListener { showHelp() }
        findViewById<View>(R.id.settingsRow).setOnClickListener { showSettings() }
        findViewById<View>(R.id.alsoFromRow).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("About Kinnect")
                .setMessage("Kinnect · version 1.0\nCS 4039 Software for Mobile Devices, Assignment 2.\nBuilt by team Shadow Developers (i230529, i230723).")
                .setPositiveButton("OK", null).show()
        }

        // Ask first: logging out by accident is disruptive (HCI: error prevention).
        findViewById<View>(R.id.logoutButton).setOnClickListener { button ->
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Log out of Kinnect?")
                .setMessage("You will need your email and password to log in again.")
                .setPositiveButton("Log out") { _, _ ->
                    Forms.setBusy(button, true)
                    Session.logout(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onStart() {
        super.onStart()
        badges.start()
    }

    override fun onStop() {
        badges.stop()
        super.onStop()
    }

    private fun showHelp() {
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Help")
            .setMessage("• Post: Home → What's on your mind? You can add photos, tag friends, a feeling, a place or an event.\n" +
                "• Stories: tap Create story on Home. They disappear after 24 hours. Close friends see the green ones only.\n" +
                "• Chat: open a friend from Chats. Press the mic for a voice message; you can edit or delete a message for 5 minutes.\n" +
                "• Calls: the phone and video icons inside a chat.\n" +
                "• Friends: search a name or username, then Add friend.\n" +
                "• Live: the LIVE mode in the camera (friends see a red LIVE tile on Home).")
            .setPositiveButton("OK", null).show()
    }

    private fun showSettings() {
        val email = Session.recentEmail(this) ?: "your account"
        val items = arrayOf("Signed in as $email", "Change password", "Clear downloaded media", "Edit profile")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Settings").setItems(items) { _, which ->
            when (which) {
                1 -> changePassword()
                2 -> {
                    MediaLoader.clear()
                    android.widget.Toast.makeText(this, "Downloaded media cleared", android.widget.Toast.LENGTH_SHORT).show()
                }
                3 -> startActivity(Intent(this, EditProfileActivity::class.java))
            }
        }.setNegativeButton("Close", null).show()
    }

    /** POST auth/password.php: the current password is checked on the server; the new one is stored as a hash. */
    private fun changePassword() {
        val density = resources.displayMetrics.density
        fun field(hint: String) = android.widget.EditText(this).apply {
            this.hint = hint
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val current = field("Current password")
        val fresh = field("New password (at least 8 characters)")
        val form = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (20 * density).toInt(); setPadding(pad, pad / 2, pad, 0)
            addView(current); addView(fresh)
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Change password").setView(form)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (fresh.text.length < 8) return@setOnClickListener run { Forms.error(fresh, "Use at least 8 characters") }
                val body = org.json.JSONObject().put("current_password", current.text.toString()).put("new_password", fresh.text.toString())
                Backend.call({ Backend.api.post("auth/password.php", body) }) { result ->
                    result.onSuccess { dialog.dismiss(); android.widget.Toast.makeText(this, "Password changed", android.widget.Toast.LENGTH_SHORT).show() }
                        .onFailure { Forms.error(current, Forms.message(it as? Exception, "Could not change the password")) }
                }
            }
        }
        dialog.show()
    }

}
