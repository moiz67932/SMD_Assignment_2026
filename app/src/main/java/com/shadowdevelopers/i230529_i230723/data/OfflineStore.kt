package com.shadowdevelopers.i230529_i230723.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/** Durable local entities and ordered mutations. All operations must run off the UI thread. */
class OfflineStore(context: Context) : SQLiteOpenHelper(context, "kinnect_offline.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entities(account_id TEXT NOT NULL,kind TEXT NOT NULL,id TEXT NOT NULL,json TEXT NOT NULL,pending INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(account_id,kind,id))")
        db.execSQL("CREATE TABLE pending_actions(sequence INTEGER PRIMARY KEY AUTOINCREMENT,action_uuid TEXT NOT NULL UNIQUE,account_id TEXT NOT NULL,operation TEXT NOT NULL,entity_id TEXT NOT NULL,payload_json TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'queued',attempt_count INTEGER NOT NULL DEFAULT 0,next_attempt_at INTEGER NOT NULL DEFAULT 0,lease_until INTEGER NOT NULL DEFAULT 0,last_error TEXT)")
        db.execSQL("CREATE INDEX pending_account_sequence ON pending_actions(account_id,sequence)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Missing migration from $oldVersion to $newVersion; refusing to discard offline actions")
    }
    data class Action(val sequence: Long, val uuid: String, val account: String, val operation: String, val entityId: String, val payload: String)

    /** Persist the optimistic entity and its queue item together before showing success. */
    @Synchronized fun enqueue(account: String, kind: String, entityId: String, entityJson: String, operation: String, payload: String): String {
        val uuid = UUID.randomUUID().toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            putEntity(db, account, kind, entityId, entityJson, true)
            db.insertOrThrow("pending_actions", null, ContentValues().apply {
                put("action_uuid", uuid); put("account_id", account); put("operation", operation)
                put("entity_id", entityId); put("payload_json", payload)
            })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return uuid
    }
    @Synchronized fun claim(account: String, now: Long = System.currentTimeMillis()): Action? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT sequence,action_uuid,operation,entity_id,payload_json,state,next_attempt_at,lease_until FROM pending_actions WHERE account_id=? ORDER BY sequence LIMIT 1", arrayOf(account)).use { c ->
                if (!c.moveToFirst() || c.getString(5) == "failed" || c.getLong(6) > now || c.getLong(7) > now) return null
                val action = Action(c.getLong(0), c.getString(1), account, c.getString(2), c.getString(3), c.getString(4))
                db.execSQL("UPDATE pending_actions SET state='in_flight',lease_until=?,attempt_count=attempt_count+1 WHERE sequence=?", arrayOf(now + 120_000, action.sequence))
                db.setTransactionSuccessful()
                return action
            }
        } finally { db.endTransaction() }
    }
    @Synchronized fun acknowledge(action: Action, kind: String, canonicalJson: String?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("pending_actions", "account_id=? AND action_uuid=?", arrayOf(action.account, action.uuid))
            // Never overwrite a newer optimistic change when an older operation is acknowledged.
            val newer = db.rawQuery("SELECT 1 FROM pending_actions WHERE account_id=? AND entity_id=? LIMIT 1", arrayOf(action.account,action.entityId)).use { it.moveToFirst() }
            if (!newer) {
                if (canonicalJson != null) putEntity(db, action.account, kind, action.entityId, canonicalJson, false)
                else db.execSQL("UPDATE entities SET pending=0 WHERE account_id=? AND kind=? AND id=?", arrayOf(action.account,kind,action.entityId))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun fail(action: Action, error: String, retryAt: Long?) {
        writableDatabase.update("pending_actions", ContentValues().apply {
            put("state", if (retryAt == null) "failed" else "queued"); put("last_error", error)
            put("lease_until", 0); put("next_attempt_at", retryAt ?: 0)
        }, "account_id=? AND action_uuid=?", arrayOf(action.account, action.uuid))
    }
    /** A permanently rejected action is removed from the queue (its optimistic copy is rolled back by the next refresh). */
    @Synchronized fun dropFailed(action: Action) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("pending_actions", "account_id=? AND action_uuid=?", arrayOf(action.account, action.uuid))
            db.execSQL("DELETE FROM entities WHERE account_id=? AND id=? AND pending=1 AND NOT EXISTS (SELECT 1 FROM pending_actions WHERE account_id=? AND entity_id=?)",
                arrayOf(action.account, action.entityId, action.account, action.entityId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** How many of my actions are still waiting to reach the server (shown as "pending"). */
    fun pendingCount(account: String): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM pending_actions WHERE account_id=?", arrayOf(account)).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Ids of entities that still have a queued action (drawn with the "pending" look). */
    fun pendingEntityIds(account: String): Set<String> = readableDatabase.rawQuery("SELECT DISTINCT entity_id FROM pending_actions WHERE account_id=?", arrayOf(account)).use { c ->
        buildSet { while (c.moveToNext()) add(c.getString(0)) }
    }

    fun hasRetryableActions(account: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM pending_actions WHERE account_id=? AND state<>'failed' LIMIT 1",arrayOf(account)).use { it.moveToFirst() }
    fun entities(account: String, kind: String): List<String> = readableDatabase.rawQuery("SELECT json FROM entities WHERE account_id=? AND kind=? ORDER BY rowid", arrayOf(account,kind)).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }
    @Synchronized fun clearAccount(account: String) {
        val db=writableDatabase; db.beginTransaction()
        try {
            db.delete("pending_actions", "account_id=?", arrayOf(account))
            db.delete("entities", "account_id=?", arrayOf(account)); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    /** Cache a server object (not pending). Screens read these first, so they work offline. */
    @Synchronized fun putEntity(account: String, kind: String, id: String, json: String) = putEntity(writableDatabase, account, kind, id, json, false)

    /** Replace the whole cached list of one kind in one transaction (for example the feed after a refresh). Pending rows stay. */
    @Synchronized fun replaceEntities(account: String, kind: String, items: List<Pair<String, String>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("entities", "account_id=? AND kind=? AND pending=0", arrayOf(account, kind))
            items.forEach { (id, json) ->
                val pending = db.rawQuery("SELECT 1 FROM entities WHERE account_id=? AND kind=? AND id=? AND pending=1", arrayOf(account, kind, id)).use { it.moveToFirst() }
                if (!pending) putEntity(db, account, kind, id, json, false)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun entity(account: String, kind: String, id: String): String? =
        readableDatabase.rawQuery("SELECT json FROM entities WHERE account_id=? AND kind=? AND id=?", arrayOf(account, kind, id)).use { if (it.moveToFirst()) it.getString(0) else null }

    /** Cached rows of one kind with their pending flag (pending = queued offline, not yet on the server). */
    fun entitiesWithState(account: String, kind: String): List<Pair<String, Boolean>> =
        readableDatabase.rawQuery("SELECT json,pending FROM entities WHERE account_id=? AND kind=? ORDER BY rowid", arrayOf(account, kind)).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to (c.getInt(1) == 1)) }
        }

    @Synchronized fun deleteEntity(account: String, kind: String, id: String) {
        writableDatabase.delete("entities", "account_id=? AND kind=? AND id=?", arrayOf(account, kind, id))
    }

    private fun putEntity(db: SQLiteDatabase, account: String, kind: String, id: String, json: String, pending: Boolean) {
        db.insertWithOnConflict("entities",null,ContentValues().apply {
            put("account_id",account); put("kind",kind); put("id",id); put("json",json); put("pending",if(pending)1 else 0)
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
}
