package com.swordfish.lemuroid.app.shared.metadata

/**
 * A bundled catalogue title can include a publisher, region, or revision
 * qualifier that Wikidata does not index in its actual game label.
 *
 * Use the original title first. Only retry a shorter title when the first
 * search produced no verified NES candidates, never to guess a match.
 */
internal object NesManualSearchTerms {
    private val trailingQualifier = Regex("""\s*\([^()]{2,40}\)\s*$""")
    private val trailingBracket = Regex("""\s*\[[^\[\]]{2,40}]\s*$""")

    fun fallback(query: String): String? {
        val trimmed = query.trim()
        val withoutQualifier =
            trimmed.replace(trailingQualifier, "")
                .replace(trailingBracket, "")
                .trim()
        return withoutQualifier.takeIf {
            it.length >= 3 && it != trimmed
        }
    }
}
