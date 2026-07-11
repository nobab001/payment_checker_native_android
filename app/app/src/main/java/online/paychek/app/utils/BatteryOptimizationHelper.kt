package online.paychek.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Battery optimization exemption — Doze/OEM kill থেকে SMS মনিটরিং রক্ষা।
 * স্ক্রিন বন্ধ বা লক থাকলে অ্যাপকে ব্যাকগ্রাউন্ডে চালু রাখতে এটি আবশ্যক।
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptHelper"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * সিস্টেম সেটিংসে battery optimization বন্ধ করার ডায়ালগ খোলে।
     * @return true যদি ইতিমধ্যে exempt থাকে বা API < 23
     */
    fun requestExemptionIfNeeded(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) return true
        // Samsung/Vivo সহ সব ফোনে আগে OEM battery পেজ খোলার চেষ্টা, না হলে স্ট্যান্ডার্ড
        if (OemBackgroundHelper.openBatteryUnrestrictedSettings(context)) return false
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Battery exemption intent failed: ${e.message}")
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Battery settings fallback failed: ${e2.message}")
            }
            false
        }
    }
}
