package com.shadowdevelopers.i230529_i230723.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.util.UUID

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
    data class Upload(val field: String, val file: File, val mime: String)
    fun multipart(path: String, fields: JSONObject, files: List<Upload>): JSONObject {
        require(!path.startsWith("/") && !path.contains("://") && !path.contains(".."))
        val boundary="Kinnect"+UUID.randomUUID().toString()
        val connection=URL(base+path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=15_000;connection.readTimeout=60_000
            connection.instanceFollowRedirects=false;connection.requestMethod="POST";connection.doOutput=true
            connection.setChunkedStreamingMode(64*1024)
            connection.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
            sessions.token()?.let {connection.setRequestProperty("Authorization","Bearer $it")}
            connection.outputStream.buffered().use { output ->
                fun write(s:String){output.write(s.toByteArray(Charsets.UTF_8))}
                val keys=fields.keys()
                while(keys.hasNext()){
                    val key=keys.next();require(key.matches(Regex("[a-zA-Z0-9_]+")))
                    write("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n${fields.get(key)}\r\n")
                }
                files.forEach { part ->
                    require(part.field.matches(Regex("[a-zA-Z0-9_]+(\\[\\])?")))
                    require(part.mime.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")))
                    val filename=part.file.name.replace(Regex("[^a-zA-Z0-9._-]"),"_")
                    write("--$boundary\r\nContent-Disposition: form-data; name=\"${part.field}\"; filename=\"$filename\"\r\nContent-Type: ${part.mime}\r\n\r\n")
                    part.file.inputStream().use {it.copyTo(output)};write("\r\n")
                }
                write("--$boundary--\r\n")
            }
            val status=connection.responseCode
            val raw=(if(status in 200..299)connection.inputStream else connection.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
            val response=try{JSONObject(raw)}catch(e:Exception){throw IOException("Invalid multipart response (HTTP $status)",e)}
            if(status !in 200..299){val error=response.optJSONObject("error");throw ApiException(status,error?.optString("code")?:"upload_failed",error?.optString("message")?:"Upload failed")}
            return response
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
