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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import online.paychek.app.MainActivity
import online.paychek.app.R
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.AppNotificationDto
import online.paychek.app.utils.GsonUtils
import online.paychek.app.utils.SecurePreferences

/**
 * Admin announcements — status-bar notification + in-app inbox (home bell).
 *
 * Delivery is pull-based (Comm Policy v1.2): notices arrive on heartbeat or
 * GET /notifications. The home header bell shows a red unread badge; tapping
 * opens the inbox. Auto full-screen popups are not used.
 *
 * Local guards:
 *  - [AppConfig.KEY_NOTIFICATION_SHOWN_IDS] — already posted to the status bar
 *  - [AppConfig.KEY_NOTIFICATION_INBOX] — recent notices for the in-app list
 *  - [AppConfig.KEY_NOTIFICATION_INBOX_SEEN_IDS] — opened in the bell inbox
 *
 * Server read receipt still means *delivered to a device* (on first delivery).
 */
object AdminNoticeManager {

    private const val TAG = "AdminNotice"
    private const val CHANNEL_ID = "paychek_admin_notice"
    private const val CHANNEL_NAME = "গুরুত্বপূর্ণ ঘোষণা"
    private const val NOTIFICATION_ID_BASE = 4000
    private const val MAX_SHOWN_IDS = 200
    private const val MAX_INBOX = 40
    private const val MAX_SEEN_IDS = 200

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _inboxRevision = MutableStateFlow(0)
    val inboxRevision: StateFlow<Int> = _inboxRevision.asStateFlow()

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
            if (fresh.isEmpty()) {
                // Still merge into inbox in case prefs were cleared mid-session.
                mergeIntoInbox(app, notices)
                publishUiState(app)
                return
            }

            ensureChannel(app)
            for (notice in fresh) {
                postSystemNotification(app, notice)
                shown.add(notice.id)
            }
            saveShownIds(app, shown)
            mergeIntoInbox(app, fresh)
            // Legacy pending-popup queue → inbox (one-time migration path)
            migratePendingIntoInbox(app)
            fresh.forEach { sendReadReceipt(app, it.id) }
            publishUiState(app)
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
            migratePendingIntoInbox(app)
            val res = RetrofitClient.gatewayApiService.getMyNotifications("Bearer $token")
            if (res.isSuccessful) {
                handleIncoming(app, res.body()?.notifications)
            } else {
                publishUiState(app)
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshFromServer failed: ${e.message}")
            publishUiState(app)
        }
    }

    /** Call once from UI composition / resume so badge matches prefs. */
    fun syncUiState(context: Context) {
        migratePendingIntoInbox(context.applicationContext)
        publishUiState(context.applicationContext)
    }

    fun inbox(context: Context): List<AppNotificationDto> {
        val raw = SecurePreferences.decrypt(context, AppConfig.KEY_NOTIFICATION_INBOX)
        if (raw.isBlank()) {
            // Fallback: older builds only had the pending popup queue.
            return pendingPopupsLegacy(context).sortedByDescending { it.id }
        }
        return try {
            val type = object : TypeToken<List<AppNotificationDto>>() {}.type
            GsonUtils.gson.fromJson<List<AppNotificationDto>>(raw, type)
                ?.sortedByDescending { it.id }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun unreadIds(context: Context): Set<Int> {
        val seen = inboxSeenIds(context)
        return inbox(context).map { it.id }.filter { it !in seen }.toSet()
    }

    /**
     * User opened the home inbox — clear the red badge for current notices.
     */
    fun markInboxSeen(context: Context) {
        val app = context.applicationContext
        val ids = inbox(app).map { it.id }.toSet()
        if (ids.isEmpty()) {
            publishUiState(app)
            return
        }
        val merged = (inboxSeenIds(app) + ids).sorted().takeLast(MAX_SEEN_IDS).toSet()
        SecurePreferences.encrypt(
            app,
            AppConfig.KEY_NOTIFICATION_INBOX_SEEN_IDS,
            GsonUtils.gson.toJson(merged.toList())
        )
        // Clear legacy popup queue so old dialog path stays empty.
        SecurePreferences.encrypt(app, AppConfig.KEY_NOTIFICATION_PENDING, "[]")
        publishUiState(app)
    }

    // ── inbox persistence ───────────────────────────────────────────────────
    private fun mergeIntoInbox(context: Context, notices: List<AppNotificationDto>) {
        if (notices.isEmpty()) return
        val merged = (notices + inbox(context))
            .distinctBy { it.id }
            .sortedByDescending { it.id }
            .take(MAX_INBOX)
        SecurePreferences.encrypt(
            context,
            AppConfig.KEY_NOTIFICATION_INBOX,
            GsonUtils.gson.toJson(merged)
        )
    }

    private fun migratePendingIntoInbox(context: Context) {
        val pending = pendingPopupsLegacy(context)
        if (pending.isEmpty()) return
        mergeIntoInbox(context, pending)
        SecurePreferences.encrypt(context, AppConfig.KEY_NOTIFICATION_PENDING, "[]")
    }

    @Deprecated("Use inbox(); kept for migration from popup queue")
    fun pendingPopups(context: Context): List<AppNotificationDto> = inbox(context)

    fun clearPopup(context: Context, id: Int) {
        // Opening a single item from inbox no longer removes history — only marks seen set.
        val seen = inboxSeenIds(context) + id
        SecurePreferences.encrypt(
            context,
            AppConfig.KEY_NOTIFICATION_INBOX_SEEN_IDS,
            GsonUtils.gson.toJson(seen.sorted().takeLast(MAX_SEEN_IDS))
        )
        publishUiState(context.applicationContext)
    }

    fun clearAllPopups(context: Context) {
        markInboxSeen(context)
    }

    private fun pendingPopupsLegacy(context: Context): List<AppNotificationDto> {
        val raw = SecurePreferences.decrypt(context, AppConfig.KEY_NOTIFICATION_PENDING)
        if (raw.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AppNotificationDto>>() {}.type
            GsonUtils.gson.fromJson<List<AppNotificationDto>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun inboxSeenIds(context: Context): Set<Int> {
        val raw = SecurePreferences.decrypt(context, AppConfig.KEY_NOTIFICATION_INBOX_SEEN_IDS)
        if (raw.isBlank()) return emptySet()
        return try {
            val type = object : TypeToken<List<Int>>() {}.type
            GsonUtils.gson.fromJson<List<Int>>(raw, type)?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun publishUiState(context: Context) {
        val count = unreadIds(context).size
        _unreadCount.value = count
        _inboxRevision.value = _inboxRevision.value + 1
    }

    // ── status-bar notification ─────────────────────────────────────────────
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Paycheck থেকে পাঠানো গুরুত্বপূর্ণ বার্তা"
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
            Log.i(TAG, "POST_NOTIFICATIONS not granted — inbox only")
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
            .setSmallIcon(R.drawable.ic_notification)
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
