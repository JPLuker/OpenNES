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

    fun fallback(query: String): String? = fallbacks(query).firstOrNull()

    /**
     * Keep every more-specific candidate before stripping another suffix.
     * Only run these additional queries if the previous verified NES search
     * failed; never infer or automatically apply a Wikidata match.
     */
    fun fallbacks(query: String): List<String> {
        var current = query.trim()
        val options = mutableListOf<String>()
        repeat(3) {
            val shortened =
                current.replace(trailingQualifier, "")
                    .replace(trailingBracket, "")
                    .trim()
            if (shortened.length < 3 || shortened == current) return options
            options.add(shortened)
            current = shortened
        }
        return options
    }
}
