package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.app.Activity
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Notes: a short text (up to 60 characters) that friends can see for 24 hours.
 *   notes/{uid} = { text, createdAt, expiresAt }
 */
object Notes {
    private val notes get() = FirebaseData.database.getReference("notes")
    private const val LIFETIME_MS = 24L * 60 * 60 * 1000

    /** Shows my note and my friends' notes; lets me write, change or delete mine. */
    fun show(activity: Activity) {
        val me = Backend.uid ?: return
        FirebaseData.friends.child(me).get().addOnSuccessListener { friendSnap ->
            val friends = friendSnap.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNull { it.key }
            val authors = listOf(me) + friends
            val found = mutableMapOf<String, String>()
            var left = authors.size
            authors.forEach { uid ->
                notes.child(uid).get().addOnCompleteListener { task ->
                    val text = task.result?.child("text")?.getValue(String::class.java).orEmpty()
                    val expires = task.result?.child("expiresAt")?.getValue(Long::class.java) ?: 0L
                    if (task.isSuccessful && text.isNotBlank() && expires > FirebaseData.now()) found[uid] = text
                    if (--left == 0 && !activity.isFinishing) display(activity, me, found)
                }
            }
        }.addOnFailureListener { Toast.makeText(activity, Forms.message(it, "Could not load notes"), Toast.LENGTH_LONG).show() }
    }

    private fun display(activity: Activity, me: String, found: Map<String, String>) {
        val mine = found[me]
        val others = found.filterKeys { it != me }
        val names = mutableMapOf<String, String>()
        var left = others.size
        fun finish() {
            val items = mutableListOf(if (mine == null) "Write a note…" else "Your note: \"$mine\" (tap to change)")
            if (mine != null) items += "Delete my note"
            others.forEach { (uid, text) -> items += "${names[uid] ?: "Friend"}: $text" }
            if (others.isEmpty()) items += "No notes from friends right now"
            AlertDialog.Builder(activity).setTitle("Notes").setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> write(activity, me, mine.orEmpty())
                    mine != null && which == 1 -> notes.child(me).removeValue().addOnSuccessListener { Toast.makeText(activity, "Note deleted", Toast.LENGTH_SHORT).show() }
                }
            }.setNegativeButton("Close", null).show()
        }
        if (others.isEmpty()) return finish()
        others.keys.forEach { uid ->
            ProfileCache.get(uid) { profile ->
                names[uid] = profile.name.substringBefore(" ")
                if (--left == 0) finish()
            }
        }
    }

    private fun write(activity: Activity, me: String, current: String) {
        val field = EditText(activity).apply {
            hint = "Share a thought…"
            setText(current)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(60))
            val pad = (20 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(activity).setTitle("Your note (24 hours)").setView(field)
            .setPositiveButton("Share") { _, _ ->
                val text = field.text.toString().trim()
                if (text.isBlank()) return@setPositiveButton
                notes.child(me).setValue(mapOf("text" to text, "createdAt" to mapOf(".sv" to "timestamp"), "expiresAt" to FirebaseData.now() + LIFETIME_MS))
                    .addOnSuccessListener { Toast.makeText(activity, "Note shared with your friends", Toast.LENGTH_SHORT).show() }
                    .addOnFailureListener { Toast.makeText(activity, Forms.message(it, "Could not share the note"), Toast.LENGTH_LONG).show() }
            }.setNegativeButton("Cancel", null).show()
    }
}
