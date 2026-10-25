package com.shadowdevelopers.i230529_i230723

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Splash (01). Visible for exactly 5 seconds. The profile check starts immediately in parallel,
 * so at t = 5 s the screen can decide without waiting: logged out -> Log in, profile known to be
 * incomplete -> Edit profile, otherwise -> Home.
 */
class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var profileComplete: Boolean? = null
    private val navigate = Runnable { route() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            profileComplete = Session.profileComplete(this, user.uid)
            FirebaseData.users.child(user.uid).child("profileSetupComplete").get().addOnSuccessListener { snapshot ->
                val complete = snapshot.getValue(Boolean::class.java) == true
                profileComplete = complete
                Session.setProfileComplete(this, user.uid, complete)
            }
        }
        handler.postDelayed(navigate, 5000)
    }

    private fun route() {
        val user = FirebaseAuth.getInstance().currentUser
        val target = when {
            user == null -> LoginActivity::class.java
            profileComplete == false -> EditProfileActivity::class.java
            else -> HomeActivity::class.java
        }
        Session.startFresh(this, target)
    }

    override fun onDestroy() {
        // If the user leaves during the 5 s, don't open another screen later.
        handler.removeCallbacks(navigate)
        super.onDestroy()
    }
}
