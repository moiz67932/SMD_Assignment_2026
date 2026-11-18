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
            var retry = false
            if (draining.compareAndSet(false, true)) {
                val store = OfflineStore(applicationContext)
                try {
                    val sessions=SecureSession(applicationContext)
                    val account=sessions.accountId()
                    if(account!=null && sessions.token()!=null){
                        val base=getSharedPreferences("api_config",Context.MODE_PRIVATE).getString("base_url",null)
                        if(base!=null){
                            val api=ApiClient(base,sessions)
                            while(!stopped.get() && sessions.accountId()==account && sessions.token()!=null){
                                val action=store.claim(account)
                                if(action==null){retry=store.hasRetryableActions(account);break}
                                try {
                                    val payload=JSONObject(action.payload).put("action_uuid",action.uuid)
                                    val attachments=payload.optJSONArray("local_files")
                                    payload.remove("local_files")
                                    val response=if(attachments!=null){
                                        val uploads=(0 until attachments.length()).map { i ->
                                            val part=attachments.getJSONObject(i)
                                            ApiClient.Upload(part.getString("field"),PendingMedia.resolve(applicationContext,account,part.getString("path")),part.getString("mime"))
                                        }
                                        api.multipart(endpoint(action.operation),payload,uploads)
                                    }else api.post(endpoint(action.operation),payload)
                                    // User may have logged out while the request was in flight.
                                    if(sessions.accountId()!=account || sessions.token()==null)break
                                    val kind=action.operation.substringBefore('.')
                                    val canonical=(response.optJSONObject(kind) ?: if(kind=="profile")response.optJSONObject("user") else null)?.toString()
                                    store.acknowledge(action,kind,canonical)
                                } catch(e:ApiClient.ApiException){
                                    if(e.status==401){store.fail(action,e.message?:"Sign in required",System.currentTimeMillis()+60_000);break}
                                    val transient=e.status==429 || e.status>=500
                                    store.fail(action,e.message?:"Request failed",if(transient)System.currentTimeMillis()+30_000 else null)
                                    retry=transient;break
                                } catch(e:IOException){store.fail(action,e.message?:"Connection unavailable",System.currentTimeMillis()+30_000);retry=true;break}
                                catch(e:Exception){store.fail(action,e.message?:"Invalid queued operation",null);break}
                            }
                        }
                    }
                } finally {store.close();draining.set(false)}
            }else retry=true
            if(!stopped.get())jobFinished(params,retry)
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {stopped.set(true);return true}
    override fun onDestroy(){stopped.set(true);executor.shutdown();super.onDestroy()}
    private fun endpoint(operation:String):String = when(operation){
        "profile.update"->"profiles/update.php"
        "post.create"->"posts/create.php"
        "post.react"->"posts/react.php"
        "comment.create"->"comments/create.php"
        "comment.like"->"comments/like.php"
        "message.send"->"messages/send.php"
        "message.edit"->"messages/edit.php"
        "message.delete"->"messages/delete.php"
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
        "close_friend.set"->"close-friends/set.php"
        else->if(operation.startsWith("friend."))"friends/action.php" else error("Unsupported operation $operation")
    }
    companion object {
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
