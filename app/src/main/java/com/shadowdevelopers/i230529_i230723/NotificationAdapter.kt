package com.shadowdevelopers.i230529_i230723

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class KinnectNotification(val id: String, val senderId: String, val type: String, val body: String, val chatId: String, val postId: String, val createdAt: Long, val read: Boolean)

/** Notifications (18) rows: avatar with a type badge, bold sender name, time, and Confirm/Delete for pending requests. */
class NotificationAdapter(
    private val isPendingRequest: (String) -> Boolean,
    private val onOpen: (KinnectNotification, String) -> Unit,
    private val onConfirm: (KinnectNotification) -> Unit,
    private val onDelete: (KinnectNotification) -> Unit,
    private val onMore: (KinnectNotification, View) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {
    private val items = mutableListOf<KinnectNotification>()

    fun setItems(notifications: List<KinnectNotification>) {
        items.clear()
        items.addAll(notifications)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        return NotificationViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar = view.findViewById<TextView>(R.id.notificationAvatar)
        private val badge = view.findViewById<ImageView>(R.id.notificationBadge)
        private val text = view.findViewById<TextView>(R.id.notificationText)
        private val time = view.findViewById<TextView>(R.id.notificationTime)
        private val actions = view.findViewById<View>(R.id.notificationActions)

        fun bind(item: KinnectNotification) {
            val context = itemView.context
            itemView.tag = item.id
            itemView.setBackgroundColor(ContextCompat.getColor(context, if (item.read) R.color.white else R.color.teal_soft))
            time.text = TimeFormat.ago(item.createdAt)
            time.setTextColor(ContextCompat.getColor(context, if (item.read) R.color.text_secondary else R.color.primary))
            val (icon, circle) = badgeFor(item.type)
            badge.setImageResource(icon)
            badge.setBackgroundResource(circle)
            actions.visibility = if (item.type == "friendRequest" && isPendingRequest(item.senderId)) View.VISIBLE else View.GONE
            itemView.findViewById<View>(R.id.notificationConfirm).setOnClickListener { onConfirm(item) }
            itemView.findViewById<View>(R.id.notificationDelete).setOnClickListener { onDelete(item) }
            itemView.findViewById<View>(R.id.notificationMore).setOnClickListener { onMore(item, it) }
            text.text = sentence("Kinnect member", item)
            avatar.text = ""
            ProfileCache.get(item.senderId) { profile ->
                if (itemView.tag != item.id) return@get
                AvatarBinder.bind(avatar, profile.name, profile.photo)
                text.text = sentence(profile.name, item)
                itemView.setOnClickListener { onOpen(item, profile.name) }
            }
        }

        /** "**Name** sent you a friend request." with the name in bold, as in the design. */
        private fun sentence(name: String, item: KinnectNotification): CharSequence {
            val rest = when (item.type) {
                "message" -> " sent you a message: “${item.body}”"
                "screenshot" -> " took a screenshot of your chat."
                "call" -> " ${item.body}"
                "reaction" -> " reacted to your post."
                "comment" -> " commented on your post: “${item.body}”"
                "reply" -> " replied to your comment: “${item.body}”"
                else -> " ${item.body.trimEnd('.')}."
            }
            return SpannableStringBuilder(name).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append(rest)
            }
        }

        private fun badgeFor(type: String) = when (type) {
            "friendRequest", "friendAccepted" -> R.drawable.ic_person_add to R.drawable.circle_teal
            "message" -> R.drawable.ic_chat to R.drawable.circle_teal_mid
            "screenshot" -> R.drawable.ic_camera to R.drawable.circle_red
            "call" -> R.drawable.ic_phone to R.drawable.circle_green
            "reaction" -> R.drawable.ic_thumb_up to R.drawable.circle_red
            else -> R.drawable.ic_comment to R.drawable.circle_green
        }
    }
}
