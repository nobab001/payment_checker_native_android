package online.paychek.app.services.sms

import android.util.Log
import online.paychek.app.data.remote.dto.GatewayMethod
import online.paychek.app.utils.SmsParser
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * SmsRoutingEngine — 3-Stage SMS Routing Decision Engine
 * =============================================================================
 * উদ্দেশ্য:
 *  একটি SMS শুধুমাত্র একবার Read হয়, কিন্তু User-এর Active Configuration
 *  অনুযায়ী সঠিক Destination-এ Route হয়।
 *
 * Architectural Principles:
 *  - Receiver কোনো Business Logic জানে না। এই class সব decision নেয়।
 *  - SmsReceiver এবং SmsPollWorker উভয়ই এই Shared Engine ব্যবহার করে।
 *  - Routing Decision হয় Template Match Result দেখে, isParseable flag দেখে নয়।
 *  - Backend Payload-এ শেষে isParseable=0/1 translate হয়; কিন্তু routing
 *    decision সর্বদা HISTORY / ARCHIVE / DROP।
 *
 * Pipeline (3 Stage):
 *  Stage-1: collectCandidates()  — sender/SIM/keyword দিয়ে সব matching methods
 *  Stage-2: resolveRoute()       — Template Match result দেখে route decide
 *  Stage-3: buildPayload()       — ParsedPayment তৈরি (isParseable server-payload)
 *
 * Route Priority:
 *  1. Template body match (regex .find()) → HISTORY (sms_history)
 *  2. Archive method configured            → ARCHIVE (custom_sms_archives)
 *  3. Neither                              → DROP
 *
 * Compatibility:
 *  - Backend, Database, API, Billing — কোনো পরিবর্তন নেই।
 *  - ProcessIncomingSmsUseCase — অপরিবর্তিত।
 *  - Server Payload: isParseable=1 (HISTORY) / isParseable=0 (ARCHIVE)
 *
 * Fixes (v2 — post senior code review):
 *  - matches() → find(): partial body match সমর্থন করে (P1)
 *  - Empty patterns → return false: no-regex config → safe DROP (P2)
 *  - Regex compile cache (ConcurrentHashMap) (P5)
 *  - Template candidates sorted by priority ascending (P6)
 *  - Archive candidate: priority-sorted deterministic selection (P3)
 * =============================================================================
 */
object SmsRoutingEngine {

    private const val TAG = "SmsRoutingEngine"

    // =========================================================================
    // Regex Pattern Cache — প্রতি SMS-এ compile না করে cache থেকে নেওয়া হয়।
    // ConcurrentHashMap — multiple threads (Guard-1 / Guard-2) safe।
    // =========================================================================
    private val patternCache = ConcurrentHashMap<String, Pattern>()

    private fun getOrCompilePattern(regex: String): Pattern? {
        return try {
            patternCache.getOrPut(regex) {
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Cache] Invalid regex pattern — compile failed: ${e.message}")
            null
        }
    }

    // =========================================================================
    // Route Type — Android-internal decision. Backend-এ translate হয় না সরাসরি।
    // =========================================================================
    enum class Route { HISTORY, ARCHIVE, DROP }

    /**
     * Routing reason — debug/logging এর জন্য।
     */
    enum class RouteReason {
        BODY_MATCH,           // regex .find() match হয়েছে → HISTORY
        BODY_FAIL_ARCHIVE,    // regex fail, archive configured → ARCHIVE
        NO_CANDIDATES,        // sender কোনো method-এর সাথে match করেনি → DROP
        NO_PERMISSION         // match হয়েছে কিন্তু কোনো route নেই → DROP
    }

    /**
     * Routing-এর চূড়ান্ত ফলাফল।
     *
     * @param route          HISTORY / ARCHIVE / DROP
     * @param reason         Debug-friendly কারণ
     * @param matchedMethod  যে GatewayMethod-এর সাথে match হয়েছে
     * @param isParseable    Server payload-এর জন্য: 1=HISTORY, 0=ARCHIVE
     */
    data class SmsRouteResult(
        val route: Route,
        val reason: RouteReason,
        val matchedMethod: GatewayMethod,
        val isParseable: Int
    )

