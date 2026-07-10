package online.paychek.app.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log

/**
 * Resolves physical SIM slot (1 or 2) from SMS broadcast / inbox subscription IDs.
 * OEMs use different intent extras — try all known keys before giving up.
 */
object SimSlotHelper {

    private const val TAG = "SimSlotHelper"
    private const val LEGACY_SUB_EXTRA = "subscription"

    fun resolveSubscriptionId(intent: Intent): Int {
        val keys = listOf(
            LEGACY_SUB_EXTRA,
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "subscription_id",
        )
        for (key in keys) {
            val value = intent.getIntExtra(key, -1)
            if (value >= 0) return value
        }
        return -1
    }

    fun resolveSimSlot(context: Context, subscriptionId: Int): Int? {
        if (subscriptionId >= 0) {
            resolveSimSlotFromSubscriptionId(context, subscriptionId)?.let { return it }
        }
        return null
    }

    fun resolveSimSlotFromIntent(context: Context, intent: Intent): Int? {
        val subscriptionId = resolveSubscriptionId(intent)
        resolveSimSlot(context, subscriptionId)?.let { return it }

        val slotKeys = listOf(
            "android.telephony.extra.SLOT_INDEX",
            "slot",
            "simslot",
            "phone",
        )
        for (key in slotKeys) {
            val slotIndex = intent.getIntExtra(key, -1)
            if (slotIndex in 0..1) return slotIndex + 1
        }

        return null
    }

    fun resolveSimNumber(context: Context, subscriptionId: Int): String? {
        if (subscriptionId < 0) return null
        return try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    subscriptionManager.getPhoneNumber(subscriptionId).takeIf { it.isNotBlank() }
                } else {
                    @Suppress("DEPRECATION")
                    subscriptionManager.getActiveSubscriptionInfo(subscriptionId)?.number?.takeIf { it.isNotBlank() }
                }
            } else {
                null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read SIM number: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve SIM number: ${e.message}")
            null
        }
    }

    private fun resolveSimSlotFromSubscriptionId(context: Context, subscriptionId: Int): Int? {
        return try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val info = subscriptionManager.getActiveSubscriptionInfo(subscriptionId) ?: return null
                (info.simSlotIndex + 1).takeIf { it in 1..2 }
            } else {
                null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read SIM slot: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve SIM slot: ${e.message}")
            null
        }
    }
}
