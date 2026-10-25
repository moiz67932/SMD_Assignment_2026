package com.shadowdevelopers.i230529_i230723

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot

data class SearchUser(val uid: String, val name: String, val username: String, val bio: String)

class UserSearchAdapter(
    private val onOpen: (SearchUser) -> Unit,
    private val onRequest: (SearchUser, Button) -> Unit,
    private val onAccept: (SearchUser, Button) -> Unit
) : RecyclerView.Adapter<UserSearchAdapter.UserViewHolder>() {
    private val users = mutableListOf<SearchUser>()

    fun setUsers(items: List<SearchUser>) {
        users.clear()
        users.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        return UserViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false))
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) = holder.bind(users[position])
    override fun getItemCount() = users.size

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.userName)
        private val subtitle = view.findViewById<TextView>(R.id.userSubtitle)
        private val avatar = view.findViewById<TextView>(R.id.userAvatar)
        private val action = view.findViewById<Button>(R.id.userFriendAction)

        fun bind(user: SearchUser) {
            name.text = user.name
            subtitle.text = if (user.username.isBlank()) user.bio else "@${user.username}"
            avatar.text = user.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            itemView.setOnClickListener { onOpen(user) }
            action.tag = user.uid
            action.isEnabled = false
            action.text = "…"
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseData.friends.child(uid).child(user.uid).get().addOnSuccessListener { friend ->
                if (action.tag != user.uid) return@addOnSuccessListener
                if (friend.getValue(Boolean::class.java) == true) {
                    action.text = "Friends"
                    action.isEnabled = false
                    return@addOnSuccessListener
                }
                FirebaseData.friendRequests.child(uid).child(user.uid).get().addOnSuccessListener { incoming ->
                    if (action.tag != user.uid) return@addOnSuccessListener
                    if (incoming.exists()) {
                        action.text = "Confirm"
                        action.isEnabled = true
                        action.setOnClickListener { onAccept(user, action) }
                    } else {
                        FirebaseData.friendRequests.child(user.uid).child(uid).get().addOnSuccessListener { outgoing ->
                            if (action.tag != user.uid) return@addOnSuccessListener
                            action.text = if (outgoing.exists()) "Requested" else "Add friend"
                            action.isEnabled = !outgoing.exists()
                            action.setOnClickListener { onRequest(user, action) }
                        }
                    }
                }
            }
        }
    }
}
