package com.shadowdevelopers.i230529_i230723.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials are encrypted with a nonexportable Android Keystore key. */
class SecureSession(context: Context) {
    private val preferences = context.getSharedPreferences("secure_api_session", Context.MODE_PRIVATE)
    private val alias = "kinnect_api_session_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(token: String, accountId: String, expiresAt: Long) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val bytes=cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString("token",Base64.encodeToString(bytes,Base64.NO_WRAP))
            .putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putString("account",accountId)
            .putLong("expires_at",expiresAt).commit()) { "Unable to persist session" }
    }
    @Synchronized fun token(): String? {
        if(preferences.getLong("expires_at",0)<=System.currentTimeMillis()) return null
        val value=preferences.getString("token",null)?:return null
        return try {
            val iv=Base64.decode(preferences.getString("iv",null),Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv)) }
                .doFinal(Base64.decode(value,Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch (_: Exception) { clear(); null }
    }
    fun accountId(): String? = preferences.getString("account",null)
    @Synchronized fun clear() { check(preferences.edit().clear().commit()) }
}
