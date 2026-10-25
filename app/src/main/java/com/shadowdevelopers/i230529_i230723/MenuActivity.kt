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
        open(R.id.homeTab, HomeActivity::class.java)
        open(R.id.friendsTab, FriendsActivity::class.java)
        open(R.id.marketTab, MarketplaceActivity::class.java)
        open(R.id.notificationsTab, NotificationsActivity::class.java)

        findViewById<View>(R.id.logoutButton).setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val logout = {
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            if (uid == null) logout() else (application as KinnectApplication).setOffline { logout() }
        }
    }
}
