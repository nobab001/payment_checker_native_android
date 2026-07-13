package online.paychek.app.utils

import android.content.Context
import com.google.gson.reflect.TypeToken
import online.paychek.app.config.AppConfig
import online.paychek.app.data.local.prefs.PrefsHelper
import online.paychek.app.data.remote.dto.GatewayMethod

/**
 * Home SMS monitor may turn ON only when Device page setup is ready:
 * at least one SIM enabled + that SIM has a number and ≥1 enabled provider.
 */
object DeviceMonitoringGate {

    data class Result(val ready: Boolean, val message: String)

    fun check(context: Context): Result {
        val prefs = context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
        val sim1On = prefs.getBoolean(AppConfig.KEY_SIM1_ENABLED, true)
        val sim2On = prefs.getBoolean(AppConfig.KEY_SIM2_ENABLED, true)
        if (!sim1On && !sim2On) {
            return Result(false, "ডিভাইস পেজে গিয়ে কমপক্ষে একটি SIM চালু করুন")
        }

        val methods = loadMethods(context)
        if (methods.isEmpty()) {
            return Result(
                false,
                "ডিভাইস পেজে নম্বর দিন এবং প্রোভাইডার সিলেক্ট করুন"
            )
        }

        val activeSlots = buildSet {
            if (sim1On) add(1)
            if (sim2On) add(2)
        }

        val readySlot = activeSlots.any { slot ->
            val slotMethods = methods.filter { it.simSlot == slot && it.isEnabled == 1 }
            val hasNumber = slotMethods.any { !it.number.isNullOrBlank() }
            val hasProvider = slotMethods.isNotEmpty()
            hasNumber && hasProvider
        }

        if (!readySlot) {
            return Result(
                false,
                "ডিভাইস পেজে নম্বর + প্রোভাইডার সিলেক্ট করে কমপক্ষে একটি SIM চালু রাখুন"
            )
        }
        return Result(true, "")
    }

    private fun loadMethods(context: Context): List<GatewayMethod> {
        val json = PrefsHelper.getGatewayMethodsCache(context)
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val type = object : TypeToken<List<GatewayMethod>>() {}.type
            GsonUtils.gson.fromJson<List<GatewayMethod>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