    // =========================================================================
    // Public Entry Point
    // =========================================================================

    /**
     * SMS-এর জন্য Routing Decision নাও।
     *
     * @param sender         Originating address (SMS sender)
     * @param body           SMS body text
     * @param simSlot        কোন SIM-এ SMS আসলো (null = unknown)
     * @param cachedMethods  GatewayMethod cache (SharedPrefs থেকে)
     * @return SmsRouteResult — Route + matched method; null হলে DROP করো
     */
    fun resolve(
        sender: String,
        body: String,
        simSlot: Int?,
        cachedMethods: List<GatewayMethod>
    ): SmsRouteResult? {
        val cleanSender = sender.trim().lowercase(Locale.US)

        // ── Stage 1: Collect Candidates ───────────────────────────────────────
        val candidates = collectCandidates(
            cleanSender   = cleanSender,
            body          = body,
            simSlot       = simSlot,
            cachedMethods = cachedMethods
        )

        if (candidates.isEmpty()) {
            Log.d(TAG, "[Route] No candidates for sender='$sender' → DROP (NO_CANDIDATES)")
            return null
        }

        // ── Stage 2: Resolve Route ────────────────────────────────────────────
        val result = resolveRoute(body = body, candidates = candidates)

        if (result == null) {
            Log.d(TAG, "[Route] All candidates failed routing → DROP (NO_PERMISSION) for sender='$sender'")
        } else {
            Log.i(TAG, "[Route] ${result.route}(${result.reason}) | Provider=${result.matchedMethod.provider} | isParseable=${result.isParseable}")
        }

        return result
    }

    // =========================================================================
    // Stage 1 — Collect Candidates
    // =========================================================================

    /**
     * 4-Step filter chain: SIM slot → Sender ID → Sender Number → Keyword.
     *
     * গুরুত্বপূর্ণ: firstOrNull নয় — সব matching methods return হয়।
     * একই sender-এর isParseable=1 ও isParseable=0 উভয় method-ই collect হবে।
     *
     * Archive mode (isParseable=0) এর জন্য:
     *  - Sender Number check skip (archive sender-এ senderNumber থাকে না)
     *  - Keyword check skip (archive SMS-এ keyword filter নেই)
     */
    private fun collectCandidates(
        cleanSender: String,
        body: String,
        simSlot: Int?,
        cachedMethods: List<GatewayMethod>
    ): List<GatewayMethod> {
        return cachedMethods.filter { method ->
            val isArchive = (method.isParseable ?: 1) == 0

            // Step 1: Method enabled + SIM slot match
            method.isEnabled == 1 &&
            (simSlot == null || method.simSlot == simSlot) &&

            // Step 2: Sender ID exact match (lowercase normalized)
            (
                if (method.templateId == null) {
                    cleanSender == method.provider.trim().lowercase(Locale.US)
                } else {
                    val targetSender = method.senderId?.trim()?.lowercase(Locale.US)
                        ?: method.provider.lowercase(Locale.US)
                    cleanSender == targetSender
                }
            ) &&

            // Step 3: Sender Number exact match (skip for archive)
            (
                isArchive ||
                method.senderNumber.isNullOrBlank() ||
                cleanSender == method.senderNumber.trim().lowercase(Locale.US)
            ) &&

            // Step 4: Keyword match (skip for archive)
            (
                isArchive ||
                method.matchingKeyword.isNullOrBlank() ||
                method.matchingKeyword.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .any { keyword -> body.contains(keyword, ignoreCase = true) }
            )
        }
    }

    // =========================================================================
    // Stage 2 — Resolve Route
    // =========================================================================

