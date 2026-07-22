package online.paychek.app.services.smartpopup

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * TrxID extraction for Smart Pop-up crop scan.
 *
 * Rules:
 * - Prefer OCR text taken from the crop bitmap only (caller responsibility).
 * - Accessibility fallback: only compact nodes mostly inside the crop — never
 *   scrape full SMS bubbles (that pulled "Grameenphone" / phones from outside).
 * - Token must look like a real TrxID: 8–15 chars, mix of letters + digits.
 *   Pure words ("Grameenphone", "encrypted") are rejected.
 */
object SmartPopupScanHelper {

    private val tokenRun = Regex("(?<![A-Za-z0-9])[A-Za-z0-9]{8,15}(?![A-Za-z0-9])")
    private val trxIdLabeled = Regex("""(?i)trx\s*id[:\s#-]*([A-Za-z0-9]{8,15})""")
    private val bdMobile = Regex("^01[3-9]\\d{8}$")

    private data class Hit(
        val value: String,
        val centerDist: Float,
        val coverage: Float,
        val quality: Int
    )

    /** Pick best TrxID from OCR / plain text that came from inside the crop only. */
    fun extractFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val hits = mutableListOf<Hit>()
        addTokensFromText(text, centerDist = 0f, coverage = 1f, out = hits)
        return pickBest(hits)
    }

    fun extractFromRegion(root: AccessibilityNodeInfo?, rect: Rect): String? {
        if (root == null) return null
        val hits = mutableListOf<Hit>()
        collectHits(root, rect, hits)
        return pickBest(hits)
    }

    fun isLikelyTrxId(token: String): Boolean {
        if (token.length !in 8..15) return false
        if (isPhoneNumber(token)) return false
        val hasLetter = token.any { it.isLetter() }
        val hasDigit = token.any { it.isDigit() }
        // Real bank/MFS TrxIDs are mixed (e.g. DGM5M7INHD). Reject "Grameenphone", "encrypted".
        if (!hasLetter || !hasDigit) return false
        if (token.all { it.isLetter() }) return false
        return true
    }

    private fun pickBest(hits: List<Hit>): String? {
        val pool = hits
            .distinctBy { it.value.uppercase() }
            .filter { isLikelyTrxId(it.value) }
        if (pool.isEmpty()) return null
        return pool.maxWithOrNull(
            compareByDescending<Hit> { it.quality }
                .thenByDescending { it.coverage }
                .thenBy { it.centerDist }
                .thenBy { abs(it.value.length - 10) }
        )?.value
    }

    private fun collectHits(node: AccessibilityNodeInfo, crop: Rect, out: MutableList<Hit>) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (Rect.intersects(bounds, crop)) {
            val nodeArea = bounds.width().toFloat() * bounds.height().toFloat().coerceAtLeast(1f)
            val cropArea = crop.width().toFloat() * crop.height().toFloat().coerceAtLeast(1f)
            val inter = intersectionArea(bounds, crop)
            val coverageOfNode = inter / nodeArea
            val coverageOfCrop = inter / cropArea
            val centerInside = crop.contains(bounds.centerX(), bounds.centerY())
            // Only tight leaf/link nodes — skip huge SMS bubbles entirely
            val nodeIsHuge = nodeArea > cropArea * 3f

            if (!nodeIsHuge &&
                centerInside &&
                coverageOfNode >= 0.65f &&
                coverageOfCrop >= 0.2f
            ) {
                val blobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                for (text in blobs) {
                    if (text.isBlank()) continue
                    addTokensFromText(
                        text = text,
                        centerDist = centerDistance(bounds, crop),
                        coverage = coverageOfNode,
                        out = out
                    )
                }
            }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectHits(child, crop, out)
                child.recycle()
            }
        }
    }

    private fun addTokensFromText(
        text: String,
        centerDist: Float,
        coverage: Float,
        out: MutableList<Hit>
    ) {
        val matches = mutableListOf<String>()
        trxIdLabeled.findAll(text).forEach { matches.add(it.groupValues[1]) }
        tokenRun.findAll(text).forEach { matches.add(it.value) }
        for (token in matches.distinctBy { it.uppercase() }) {
            if (!isLikelyTrxId(token)) continue
            out.add(
                Hit(
                    value = token,
                    centerDist = centerDist,
                    coverage = coverage,
                    quality = qualityScore(token, text)
                )
            )
        }
    }

    private fun isPhoneNumber(token: String): Boolean {
        if (!token.all { it.isDigit() }) return false
        if (bdMobile.matches(token)) return true
        if (token.startsWith("01") && token.length == 11) return true
        if (token.startsWith("8801") && token.length in 13..14) return true
        if (token.length in 10..15) return true
        return false
    }

    private fun qualityScore(token: String, sourceText: String): Int {
        var score = 0
        if (token.any { it.isLetter() } && token.any { it.isDigit() }) score += 250
        val labeled = trxIdLabeled.find(sourceText)?.groupValues?.getOrNull(1)
        if (labeled != null && labeled.equals(token, ignoreCase = true)) score += 120
        score += (10 - abs(token.length - 10)) * 3
        return score
    }

    private fun intersectionArea(a: Rect, b: Rect): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        return (right - left).toFloat() * (bottom - top).toFloat()
    }

    private fun centerDistance(a: Rect, b: Rect): Float {
        return hypot(
            a.exactCenterX() - b.exactCenterX(),
            a.exactCenterY() - b.exactCenterY()
        )
    }
}
