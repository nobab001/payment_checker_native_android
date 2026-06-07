package online.paychek.app.services.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import online.paychek.app.config.AppConfig
import online.paychek.app.services.foreground.SmsMonitorService

/**
 * BootReceiver — ফোন রিস্টার্টের পর SMS Monitoring Service স্বয়ংক্রিয়ভাবে চালু করে।
 *
 * কখন চালু হয়:
 *  • android.intent.action.BOOT_COMPLETED — সাধারণ রিবুটের পর
 *  • android.intent.action.MY_PACKAGE_REPLACED — অ্যাপ আপডেটের পর
 *  • android.intent.action.LOCKED_BOOT_COMPLETED — Encrypted storage unlock হওয়ার আগে
 *
 * শর্ত: AppConfig.KEY_SMS_SERVICE_ACTIVE == true হলেই শুধু সার্ভিস চালু করবে।
 * ব্যবহারকারী নিজে সার্ভিস বন্ধ রাখলে রিবুটের পর আপনাআপনি চালু হবে না।
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Boot broadcast ধরা হয়েছে — Action: $action")

        val isBootEvent = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.LOCKED_BOOT_COMPLETED"

        if (!isBootEvent) return

        // SharedPreferences থেকে চেক — ব্যবহারকারী কি সার্ভিস চালু রেখেছিলেন?
        val prefs      = context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        val isEnabled  = prefs.getBoolean(AppConfig.KEY_SMS_SERVICE_ACTIVE, false)
        val hasToken   = prefs.getString(AppConfig.KEY_AUTH_TOKEN, "")?.isNotEmpty() == true

        if (!isEnabled) {
            Log.i(TAG, "SMS Service বন্ধ ছিল — boot-এ চালু করা হচ্ছে না")
            return
        }

        if (!hasToken) {
            Log.w(TAG, "Auth Token নেই — ব্যবহারকারী লগইন করেননি, সার্ভিস বাতিল")
            return
        }

        // সব শর্ত পূরণ হলে Foreground Service চালু করা
        try {
            val serviceIntent = Intent(context, SmsMonitorService::class.java).apply {
                this.action = SmsMonitorService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8+ — startForegroundService() ব্যবহার করতে হবে
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.i(TAG, "✅ Boot-এ SMS Monitor Service চালু করা হয়েছে")

        } catch (e: Exception) {
            Log.e(TAG, "Boot-এ Service চালু করতে ব্যর্থ: ${e.message}")
        }
    }
}
