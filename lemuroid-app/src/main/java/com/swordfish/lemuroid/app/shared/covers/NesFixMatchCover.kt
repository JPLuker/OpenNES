package com.swordfish.lemuroid.app.shared.covers

import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.NesGameTitle
import java.util.Locale

/**
 * Box art for a title the player picked in Fix Match.
 *
 * Fix Match candidates come from Wikidata ("Zelda II: The Adventure of
 * Link"), while covers come from the bundled Libretro NES catalogue ("Zelda
 * II - The Adventure of Link (USA)"). A cover is only returned when a
 * catalogue entry is the same title once punctuation, region tags and article
 * order are ignored, so a near miss never replaces the current art.
 */
object NesFixMatchCover {
    private const val NES = "nes"
    private val subtitleSeparator = Regex("""\s*(?::|\s-\s|\s–\s)\s*""")
    private val leadingArticle = Regex("""^(The|An|A)\s+(.+)$""", RegexOption.IGNORE_CASE)

    // Strip catalogue release metadata, not meaningful parenthesized title text.
    // For example, "Batman (Return of the Joker)" must not match "Batman".
    private val tag =
        Regex(
            """\s*(?:\((?:USA|World|Europe|Japan|Rev(?:ision)?\s*\d+|Beta(?:\s*\d+)?|""" +
                """Proto(?:type)?|Demo|Sample|Unl|Unlicensed)\)|\[(?:[a-zA-Z!+]+|[a-zA-Z]\d+)\])""",
            RegexOption.IGNORE_CASE,
        )
    private val preferredRegions = listOf("(usa", "(world", "(europe", "(japan")

    /**
     * Substrings to look up in the catalogue. The whole title rarely matches
     * because the catalogue writes subtitles with " - " instead of ":", so
     * each part of the title is offered as well, longest first.
     */
    fun searchTerms(selectedTitle: String): List<String> {
        val title = selectedTitle.trim()
        val parts =
            title.split(subtitleSeparator)
                .map(String::trim)
                .sortedByDescending(String::length)
        val terms = linkedSetOf(title)
        for (part in parts) {
            terms += part
            // "The Legend of Zelda" is catalogued as "Legend of Zelda, The".
            leadingArticle.matchEntire(part)?.let { terms += it.groupValues[2] }
        }
        return terms.filter { it.length in 3..120 }.take(6)
    }

    /** The best catalogue cover for exactly this title, or null. */
    fun choose(
        selectedTitle: String,
        catalogue: List<GameMetadata>,
    ): String? {
        val wanted = comparable(selectedTitle)
        if (wanted.length < 2) return null
        return catalogue
            .asSequence()
            .filter { it.system == NES && !it.thumbnail.isNullOrBlank() }
            .filter { entry -> entry.name?.let(::comparable) == wanted }
            .sortedWith(compareBy({ regionRank(it.name.orEmpty()) }, { tagCount(it.name.orEmpty()) }))
            .firstOrNull()
            ?.thumbnail
    }

    internal fun comparable(raw: String): String {
        // Drop "(USA)", "(Rev 1)", "[b]" and restore "Legend of Zelda, The".
        val title = NesGameTitle.clean(raw.replace(tag, " ").trim())
        return title.lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun regionRank(name: String): Int {
        val lower = name.lowercase(Locale.ROOT)
        val index = preferredRegions.indexOfFirst { lower.contains(it) }
        return if (index >= 0) index else preferredRegions.size
    }

    // Plain releases before revisions, betas and prototypes.
    private fun tagCount(name: String): Int = tag.findAll(name).count()
}
