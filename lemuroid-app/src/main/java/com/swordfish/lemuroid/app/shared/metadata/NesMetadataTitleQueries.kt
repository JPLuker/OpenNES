package com.swordfish.lemuroid.app.shared.metadata

import com.swordfish.lemuroid.lib.library.metadata.NesGameTitle
import java.util.Locale

/**
 * Alternate searches preserve the full cartridge identity. In particular,
 * multi-game titles must never fall back to only one component game.
 */
internal object NesMetadataTitleQueries {
    private val leadingArticle = Regex("""^(The|An|A)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val trailingArticle = Regex("""^(.+),\s*(The|An|A)$""", RegexOption.IGNORE_CASE)
    private val multiSeparator = Regex("""\s*(?:\+|/|&|\band\b)\s*""", RegexOption.IGNORE_CASE)
    private val multiPrefix = Regex("""^(?:2-in-1|2 in 1)\s*[:\-]?\s*""", RegexOption.IGNORE_CASE)

    fun isCompilation(title: String): Boolean {
        val cleaned = NesGameTitle.clean(title).replace(multiPrefix, "")
        return cleaned.split(multiSeparator).count { it.trim().length >= 3 } >= 2
    }

    fun normalized(title: String): String {
        val cleaned = NesGameTitle.clean(title).replace(multiPrefix, "").trim()
        val article =
            trailingArticle.matchEntire(cleaned)?.let {
                "${it.groupValues[2]} ${it.groupValues[1]}"
            } ?: cleaned
        return article.lowercase(Locale.ROOT)
            .replace(multiSeparator, " / ")
            .replace(Regex("""[^a-z0-9/]+"""), "")
    }

    /**
     * Loose punctuation is okay, but a compilation must remain a compilation
     * with all its component games. Never match an SMB + Duck Hunt cartridge
     * to SMB alone or Duck Hunt alone.
     */
    fun equivalent(
        requested: String,
        candidate: String,
    ): Boolean = normalized(requested) == normalized(candidate)

    fun compatibleCandidate(
        requested: String,
        candidate: String,
    ): Boolean =
        !isCompilation(requested) ||
            (isCompilation(candidate) && equivalent(requested, candidate))

    fun alternateQueries(title: String): List<String> {
        val base = NesGameTitle.clean(title).replace(multiPrefix, "").trim()
        if (base.length !in 3..120) return emptyList()
        val variants = linkedSetOf<String>()
        val article = leadingArticle.matchEntire(base)
        if (article != null) {
            variants += "${article.groupValues[2]}, ${article.groupValues[1]}"
        }
        val trailing = trailingArticle.matchEntire(base)
        if (trailing != null) {
            variants += "${trailing.groupValues[2]} ${trailing.groupValues[1]}"
        }
        val components = base.split(multiSeparator).map(String::trim)
        if (components.size in 2..3 && components.all { it.length >= 3 }) {
            for (separator in listOf(" / ", " + ", " & ", " and ")) {
                variants += components.joinToString(separator)
            }
        }
        return variants.filter { it != base && it.length in 3..120 }.take(8)
    }
}
