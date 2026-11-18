package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** Dialogs that let the user choose one or more of their friends (Send story, Groups, Share, Tag...). */
object FriendPicker {
    /** Loads my friends with their names; answers with (uid, name) pairs, sorted by name. */
    fun load(activity: Activity, onReady: (List<Pair<String, String>>) -> Unit) {
        val me = Backend.uid ?: return
        // My friends from the PHP API (cached in SQLite, so the picker also works offline).
        Backend.call({ runCatching { Backend.api.get("friends/list.php") }.getOrNull()?.also { Backend.store.putEntity(me, "friends", "all", it.toString()) }
            ?: Backend.store.entity(me, "friends", "all")?.let { org.json.JSONObject(it) } }) { result ->
            val list = result.getOrNull()?.optJSONArray("friends") ?: org.json.JSONArray()
            val friends = (0 until list.length()).map { list.getJSONObject(it).also(ProfileCache::putJson).getString("id") }
            if (friends.isEmpty()) {
                Toast.makeText(activity, "Add friends first", Toast.LENGTH_SHORT).show()
                return@call
            }
            val result = arrayOfNulls<Pair<String, String>>(friends.size)
            var left = friends.size
            friends.forEachIndexed { index, uid ->
                ProfileCache.get(uid) { profile ->
                    result[index] = uid to profile.name
                    left--
                    if (left == 0 && !activity.isFinishing) onReady(result.filterNotNull().sortedBy { it.second.lowercase() })
                }
            }
        }
    }

    fun single(activity: Activity, title: String, onPick: (uid: String, name: String) -> Unit) {
        load(activity) { friends ->
            AlertDialog.Builder(activity).setTitle(title)
                .setItems(friends.map { it.second }.toTypedArray()) { _, which -> onPick(friends[which].first, friends[which].second) }
                .setNegativeButton("Cancel", null).show()
        }
    }

    fun multi(activity: Activity, title: String, preselected: Set<String> = emptySet(), onDone: (List<Pair<String, String>>) -> Unit) {
        load(activity) { friends ->
            val checked = BooleanArray(friends.size) { friends[it].first in preselected }
            AlertDialog.Builder(activity).setTitle(title)
                .setMultiChoiceItems(friends.map { it.second }.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
                .setPositiveButton("Done") { _, _ -> onDone(friends.filterIndexed { index, _ -> checked[index] }) }
                .setNegativeButton("Cancel", null).show()
        }
    }
}
