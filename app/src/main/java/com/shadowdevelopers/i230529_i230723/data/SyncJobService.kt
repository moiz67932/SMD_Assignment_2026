package com.shadowdevelopers.i230529_i230723.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Network-constrained, persisted queue draining. Foreground and background share one process lock. */
class SyncJobService : JobService() {
    private val executor = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    override fun onStartJob(params: JobParameters): Boolean {
        stopped.set(false)
        executor.execute {
            val retry = drain(applicationContext) { stopped.get() }
            if (!stopped.get()) jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {stopped.set(true);return true}
    override fun onDestroy(){stopped.set(true);executor.shutdown();super.onDestroy()}
    companion object {
        private fun endpoint(operation:String):String = when(operation){
            "profile.update"->"profiles/update.php"
            "post.create"->"posts/create.php"
            "post.react"->"posts/react.php"
        "post.delete"->"posts/delete.php"
        "call.status"->"calls/status.php"
        "note.set"->"notes/set.php"
        "group.create"->"groups/create.php"
        "group.send"->"groups/send.php"
        "market.create"->"market/create.php"
        "highlight.add"->"highlights/add.php"
            "comment.create"->"comments/create.php"
            "comment.like"->"comments/like.php"
            "message.send"->"messages/send.php"
            "message.edit"->"messages/edit.php"
            "message.delete"->"messages/delete.php"
        "message.heart"->"messages/heart.php"
            "chat.ensure"->"chats/ensure.php"
            "chat.screenshot"->"chats/screenshot.php"
            "story.create"->"stories/create.php"
            "story.view"->"stories/view.php"
            "device.register"->"devices/register.php"
            "device.unregister"->"devices/unregister.php"
            "media.upload"->"media/upload.php"
            "story.delete"->"stories/delete.php"
            "story.react"->"stories/react.php"
            "comment.delete"->"comments/delete.php"
            "notification.read"->"notifications/read.php"
            "notification.delete"->"notifications/delete.php"
            "close_friend.set"->"close-friends/set.php"
            else->if(operation.startsWith("friend."))"friends/action.php" else error("Unsupported operation $operation")
        }

        /**
         * Sends queued actions to the server in order (oldest first). One drainer at a time for the whole process:
         * the JobScheduler job and the "send it now" path share the [draining] flag, so actions never go out of order.
         * Returns true when something is left to retry later (offline or a temporary server error).
         */
        fun drain(context: Context, stopped: () -> Boolean = { false }): Boolean {
            if (!draining.compareAndSet(false, true)) return true
            var retry = false
            var sentAny = false
            val store = Backend.store
            try {
                val sessions = Backend.session
                val account = sessions.accountId()
                if (account != null && sessions.token() != null) {
                    val api = Backend.api
                    while (!stopped() && sessions.accountId() == account && sessions.token() != null) {
                        val action = store.claim(account)
                        if (action == null) { retry = store.hasRetryableActions(account); break }
                        try {
                            val payload = JSONObject(action.payload).put("action_uuid", action.uuid)
                            val attachments = payload.optJSONArray("local_files")
                            payload.remove("local_files")
                            val response = if (attachments != null) {
                                val uploads = (0 until attachments.length()).map { i ->
                                    val part = attachments.getJSONObject(i)
                                    ApiClient.Upload(part.getString("field"), PendingMedia.resolve(context, account, part.getString("path")), part.getString("mime"))
                                }
                                api.multipart(endpoint(action.operation), payload, uploads)
                            } else api.post(endpoint(action.operation), payload)
                            // The user may have logged out while the request was in flight.
                            if (sessions.accountId() != account || sessions.token() == null) break
                            val kind = action.operation.substringBefore('.')
                            val canonical = (response.optJSONObject(kind) ?: if (kind == "profile") response.optJSONObject("user") else null)?.toString()
                            store.acknowledge(action, kind, canonical)
                            sentAny = true
                        } catch (e: ApiClient.ApiException) {
                            if (e.status == 401) { store.fail(action, e.message ?: "Sign in required", System.currentTimeMillis() + 60_000); break }
                            val transient = e.status == 429 || e.status >= 500
                            // A permanent rejection (for example an edit after 5 minutes) is marked failed so later actions can continue.
                            store.fail(action, e.message ?: "Request failed", if (transient) System.currentTimeMillis() + 30_000 else null)
                            if (transient) { retry = true; break }
                            store.dropFailed(action)
                            Backend.reportRejected(action.operation, e.message ?: "The server refused this change")
                        } catch (e: IOException) {
                            store.fail(action, e.message ?: "Connection unavailable", System.currentTimeMillis() + 15_000); retry = true; break
                        } catch (e: Exception) {
                            store.fail(action, e.message ?: "Invalid queued operation", null); store.dropFailed(action)
                        }
                    }
                }
            } finally { draining.set(false) }
            if (sentAny) Backend.notifySynced()
            return retry
        }

        private const val JOB_ID=30301
        private val draining=AtomicBoolean(false)
        fun schedule(context:Context){
            val job=JobInfo.Builder(JOB_ID,ComponentName(context,SyncJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                .setBackoffCriteria(30_000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build()
            val scheduler=context.getSystemService(JobScheduler::class.java)
            check(scheduler.schedule(job)==JobScheduler.RESULT_SUCCESS){"Unable to schedule synchronization"}
        }
        fun cancel(context:Context){context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)}
    }
}
