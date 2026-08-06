package online.paychek.app.services.sync

import android.content.Context
import android.util.Log
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.services.foreground.SmsServiceGuard

/**
 * Server-driven subscription lifecycle — remote shutdown when account is suspended.
 * Called from heartbeat when server returns action=STOP_MONITORING.
 */
object SubscriptionLifecycleHelper {
    private const val TAG = "SubLifecycle"

    /** Stop monitoring, workers, and FGS — SMS ingest prefs OFF. */
    fun stopMonitoringRemote(context: Context, reason: String? = null) {
        val app = context.applicationContext
        if (!PrefsHelper.isSmsServiceActive(app)) {
            Log.i(TAG, "Remote stop ignored — monitoring already OFF")
            return
        }
        Log.w(TAG, "Remote STOP_MONITORING — ${reason ?: "subscription suspended"}")
        PrefsHelper.setSmsServiceActive(app, false)
        SmsServiceGuard.stopService(app)
        SmsServiceGuard.cancelWatchdog(app)
        NumberHeartbeatEngine.signalOffline(app)
    }
}
