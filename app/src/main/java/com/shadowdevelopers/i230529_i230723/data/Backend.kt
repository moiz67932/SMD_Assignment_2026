package com.shadowdevelopers.i230529_i230723.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.shadowdevelopers.i230529_i230723.BuildConfig
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The single entry point screens use to talk to the Kinnect PHP API (Assignment 3).
 *
 *  - [session]: the server session token, encrypted with an Android Keystore key (SecureSession).
 *  - [api]: HTTP client that sends `Authorization: Bearer <token>` on every request.
 *  - [store]: SQLite cache of server data plus the `pending_actions` queue for offline writes.
 *
 * Network calls never run on the main thread: [call] runs the work on a background pool and delivers the
 * result on the main thread, so Activities keep simple callbacks like they had with Firebase.
 */
object Backend {
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(4)

    val session by lazy { SecureSession(app) }
    val api by lazy { ApiClient(BuildConfig.API_BASE_URL, session) }
    val store by lazy { OfflineStore(app) }

    fun init(context: Context) {
        app = context.applicationContext
        // SyncJobService reads the address from here (it can start without the Activity that knows BuildConfig).
        app.getSharedPreferences("api_config", Context.MODE_PRIVATE).edit().putString("base_url", BuildConfig.API_BASE_URL).apply()
    }

    /** The signed-in account id, or null. Works offline: the token is stored on the phone (Splash goes to Home offline). */
    val uid: String?
        get() = session.accountId()?.takeIf { session.token() != null }

    /** The raw token, for media downloads (Glide sends it as a header). */
    fun token(): String? = session.token()

    fun newId(): String = UUID.randomUUID().toString()

    /** True when the phone has a validated internet connection (a hint only: a request can still fail). */
    fun online(): Boolean {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Runs [work] off the main thread and reports the outcome on the main thread. */
    fun <T> call(work: () -> T, onDone: (Result<T>) -> Unit) {
        io.execute {
            val result = runCatching(work)
            main.post { onDone(result) }
        }
    }

    /** Fire-and-forget background work (no callback). */
    fun background(work: () -> Unit) = io.execute { runCatching(work) }

    // ----- Authentication (screens 02, 03, 19) -----

    /** POST auth/login.php; the server checks the password hash and returns a fresh session token. */
    fun login(email: String, password: String): JSONObject = api.login(email, password)

    /** POST auth/signup.php; the server hashes the password (password_hash) and signs the user in. */
    fun signup(email: String, password: String, first: String, last: String, birthday: String, gender: String): JSONObject {
        val body = JSONObject().put("email", email).put("password", password).put("name", "$first $last".trim())
            .put("first_name", first).put("last_name", last).put("birthday", birthday).put("gender", gender)
        val response = api.post("auth/signup.php", body)
        val user = response.getJSONObject("user")
        session.save(response.getString("token"), user.getString("id"), System.currentTimeMillis() + response.getLong("expires_in") * 1000)
        return user
    }

    /** GET auth/me.php: the signed-in user (and a check that the token is still valid). */
    fun me(): JSONObject = api.get("auth/me.php").getJSONObject("user")

    /**
     * Log out: the server revokes the token and ends this phone's presence (user shows offline), then the
     * phone forgets the token, clears the SQLite cache and cancels pending sync. The server call is best effort:
     * offline, the token is still deleted from the phone.
     */
    fun logout() {
        val account = session.accountId()
        runCatching { api.post("auth/logout.php", JSONObject()) }
        SyncJobService.cancel(app)
        if (account != null) store.clearAccount(account)
        session.clear()
    }

    // ----- Offline queue -----

    /**
     * Saves an optimistic local copy and the action in ONE SQLite transaction, then asks the sync job to send it.
     * The action carries a client UUID; the server uses it to ignore repeats, so a retry never duplicates anything.
     */
    fun enqueue(kind: String, entityId: String, entity: JSONObject, operation: String, payload: JSONObject): String {
        val account = uid ?: error("Not signed in")
        val actionId = store.enqueue(account, kind, entityId, entity.toString(), operation, payload.toString())
        requestSync()
        return actionId
    }

    /**
     * Sends queued actions: straight away on a background thread when online, and always schedules the persisted,
     * network-constrained job too, so they still go out after the app is closed or once the internet returns.
     */
    fun requestSync() {
        runCatching { SyncJobService.schedule(app) }
        if (online()) io.execute { runCatching { SyncJobService.drain(app) } }
    }

    // ----- Screens refresh when the server has confirmed queued actions -----

    private val syncedListeners = mutableListOf<() -> Unit>()
    fun onSynced(listener: () -> Unit) { synchronized(syncedListeners) { syncedListeners.add(listener) } }
    fun removeOnSynced(listener: () -> Unit) { synchronized(syncedListeners) { syncedListeners.remove(listener) } }
    internal fun notifySynced() {
        val copy = synchronized(syncedListeners) { syncedListeners.toList() }
        main.post { copy.forEach { it() } }
    }

    /** A queued action the server refused for good (for example an edit after 5 minutes): tell the user. */
    internal fun reportRejected(operation: String, message: String) {
        main.post { android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_LONG).show() }
        notifySynced()
    }
}