    /**
     * Template Match Result দেখে Route নির্ধারণ করে।
     *
     * Priority:
     *  1. isParseable=1 candidate এর body regex match → HISTORY
     *  2. isParseable=0 candidate আছে             → ARCHIVE
     *  3. কোনোটাই না                               → null (DROP)
     *
     * "isParseable=1 আগে" নয়, বরং "Template Match হয়েছে কিনা" — এটাই decision।
     * ভবিষ্যতে বহু template type আসলেও এই logic অপরিবর্তিত থাকবে।
     *
     * Template candidates priority ascending sort করা হয় — priority=1 সবচেয়ে উপরে।
     * Archive candidates priority ascending sort — deterministic selection।
     */
    private fun resolveRoute(
        body: String,
        candidates: List<GatewayMethod>
    ): SmsRouteResult? {

        // ── Priority 1: Template body match (HISTORY) ─────────────────────────
        // isParseable=1 candidates priority ascending sort করো।
        val templateCandidates = candidates
            .filter { (it.isParseable ?: 1) == 1 }
            .sortedBy { it.priority }

        for (method in templateCandidates) {
            if (matchesBodyPattern(body, method)) {
                return SmsRouteResult(
                    route         = Route.HISTORY,
                    reason        = RouteReason.BODY_MATCH,
                    matchedMethod = method,
                    isParseable   = 1
                )
            }
        }

        if (templateCandidates.isNotEmpty()) {
            Log.d(TAG, "[Route] ${templateCandidates.size} template candidate(s) found but none matched body regex → checking archive")
        }

        // ── Priority 2: Archive configured (ARCHIVE) ──────────────────────────
        // Priority ascending sort — ছোট priority number = আগে। Deterministic।
        val archiveCandidate = candidates
            .filter { (it.isParseable ?: 1) == 0 }
            .minByOrNull { it.priority }

        if (archiveCandidate != null) {
            return SmsRouteResult(
                route         = Route.ARCHIVE,
                reason        = RouteReason.BODY_FAIL_ARCHIVE,
                matchedMethod = archiveCandidate,
                isParseable   = 0
            )
        }

        // ── Priority 3: Drop ──────────────────────────────────────────────────
        return null
    }

    // =========================================================================
    // Body Pattern Match Helper
    // =========================================================================

