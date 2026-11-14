package com.swordfish.lemuroid.app.shared.covers

/**
 * A game whose file name carries no region tag can't be looked up in the
 * Libretro box-art catalogue by file name. Its title usually can: the
 * catalogue names US releases "Title (USA)", with a leading "The" moved
 * behind the name and a subtitle joined by " - ".
 */
internal object NesBoxArtNameGuess {
    private val tags = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val revision = Regex("""\s+Rev\s+\S+$""", RegexOption.IGNORE_CASE)
    private val subtitle = Regex("""\s*:\s+|\s+-\s+""")
    private val forbidden = Regex("""[&*/:`<>?\\|"]""")

    /** The catalogue file stem to try for [title], or null when there is nothing to go on. */
    fun usaStem(title: String): String? {
        val clean = title.replace(tags, "").replace(revision, "").trim()
        if (clean.length < 2) return null
        val parts = clean.split(subtitle, limit = 2)
        val main =
            parts[0].trim().let { name ->
                if (name.startsWith("The ", ignoreCase = true) && name.length > 4) "${name.substring(4)}, The" else name
            }
        val joined = if (parts.size > 1) "$main - ${parts[1].trim()}" else main
        return joined.replace(forbidden, "_") + " (USA)"
    }
}
