package online.paychek.app.services.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import online.paychek.app.utils.SmsParser

class SmsReceiver(
    private val onPaymentSmsReceived: ((SmsParser.ParsedPayment) -> Unit)? = null
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                for (msg in messages) {
                    val sender = msg.originatingAddress ?: continue
                    val body = msg.messageBody ?: continue
                    val timestamp = msg.timestampMillis

                    Log.d("SmsReceiver", "Captured SMS from: $sender, body length: ${body.length}")

                    // Parse SMS content using regex templates
                    val parsed = SmsParser.parseSms(sender, body, timestamp)
                    if (parsed != null) {
                        Log.i("SmsReceiver", "Matched Payment SMS: TrxID: ${parsed.trxId}, Amount: ${parsed.amount}")
                        // Forward to callback (active service)
                        onPaymentSmsReceived?.invoke(parsed)
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error receiving SMS: ", e)
            }
        }
    }
}
