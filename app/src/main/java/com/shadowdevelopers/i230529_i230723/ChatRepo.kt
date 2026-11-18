package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend

/** Chats (20) / Chat (21) data from the PHP API, cached in SQLite. Blocking calls: run them through Backend.call. */
object ChatRepo {
    /** How many conversations have unread messages (red badge on the chat icon). */
    fun unreadConversations(): Int = runCatching {
        val list = Backend.api.get("chats/list.php").getJSONArray("chats")
        (0 until list.length()).count { list.getJSONObject(it).optInt("unread") > 0 }
    }.getOrDefault(0)
}
