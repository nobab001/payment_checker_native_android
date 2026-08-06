package online.paychek.app.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.AccountEntitlementsDto

object AccountEntitlementsStore {

    const val DEFAULT_CUSTOM_ALL_CHIP = "কাস্টম অল"
    const val DEFAULT_CUSTOM_ALL_TITLE = "কাস্টম অল এসএমএস লকড"
    const val DEFAULT_CUSTOM_ALL_NOTICE =
        "আপনার এরকম প্যাকেজ অ্যাক্টিভ নেই যার কারণে আপনি কাস্টম অল এসএমএস মার্ক করতে পারছেন না। এটি ব্যবহার করতে পার্সোনাল / কাস্টম সেন্ডার প্যাকেজ কিনুন।"

    fun readCached(context: Context): AccountEntitlementsDto {
        val prefs = context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        return AccountEntitlementsDto(
            permCustomSender = if (SecurePreferences.decrypt(context, AppConfig.KEY_PERM_CUSTOM_SENDER) == "1") 1 else 0,
            permTemplate = if (SecurePreferences.decrypt(context, AppConfig.KEY_PERM_TEMPLATE) == "1") 1 else 0,
            permWebsite = if (SecurePreferences.decrypt(context, AppConfig.KEY_PERM_WEBSITE) == "1") 1 else 0,
            permDevice = if (SecurePreferences.decrypt(context, AppConfig.KEY_PERM_DEVICE) == "1") 1 else 0,
            permSmartPopup = if (SecurePreferences.decrypt(context, AppConfig.KEY_PERM_SMART_POPUP) == "1") 1 else 0,
            permManualTransaction = if (SecurePreferences.decrypt(context, AppConfig.KEY_PERM_MANUAL_TXN) == "1") 1 else 0,
            effMaxDevices = SecurePreferences.decrypt(context, AppConfig.KEY_EFF_MAX_DEVICES).toIntOrNull() ?: 0,
            effMaxSites = SecurePreferences.decrypt(context, AppConfig.KEY_EFF_MAX_SITES).toIntOrNull() ?: 0,
            commProfile = online.paychek.app.services.sync.CommPolicyStore.profile(context),
            subscriptionStatus = SecurePreferences.decrypt(context, AppConfig.KEY_SUBSCRIPTION_STATUS).ifBlank { null },
            customAllChipLabel = prefs.getString(AppConfig.KEY_CUSTOM_ALL_CHIP_LABEL, null),
            customAllPopupTitle = prefs.getString(AppConfig.KEY_CUSTOM_ALL_POPUP_TITLE, null),
            customAllPopupNotice = prefs.getString(AppConfig.KEY_CUSTOM_ALL_POPUP_NOTICE, null)
        )
    }

    fun save(context: Context, ent: AccountEntitlementsDto) {
        SecurePreferences.encrypt(context, AppConfig.KEY_PERM_CUSTOM_SENDER, if (ent.permCustomSender == 1) "1" else "0")
        SecurePreferences.encrypt(context, AppConfig.KEY_PERM_TEMPLATE, if (ent.permTemplate == 1) "1" else "0")
        SecurePreferences.encrypt(context, AppConfig.KEY_PERM_WEBSITE, if (ent.permWebsite == 1) "1" else "0")
        SecurePreferences.encrypt(context, AppConfig.KEY_PERM_DEVICE, if (ent.permDevice == 1) "1" else "0")
        SecurePreferences.encrypt(context, AppConfig.KEY_PERM_SMART_POPUP, if (ent.permSmartPopup == 1) "1" else "0")
        SecurePreferences.encrypt(context, AppConfig.KEY_PERM_MANUAL_TXN, if (ent.permManualTransaction == 1) "1" else "0")
        SecurePreferences.encrypt(context, AppConfig.KEY_EFF_MAX_DEVICES, ent.effMaxDevices.toString())
        SecurePreferences.encrypt(context, AppConfig.KEY_EFF_MAX_SITES, ent.effMaxSites.toString())
        // Always overwrite — leaving a stale "suspended" after renew keeps the lock forever.
        val status = when {
            ent.suspended == true -> "suspended"
            !ent.subscriptionStatus.isNullOrBlank() -> ent.subscriptionStatus!!.lowercase().trim()
            else -> null
        }
        if (status.isNullOrBlank()) {
            SecurePreferences.remove(context, AppConfig.KEY_SUBSCRIPTION_STATUS)
        } else {
            SecurePreferences.encrypt(context, AppConfig.KEY_SUBSCRIPTION_STATUS, status)
        }
        online.paychek.app.services.sync.CommPolicyStore.applyEntitlements(context, ent)
        context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AppConfig.KEY_HAS_CUSTOM_SENDER_ADDON, ent.hasCustomSender)
            .apply {
                ent.customAllChipLabel?.takeIf { it.isNotBlank() }?.let {
                    putString(AppConfig.KEY_CUSTOM_ALL_CHIP_LABEL, it)
                }
                ent.customAllPopupTitle?.takeIf { it.isNotBlank() }?.let {
                    putString(AppConfig.KEY_CUSTOM_ALL_POPUP_TITLE, it)
                }
                ent.customAllPopupNotice?.takeIf { it.isNotBlank() }?.let {
                    putString(AppConfig.KEY_CUSTOM_ALL_POPUP_NOTICE, it)
                }
            }
            .apply()
    }

    suspend fun refresh(context: Context): AccountEntitlementsDto? = withContext(Dispatchers.IO) {
        val token = SecurePreferences.decrypt(context, AppConfig.KEY_AUTH_TOKEN)
        if (token.isEmpty()) return@withContext null
        runCatching {
            val res = RetrofitClient.paymentApiService.getAccountEntitlements("Bearer $token")
            if (res.isSuccessful && res.body()?.success == true) {
                val ent = res.body()!!.entitlements ?: return@runCatching null
                save(context, ent)
                ent
            } else null
        }.getOrNull()
    }
}
