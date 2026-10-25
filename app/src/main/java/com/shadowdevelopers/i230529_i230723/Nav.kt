package com.shadowdevelopers.i230529_i230723

import android.app.Activity
import android.content.Intent
import android.view.View

/** Bottom-tab navigation: brings an already open tab to the front instead of stacking a new copy. */
object Nav {
    fun goTab(activity: Activity, target: Class<*>) {
        if (activity.javaClass == target) return
        activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    /** Wires a bottom-tab or toolbar view to a tab screen. */
    fun tab(activity: Activity, viewId: Int, target: Class<*>) {
        activity.findViewById<View>(viewId)?.setOnClickListener { goTab(activity, target) }
    }
}
