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
            """\s*\((?:USA|Europe|Japan|World|Nintendo|Australia|Asia|Brazil|Korea|Canada|France|Germany|Italy|Spain|United Kingdom|Rev(?:ision)?\s*[A-Za-z0-9.]+)\)\s*$""",
            RegexOption.IGNORE_CASE,
        )
    private val prgRevisionBeforeArticle =
        Regex(
            """\s+Rev(?:ision)?\s*PRG\s*\d+(?=\s*,\s*(?:The|An|A)\s*$)""",
            RegexOption.IGNORE_CASE,
        )
    private val trailingArticle = Regex("""^(.+),\s*(The|An|A)$""", RegexOption.IGNORE_CASE)

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
