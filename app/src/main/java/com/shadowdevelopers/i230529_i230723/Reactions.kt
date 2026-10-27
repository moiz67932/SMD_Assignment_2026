package com.shadowdevelopers.i230529_i230723

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.database.DataSnapshot

/**
 * The six post reactions. Stored as postReactions/{postId}/{uid} = "Like" | "Love" | ..., so each user has
 * exactly one reaction per post: writing a new value changes it, removing the node removes it.
 */
object Reactions {
    val TYPES = listOf("Like", "Love", "Laugh", "Wow", "Sad", "Angry")

    fun icon(type: String) = when (type) {
        "Love" -> R.drawable.reaction_love
        "Laugh" -> R.drawable.reaction_laugh
        "Wow" -> R.drawable.reaction_wow
        "Sad" -> R.drawable.reaction_sad
        "Angry" -> R.drawable.reaction_angry
        else -> R.drawable.reaction_like
    }

    fun color(type: String?) = when (type) {
        null -> R.color.text_primary
        "Love", "Angry" -> R.color.reaction_pink
        "Laugh", "Wow", "Sad" -> R.color.reaction_yellow
        else -> R.color.primary
    }

    /** total reactions, the three most used types (for the icons), the user's own reaction, and one other reactor. */
    data class Summary(val total: Int, val top: List<String>, val mine: String?, val someoneElse: String?, val counts: Map<String, Int>)

    fun summarize(snapshot: DataSnapshot, me: String): Summary {
        val all = snapshot.children.mapNotNull { child -> child.getValue(String::class.java)?.takeIf { it in TYPES }?.let { child.key.orEmpty() to it } }
        val counts = all.groupingBy { it.second }.eachCount()
        return Summary(
            total = all.size,
            top = counts.entries.sortedByDescending { it.value }.take(3).map { it.key },
            mine = all.firstOrNull { it.first == me }?.second,
            someoneElse = all.firstOrNull { it.first != me }?.first,
            counts = counts
        )
    }

    /** 950 -> "950", 1240 -> "1.2K", 3400000 -> "3.4M" */
    fun compact(value: Int): String = when {
        value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000f).replace(".0M", "M")
        value >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", value / 1_000f).replace(".0K", "K")
        else -> value.toString()
    }

    /** Fills the three summary icons and the "You and 3 others" / "Aisha and 1.2K others" line. */
    fun bindSummary(icons: List<ImageView>, label: TextView, summary: Summary) {
        icons.forEachIndexed { index, view ->
            val type = summary.top.getOrNull(index)
            view.visibility = if (type == null) View.GONE else View.VISIBLE
            if (type != null) view.setImageResource(icon(type))
        }
        val token = Any()
        label.tag = token
        val others = summary.total - 1
        label.text = when {
            summary.total == 0 -> ""
            summary.mine != null -> if (others == 0) "You" else "You and ${compact(others)} ${if (others == 1) "other" else "others"}"
            else -> compact(summary.total)
        }
        val firstOther = summary.someoneElse
        if (summary.mine == null && firstOther != null) {
            ProfileCache.get(firstOther) { profile ->
                if (label.tag !== token) return@get
                val first = profile.name.substringBefore(" ")
                label.text = if (others == 0) first else "$first and ${compact(others)} ${if (others == 1) "other" else "others"}"
            }
        }
    }

    /** Sets, changes or removes my reaction. Tapping the reaction I already have removes it. */
    fun react(context: Context, postId: String, authorId: String, me: String, type: String, current: String?) {
        val reference = FirebaseData.postReactions.child(postId).child(me)
        val task = if (current == type) reference.removeValue() else reference.setValue(type)
        task.addOnFailureListener { Toast.makeText(context, it.localizedMessage ?: "Could not update your reaction", Toast.LENGTH_SHORT).show() }
        // Tell the author about a new reaction (not about changes or removals, and not about my own posts).
        if (current == null && authorId.isNotBlank() && authorId != me) {
            FirebaseData.database.getReference("notifications").child(authorId).push().setValue(mapOf(
                "senderId" to me, "type" to "reaction", "body" to type, "postId" to postId,
                "createdAt" to mapOf(".sv" to "timestamp"), "read" to false
            ))
        }
    }
}
