package online.paychek.app.services.smartpopup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.paychek.app.utils.AccountEntitlementsStore

object SmartPopupManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun isEligible(context: Context): Boolean {
        return AccountEntitlementsStore.readCached(context).hasSmartPopup
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(context.applicationContext)
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(
                context,
                "স্মার্ট পপ-আপ চালু করতে 'অন্য অ্যাপের উপর প্রদর্শন' অনুমতি দিন",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun open(context: Context) {
        if (!hasOverlayPermission(context)) {
            requestOverlayPermission(context)
            return
        }

        val appCtx = context.applicationContext
        scope.launch {
            // Live DB check via entitlements API before opening
            val live = AccountEntitlementsStore.refresh(appCtx)
                ?: AccountEntitlementsStore.readCached(appCtx)
            if (!live.hasSmartPopup) {
                Toast.makeText(
                    appCtx,
                    "আপনার প্যাকেজে স্মার্ট পপ-আপ পারমিশন নেই",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val intent = Intent(appCtx, SmartPopupService::class.java).apply {
                action = SmartPopupService.ACTION_OPEN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appCtx.startForegroundService(intent)
            } else {
                appCtx.startService(intent)
            }

            SmartPopupLoader.loadLast7Days(appCtx)
                .onSuccess {
                    appCtx.sendBroadcast(
                        Intent(SmartPopupService.ACTION_CACHE_UPDATED).setPackage(appCtx.packageName)
                    )
                }
        }
    }

    fun close(context: Context) {
        val appCtx = context.applicationContext
        appCtx.startService(
            Intent(appCtx, SmartPopupService::class.java).apply {
                action = SmartPopupService.ACTION_CLOSE
            }
        )
    }
}
