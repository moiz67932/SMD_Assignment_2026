package com.shadowdevelopers.i230529_i230723

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Red unread counts on the chat icon (conversations with unread messages) and the notification bell
 * (unread notifications), as in the design's top bar and tab bar. Screens call start() in onStart and stop() in onStop.
 * The icon is wrapped in a FrameLayout at runtime so the badge can sit on top of it; layouts stay unchanged.
 */
class TabBadges(private val activity: Activity) {
    private val me = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val listeners = ListenerBag()
    private val perChat = mutableMapOf<String, Pair<Query, ValueEventListener>>()
    private val unreadChats = mutableSetOf<String>()
    private val chatBadge by lazy { badgeFor(R.id.chatsButton, onTab = false) }
    private val bellBadge by lazy { badgeFor(R.id.notificationsTab, onTab = true) }

    fun start() {
        if (me.isBlank()) return
        listeners.value(FirebaseData.database.getReference("notifications").child(me).orderByChild("read").equalTo(false)) { snapshot ->
            show(bellBadge, snapshot.childrenCount.toInt())
        }
        listeners.value(FirebaseData.userChats.child(me)) { snapshot ->
            val ids = snapshot.children.mapNotNull { it.key }.toSet()
            perChat.keys.filter { it !in ids }.forEach { id -> perChat.remove(id)?.let { (q, l) -> q.removeEventListener(l) }; unreadChats.remove(id) }
            ids.filter { it !in perChat }.forEach { id ->
                val query = FirebaseData.database.getReference("chats").child(id).child("unread").child(me)
                val listener = object : ValueEventListener {
                    override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                        if ((s.getValue(Int::class.java) ?: 0) > 0) unreadChats.add(id) else unreadChats.remove(id)
                        show(chatBadge, unreadChats.size)
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
                }
                query.addValueEventListener(listener)
                perChat[id] = query to listener
            }
            show(chatBadge, unreadChats.size)
        }
    }

    fun stop() {
        listeners.removeAll()
        perChat.values.forEach { (query, listener) -> query.removeEventListener(listener) }
        perChat.clear()
        unreadChats.clear()
    }

    private fun show(badge: TextView?, count: Int) {
        badge ?: return
        badge.visibility = if (count > 0) View.VISIBLE else View.GONE
        badge.text = if (count > 9) "9+" else count.toString()
    }

    /** Puts the icon inside a FrameLayout (taking over its size/weight) and adds a hidden red badge in the corner. */
    private fun badgeFor(iconId: Int, onTab: Boolean): TextView? {
        val icon = activity.findViewById<View>(iconId) ?: return null
        val parent = icon.parent as? ViewGroup ?: return null
        val density = activity.resources.displayMetrics.density
        val index = parent.indexOfChild(icon)
        val params = icon.layoutParams
        parent.removeView(icon)
        val frame = FrameLayout(activity)
        frame.addView(icon, FrameLayout.LayoutParams(params.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
            params.height.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        val badge = TextView(activity).apply {
            setBackgroundResource(R.drawable.badge_red)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 9f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            minWidth = (18 * density).toInt()
            setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            visibility = View.GONE
        }
        val badgeParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (18 * density).toInt(),
            if (onTab) Gravity.CENTER else Gravity.TOP or Gravity.END)
        if (onTab) { badge.translationX = 11 * density; badge.translationY = -10 * density }
        frame.addView(badge, badgeParams)
        if (!onTab) frame.clipChildren = false
        parent.addView(frame, index, params)
        return badge
    }
}
