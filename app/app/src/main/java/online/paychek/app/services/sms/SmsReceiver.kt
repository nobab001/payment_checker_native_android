package online.paychek.app.services.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.GatewayMethod
import online.paychek.app.utils.SmsParser
import java.util.Locale
import java.util.regex.Pattern

/**
 * SmsReceiver — ইনকামিং SMS ধরে, ৩-স্তরের হ্যাকিং ফিল্টার প্রয়োগ করে এবং ডাইনামিক ফিল্টারিং নিশ্চিত করে।
 *
 * সিকিউরিটি ফিল্টারিং লজিক:
 *  ১. কন্ডিশন ১ ও ২ (বাধ্যতামূলক): SIM Slot এবং Sender ID গ্রাহকের সক্রিয় কনফিগারের সাথে ম্যাচ করতে হবে।
 *  ২. কন্ডিশন ৩ (Twin-Mode Logic):
 *     - অফিশিয়াল প্রোভাইডার (Strict Mode): matchingKeyword ভেরিফিকেশন এবং regex_pattern দিয়ে ট্রানজেকশন আইডি ও অ্যামাউন্ট পার্স।
 *     - কাস্টম প্রোভাইডার (Backup Mode): কোনো Regex বা কিওয়ার্ড চেক ছাড়াই ডামি ট্রানজেকশন আইডি (BKUP-timestamp-hash) ও ০৳ অ্যামাউন্ট দিয়ে সরাসরি সেন্ট্রাল ডাটাবেজে ব্যাকআপ।
 */
