package com.combadge.app.speech

import com.combadge.app.model.Peer
import kotlin.math.min

/**
 * Parses free-form speech transcriptions into hail targets.
 *
 * Supported patterns:
 *   "[Name]"                      → Hail "Name"
 *   "[Caller] to [Name]"          → Hail "Name"
 *   "Hail [Name]"                 → Hail "Name"
 *   "Open a channel to [Name]"    → Hail "Name"
 *
 * Name matching (case-insensitive):
 *   1. Exact match
 *   2. Starts-with match (single candidate)
 *   3. Levenshtein distance ≤ 2 (single candidate)
 */
object CommandParser {

    private val HAIL_PREFIXES = listOf("hail ")
    private val CHANNEL_PREFIXES = listOf(
        "open a channel to ",
        "open channel to ",
        "channel to "
    )
    private val TO_PATTERN = Regex("""(.+?)\s+to\s+(.+)""", RegexOption.IGNORE_CASE)

    sealed class ParseResult {
        data class Target(val name: String) : ParseResult()
        data object Unknown : ParseResult()
    }

    /** Returns the best-guess target name from the transcribed text. */
    fun extractTarget(text: String): String? {
        val t = text.trim()

        // "Open a channel to Name"
        for (prefix in CHANNEL_PREFIXES) {
            if (t.startsWith(prefix, ignoreCase = true)) {
                return t.substring(prefix.length).trim()
            }
        }

        // "Hail Name"
        for (prefix in HAIL_PREFIXES) {
            if (t.startsWith(prefix, ignoreCase = true)) {
                return t.substring(prefix.length).trim()
            }
        }

        // "Caller to Name"
        val match = TO_PATTERN.matchEntire(t)
        if (match != null) {
            return match.groupValues[2].trim()
        }

        // Bare name — treat entire text as target
        return t.ifBlank { null }
    }

    /**
     * Match a candidate target name against the known peer list.
     * Returns:
     *   - An empty list if no match.
     *   - A list with one peer for an unambiguous match.
     *   - A list with multiple peers if disambiguation is needed.
     */
    fun matchPeers(targetName: String, peers: List<Peer>): List<Peer> {
        val q = targetName.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        // 1. Exact match
        val exact = peers.filter { peer -> peer.allNames.any { it == q } }
        if (exact.isNotEmpty()) return exact

        // 2. Starts-with match
        val startsWith = peers.filter { peer -> peer.allNames.any { it.startsWith(q) } }
        if (startsWith.size == 1) return startsWith
        if (startsWith.size > 1) return startsWith  // disambiguation needed

        // 3. Levenshtein distance ≤ 2
        val fuzzy = peers.filter { peer ->
            peer.allNames.any { levenshtein(it, q) <= 2 }
        }
        if (fuzzy.size == 1) return fuzzy
        if (fuzzy.size > 1) return fuzzy  // disambiguation needed

        return emptyList()
    }

    // Standard iterative Levenshtein distance
    private fun levenshtein(a: String, b: String): Int {
        val la = a.length
        val lb = b.length
        if (la == 0) return lb
        if (lb == 0) return la

        val dp = Array(la + 1) { IntArray(lb + 1) }
        for (i in 0..la) dp[i][0] = i
        for (j in 0..lb) dp[0][j] = j

        for (i in 1..la) {
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[la][lb]
    }
}
