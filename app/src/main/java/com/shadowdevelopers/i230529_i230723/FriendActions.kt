package com.shadowdevelopers.i230529_i230723

import android.content.Context
import android.widget.Toast
import com.shadowdevelopers.i230529_i230723.data.Backend
import org.json.JSONObject

/**
 * Every friend-request change in one place (Search 13, Friends 14, Other profile 17, Notifications 18).
 * Each change is shown at once and saved to the SQLite pending_actions queue with a client UUID, then sent to
 * POST friends/action.php (operation send / cancel / accept / decline / remove). Offline, it waits and syncs
 * automatically; the server uses the UUID so a retry never creates a second request.
 */
object FriendActions {
    fun sendRequest(context: Context, me: String, other: String) = act(context, other, "send", "requested", "Could not send the request")
    fun cancelRequest(context: Context, me: String, other: String) = act(context, other, "cancel", "none", "Could not cancel the request")
    fun confirm(context: Context, me: String, other: String) = act(context, other, "accept", "friends", "Could not confirm the request")
    fun deleteIncoming(context: Context, me: String, other: String) = act(context, other, "decline", "none", "Could not delete the request")
    fun unfriend(context: Context, me: String, other: String) = act(context, other, "remove", "none", "Could not remove this friend")

    private fun act(context: Context, other: String, operation: String, newState: String, error: String) {
        Friends.applyLocal(other, newState)
        Friends.markPending(other)
        val entity = JSONObject().put("other", other).put("state", newState)
        val payload = JSONObject().put("other_id", other).put("operation", operation)
        Backend.call({ Backend.enqueue("relation", other, entity, "friend.$operation", payload) }) { result ->
            result.onFailure {
                Toast.makeText(context, Forms.message(it as? Exception, error), Toast.LENGTH_LONG).show()
                Friends.refresh()
            }
        }
    }
}
