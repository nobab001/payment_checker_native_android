package online.paychek.app.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

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

        if (!AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
            steps += BackgroundSetupStep(
                id = "accessibility",
                title = "Accessibility চালু করুন",
                description = "Settings → Accessibility → Paychek Background Guard → ON করুন।"
            )
        }

        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
            steps += BackgroundSetupStep(
                id = "battery_exempt",
                title = "Battery Unrestricted",
                description = "Settings → Apps → Paychek → Battery → Unrestricted / অপ্টিমাইজ করবেন না।"
            )
        }

        return steps
    }

    /** সব ফোনে battery unrestricted পেজ খোলার চেষ্টা — Samsung/Vivo সহ */
    fun openBatteryUnrestrictedSettings(context: Context): Boolean {
        val pkg = context.packageName
        val intents = when (detectVendor()) {
            OemVendor.SAMSUNG -> samsungIntents(pkg)
            OemVendor.VIVO -> listOf(appDetailsIntent(pkg)) + vivoIntents(pkg)
            OemVendor.XIAOMI -> xiaomiIntents(pkg)
            OemVendor.OPPO, OemVendor.REALME -> oppoIntents(pkg)
            else -> listOf(appDetailsIntent(pkg))
        }
        for (intent in intents) {
            if (launchIntent(context, intent)) return true
        }
        return launchIntent(context, appDetailsIntent(pkg))
    }

    fun openStep(context: Context, stepId: String): Boolean {
        return when (stepId) {
            "accessibility" -> {
                AccessibilityHelper.openAccessibilitySettings(context)
                true
            }
            "battery_exempt" -> openBatteryUnrestrictedSettings(context)
            else -> openBatteryUnrestrictedSettings(context)
        }
    }

    fun needsBackgroundSetup(context: Context): Boolean =
        !AccessibilityHelper.isBackgroundReady(context)

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
