package com.swordfish.lemuroid.app.shared.cheats

/**
 * Only code forms parsed by the FCEUmm Libretro cheat entry point.
 * Unsupported PAR variants and combined-code strings remain disabled until
 * verified. Never pass unchecked user text to the native parser.
 */
internal object NesCheatCodes {
    private val gameGenie = Regex("[APZLGITYEOXUKSVN]{6}([APZLGITYEOXUKSVN]{2})?")
    private val raw = Regex("[0-9A-F]{4}:[0-9A-F]{2}")
    private val compare = Regex("[0-9A-F]{4}\\?[0-9A-F]{2}:[0-9A-F]{2}")

    fun normalize(input: String): String? {
        val code = input.trim().uppercase()
        val candidate =
            if (!code.contains(':') && !code.contains('?')) {
                code.replace("-", "").replace(" ", "")
            } else {
                code
            }
        if (candidate.length > 10) return null
        return candidate.takeIf {
            gameGenie.matches(it) || raw.matches(it) || compare.matches(it)
        }
    }
}

internal data class NesCheatDefinition(
    val id: String,
    val name: String,
    val code: String,
)
