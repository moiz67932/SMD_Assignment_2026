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
    /** My note and my friends' notes (GET notes/list.php; the server hides expired ones). */
    fun show(activity: Activity) {
        val me = Backend.uid ?: return
        Backend.call({
            val list = Backend.api.get("notes/list.php").getJSONArray("notes")
            (0 until list.length()).map { list.getJSONObject(it) }.associate { it.getString("user_id") to it.getString("body") }
        }) { result ->
            result.onSuccess { display(activity, me, it) }
                .onFailure { Toast.makeText(activity, Forms.message(it as? Exception, "Could not load notes"), Toast.LENGTH_LONG).show() }
        }
    }

    /** Queued (works offline). An empty note deletes mine. */
    private fun save(activity: Activity, body: String, done: String) {
        Backend.call({ Backend.enqueue("note", Backend.newId(), org.json.JSONObject().put("body", body), "note.set", org.json.JSONObject().put("body", body)) }) { result ->
            result.onSuccess { Toast.makeText(activity, done, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(activity, Forms.message(it as? Exception, "Could not save the note"), Toast.LENGTH_LONG).show() }
        }
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
                    mine != null && which == 1 -> save(activity, "", "Note deleted")
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
                save(activity, text, "Note shared with your friends")
            }.setNegativeButton("Cancel", null).show()
    }
}
