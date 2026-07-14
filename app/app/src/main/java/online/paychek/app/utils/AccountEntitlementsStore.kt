package online.paychek.app.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.AccountEntitlementsDto

object AccountEntitlementsStore {

    fun readCached(context: Context): AccountEntitlementsDto {
        return AccountEntitlementsDto(
            permCustomSender = if (SecurePreferences.decrypt(context, AppConfig.KEY_PERM_CUSTOM_SENDER) == "1") 1 else 0,
            permTemplate = if (SecurePreferences.decrypt(context, AppConfig.KEY_PERM_TEMPLATE) == "1") 1 else 0,
            permWebsite = if (SecurePreferences.decrypt(context, AppConfig.KEY_PERM_WEBSITE) == "1") 1 else 0,
            permDevice = if (SecurePreferences.decrypt(context, AppConfig.KEY_PERM_DEVICE) == "1") 1 else 0,
            effMaxDevices = SecurePreferences.decrypt(context, AppConfig.KEY_EFF_MAX_DEVICES).toIntOrNull() ?: 0,
            effMaxSites = SecurePreferences.decrypt(context, AppConfig.KEY_EFF_MAX_SITES).toIntOrNull() ?: 0
        )
    }

    fun save(context: Context, ent: AccountEntitlementsDto) {
        SecurePreferences.encrypt(context, AppConfig.KEY_PERM_CUSTOM_SENDER, if (ent.permCustomSender == 1) "1" else "0")
        SecurePreferences.encrypt(context, AppConfig.KEY_PERM_TEMPLATE, if (ent.permTemplate == 1) "1" else "0")
        SecurePreferences.encrypt(context, AppConfig.KEY_PERM_WEBSITE, if (ent.permWebsite == 1) "1" else "0")
        SecurePreferences.encrypt(context, AppConfig.KEY_PERM_DEVICE, if (ent.permDevice == 1) "1" else "0")
        SecurePreferences.encrypt(context, AppConfig.KEY_EFF_MAX_DEVICES, ent.effMaxDevices.toString())
        SecurePreferences.encrypt(context, AppConfig.KEY_EFF_MAX_SITES, ent.effMaxSites.toString())
        online.paychek.app.services.sync.CommPolicyStore.applyEntitlements(context, ent)
        context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AppConfig.KEY_HAS_CUSTOM_SENDER_ADDON, ent.hasCustomSender)
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
