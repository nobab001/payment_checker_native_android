package online.paychek.app.services.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import online.paychek.app.utils.SmsParser

/**
 * SmsReceiver — ইনকামিং SMS ধরে, পার্স করে এবং SIM স্লট তথ্য যোগ করে।
 *
 * SIM Slot Detection লজিক:
 *  • SMS Intent থেকে `subscription` extra পড়া হয় (subscriptionId)
 *  • SubscriptionManager দিয়ে subscriptionId → simSlotIndex ম্যাপ করা হয়
 *  • simSlotIndex 0-indexed, তাই +1 করে 1 বা 2 রিটার্ন করা হয়
 *  • READ_PHONE_STATE permission না থাকলে simSlot = null রাখা হয় (crash নয়)
 */
class SmsReceiver(
    private val onPaymentSmsReceived: ((SmsParser.ParsedPayment) -> Unit)? = null
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        // SMS intent-এ subscriptionId থাকে এই key-তে
        private const val EXTRA_SUBSCRIPTION_ID = "subscription"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            // ─── SIM Slot সনাক্তকরণ ──────────────────────────────────────
            val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1)
            val simSlot        = resolveSimSlot(context, subscriptionId) // 1, 2, বা null
            val simNumber      = resolveSimNumber(context, subscriptionId)
            // ─────────────────────────────────────────────────────────────

            for (msg in messages) {
                val sender    = msg.originatingAddress ?: continue
                val body      = msg.messageBody       ?: continue
                val timestamp = msg.timestampMillis

                Log.d(TAG, "SMS ধরা হয়েছে — From: $sender | SIM Slot: $simSlot | Length: ${body.length}")

                // Regex দিয়ে পেমেন্ট SMS পার্স করা
                val parsed = SmsParser.parseSms(sender, body, timestamp)

                if (parsed != null) {
                    // SIM তথ্য copy() দিয়ে ParsedPayment-এ যোগ করা
                    val parsedWithSim = parsed.copy(
                        simSlot   = simSlot,
                        simNumber = simNumber
                    )
                    Log.i(TAG, "✅ পেমেন্ট SMS পার্স সফল — TrxID: ${parsedWithSim.trxId} | " +
                            "Amount: ${parsedWithSim.amount} | Provider: ${parsedWithSim.providerTag} | " +
                            "SIM: $simSlot")
                    onPaymentSmsReceived?.invoke(parsedWithSim)
                } else {
                    Log.d(TAG, "পেমেন্ট প্যাটার্ন মেলেনি — From: $sender")
                }
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException — SIM পড়ার অনুমতি নেই: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "SMS প্রসেস করতে ত্রুটি: ", e)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // subscriptionId → Physical SIM Slot (1 বা 2) রূপান্তর
    // ──────────────────────────────────────────────────────────────────────────
    private fun resolveSimSlot(context: Context, subscriptionId: Int): Int? {
        if (subscriptionId == -1) return null
        return try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val info = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                    ?: return null
                // simSlotIndex = 0-based → আমরা 1-based ব্যবহার করব
                (info.simSlotIndex + 1).takeIf { it in 1..2 }
            } else {
                // পুরনো API — সরাসরি subscriptionId থেকে slot অনুমান করা যায় না
                null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SIM slot পড়তে permission নেই: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "SIM slot resolve করা যায়নি: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // subscriptionId → SIM ফোন নম্বর রিড (পাওয়া না গেলে null)
    // ──────────────────────────────────────────────────────────────────────────
    private fun resolveSimNumber(context: Context, subscriptionId: Int): String? {
        if (subscriptionId == -1) return null
        return try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val info = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                // কিছু ডিভাইস ও অপারেটর নম্বর দেয় না — সে ক্ষেত্রে null
                info?.number?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
