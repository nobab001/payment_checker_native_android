package online.paychek.app.services.sms

import online.paychek.app.data.remote.dto.GatewayMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Block-list + HISTORY sender matching helpers (no Android Log).
 */
class SmsRoutingBlockSenderTest {

    private fun method(
        id: Int,
        sim: Int,
        senderId: String?,
        parseable: Int,
        enabled: Int = 1,
        keyword: String? = null,
        provider: String = "x",
        senderNumber: String? = null,
        priority: Int = 1
    ) = GatewayMethod(
        id = id,
        simSlot = sim,
        provider = provider,
        number = null,
        displayName = null,
        isEnabled = enabled,
        priority = priority,
        templateId = id,
        senderId = senderId,
        senderNumber = senderNumber,
        matchingKeyword = keyword,
        regexPattern = null,
        customPatterns = null,
        isOfficial = 0,
        isParseable = parseable,
        singleNumberInstruction = null,
        multipleNumberInstruction = null
    )

    @Test
    fun isBlockSenderMethod_detectsKeywordAndProvider() {
        val byKw = method(1, 1, "spam", 0, keyword = SmsRoutingEngine.BLOCK_SENDER_KEYWORD)
        val byProv = method(2, 1, "spam", 0, provider = "BLOCK")
        val all = method(3, 1, "*", 0, provider = "ALL")
        assertTrue(SmsRoutingEngine.isBlockSenderMethod(byKw))
        assertTrue(SmsRoutingEngine.isBlockSenderMethod(byProv))
        assertFalse(SmsRoutingEngine.isBlockSenderMethod(all))
    }

    @Test
    fun isSenderBlocked_matchesSlotAndSender() {
        val methods = listOf(
            method(1, 1, "spam", 0, keyword = SmsRoutingEngine.BLOCK_SENDER_KEYWORD, provider = "BLOCK"),
            method(2, 2, "spam", 0, keyword = SmsRoutingEngine.BLOCK_SENDER_KEYWORD, provider = "BLOCK"),
            method(3, 1, "*", 0, provider = "ALL")
        )
        assertTrue(SmsRoutingEngine.isSenderBlocked("spam", 1, methods))
        assertTrue(SmsRoutingEngine.isSenderBlocked("spam", 2, methods))
        assertFalse(SmsRoutingEngine.isSenderBlocked("gp", 1, methods))
        assertFalse(SmsRoutingEngine.isSenderBlocked("spam", 1, methods.filter { it.simSlot == 2 }))
    }

    @Test
    fun matchesMethodSender_acceptsBrandOrShortCode() {
        val m = method(
            id = 1,
            sim = 1,
            senderId = "bkash",
            parseable = 1,
            provider = "bKash",
            senderNumber = "16216"
        )
        assertTrue(SmsRoutingEngine.matchesMethodSender("bkash", m))
        assertTrue(SmsRoutingEngine.matchesMethodSender("16216", m))
        assertFalse(SmsRoutingEngine.matchesMethodSender("nagad", m))
    }

    @Test
    fun isSenderBlocked_globalListWinsWithoutDeviceBlock() {
        val methods = listOf(
            method(3, 1, "*", 0, provider = "ALL")
        )
        assertTrue(
            SmsRoutingEngine.isSenderBlocked(
                cleanSender = "promo",
                simSlot = 1,
                cachedMethods = methods,
                globalBlockedSenders = listOf("PROMO", "ads")
            )
        )
        assertFalse(
            SmsRoutingEngine.isSenderBlocked(
                cleanSender = "gp",
                simSlot = 1,
                cachedMethods = methods,
                globalBlockedSenders = listOf("promo")
            )
        )
    }

    @Test
    fun disabledBlock_doesNotMatch() {
        val methods = listOf(
            method(1, 1, "spam", 0, enabled = 0, keyword = SmsRoutingEngine.BLOCK_SENDER_KEYWORD, provider = "BLOCK")
        )
        assertFalse(SmsRoutingEngine.isSenderBlocked("spam", 1, methods))
    }
}
