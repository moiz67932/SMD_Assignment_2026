package com.shadowdevelopers.i230529_i230723

/**
 * Asks the FCM sender (fcm-server/index.js) to push a notification to someone's phones.
 * The app only writes pushQueue/{pushId}; the server reads it with the Admin SDK, sends it with FCM and deletes it.
 * entry() returns a (path, value) pair so it can go into the same multi-path update as the message itself.
 */
object PushQueue {
    fun entry(toUid: String, fromUid: String, type: String, title: String, body: String, extra: Map<String, Any> = emptyMap()): Pair<String, Any> {
        val id = FirebaseData.database.getReference("pushQueue").push().key.orEmpty()
        val item = mutableMapOf<String, Any>(
            "toUid" to toUid, "fromUid" to fromUid, "type" to type,
            "title" to title.take(80), "body" to body.take(160),
            "createdAt" to mapOf(".sv" to "timestamp")
        )
        item.putAll(extra)
        return "pushQueue/$id" to item
    }

    fun send(toUid: String, fromUid: String, type: String, title: String, body: String, extra: Map<String, Any> = emptyMap()) {
        val (path, value) = entry(toUid, fromUid, type, title, body, extra)
        FirebaseData.database.reference.child(path).setValue(value)
    }
}
