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
import com.scottyab.rootbeer.RootBeer
import online.paychek.app.config.AppConfig
import online.paychek.app.ui.screen.auth.pin.SecurityGateScreen
import online.paychek.app.ui.screen.device.RemoteLockScreen
import online.paychek.app.ui.theme.AppTheme
import online.paychek.app.utils.SecurePreferences

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

        // ─────────────────────────────────────────────────────────────────
        // ROOT DETECTION — Layer 1: RootBeer
        // সবার আগে চলে — Root পাওয়া গেলে App চলবে না।
        //
        // RootBeer checks:
        //  • su binary presence (Magisk, SuperSU, etc.)
        //  • Dangerous system properties (ro.debuggable, etc.)
        //  • Test-keys build signature
        //  • Known root app packages
        //
        // Future Layer 2 (optional): Play Integrity API
        //   → isDeviceIntegrityMet() check যোগ করা যাবে independently
        //   → Play Services dependency — তাই এখন optional রাখা হয়েছে
        // ─────────────────────────────────────────────────────────────────
        val rootBeer = RootBeer(this)
        if (rootBeer.isRooted) {
            AlertDialog.Builder(this)
                .setTitle("নিরাপত্তা সতর্কতা")
                .setMessage(
                    "এই ডিভাইসে Root সনাক্ত হয়েছে।\n\n" +
                    "নিরাপত্তার কারণে Paychek এই ডিভাইসে চলতে পারবে না। " +
                    "Root করা ডিভাইসে আপনার HMAC Secret Key এবং পেমেন্ট " +
                    "তথ্য নিরাপদ নাও থাকতে পারে।"
                )
                .setCancelable(false)
                .setPositiveButton("বন্ধ করুন") { _, _ ->
                    finishAffinity() // সব Activity বন্ধ করো
                }
                .show()
            return // বাকি onCreate চালানো হবে না
        }

        val sharedPrefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        isAppDeactivated = !sharedPrefs.getBoolean("pcu_is_app_active", true)

        // Lock the app on start if a token exists and profile is complete
        val hasToken = SecurePreferences.decrypt(this, AppConfig.KEY_AUTH_TOKEN).isNotEmpty()
        val isProfileComplete = SecurePreferences.decrypt(this, "pcu_profile_complete") != "false"
        if (hasToken && isProfileComplete) {
            isAppLocked = true
        }

        enableEdgeToEdge()

        online.paychek.app.data.remote.api.RetrofitClient.init(applicationContext)
        online.paychek.app.services.connectivity.ConnectionEngine.init(applicationContext)

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
                                onUnlockSuccess = {
                                    isAppLocked = false
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
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
        val hasToken = SecurePreferences.decrypt(this, AppConfig.KEY_AUTH_TOKEN).isNotEmpty()
        val isProfileComplete = SecurePreferences.decrypt(this, "pcu_profile_complete") != "false"
        
        val sharedPrefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        val lastBackgroundTime = sharedPrefs.getLong("last_background_time", 0)
        val currentTime = System.currentTimeMillis()
        val timePassedMs = currentTime - lastBackgroundTime
        
        if (hasToken && isProfileComplete && wasStopped && !wasRequesting && timePassedMs > 300000) {
            isAppLocked = true
        }
        wasStopped = false
    }

    override fun onResume() {
        super.onResume()
        isRequestingPermission = false
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

