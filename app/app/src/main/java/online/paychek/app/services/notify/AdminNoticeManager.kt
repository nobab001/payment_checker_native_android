package online.paychek.app.services.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.reflect.TypeToken
import online.paychek.app.MainActivity
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.AppNotificationDto
import online.paychek.app.utils.GsonUtils
import online.paychek.app.utils.SecurePreferences

/**
 * Admin announcements — status-bar notification + in-app popup.
 *
 * Delivery is pull-based (Comm Policy v1.2): notices arrive piggybacked on the
 * heartbeat response, or from GET /notifications when the app opens. There is no
 * socket and no FCM.
 *
 * Two local guards keep repeat heartbeats from re-nagging the user:
 *  - [AppConfig.KEY_NOTIFICATION_SHOWN_IDS] — already posted to the status bar
 *  - [AppConfig.KEY_NOTIFICATION_PENDING]  — queued for the next app open
 *
 * The read receipt is sent as soon as the notice reaches the device, so the
 * server stops returning it on every heartbeat. Admin-side "read" therefore
 * means *delivered to a device*, not *opened by the user*.
 */
object AdminNoticeManager {

    private const val TAG = "AdminNotice"
    private const val CHANNEL_ID = "paychek_admin_notice"
    private const val CHANNEL_NAME = "গুরুত্বপূর্ণ ঘোষণা"

    /** Base id for notice notifications — kept clear of the FGS id (991). */
    private const val NOTIFICATION_ID_BASE = 4000

    /** Cap on remembered ids so the pref cannot grow without bound. */
    private const val MAX_SHOWN_IDS = 200

    /** Cap on queued popups — the status bar already holds the full history. */
    private const val MAX_PENDING = 10

    /**
     * Handle notices from a heartbeat or app-open fetch.
     * Safe to call from any thread; never throws.
     */
    suspend fun handleIncoming(context: Context, notices: List<AppNotificationDto>?) {
        if (notices.isNullOrEmpty()) return
        val app = context.applicationContext
        try {
            val shown = shownIds(app).toMutableSet()
            val fresh = notices.filter { it.id !in shown }
            if (fresh.isEmpty()) return

            ensureChannel(app)
            for (notice in fresh) {
                postSystemNotification(app, notice)
                shown.add(notice.id)
            }
            saveShownIds(app, shown)
            enqueueForPopup(app, fresh)
            fresh.forEach { sendReadReceipt(app, it.id) }
            Log.i(TAG, "Delivered ${fresh.size} admin notice(s)")
        } catch (e: Exception) {
            Log.w(TAG, "handleIncoming failed: ${e.message}")
        }
    }

    /** Pull notices when the app opens — covers suspended accounts too. */
    suspend fun refreshFromServer(context: Context) {
        val app = context.applicationContext
        val token = SecurePreferences.decrypt(app, AppConfig.KEY_AUTH_TOKEN)
        if (token.isBlank()) return
        try {
            val res = RetrofitClient.gatewayApiService.getMyNotifications("Bearer $token")
            if (res.isSuccessful) {
                handleIncoming(app, res.body()?.notifications)
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshFromServer failed: ${e.message}")
        }
    }

    // ── in-app popup queue ──────────────────────────────────────────────────
    fun pendingPopups(context: Context): List<AppNotificationDto> {
        val raw = SecurePreferences.decrypt(context, AppConfig.KEY_NOTIFICATION_PENDING)
        if (raw.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AppNotificationDto>>() {}.type
            GsonUtils.gson.fromJson<List<AppNotificationDto>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Called once the user has dismissed the popup for [id]. */
    fun clearPopup(context: Context, id: Int) {
        val remaining = pendingPopups(context).filterNot { it.id == id }
        SecurePreferences.encrypt(
            context,
            AppConfig.KEY_NOTIFICATION_PENDING,
            GsonUtils.gson.toJson(remaining)
        )
    }

    private fun enqueueForPopup(context: Context, notices: List<AppNotificationDto>) {
        val merged = (pendingPopups(context) + notices)
            .distinctBy { it.id }
            .takeLast(MAX_PENDING)
        SecurePreferences.encrypt(
            context,
            AppConfig.KEY_NOTIFICATION_PENDING,
            GsonUtils.gson.toJson(merged)
        )
    }

    // ── status-bar notification ─────────────────────────────────────────────
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "PayChek থেকে পাঠানো গুরুত্বপূর্ণ বার্তা"
            enableVibration(true)
            setShowBadge(true)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun postSystemNotification(context: Context, notice: AppNotificationDto) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // No runtime permission — the in-app popup still delivers the message.
            Log.i(TAG, "POST_NOTIFICATIONS not granted — popup only")
            return
        }

        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_BASE + notice.id,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID_BASE + notice.id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notify() blocked: ${e.message}")
        }
    }

    // ── read receipt ────────────────────────────────────────────────────────
    private suspend fun sendReadReceipt(context: Context, id: Int) {
        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
        if (token.isBlank()) return
        try {
            RetrofitClient.gatewayApiService.markNotificationRead("Bearer $token", id)
        } catch (e: Exception) {
            // Not fatal: the local shown-id guard still suppresses a repeat popup.
            Log.w(TAG, "read receipt failed for #$id: ${e.message}")
        }
    }

    // ── shown-id bookkeeping ────────────────────────────────────────────────
    private fun shownIds(context: Context): Set<Int> {
        val raw = SecurePreferences.decrypt(context, AppConfig.KEY_NOTIFICATION_SHOWN_IDS)
        if (raw.isBlank()) return emptySet()
        return try {
            val type = object : TypeToken<List<Int>>() {}.type
            GsonUtils.gson.fromJson<List<Int>>(raw, type)?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveShownIds(context: Context, ids: Set<Int>) {
        val trimmed = ids.sorted().takeLast(MAX_SHOWN_IDS)
        SecurePreferences.encrypt(
            context,
            AppConfig.KEY_NOTIFICATION_SHOWN_IDS,
            GsonUtils.gson.toJson(trimmed)
        )
    }
}
