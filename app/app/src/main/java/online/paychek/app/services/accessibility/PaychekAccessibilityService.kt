package online.paychek.app.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.services.foreground.ServiceKeepAliveScheduler
import online.paychek.app.services.foreground.SmsServiceGuard
import online.paychek.app.utils.BatteryOptimizationHelper

/**
 * Accessibility anchor — অন্যান্য পেমেন্ট অ্যাপের মতো প্রক্রিয়া জীবিত রাখে।
 * Accessibility ON + Battery Unrestricted = ২৪/৭ চালু (Samsung/Vivo সহ)।
 */
class PaychekAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            runKeepAliveCheck()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "Accessibility connected — keep-alive engine started")

        val ctx = applicationContext
        if (PrefsHelper.isSmsServiceActive(ctx)) {
            SmsServiceGuard.startService(ctx)
            SmsServiceGuard.scheduleWatchdog(ctx)
            ServiceKeepAliveScheduler.schedule(ctx)
        }

        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(ctx)) {
            BatteryOptimizationHelper.requestExemptionIfNeeded(ctx)
        }

        handler.removeCallbacks(keepAliveRunnable)
        handler.postDelayed(keepAliveRunnable, 5_000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ইভেন্ট প্রসেসিং দরকার নেই — সার্ভিস বাইন্ডিংই প্রক্রিয়া জীবিত রাখে
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility interrupted")
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(keepAliveRunnable)
        val ctx = applicationContext
        if (PrefsHelper.isSmsServiceActive(ctx)) {
            Log.w(TAG, "Accessibility destroyed while SMS ON — scheduling recovery")
            SmsServiceGuard.enqueueImmediateRecovery(ctx)
            ServiceKeepAliveScheduler.schedule(ctx)
        }
        super.onDestroy()
    }

    private fun runKeepAliveCheck() {
        val ctx = applicationContext
        if (!PrefsHelper.isSmsServiceActive(ctx)) return

        if (!SmsServiceGuard.isServiceAlive()) {
            Log.w(TAG, "Watchdog: SMS service dead — restarting via accessibility anchor")
            SmsServiceGuard.startService(ctx)
            SmsServiceGuard.scheduleWatchdog(ctx)
        }
        ServiceKeepAliveScheduler.schedule(ctx)
    }

    companion object {
        private const val TAG = "PaychekA11y"
        private const val CHECK_INTERVAL_MS = 45_000L

        @Volatile
        var isRunning: Boolean = false
    }
}
