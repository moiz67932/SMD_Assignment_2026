package com.shadowdevelopers.i230529_i230723

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class SearchUser(val uid: String, val name: String, val username: String, val bio: String, val photo: String = "")

/** Search (13) results. The friend button text comes from the live FriendState, never from a one-time read. */
class UserSearchAdapter(
    private val relationOf: (String) -> Relation,
    private val onOpen: (SearchUser) -> Unit,
    private val onAction: (SearchUser, Relation) -> Unit
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
            val relation = relationOf(user.uid)
            name.text = user.name
            subtitle.text = listOfNotNull(
                if (relation == Relation.FRIENDS) "Friend" else null,
                user.username.takeIf { it.isNotBlank() }?.let { "@$it" }
            ).joinToString(" · ").ifBlank { user.bio }
            AvatarBinder.bind(avatar, user.name, user.photo)
            itemView.setOnClickListener { onOpen(user) }
            action.text = FriendState.label(relation)
            // Teal for an action you can take, light grey once you are friends or the request is pending.
            val filled = relation == Relation.NONE || relation == Relation.INCOMING
            action.setBackgroundResource(if (filled) R.drawable.rounded_teal else R.drawable.rounded_light)
            action.setTextColor(ContextCompat.getColor(itemView.context, if (filled) R.color.white else R.color.text_primary))
            action.setOnClickListener { onAction(user, relation) }
        }
    }
}
