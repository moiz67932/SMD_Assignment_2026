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
        FirebaseData.friends.child(me).get().addOnSuccessListener { snapshot ->
            val friends = snapshot.children.filter { it.getValue(Boolean::class.java) == true }.mapNotNull { it.key }
            if (friends.isEmpty()) {
                Toast.makeText(activity, "Add friends first", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
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
        }.addOnFailureListener { Toast.makeText(activity, Forms.message(it, "Could not load your friends"), Toast.LENGTH_LONG).show() }
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
