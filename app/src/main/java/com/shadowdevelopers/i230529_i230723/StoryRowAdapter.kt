package com.shadowdevelopers.i230529_i230723

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Everything one person has active right now, for the Home stories row. */
data class StoryOwner(val uid: String, val stories: List<Story>, val allSeen: Boolean) {
    val latest get() = stories.maxByOrNull { it.createdAt }
}

/** Home stories row (04): position 0 is always "Create story", then one tile per person. */
class StoryRowAdapter(
    private val me: String,
    private val onCreate: () -> Unit,
    private val onOpen: (StoryOwner) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val owners = mutableListOf<StoryOwner>()

    fun setOwners(items: List<StoryOwner>) {
        owners.clear()
        owners.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = if (position == 0) TYPE_CREATE else TYPE_STORY
    override fun getItemCount() = owners.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CREATE) CreateHolder(inflater.inflate(R.layout.item_story_create, parent, false))
        else TileHolder(inflater.inflate(R.layout.item_story_tile, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is CreateHolder) holder.bind() else (holder as TileHolder).bind(owners[position - 1])
    }

    inner class CreateHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val avatar = itemView.findViewById<TextView>(R.id.createStoryAvatar)
            ProfileCache.get(me) { AvatarBinder.bind(avatar, it.name, it.photo, cornerRadiusDp = 0f) }
            itemView.setOnClickListener { onCreate() }
        }
    }

    inner class TileHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val image = view.findViewById<ImageView>(R.id.storyTileImage)
        private val ring = view.findViewById<View>(R.id.storyTileRing)
        private val avatar = view.findViewById<TextView>(R.id.storyTileAvatar)
        private val name = view.findViewById<TextView>(R.id.storyTileName)

        fun bind(owner: StoryOwner) {
            itemView.tag = owner.uid
            val latest = owner.latest
            if (latest?.type == MediaUploader.VIDEO) MediaLoader.loadVideoThumbnail(image, latest.mediaRef) else MediaLoader.load(image, latest?.mediaRef)
            ring.setBackgroundResource(if (owner.allSeen || owner.uid == me) R.drawable.story_ring_seen else R.drawable.story_ring_unseen)
            name.text = if (owner.uid == me) "Your story" else ""
            ProfileCache.get(owner.uid) { profile ->
                if (itemView.tag != owner.uid) return@get
                AvatarBinder.bind(avatar, profile.name, profile.photo)
                if (owner.uid != me) name.text = profile.name
            }
            itemView.setOnClickListener { onOpen(owner) }
        }
    }

    companion object {
        private const val TYPE_CREATE = 0
        private const val TYPE_STORY = 1
    }
}
