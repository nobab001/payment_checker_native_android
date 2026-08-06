package online.paychek.app

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.pm.PackageInfoCompat
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
import online.paychek.app.ui.component.AdminNoticeDialog
import online.paychek.app.utils.SecurePreferences
import online.paychek.app.ui.screen.auth.pin.SecurityGateScreen
import online.paychek.app.ui.screen.device.RemoteLockScreen
import online.paychek.app.ui.screen.maintenance.MaintenanceScreen
import online.paychek.app.ui.theme.AppTheme
import online.paychek.app.utils.SessionFlags
import online.paychek.app.utils.SessionLockPolicy

/**
 * Cold-start optimized:
 *  - No Keystore decrypt / RootBeer on the main thread before first frame
 *  - Session lock uses plain [SessionFlags] (instant after reboot)
 *  - Root check runs after UI is shown
 *  - Cold start and background resume share the same 5-minute [SessionLockPolicy]
 */
class MainActivity : FragmentActivity() {
    private var isAppLocked by mutableStateOf(false)
    private var isAppDeactivated by mutableStateOf(false)
    private var isMaintenanceBlocked by mutableStateOf(false)
    /** Bumped after each admin-notice fetch so the popup re-reads its queue. */
    private var noticeRefreshKey by mutableStateOf(0)
    private var wasStopped = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "pcu_is_app_active") {
            isAppDeactivated = !prefs.getBoolean("pcu_is_app_active", true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // APK update crash fix: Navigation3 rememberNavBackStack পুরনো Bundle থেকে
        // NavKey deserialize করতে গিয়ে SerializationException throw করে।
        // Fix: version change ধরা পড়লে savedInstanceState null করে দেওয়া হয় —
        // fresh navigation শুরু হয়, কোনো stale back stack restore হয় না।
        val effectiveSavedState = if (hasVersionChanged()) null else savedInstanceState
        super.onCreate(effectiveSavedState)

        val sharedPrefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        isAppDeactivated = !sharedPrefs.getBoolean("pcu_is_app_active", true)

        // Fast path — plain prefs only (no Android Keystore).
        // Same 5-minute rule as background resume (not always-lock on cold start).
        isAppLocked = shouldLockForCurrentSession(skipWasStoppedCheck = true)

        consumeBillingSuccessIntent(intent)

        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainNavigation()

                        if (isMaintenanceBlocked) {
                            MaintenanceScreen(modifier = Modifier.fillMaxSize())
                        } else if (isAppDeactivated) {
                            RemoteLockScreen(modifier = Modifier.fillMaxSize())
                        } else if (isAppLocked) {
                            SecurityGateScreen(
                                onUnlockSuccess = { isAppLocked = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Admin announcements — only once the user is past every gate.
                            AdminNoticeDialog(refreshKey = noticeRefreshKey)
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeBillingSuccessIntent(intent)
    }

    private fun consumeBillingSuccessIntent(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "paychek" && data.host == "billing" && (data.path ?: "").startsWith("/success")) {
            pendingBillingOrderId = data.getQueryParameter("orderId")
            pendingBillingSuccess.value = true
        }
    }

    /**
     * APK update ধরার জন্য — versionCode আগের launch-এর সাথে compare করা হয়।
     * Update হলে prefs-এ নতুন code save করে true return করে।
     * First install (stored == -1): savedState এমনিতেই null, false return করো।
     */
    private fun hasVersionChanged(): Boolean {
        val prefs = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getLong(KEY_LAST_VERSION_CODE, -1L)
        val current = try {
            PackageInfoCompat.getLongVersionCode(
                packageManager.getPackageInfo(packageName, 0)
            )
        } catch (_: Exception) { -1L }
        if (stored != current) {
            prefs.edit().putLong(KEY_LAST_VERSION_CODE, current).apply()
            return stored != -1L // First install-এ false (savedState এমনিতেই null)
        }
        return false
    }

    companion object {
        var isRequestingPermission = false
        /** User left for Accessibility / Battery settings — skip PIN lock on quick return. */
        private const val KEY_SETTINGS_HANDOFF_AT = "pcu_settings_handoff_at_ms"
        private const val SETTINGS_HANDOFF_GRACE_MS = 180_000L // 3 minutes
        private const val KEY_LAST_VERSION_CODE    = "pcu_last_version_code"

        val pendingBillingSuccess = mutableStateOf(false)
        var pendingBillingOrderId: String? = null

        fun markSystemSettingsHandoff(context: Context) {
            isRequestingPermission = true
            context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_SETTINGS_HANDOFF_AT, System.currentTimeMillis())
                .apply()
        }

        fun clearSystemSettingsHandoff(context: Context) {
            context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SETTINGS_HANDOFF_AT)
                .apply()
        }

        fun isWithinSystemSettingsHandoff(context: Context): Boolean {
            val at = context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_SETTINGS_HANDOFF_AT, 0L)
            if (at <= 0L) return false
            return System.currentTimeMillis() - at <= SETTINGS_HANDOFF_GRACE_MS
        }
    }

    /**
     * Whether the security gate should show for the current authenticated session.
     * @param skipWasStoppedCheck true for cold start (process recreate); false for resume
     *        where we only re-lock after a real onStop → onStart cycle.
     */
    private fun shouldLockForCurrentSession(skipWasStoppedCheck: Boolean): Boolean {
        if (!skipWasStoppedCheck && !wasStopped) return false
        val skipHandoff = isRequestingPermission || isWithinSystemSettingsHandoff(this)
        val hasSession = SessionFlags.hasAuth(this) && SessionFlags.isProfileComplete(this)
        val lastBackgroundTime = getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            .getLong("last_background_time", 0L)
        return SessionLockPolicy.shouldLock(
            hasAuthSession = hasSession,
            lastBackgroundTimeMs = lastBackgroundTime,
            skipForSystemHandoff = skipHandoff,
        )
    }

    override fun onStart() {
        super.onStart()
        if (shouldLockForCurrentSession(skipWasStoppedCheck = false)) {
            isAppLocked = true
        }
        wasStopped = false
    }

    override fun onResume() {
        super.onResume()
        isRequestingPermission = false
        if (isWithinSystemSettingsHandoff(this)) {
            // Came back from system Settings — stay inside the app (no PIN wall).
            clearSystemSettingsHandoff(this)
            isAppLocked = false
        }
        if (SessionFlags.hasAuth(this) && SessionFlags.isProfileComplete(this)) {
            online.paychek.app.services.foreground.SmsServiceGuard.healIfNeeded(this)
            // Guard-2: recover SMS that arrived while process was dead (inbox ContentProvider).
            if (online.paychek.app.data.local.prefs.PrefsHelper.isSmsServiceActive(this)) {
                online.paychek.app.services.sync.SmsPollWorker.scheduleImmediate(this)
                online.paychek.app.services.sync.SmsPollWorker.schedule(this)
            }
            healDeviceConfigCache()
            refreshMaintenanceGate()
            refreshAdminNotices()
        }
    }

    /**
     * Pull admin announcements on app open. The heartbeat also delivers these,
     * but this path works for suspended packages (heartbeat returns
     * STOP_MONITORING) and for devices with SMS monitoring off.
     */
    private fun refreshAdminNotices() {
        lifecycleScope.launch(Dispatchers.IO) {
            online.paychek.app.services.notify.AdminNoticeManager.refreshFromServer(this@MainActivity)
            withContext(Dispatchers.Main) { noticeRefreshKey++ }
        }
    }

    private fun refreshMaintenanceGate() {
        if (!SessionFlags.hasAuth(this)) {
            isMaintenanceBlocked = false
            return
        }
        if (SessionFlags.userRole(this) == "admin") {
            isMaintenanceBlocked = false
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val blocked = try {
                val res = RetrofitClient.authApiService.getPublicConfig()
                res.isSuccessful && res.body()?.configs?.get("maintenance_mode") == "true"
            } catch (_: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                isMaintenanceBlocked = blocked
            }
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
                    val body = statusRes.body()
                    if (body != null) {
                        if (body.setupCompleted) {
                            getSharedPreferences(AppConfig.PREF_NAME, MODE_PRIVATE)
                                .edit().putBoolean("pcu_setup_completed", true).apply()
                        }
                        SecurePreferences.encrypt(
                            this@MainActivity,
                            "pcu_is_approved",
                            if (body.isApproved) "true" else "false"
                        )
                        online.paychek.app.utils.DeviceSecurityCache.applyRoleAndPin(
                            this@MainActivity,
                            body.deviceRole,
                            body.deviceSpecificPin
                        )
                    }
                }
                val configRes = RetrofitClient.gatewayApiService.getMyDeviceConfig(authHeader)
                if (configRes.isSuccessful && configRes.body()?.success == true) {
                    val device = configRes.body()?.data
                    if (device != null) {
                        online.paychek.app.utils.DeviceSecurityCache.applyRoleAndPin(
                            this@MainActivity,
                            device.deviceRole,
                            device.deviceSpecificPin
                        )
                    }
                }
                online.paychek.app.utils.AccountEntitlementsStore.refresh(this@MainActivity)
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
