package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * Back-stack rules (so Back never walks through every screen that was visited):
 *  1. Home is the root of the app. Back from Home leaves the app.
 *  2. The main screens (TOP_LEVEL: Home, Friends, Marketplace, Notifications, Menu, Chats, Profile, Search) are
 *     one flat group: moving between them REPLACES the current one, so the stack is never more than
 *     Home + one main screen, and one Back from any main screen returns to Home.
 *  3. Detail screens (a chat, someone's profile, comments, editors...) are pushed normally and Back goes
 *     to the screen the user came from.
 *  4. A screen that is the first of its task (opened from a notification while the app was closed) sends Back to
 *     Home instead of leaving the app.
 */
object Nav {
    private val TOP_LEVEL = setOf<Class<*>>(
        HomeActivity::class.java, FriendsActivity::class.java, MarketplaceActivity::class.java, NotificationsActivity::class.java,
        MenuActivity::class.java, ChatsActivity::class.java, ProfileActivity::class.java, SearchActivity::class.java
    )

    /** Screens that handle Back themselves or are entry points; they never get the "Back goes Home" rule. */
    private val OWN_BACK = setOf<Class<*>>(
        MainActivity::class.java, LoginActivity::class.java, SignupActivity::class.java, HomeActivity::class.java,
        IncomingCallActivity::class.java, EditProfileActivity::class.java
    )

    /** Opens a screen following the rules above. Works for tabs, toolbar buttons and ordinary screens alike. */
    fun goTab(activity: Activity, target: Class<*>) {
        if (activity.javaClass == target) return
        if (target == HomeActivity::class.java) {
            // Pops everything above Home; Home itself is reused.
            activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            return
        }
        val intent = Intent(activity, target)
        val fromMainScreen = activity.javaClass in TOP_LEVEL && activity.javaClass != HomeActivity::class.java
        if (fromMainScreen && target in TOP_LEVEL) {
            // Replace instead of stacking.
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            activity.finish()
        } else {
            activity.startActivity(intent)
        }
    }

    /** Wires a bottom-tab or toolbar view to a screen. */
    fun tab(activity: Activity, viewId: Int, target: Class<*>) {
        activity.findViewById<View>(viewId)?.setOnClickListener { goTab(activity, target) }
    }

    /**
     * Rule 4. Installed for every screen by KinnectApplication. If this screen is the first of its task and the user is
     * signed in, Back opens Home first. Otherwise Back behaves normally. Screens add their own Back handling later,
     * and handlers added later run first, so those still win.
     */
    fun installBackToHome(activity: Activity) {
        if (activity !is ComponentActivity || activity.javaClass in OWN_BACK) return
        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activity.isTaskRoot && Backend.uid != null) {
                    activity.startActivity(Intent(activity, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    activity.finish()
                } else {
                    isEnabled = false
                    activity.onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
