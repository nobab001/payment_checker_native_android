package online.paychek.app.services.foreground

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import online.paychek.app.data.local.prefs.PrefsHelper

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
    }

    companion object {
        private const val TAG = "KeepAliveAlarm"
    }
}
