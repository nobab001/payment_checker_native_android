package online.paychek.app.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurePreferences {
    private const val KEY_ALIAS = "PaychekSecureKey"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREF_NAME = "paychek_secure_prefs"

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (secretKey != null) return secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encrypt(context: Context, key: String, value: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            
            // Format: Base64(iv) + ":" + Base64(encryptedBytes)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val combined = "$ivBase64:$encryptedBase64"
            
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(key, combined).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun decrypt(context: Context, key: String): String {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val combined = prefs.getString(key, null) ?: return ""
            val parts = combined.split(":")
            if (parts.size != 2) return ""
            
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    fun remove(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(key).apply()
    }
}
