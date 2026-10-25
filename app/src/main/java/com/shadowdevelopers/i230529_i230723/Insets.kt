package com.shadowdevelopers.i230529_i230723

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-edge support. targetSdk 37 always draws the app behind the status and navigation bars,
 * so every screen's root view gets padding equal to the bars (and the keyboard, when it is open).
 * KinnectApplication calls these two functions for every Activity, so screens don't have to.
 */
object Insets {
    /** Called before setContentView: lets the app draw behind the system bars on every API level. */
    fun prepare(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }

    /** Called once the layout exists: pads the root view and picks dark or light status-bar icons. */
    fun apply(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        if (root.getTag(R.id.insetsApplied) == true) return
        root.setTag(R.id.insetsApplied, true)

        val start = root.paddingLeft
        val top = root.paddingTop
        val end = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(start + bars.left, top + bars.top, end + bars.right, bottom + maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)

        // Dark screens (camera, stories, call, splash) need light status-bar icons.
        val controller = WindowCompat.getInsetsController(activity.window, root)
        val light = !isDark(root)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    private fun isDark(view: View): Boolean {
        val color = (view.background as? ColorDrawable)?.color ?: return false
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return luminance < 0.5
    }
}
