package online.paychek.app.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import online.paychek.app.config.AppConfig

enum class OemVendor {
    SAMSUNG,
    VIVO,
    OPPO,
    REALME,
    ONEPLUS,
    XIAOMI,
    HUAWEI,
    OTHER
}

data class BackgroundSetupStep(
    val id: String,
    val title: String,
    val description: String,
    val required: Boolean = true
)

/**
 * Samsung/Vivo/Oppo ইত্যাদি aggressive OEM-এ শুধু battery exemption যথেষ্ট নয় —
 * autostart, sleeping apps, background unrestricted আলাদা সেটিংস লাগে।
 */
object OemBackgroundHelper {
    private const val TAG = "OemBackgroundHelper"
    private const val PREF_OEM_PREFIX = "pcu_oem_step_"

    fun detectVendor(): OemVendor {
        val brand = Build.BRAND.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        return when {
            manufacturer.contains("samsung") || brand.contains("samsung") -> OemVendor.SAMSUNG
            manufacturer.contains("vivo") || brand.contains("vivo") -> OemVendor.VIVO
            manufacturer.contains("iqoo") || brand.contains("iqoo") -> OemVendor.VIVO
            manufacturer.contains("oppo") || brand.contains("oppo") -> OemVendor.OPPO
            manufacturer.contains("realme") || brand.contains("realme") -> OemVendor.REALME
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> OemVendor.ONEPLUS
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                manufacturer.contains("redmi") || brand.contains("poco") -> OemVendor.XIAOMI
            manufacturer.contains("huawei") || brand.contains("honor") -> OemVendor.HUAWEI
            else -> OemVendor.OTHER
        }
    }

    fun isAggressiveOem(): Boolean = when (detectVendor()) {
        OemVendor.SAMSUNG, OemVendor.VIVO, OemVendor.OPPO, OemVendor.REALME, OemVendor.XIAOMI, OemVendor.HUAWEI -> true
        else -> false
    }

    fun vendorLabel(): String = when (detectVendor()) {
        OemVendor.SAMSUNG -> "Samsung"
        OemVendor.VIVO -> "Vivo / iQOO"
        OemVendor.OPPO -> "Oppo"
        OemVendor.REALME -> "Realme"
        OemVendor.ONEPLUS -> "OnePlus"
        OemVendor.XIAOMI -> "Xiaomi"
        OemVendor.HUAWEI -> "Huawei / Honor"
        OemVendor.OTHER -> Build.MANUFACTURER.ifBlank { "Android" }
    }

    fun getRequiredSteps(context: Context): List<BackgroundSetupStep> {
        val steps = mutableListOf<BackgroundSetupStep>()

        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
            steps += BackgroundSetupStep(
                id = "battery_exempt",
                title = "ব্যাটারি অপ্টিমাইজেশন বন্ধ",
                description = "সেটিংসে গিয়ে Paychek-কে \"অপ্টিমাইজ করবেন না\" বা Unrestricted দিন।"
            )
        }

        when (detectVendor()) {
            OemVendor.SAMSUNG -> {
                steps += BackgroundSetupStep(
                    id = "samsung_unrestricted",
                    title = "Samsung — ব্যাকগ্রাউন্ড সীমাহীন",
                    description = "Settings → Apps → Paychek → Battery → Unrestricted (সীমাহীন) সিলেক্ট করুন।"
                )
                steps += BackgroundSetupStep(
                    id = "samsung_never_sleeping",
                    title = "Samsung — Sleeping apps থেকে বাদ",
                    description = "Settings → Battery → Background usage limits → Never sleeping apps-এ Paychek যোগ করুন।"
                )
            }
            OemVendor.VIVO -> {
                steps += BackgroundSetupStep(
                    id = "vivo_autostart",
                    title = "Vivo — Autostart চালু",
                    description = "Settings → Apps → Autostart → Paychek ON করুন।"
                )
                steps += BackgroundSetupStep(
                    id = "vivo_high_bg",
                    title = "Vivo — High background power",
                    description = "Settings → Battery → Background power consumption management → Paychek-কে Allow দিন।"
                )
            }
            OemVendor.OPPO, OemVendor.REALME -> {
                steps += BackgroundSetupStep(
                    id = "oppo_autostart",
                    title = "Autostart চালু",
                    description = "Settings → App Management → Auto launch → Paychek চালু করুন।"
                )
            }
            OemVendor.XIAOMI -> {
                steps += BackgroundSetupStep(
                    id = "xiaomi_autostart",
                    title = "Xiaomi — Autostart",
                    description = "Security → Permissions → Autostart → Paychek চালু করুন।"
                )
                steps += BackgroundSetupStep(
                    id = "xiaomi_no_restrict",
                    title = "Xiaomi — No restrictions",
                    description = "Settings → Apps → Paychek → Battery saver → No restrictions।"
                )
            }
            else -> Unit
        }

        return steps.filter { !isStepAcknowledged(context, it.id) }
    }

    fun isStepAcknowledged(context: Context, stepId: String): Boolean {
        if (stepId == "battery_exempt" && BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
            return true
        }
        return context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_OEM_PREFIX + stepId, false)
    }

    fun acknowledgeStep(context: Context, stepId: String) {
        context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_OEM_PREFIX + stepId, true)
            .apply()
    }

    fun needsBackgroundSetup(context: Context): Boolean = getRequiredSteps(context).isNotEmpty()

    fun openStep(context: Context, stepId: String): Boolean {
        val pkg = context.packageName
        val intents = when (stepId) {
            "battery_exempt" -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$pkg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            "samsung_unrestricted", "samsung_never_sleeping" -> samsungIntents(pkg)
            "vivo_autostart", "vivo_high_bg" -> vivoIntents(pkg)
            "oppo_autostart" -> oppoIntents(pkg)
            "xiaomi_autostart", "xiaomi_no_restrict" -> xiaomiIntents(pkg)
            else -> listOf(appDetailsIntent(pkg))
        }

        for (intent in intents) {
            if (launchIntent(context, intent)) return true
        }
        return launchIntent(context, appDetailsIntent(pkg))
    }

    private fun appDetailsIntent(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$pkg")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun samsungIntents(pkg: String): List<Intent> = listOf(
        Intent().apply {
            component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        Intent().apply {
            component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        appDetailsIntent(pkg)
    )

    private fun vivoIntents(pkg: String): List<Intent> = listOf(
        Intent().apply {
            component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        Intent().apply {
            component = ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        Intent().apply {
            component = ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
            putExtra("packageName", pkg)
            putExtra("pkgname", pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        appDetailsIntent(pkg)
    )

    private fun oppoIntents(pkg: String): List<Intent> = listOf(
        Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        Intent().apply {
            component = ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        appDetailsIntent(pkg)
    )

    private fun xiaomiIntents(pkg: String): List<Intent> = listOf(
        Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        appDetailsIntent(pkg)
    )

    private fun launchIntent(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Intent failed (${intent.component}): ${e.message}")
            false
        }
    }
}