class SmsReceiver(
    private val onPaymentSmsReceived: ((SmsParser.ParsedPayment) -> Unit)? = null
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
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

            // লোকাল SharedPreferences থেকে কনফিগ ও গেটওয়ে মেথড ক্যাশ লোড করা
            val prefs = context.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
            val sim1Enabled = prefs.getBoolean(AppConfig.KEY_SIM1_ENABLED, true)
            val sim2Enabled = prefs.getBoolean(AppConfig.KEY_SIM2_ENABLED, true)

            // ১. SIM Slot ফিল্টার (কন্ডিশন ১ ও ২ - বাধ্যতামূলক)
            if (simSlot != null) {
                val isSimEnabled = if (simSlot == 1) sim1Enabled else sim2Enabled
                if (!isSimEnabled) {
                    Log.d(TAG, "SMS ignored: SIM slot $simSlot is disabled in master settings.")
                    return
                }
            }

            val methodsJson = prefs.getString(AppConfig.KEY_GATEWAY_METHODS_CACHE, "[]") ?: "[]"
            val methodsType = object : com.google.gson.reflect.TypeToken<List<GatewayMethod>>() {}.type
            val cachedMethods: List<GatewayMethod> = try {
                com.google.gson.Gson().fromJson(methodsJson, methodsType)
            } catch (e: Exception) {
                emptyList()
            }

            for (msg in messages) {
                val sender    = msg.originatingAddress ?: continue
                val body      = msg.messageBody       ?: continue
                val timestamp = msg.timestampMillis

                Log.d(TAG, "SMS ধরা হয়েছে — From: $sender | SIM Slot: $simSlot | Length: ${body.length}")

                val cleanSender = sender.trim().lowercase(Locale.US)

                // ২. কন্ডিশন ১ ও ২ (বাধ্যতামূলক): Sender ID এবং SIM Slot মিলতে হবে
                // (যে মেথডটি চালু আছে এবং যার simSlot ও sender আইডি ম্যাচ করে)
                val matchingMethod = cachedMethods.firstOrNull { method ->
                    method.isEnabled == 1 &&
                    (simSlot == null || method.simSlot == simSlot) &&
                    (
                        // অফিসিয়াল প্রোভাইডার চেক
                        if (isOfficialProvider(method.provider) || method.isOfficial == 1) {
                            val targetSender = method.senderId?.trim()?.lowercase(Locale.US) ?: method.provider.lowercase(Locale.US)
                            cleanSender.contains(targetSender) || 
                            (targetSender == "rocket" && cleanSender == "16216")
                        } else {
                            // কাস্টম প্রোভাইডার (Backup Mode) -> সেন্ডার আইডি বা ডিসপ্লে নেম বা প্রোভাইডার হুবহু মিলতে হবে
                            val targetSender = method.senderId?.trim()?.lowercase(Locale.US) ?: ""
                            val displayName = method.displayName?.trim()?.lowercase(Locale.US) ?: ""
                            val provider = method.provider.trim().lowercase(Locale.US)
                            cleanSender == targetSender || cleanSender == displayName || cleanSender == provider
                        }
                    )
                }

                if (matchingMethod == null) {
                    Log.d(TAG, "পেমেন্ট এসএমএস ইগনোর (কন্ডিশন ১ ও ২ মিলেনি) — From: $sender | SIM Slot: $simSlot")
                    continue
                }

                Log.i(TAG, "ম্যাচিং গেটওয়ে মেথড পাওয়া গেছে — ID: ${matchingMethod.id} | Provider: ${matchingMethod.provider}")

                // ৩. কন্ডিশন ৩ (টুইন-মোড লজিক)
                val isOfficial = isOfficialProvider(matchingMethod.provider) || (matchingMethod.isOfficial == 1)

                if (isOfficial) {
                    // ── ক) Strict Mode (অফিসিয়াল এডমিন টেমপ্লেট) ────────────────
                    val matchingKeyword = matchingMethod.matchingKeyword
                    if (!matchingKeyword.isNullOrBlank()) {
                        // কিওয়ার্ড ভেরিফিকেশন (সবগুলো কিওয়ার্ড কমা দিয়ে আলাদা করে বডিতে থাকতে হবে)
                        val keywords = matchingKeyword.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val hasAllKeywords = keywords.all { keyword ->
                            body.contains(keyword, ignoreCase = true)
                        }
                        if (!hasAllKeywords) {
                            Log.w(TAG, "Strict Mode matching keywords missing. Dropping SMS from $sender")
                            continue
                        }
                    }

                    // Regex দিয়ে ট্রানজেকশন পার্স করা (কাস্টম বা ডিফল্ট)
                    val customRegex = matchingMethod.regexPattern
                    val parsedPayment = if (!customRegex.isNullOrBlank()) {
                        parseWithCustomRegex(body, customRegex, matchingMethod.provider, timestamp)
                    } else {
                        // ডিফল্ট হার্ডকোডেড রেজেক্স ফ্যালব্যাক
                        SmsParser.parseSms(sender, body, timestamp)
                    }

                    if (parsedPayment != null) {
                        val finalPayment = parsedPayment.copy(
                            simSlot   = simSlot,
                            simNumber = simNumber ?: matchingMethod.number
                        )
                        Log.i(TAG, "✅ পেমেন্ট SMS Strict Mode পার্স সফল — TrxID: ${finalPayment.trxId}")
                        onPaymentSmsReceived?.invoke(finalPayment)
                    } else {
                        Log.w(TAG, "Strict Mode Regex parsing failed. Dropping SMS from $sender")
                    }

                } else {
                    // ── খ) Backup Mode (কাস্টম সেন্ডার আইডি - All Pass) ────────
                    // কোনো কন্ডিশন ছাড়া রিড করে ডামি TrxID সহ সেন্ট্রাল ডাটাবেজে পাঠিয়ে দেবে
                    val bodyHash = body.hashCode().toString(16)
                    val dummyTrxId = "BKUP-${timestamp}-${bodyHash}".uppercase(Locale.US)
                    
                    val backupPayment = SmsParser.ParsedPayment(
                        amount = 0.0,
                        trxId = dummyTrxId,
                        providerTag = matchingMethod.provider,
                        senderNumber = sender,
                        rawBody = body,
                        smsTimestamp = timestamp,
                        simSlot = simSlot,
                        simNumber = simNumber ?: matchingMethod.number
                    )

                    Log.i(TAG, "✅ Backup Mode (All Pass) — TrxID: $dummyTrxId | Provider: ${matchingMethod.provider}")
                    onPaymentSmsReceived?.invoke(backupPayment)
                }
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException — SIM পড়ার অনুমতি নেই: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "SMS প্রসেস করতে ত্রুটি: ", e)
        }
    }

    private fun isOfficialProvider(provider: String): Boolean {
        val clean = provider.trim().lowercase(Locale.US)
        return clean == "bkash" || clean == "nagad" || clean == "rocket" || clean == "upay"
    }

    private fun parseWithCustomRegex(body: String, patternStr: String, providerTag: String, timestamp: Long): SmsParser.ParsedPayment? {
        return try {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", "") ?: "0.0"
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                val trxId = matcher.group(2) ?: ""
                val senderNumber = if (matcher.groupCount() >= 3) matcher.group(3) ?: "Unknown" else "Unknown"

                if (trxId.isNotEmpty()) {
                    SmsParser.ParsedPayment(
                        amount = amount,
                        trxId = trxId.uppercase(Locale.US),
                        providerTag = providerTag,
                        senderNumber = senderNumber,
                        rawBody = body,
                        smsTimestamp = timestamp
                    )
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing with custom regex: ${e.message}")
            null
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
                (info.simSlotIndex + 1).takeIf { it in 1..2 }
            } else {
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
