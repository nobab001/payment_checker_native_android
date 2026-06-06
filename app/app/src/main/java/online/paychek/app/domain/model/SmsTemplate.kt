package online.paychek.app.domain.model

/**
 * SMS parsing template — admin-configured regex rules.
 * Corresponds to blueprint: models/sms_template.dart
 *
 * Token → RegExp mapping (blueprint Section 3.A.1):
 *   [Amount]   → ([\d,]+(?:\.\d+)?)
 *   [Sender]   → (01[3-9]\d{8}|[\d*Xx]+|\S+(?:\s+\S+)?)
 *   [TrxID]    → ([A-Z0-9]{6,})
 *   [DateTime] → ([\d/:.\ -\s]+(?:AM|PM|am|pm)?)
 *   [Balance]  → ([\d,]+(?:\.\d+)?)
 *   [random]   → (.+?)
 *   [variable] → (.+?)
 */
data class SmsTemplate(
    val id: Int,
    val customerPreview: String,    // e.g. "bKash Personal", "Nagad Agent"
    val senderId: String,           // e.g. "bKash", "01700000000"
    val formats: List<String>,      // format strings with token placeholders
    val isActive: Boolean,
)
