package online.paychek.app.services.foreground

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.services.sync.NumberHeartbeatEngine

class KeepAliveAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        if (!PrefsHelper.isSmsServiceActive(app)) {
            ServiceKeepAliveScheduler.cancel(app)
            return
        }
        if (!SmsServiceGuard.isServiceAlive()) {
            Log.w(TAG, "Keep-alive: service dead — restarting")
            SmsServiceGuard.startService(app)
        }
        SmsServiceGuard.scheduleWatchdog(app)
        ServiceKeepAliveScheduler.schedule(app)

        // Doze-resilient heartbeat: the NumberHeartbeatEngine coroutine `delay` loop
        // is suspended while the device sleeps, so a backgrounded phone stops
        // heartbeating and the server shows its numbers OFFLINE. This exact alarm
        // (every 3–8 min, setExactAndAllowWhileIdle) fires even in Doze — piggyback
        // a heartbeat on it and hold the wakelock via goAsync until the POST lands.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                NumberHeartbeatEngine.sendHeartbeatBlocking(app)
            } catch (e: Exception) {
                Log.w(TAG, "Keep-alive heartbeat failed: ${e.message}")
            } finally {
                try { pending.finish() } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private const val TAG = "KeepAliveAlarm"
    }
}
