package com.shadowdevelopers.i230529_i230723

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

/** Small on-device memory (SharedPreferences "kinnect") and the logout sequence. */
object Session {
    private fun prefs(context: Context) = context.getSharedPreferences("kinnect", Context.MODE_PRIVATE)

    /** Cached "has this user finished Edit profile?" so Splash can route without waiting for the network. */
    fun setProfileComplete(context: Context, uid: String, complete: Boolean) =
        prefs(context).edit().putBoolean("profileComplete_$uid", complete).apply()

    fun profileComplete(context: Context, uid: String): Boolean? =
        prefs(context).let { if (it.contains("profileComplete_$uid")) it.getBoolean("profileComplete_$uid", true) else null }

    /** The Recent login card on Log in shows the last account that signed in on this device. */
    fun saveRecent(context: Context, email: String, name: String, photo: String?) =
        prefs(context).edit().putString("recent_email", email).putString("recent_name", name).putString("recent_photo", photo.orEmpty()).apply()

    fun recentEmail(context: Context) = prefs(context).getString("recent_email", null)
    fun recentName(context: Context) = prefs(context).getString("recent_name", null)
    fun recentPhoto(context: Context) = prefs(context).getString("recent_photo", null)
    fun clearRecent(context: Context) = prefs(context).edit().remove("recent_email").remove("recent_name").remove("recent_photo").apply()

    /** Starts a screen as the only one in the task (used after login, signup, setup and logout). */
    fun startFresh(activity: Activity, target: Class<*>) {
        activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        activity.finish()
    }

    /**
     * Log out in this order: mark offline and remove this phone's FCM token while still signed in
     * (the rules need auth for both writes), stop the global listeners, sign out, then clear the back stack.
     */
    fun logout(activity: Activity) {
        val app = activity.application as KinnectApplication
        val finish = {
            app.stopUserListeners()
            FirebaseAuth.getInstance().signOut()
            startFresh(activity, LoginActivity::class.java)
        }
        if (FirebaseAuth.getInstance().currentUser == null) {
            finish()
            return
        }
        app.setOffline { FirebaseNotifications.removeToken { finish() } }
    }
}