    /**
     * SMS body টি GatewayMethod-এর regex/customPatterns-এর সাথে match করে কিনা।
     *
     * Fix (P1): matches() → find()
     *  matches() = full string match (পুরো string এক regex-এ মিলতে হবে)।
     *  find()    = partial match (body-র যেকোনো অংশে pattern থাকলেই true)।
     *  SMS body partial match দরকার — "আপনার Cash Out সফল হয়েছে" → pattern "Cash Out" → true।
     *
     * Fix (P2): empty patterns → return false (safe)
     *  Admin regex configure না করলে সব SMS History-তে যাবে না।
     *  Pattern নেই = admin অসম্পূর্ণ config → safe drop।
     *  (Keyword pre-filter ইতিমধ্যে Step 4-এ হয়েছে।)
     *
     * Fix (P5): ConcurrentHashMap regex cache।
     *  প্রতি SMS-এ Pattern.compile() না করে cache থেকে নেওয়া হয়।
     *
     * Pattern source (priority ক্রমে):
     *  1. method.customPatterns  — admin-defined custom regex list
     *  2. method.regexPattern    — main regex pattern
     *
     * Pattern separator "|||" দিয়ে sub-patterns আলাদা করা থাকে।
     * যেকোনো একটি sub-pattern body-তে .find() হলে true।
     */
    private fun matchesBodyPattern(body: String, method: GatewayMethod): Boolean {
        if ((method.isParseable ?: 1) == 0) return false // archive-এ pattern match নেই

        val patternsToTry = mutableListOf<String>()
        method.customPatterns?.let { patternsToTry.addAll(it) }
        if (!method.regexPattern.isNullOrBlank()) {
            patternsToTry.add(method.regexPattern)
        }

        // Fix P2: empty patterns → return false (safe drop, not auto-history)
        if (patternsToTry.isEmpty()) {
            Log.d(TAG, "[Pattern] No regex patterns configured for provider=${method.provider} → false (safe)")
            return false
        }

        val trimmedBody = body.trim()
        for (pattern in patternsToTry) {
            if (pattern.isBlank()) continue
            try {
                val subPatterns = pattern.split("|||")
                for (sub in subPatterns) {
                    if (sub.isBlank()) continue
                    // Fix P5: cache থেকে compiled Pattern নেওয়া
                    val compiled = getOrCompilePattern(sub) ?: continue
                    // Fix P1: matches() → find() — partial body match সমর্থন করে
                    if (compiled.matcher(trimmedBody).find()) {
                        Log.d(TAG, "[Pattern] find() match! provider=${method.provider} | sub='${sub.take(40)}'")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "[Pattern] Matcher error for provider=${method.provider}: ${e.message}")
            }
        }

        return false
    }

    // =========================================================================
    // Stage 3 — ParsedPayment Builder
    // =========================================================================

    /**
     * SmsRouteResult থেকে ParsedPayment তৈরি করো।
     * ProcessIncomingSmsUseCase-এ পাঠানোর জন্য।
     *
     * HISTORY route: SmsParser.parseWithDynamicRegex() বা parseSms() দিয়ে parse।
     * ARCHIVE route: amount=0, trxId="" দিয়ে raw payload।
     *
     * @param result      Stage-2 এর SmsRouteResult
     * @param sender      Original sender address
     * @param body        SMS body
     * @param timestamp   SMS timestamp epoch ms
     * @param simSlot     SIM slot number (nullable)
     * @param simNumber   SIM phone number (nullable)
     */
    fun buildPayload(
        result: SmsRouteResult,
        sender: String,
        body: String,
        timestamp: Long,
        simSlot: Int?,
        simNumber: String?
    ): SmsParser.ParsedPayment {
        val method = result.matchedMethod

        return when (result.route) {
            Route.HISTORY -> {
                // Dynamic regex parse চেষ্টা করো; fallback: SmsParser.parseSms()
                SmsParser.parseWithDynamicRegex(
                    body           = body,
                    regexPattern   = method.regexPattern,
                    providerTag    = method.provider,
                    senderNumber   = sender,
                    timestamp      = timestamp,
                    simSlot        = simSlot,
                    simNumber      = simNumber ?: method.number,
                    isCustomSender = false
                ) ?: SmsParser.parseSms(sender, body, timestamp)?.copy(
                    simSlot        = simSlot,
                    simNumber      = simNumber ?: method.number,
                    isCustomSender = false,
                    providerTag    = method.provider
                ) ?: SmsParser.ParsedPayment(
                    amount         = 0.0,
                    trxId          = "",
                    providerTag    = method.provider,
                    senderNumber   = sender,
                    rawBody        = body,
                    smsTimestamp   = timestamp,
                    simSlot        = simSlot,
                    simNumber      = simNumber ?: method.number,
                    isCustomSender = false,
                    fullSms        = body,
                    isParseable    = 1
                )
            }

            Route.ARCHIVE -> {
                SmsParser.ParsedPayment(
                    amount         = 0.0,
                    trxId          = "",
                    providerTag    = method.provider,
                    senderNumber   = sender,
                    rawBody        = body,
                    smsTimestamp   = timestamp,
                    simSlot        = simSlot,
                    simNumber      = simNumber ?: method.number,
                    isCustomSender = true,
                    fullSms        = body,
                    isParseable    = 0
                )
            }

            Route.DROP -> throw IllegalStateException("DROP route should never reach buildPayload()")
        }
    }

    // =========================================================================
    // Cache Management
    // =========================================================================

    /**
     * Regex cache clear করো।
     * GatewayMethod sync হলে (নতুন template/regex এলে) call করো।
     * সাধারণত gateway config refresh-এর পরে।
     */
    fun clearPatternCache() {
        val size = patternCache.size
        patternCache.clear()
        Log.i(TAG, "[Cache] Pattern cache cleared — $size entries removed")
    }
}
