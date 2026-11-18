package com.shadowdevelopers.i230529_i230723.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Synchronous transport for repository workers; never call from the main thread. */
class ApiClient(baseUrl: String, private val sessions: SecureSession) {
    private val base = baseUrl.trimEnd('/') + "/"
    init { require(base.startsWith("https://") || base.startsWith("http://")) }
    class ApiException(val status: Int, val code: String, message: String) : IOException(message)
    fun get(path: String): JSONObject = request(path, null)
    fun post(path: String, body: JSONObject): JSONObject = request(path, body)
    private fun request(path: String, body: JSONObject?): JSONObject {
        require(!path.startsWith("/") && !path.contains("://") && !path.contains(".."))
        val connection=URL(base+path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=15_000; connection.readTimeout=30_000
            connection.instanceFollowRedirects=false
            connection.requestMethod=if(body==null)"GET" else "POST"
            connection.setRequestProperty("Accept","application/json")
            sessions.token()?.let { connection.setRequestProperty("Authorization","Bearer $it") }
            if(body!=null){connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }}
            val status=connection.responseCode
            val stream=if(status in 200..299)connection.inputStream else connection.errorStream
            val text=stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val result=try { JSONObject(text) } catch(e:Exception) { throw IOException("Server returned invalid JSON (HTTP $status)",e) }
            if(status !in 200..299){val error=result.optJSONObject("error");throw ApiException(status,error?.optString("code")?:"http_error",error?.optString("message")?:"Request failed")}
            return result
        }finally{connection.disconnect()}
    }
    /** Login credentials are used only for this request; the password is never persisted. */
    fun login(email:String,password:String):JSONObject {
        val response=post("auth/login.php",JSONObject().put("email",email).put("password",password))
        val user=response.getJSONObject("user")
        sessions.save(response.getString("token"),user.getString("id"),System.currentTimeMillis()+response.getLong("expires_in")*1000)
        return user
    }
}
