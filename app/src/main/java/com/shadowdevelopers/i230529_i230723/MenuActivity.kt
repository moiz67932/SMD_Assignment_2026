package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
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
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val items = arrayOf("Signed in as ${user?.email ?: "unknown"}", "Send a password reset email", "Clear saved media cache", "Edit profile")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Settings").setItems(items) { _, which ->
            when (which) {
                1 -> user?.email?.let { email ->
                    com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        .addOnSuccessListener { android.widget.Toast.makeText(this, "Reset email sent to $email", android.widget.Toast.LENGTH_LONG).show() }
                        .addOnFailureListener { android.widget.Toast.makeText(this, Forms.message(it, "Could not send the email"), android.widget.Toast.LENGTH_LONG).show() }
                }
                2 -> {
                    java.io.File(cacheDir, "media_files").deleteRecursively()
                    android.widget.Toast.makeText(this, "Cache cleared", android.widget.Toast.LENGTH_SHORT).show()
                }
                3 -> startActivity(Intent(this, EditProfileActivity::class.java))
            }
        }.setNegativeButton("Close", null).show()
    }
}
