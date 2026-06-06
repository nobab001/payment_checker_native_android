package online.paychek.app.domain.model

/**
 * SIM slot filter configuration.
 * Corresponds to blueprint: models/sim_filter_preferences.dart
 */
data class SimSlotConfig(
    val slot: Int,                      // 1 বা 2
    val isActive: Boolean,
    val simNumber: String?,             // 11-digit BD number
    val allowedTemplateIds: List<Int>,  // কোন কোন SmsTemplate এই SIM-এ match করবে
    val customSenders: List<String>,    // e.g. ["MYBANK", "017XXXXXXXX"]
)
