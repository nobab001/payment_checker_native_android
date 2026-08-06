package online.paychek.app.services.foreground

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * DEPRECATED — keep-alive exact alarms removed.
 * Any leftover PendingIntent deliveries are cancelled and ignored.
 */
class KeepAliveAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Ignored obsolete keep-alive alarm (${intent?.action})")
        ServiceKeepAliveScheduler.cancel(context.applicationContext)
    }

    companion object {
        private const val TAG = "KeepAliveAlarm"
    }
}
