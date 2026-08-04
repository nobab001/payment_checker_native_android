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
        val immediate = intent?.action == ServiceKeepAliveScheduler.ACTION_IMMEDIATE
        if (SmsServiceGuard.isServiceRunning(app)) {
            SmsServiceGuard.scheduleWatchdog(app)
            online.paychek.app.services.sync.SmsPollWorker.scheduleImmediate(app)
        } else {
            Log.w(TAG, "Keep-alive: service not running — starting (immediate=$immediate)")
            SmsServiceGuard.startService(app)
            SmsServiceGuard.healIfNeeded(app)
        }
        ServiceKeepAliveScheduler.schedule(app)

        // Doze-resilient heartbeat: the NumberHeartbeatEngine coroutine `delay` loop
        // is suspended while the device sleeps, so a backgrounded phone stops
        // heartbeating and the server shows its numbers OFFLINE. This exact alarm
        // (every 2–5 min, setExactAndAllowWhileIdle) fires even in Doze — piggyback
        // a heartbeat on it and hold the wakelock via goAsync until the POST lands.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                NumberHeartbeatEngine.sendHeartbeatBlocking(app)
                // Heartbeat path flushes pending SMS when server is reachable.
                // Also arm durable probe if queue still has items (covers HB skip / empty numbers).
                val dao = online.paychek.app.data.local.AppDatabase.getInstance(app).pendingSmsDao()
                if (dao.countPendingUnsynced() > 0) {
                    online.paychek.app.services.sync.ServerProbeWorker.scheduleSoon(app)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Keep-alive heartbeat failed: ${e.message}")
                online.paychek.app.services.sync.ServerProbeWorker.scheduleSoon(app)
            } finally {
                try { pending.finish() } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private const val TAG = "KeepAliveAlarm"
    }
}
