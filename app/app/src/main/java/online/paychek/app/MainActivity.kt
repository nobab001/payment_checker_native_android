package online.paychek.app

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.scottyab.rootbeer.RootBeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.utils.SecurePreferences
import online.paychek.app.ui.screen.auth.pin.SecurityGateScreen
import online.paychek.app.ui.screen.device.RemoteLockScreen
import online.paychek.app.ui.theme.AppTheme
import online.paychek.app.utils.SessionFlags

/**
 * Cold-start optimized:
 *  - No Keystore decrypt / RootBeer on the main thread before first frame
 *  - Session lock uses plain [SessionFlags] (instant after reboot)
 *  - Root check runs after UI is shown
 */
class MainActivity : FragmentActivity() {
    private var isAppLocked by mutableStateOf(false)
    private var isAppDeactivated by mutableStateOf(false)
    private var wasStopped = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "pcu_is_app_active") {
            isAppDeactivated = !prefs.getBoolean("pcu_is_app_active", true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        isAppDeactivated = !sharedPrefs.getBoolean("pcu_is_app_active", true)

        // Fast path — plain prefs only (no Android Keystore)
        if (SessionFlags.hasAuth(this) && SessionFlags.isProfileComplete(this)) {
            isAppLocked = true
        }

        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainNavigation()

                        if (isAppDeactivated) {
                            RemoteLockScreen(modifier = Modifier.fillMaxSize())
                        } else if (isAppLocked) {
                            SecurityGateScreen(
                                onUnlockSuccess = { isAppLocked = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // Root check AFTER first frame — RootBeer is slow on cold boot
        lifecycleScope.launch(Dispatchers.Default) {
            val rooted = try {
                RootBeer(this@MainActivity).isRooted
            } catch (_: Exception) {
                false
            }
            if (rooted) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("নিরাপত্তা সতর্কতা")
                        .setMessage(
                            "এই ডিভাইসে Root সনাক্ত হয়েছে।\n\n" +
                                "নিরাপত্তার কারণে Paychek এই ডিভাইসে চলতে পারবে না। " +
                                "Root করা ডিভাইসে আপনার HMAC Secret Key এবং পেমেন্ট " +
                                "তথ্য নিরাপদ নাও থাকতে পারে।"
                        )
                        .setCancelable(false)
                        .setPositiveButton("বন্ধ করুন") { _, _ -> finishAffinity() }
                        .show()
                }
            }
        }
    }

    companion object {
        var isRequestingPermission = false
    }

    override fun onStart() {
        val wasRequesting = isRequestingPermission
        super.onStart()

        val sharedPrefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        val lastBackgroundTime = sharedPrefs.getLong("last_background_time", 0)
        val timePassedMs = System.currentTimeMillis() - lastBackgroundTime

        val hasSession = SessionFlags.hasAuth(this) && SessionFlags.isProfileComplete(this)
        if (hasSession && wasStopped && !wasRequesting && timePassedMs > 300000) {
            isAppLocked = true
        }
        wasStopped = false
    }

    override fun onResume() {
        super.onResume()
        isRequestingPermission = false
        if (SessionFlags.hasAuth(this) && SessionFlags.isProfileComplete(this)) {
            online.paychek.app.services.foreground.SmsServiceGuard.startIfEnabled(this)
            healDeviceConfigCache()
        }
    }

    private fun healDeviceConfigCache() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = SecurePreferences.decrypt(this@MainActivity, AppConfig.KEY_AUTH_TOKEN)
                if (token.isEmpty()) return@launch
                val authHeader = "Bearer $token"
                val statusRes = RetrofitClient.gatewayApiService.checkApprovalStatus(authHeader)
                if (statusRes.isSuccessful) {
                    val body = statusRes.body() ?: return@launch
                    SecurePreferences.encrypt(this@MainActivity, "pcu_is_approved", if (body.isApproved) "true" else "false")
                    SecurePreferences.encrypt(this@MainActivity, "pcu_device_role", body.deviceRole ?: "pending")
                    SecurePreferences.encrypt(
                        this@MainActivity,
                        AppConfig.KEY_IS_OWNER_DEVICE,
                        if (body.deviceRole == "owner") "true" else "false"
                    )
                    if (!body.deviceSpecificPin.isNullOrEmpty()) {
                        SecurePreferences.encrypt(this@MainActivity, AppConfig.KEY_DEVICE_SPECIFIC_PIN, body.deviceSpecificPin)
                    } else {
                        SecurePreferences.remove(this@MainActivity, AppConfig.KEY_DEVICE_SPECIFIC_PIN)
                    }
                }
                val configRes = RetrofitClient.gatewayApiService.getMyDeviceConfig(authHeader)
                if (configRes.isSuccessful && configRes.body()?.success == true) {
                    val device = configRes.body()!!.data
                    SecurePreferences.encrypt(this@MainActivity, "pcu_device_role", device.deviceRole)
                    SecurePreferences.encrypt(
                        this@MainActivity,
                        AppConfig.KEY_IS_OWNER_DEVICE,
                        if (device.deviceRole == "owner") "true" else "false"
                    )
                    if (!device.deviceSpecificPin.isNullOrEmpty()) {
                        SecurePreferences.encrypt(this@MainActivity, AppConfig.KEY_DEVICE_SPECIFIC_PIN, device.deviceSpecificPin)
                    }
                }
            } catch (_: Exception) {
                // Best-effort cache heal
            }
        }
    }

    override fun onStop() {
        super.onStop()
        wasStopped = true
        val sharedPrefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putLong("last_background_time", System.currentTimeMillis()).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        val sharedPrefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
