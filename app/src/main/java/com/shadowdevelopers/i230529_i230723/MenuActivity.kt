package com.shadowdevelopers.i230529_i230723

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import android.widget.TextView

class MenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseData.profiles.child(uid).get().addOnSuccessListener { profile ->
                val name = profile.child("name").getValue(String::class.java).orEmpty()
                findViewById<TextView>(R.id.menuProfileName).text = name
                AvatarBinder.bind(findViewById(R.id.menuProfileAvatar), name, profile.child("photo").getValue(String::class.java))
            }
        }

        fun open(id: Int, target: Class<*>) {
            findViewById<View>(id).setOnClickListener {
                startActivity(Intent(this, target))
            }
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
        listOf(R.id.memoriesShortcut, R.id.savedShortcut, R.id.groupsShortcut, R.id.eventsShortcut, R.id.helpRow, R.id.settingsRow, R.id.alsoFromRow).forEach { id ->
            findViewById<View>(id).setOnClickListener {
                android.widget.Toast.makeText(this, "Not part of this version", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.logoutButton).setOnClickListener {
            it.isEnabled = false
            Session.logout(this)
        }
    }
}
