package online.paychek.app.utils

/**
 * Shared limit for transaction / SMS search fields (home, archive, search).
 * Typing or paste: keep at most [MAX_SEARCH_CHARS] characters (letters, digits, spaces, etc.).
 */
object SearchInputLimits {
    const val MAX_SEARCH_CHARS = 15

    fun clamp(raw: String): String =
        if (raw.length <= MAX_SEARCH_CHARS) raw else raw.take(MAX_SEARCH_CHARS)
}
