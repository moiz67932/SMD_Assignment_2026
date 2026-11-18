package com.shadowdevelopers.i230529_i230723

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.shadowdevelopers.i230529_i230723.data.Backend

/**
 * Splash (01). Visible for exactly 5 seconds. The profile check starts immediately in parallel,
 * so at t = 5 s the screen can decide without waiting:
 *   logged out -> Log in
 *   logged in with a finished profile (from the database, or remembered on this phone) -> Home
 *   logged in but the profile is missing or unfinished -> Edit profile (setup step)
 *   logged in but the profile could not be read and nothing is remembered -> sign out, Log in
 *     (never show an empty Home to someone whose account the app cannot load)
 */
class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var profileComplete: Boolean? = null
    private var checkFailed = false
    private val navigate = Runnable { route() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // A stored session token is enough to continue (works offline). The server check refreshes the
        // remembered "profile finished?" flag and catches a revoked token (then the user logs in again).
        val uid = Backend.uid
        if (uid != null) {
            profileComplete = Session.profileComplete(this, uid)
            Backend.call({ Backend.me() }) { result ->
                result.onSuccess { user ->
                    val complete = user.optInt("profile_setup_complete") == 1 || user.optBoolean("profile_setup_complete")
                    profileComplete = complete
                    Session.setProfileComplete(this, uid, complete)
                }.onFailure { error ->
                    if (error is com.shadowdevelopers.i230529_i230723.data.ApiClient.ApiException && error.status == 401) checkFailed = true
                }
            }
        }
        handler.postDelayed(navigate, 5000)
    }

    private fun route() {
        val uid = Backend.uid
        val target = when {
            uid == null -> LoginActivity::class.java
            checkFailed -> {
                // The server says the token is no longer valid (logged out elsewhere or expired).
                Backend.background { Backend.logout() }
                android.widget.Toast.makeText(this, "Your session ended. Please log in again.", android.widget.Toast.LENGTH_LONG).show()
                LoginActivity::class.java
            }
            profileComplete == false -> EditProfileActivity::class.java
            // Finished profile, or offline with a stored token: straight to Home (it shows the SQLite cache).
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
