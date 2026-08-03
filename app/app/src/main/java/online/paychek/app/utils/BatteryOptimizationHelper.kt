package online.paychek.app.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import online.paychek.app.config.AppConfig

/**
 * Battery optimization exemption — Doze/OEM kill থেকে SMS মনিটরিং রক্ষা।
 *
 * Flow:
 * 1) যদি সিস্টেম `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` সত্যিই Allow ডায়ালগ খোলে → সেটাই
 * 2) যদি সেই intent App battery usage / Allow background usage পেজে যায় → App Info খোলো
 *    (ইউজার Battery → Unrestricted নিজে সেট করবে — এটাই `isIgnoringBatteryOptimizations` সেট করে)
 * 3) ডায়ালগ পাথ কাজ না করলে পরের ট্যাপে App Info ফোর্স করা হয়
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptHelper"
    private const val KEY_FORCE_APP_INFO = "pcu_battery_force_app_info"
    private const val KEY_AWAITING_EXEMPTION = "pcu_battery_awaiting_exemption"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * @return true যদি ইতিমধ্যে exempt থাকে বা API < 23
     */
    fun requestExemptionIfNeeded(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) {
            clearAwaitingExemption(context)
            return true
        }
        online.paychek.app.MainActivity.markSystemSettingsHandoff(context)

        if (shouldOpenAppInfoDirectly(context)) {
            Log.i(TAG, "Opening App Info (dialog target unsafe or previously failed)")
            openAppInfo(context)
            return false
        }

        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            markAwaitingExemption(context)
            context.startActivity(intent)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Battery exemption dialog failed: ${e.message}")
            clearAwaitingExemption(context)
            setForceAppInfo(context, true)
            openAppInfo(context)
            false
        }
    }

    /**
     * Settings থেকে ফিরে এসে কল করুন। ডায়ালগ/ভুল পেজ থেকে ফিরেও exemption না থাকলে
     * পরের Set Now → App Info।
     */
    fun onReturnedFromBatterySettings(context: Context) {
        if (!isAwaitingExemption(context)) return
        clearAwaitingExemption(context)
        if (isIgnoringBatteryOptimizations(context)) {
            setForceAppInfo(context, false)
            Log.i(TAG, "Battery exemption granted via system dialog")
        } else {
            setForceAppInfo(context, true)
            Log.i(TAG, "Exemption still missing — next attempt will open App Info")
        }
    }

    fun openAppInfo(context: Context): Boolean {
        return try {
            online.paychek.app.MainActivity.markSystemSettingsHandoff(context)
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "App Info open failed: ${e.message}")
            false
        }
    }

    fun shouldOpenAppInfoDirectly(context: Context): Boolean {
        if (isForceAppInfo(context)) return true
        val target = resolveRequestIgnoreTarget(context) ?: return true
        return !BatteryExemptionIntentClassifier.looksLikeSystemExemptionDialog(target)
    }

    fun resolveRequestIgnoreTarget(context: Context): ComponentName? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        val ri = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return null
        val info = ri.activityInfo ?: return null
        return ComponentName(info.packageName, info.name)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)

    private fun isForceAppInfo(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FORCE_APP_INFO, false)

    private fun setForceAppInfo(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_FORCE_APP_INFO, value).apply()
    }

    private fun markAwaitingExemption(context: Context) {
        prefs(context).edit().putBoolean(KEY_AWAITING_EXEMPTION, true).apply()
    }

    private fun clearAwaitingExemption(context: Context) {
        prefs(context).edit().putBoolean(KEY_AWAITING_EXEMPTION, false).apply()
    }

    private fun isAwaitingExemption(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AWAITING_EXEMPTION, false)
}

/**
 * Pure classifier — unit-testable without Android Settings.
 * Detects whether REQUEST_IGNORE resolves to a real Allow dialog vs App battery usage.
 */
object BatteryExemptionIntentClassifier {

    fun looksLikeSystemExemptionDialog(component: ComponentName): Boolean =
        looksLikeSystemExemptionDialog(component.className)

    fun looksLikeSystemExemptionDialog(className: String): Boolean {
        val name = className.lowercase()

        // Explicit Allow / whitelist dialog (AOSP / Pixel / many stock builds)
        if (name.contains("requestignorebattery")) return true

        // Known wrong destinations: App battery usage / background usage / hibernation
        val badMarkers = listOf(
            "appbatteryusage",
            "app_battery_usage",
            "batteryusageactivity",
            "battery.ui.usage",
            "advancedpowerusagedetail",
            "powerusagesummary",
            "backgroundusage",
            "backgroundrestrict",
            "managebackground",
            "hibernation",
            "unusedapp",
            "checkableapplist", // Samsung sleeping-apps style lists
            "apppowermanagement",
            "powerallowlist", // generic list UIs that are easy to confuse — prefer App Info
        )
        if (badMarkers.any { name.contains(it) }) return false

        // Unknown resolver — allow one try of the system intent; learn-on-failure covers misses.
        return true
    }
}
