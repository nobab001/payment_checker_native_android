package online.paychek.app.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import online.paychek.app.config.AppConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurePreferences {
    private const val TAG = "SecurePreferences"
    private const val KEY_ALIAS = "PaychekSecureKey"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREF_NAME = "paychek_secure_prefs"

    @Volatile
    private var cachedSecretKey: SecretKey? = null

    private fun getSecretKey(): SecretKey {
        cachedSecretKey?.let { return it }
        synchronized(this) {
            cachedSecretKey?.let { return it }
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                cachedSecretKey = existing
                return existing
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            keyGenerator.init(spec)
            val created = keyGenerator.generateKey()
            cachedSecretKey = created
            return created
        }
    }

    /**
     * Pre-load Android Keystore on a background thread after boot/app start
     * so the first UI decrypt does not stall the main thread.
     */
    fun warmUp(context: Context) {
        try {
            getSecretKey()
            SessionFlags.migrateFromSecureStore(context.applicationContext)
            Log.i(TAG, "Keystore warm-up complete")
        } catch (e: Exception) {
            Log.w(TAG, "Keystore warm-up failed: ${e.message}")
        }
    }

    fun encrypt(context: Context, key: String, value: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val combined = "$ivBase64:$encryptedBase64"

            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(key, combined).apply()
            mirrorSessionFlag(context, key, value)
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
        mirrorSessionFlag(context, key, null)
    }

    private fun mirrorSessionFlag(context: Context, key: String, value: String?) {
        when (key) {
            AppConfig.KEY_AUTH_TOKEN -> {
                if (value.isNullOrEmpty()) SessionFlags.clear(context)
                else SessionFlags.setHasAuth(context, true)
            }
            "pcu_profile_complete" -> {
                SessionFlags.setProfileComplete(context, value != "false")
            }
            "pcu_user_role" -> {
                SessionFlags.setUserRole(context, value ?: "")
            }
            "pcu_contact" -> {
                SessionFlags.setContact(context, value ?: "")
            }
        }
    }
}
