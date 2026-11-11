package com.swordfish.lemuroid.lib.library.metadata

/**
 * Presentation/search title for NES games.
 *
 * Libretro and No-Intro names can contain catalogue suffixes that are not part
 * of the game's actual name. This intentionally does not change the saved
 * database title, ROM filename, hash, or any user-provided custom name.
 *
 * Only recognized trailing catalogue tags are removed. Other parentheses
 * (such as a genuine subtitle) are preserved rather than guessed away.
 */
object NesGameTitle {
    private val nesExtension = Regex("""\.nes$""", RegexOption.IGNORE_CASE)
    private val catalogueSuffix =
        Regex(
            """\s*\((?:USA|Europe|Japan|World|Nintendo|Australia|Asia|Brazil|Korea|Canada|""" +
                """France|Germany|Italy|Spain|United Kingdom|Rev(?:ision)?\s*[A-Za-z0-9.]+)\)\s*$""",
            RegexOption.IGNORE_CASE,
        )
    private val prgRevisionBeforeArticle =
        Regex(
            """\s+Rev(?:ision)?\s*PRG\s*\d+(?=\s*,\s*(?:The|An|A)\s*$)""",
            RegexOption.IGNORE_CASE,
        )
    private val trailingArticle = Regex("""^(.+),\s*(The|An|A)$""", RegexOption.IGNORE_CASE)

    private val bundlePrefix = Regex("""^(?:2-in-1|2 in 1)\\s*[:\\-]?\\s*""", RegexOption.IGNORE_CASE)
    private val multiGameSeparator = Regex("""\\s*(?:\\+|/|&|\\band\\b)\\s*""", RegexOption.IGNORE_CASE)

    /**
     * Manual NES search accepts reversed articles and naming differences on
     * multi-cart labels, but never searches an individual game in place of a
     * bundle. All variants must retain the full cartridge identity.
     */
    fun searchVariants(raw: String): List<String> {
        val original = raw.trim()
        val cleaned = clean(original)
        val withoutPrefix = cleaned.replace(bundlePrefix, "").trim()
        val results = linkedSetOf(original, cleaned, withoutPrefix)
        val article = Regex("""^(The|An|A)\\s+(.+)$""", RegexOption.IGNORE_CASE)
            .matchEntire(withoutPrefix)
        if (article != null) {
            results += "${article.groupValues[2]}, ${article.groupValues[1]}"
        }
        val components = withoutPrefix.split(multiGameSeparator).map(String::trim)
        if (components.size in 2..3 && components.all { it.length >= 3 }) {
            for (joiner in listOf(" / ", " + ", " & ", " and ")) {
                results += components.joinToString(joiner)
            }
        }
        return results.filter { it.length in 3..120 }.take(9)
    }

    fun isCompilation(raw: String): Boolean {
        val parts = clean(raw).replace(bundlePrefix, "").split(multiGameSeparator)
        return parts.count { it.trim().length >= 3 } >= 2
    }

    fun canonicalReleaseName(raw: String): String =
        clean(raw).replace(bundlePrefix, "").lowercase(java.util.Locale.ROOT)
            .replace(multiGameSeparator, "/")
            .replace(Regex("[^a-z0-9/]"), "")

    fun clean(raw: String): String {
        var title = raw.trim().replace(nesExtension, "").trim()
        while (true) {
            val stripped = title.replace(catalogueSuffix, "").trim()
            if (stripped == title) break
            title = stripped
        }
        title = title.replace(prgRevisionBeforeArticle, "").trim()

        trailingArticle.matchEntire(title)?.let { match ->
            title = "${match.groupValues[2]} ${match.groupValues[1].trim()}"
        }
        return title.ifBlank { raw.trim() }
    }
}
