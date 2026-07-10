package online.paychek.app.config

/**
 * Central configuration for API base URLs and app-level constants.
 * Corresponds to blueprint: config/api_config.dart
 *
 * VPS-Ready: শুধু BASE_URL পরিবর্তন করলেই production-এ কাজ করবে।
 * Local  → BASE_URL = "http://10.0.2.2:3000/"   (Android Emulator → localhost)
 * XAMPP  → BASE_URL = "http://192.168.x.x:3000/" (Real device → LAN IP)
 * VPS    → BASE_URL = "https://paychek.online/"
 */
object AppConfig {

    // -----------------------------------------------------------------------
    // Server URLs — VPS-ready: শুধু এই একটি লাইন পরিবর্তন করুন
    // -----------------------------------------------------------------------
    // 🔴 XAMPP LOCAL TEST MODE (আপনার LAN IP + Node.js port)
    const val BASE_URL        = "https://drastic-fringe-unlined.ngrok-free.dev/"
    // ✅ VPS PRODUCTION — সার্ভারে deploy করার সময় এই লাইনটি uncomment করুন:
    // const val BASE_URL     = "https://paychek.online/"
    const val API_BASE_URL    = "${BASE_URL}api/"
    const val SOCKET_URL      = BASE_URL

    // -----------------------------------------------------------------------
    // SharedPreferences keys
    // -----------------------------------------------------------------------
    const val PREF_NAME               = "paychek_prefs"
    const val KEY_AUTH_TOKEN          = "pcu_auth_token"
    const val KEY_DEVICE_ID           = "pcu_hw_device_id_v1"
    const val KEY_SMS_SERVICE_ACTIVE  = "pcu_sms_service_active"
    const val KEY_OFFLINE_INGEST_QUEUE= "pcu_payment_ingest_pending_v1"
    const val KEY_PIN_VERIFIED        = "pcu_pin_verified_session"
    const val KEY_GATEWAY_METHODS_CACHE = "pcu_gateway_methods_cache"
    const val KEY_SMS_TEMPLATES_CACHE   = "pcu_sms_templates_cache"
    const val KEY_SIM1_ENABLED        = "pcu_sim1_enabled_cache"
    const val KEY_SIM2_ENABLED        = "pcu_sim2_enabled_cache"
    const val KEY_IS_OWNER_DEVICE     = "pcu_is_owner_device"
    const val KEY_DEVICE_SPECIFIC_PIN = "pcu_device_specific_pin"
    const val KEY_HAS_CUSTOM_SENDER_ADDON = "pcu_has_custom_sender_addon"
    const val KEY_PERM_CUSTOM_SENDER    = "pcu_perm_custom_sender"
    const val KEY_PERM_TEMPLATE         = "pcu_perm_template"
    const val KEY_PERM_WEBSITE          = "pcu_perm_website"
    const val KEY_PERM_DEVICE           = "pcu_perm_device"
    const val KEY_EFF_MAX_DEVICES       = "pcu_eff_max_devices"
    const val KEY_EFF_MAX_SITES         = "pcu_eff_max_sites"
    const val KEY_DASHBOARD_STATS_CACHE   = "pcu_dashboard_stats_cache_v1"

    // -----------------------------------------------------------------------
    // P2P Sync defaults
    // -----------------------------------------------------------------------
    const val DEFAULT_SYNC_PORT    = 8765
    const val SYNC_PING_PATH       = "/ping"
    const val SYNC_POST_PATH       = "/sync"

    // -----------------------------------------------------------------------
    // Polling & Worker intervals
    // -----------------------------------------------------------------------
    const val DEVICE_POLL_INTERVAL_MS   = 12_000L   // 12 seconds
    const val SYNC_WORKER_INTERVAL_MIN  = 15L        // 15 minutes
    const val SMS_POLL_WORKER_INTERVAL_MIN = 15L     // Guard-2: ContentProvider polling interval
    const val SMS_INBOX_SCAN_LIMIT      = 50         // Guard-2: এক ব্যাচে সর্বোচ্চ কতটি SMS স্ক্যান করবে
    const val HEARTBEAT_INTERVAL_MS         = 60_000L    // legacy alias
    const val FALLBACK_HEARTBEAT_INTERVAL_MS = 15 * 60_000L // HTTP ping only while socket is down

    // -----------------------------------------------------------------------
    // HTTP Headers
    // -----------------------------------------------------------------------
    const val HEADER_DEVICE_ID    = "X-Device-Id"
    const val HEADER_AUTH_TOKEN   = "Authorization"
}
