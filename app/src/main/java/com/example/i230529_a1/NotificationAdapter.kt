package com.example.i230529_a1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import java.util.Date

data class KinnectNotification(val id: String, val senderId: String, val type: String, val body: String, val chatId: String, val createdAt: Long, val read: Boolean)

class NotificationAdapter(private val onOpen: (KinnectNotification, String) -> Unit) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {
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
        private val text = view.findViewById<TextView>(R.id.notificationText)
        private val time = view.findViewById<TextView>(R.id.notificationTime)

        fun bind(item: KinnectNotification) {
            itemView.setBackgroundColor(if (item.read) android.graphics.Color.WHITE else android.graphics.Color.rgb(228, 245, 243))
            time.text = if (item.createdAt > 0) android.text.format.DateUtils.getRelativeTimeSpanString(item.createdAt).toString() else "Just now"
            FirebaseDatabase.getInstance().getReference("profiles").child(item.senderId).get().addOnSuccessListener { profile ->
                val name = profile.child("name").getValue(String::class.java) ?: "Kinnect member"
                avatar.text = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
                text.text = if (item.type == "message" || item.type == "screenshot") "$name: ${item.body}" else "$name ${item.body}"
                itemView.setOnClickListener { onOpen(item, name) }
            }
        }
    }
}
