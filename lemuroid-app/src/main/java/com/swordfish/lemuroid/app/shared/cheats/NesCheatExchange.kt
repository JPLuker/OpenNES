package com.swordfish.lemuroid.app.shared.cheats

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Versioned, local-only cheat code interchange. Only names and code strings
 * are exchanged; there is deliberately no `enabled` property. Imported
 * codes cannot silently modify a running game.
 */
internal object NesCheatExchange {
    private const val FORMAT = "OpenNES-cheats-v1"
    private const val MAX_FILE_BYTES = 64 * 1024

    fun encode(entries: List<NesCheatDefinition>): ByteArray {
        require(entries.size <= NesCheatRepository.MAX_CHEATS)
        val values = JSONArray()
        entries.forEach { entry ->
            require(entry.name.length in 1..64 && NesCheatCodes.normalize(entry.code) == entry.code)
            values.put(JSONObject().put("name", entry.name).put("code", entry.code))
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("entries", values)
            .toString(2)
            .toByteArray(Charsets.UTF_8)
            .also { require(it.size <= MAX_FILE_BYTES) }
    }

    fun readBounded(stream: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        while (true) {
            val amount = stream.read(buffer)
            if (amount < 0) break
            if (amount == 0) continue
            if (output.size() + amount > MAX_FILE_BYTES) return null
            output.write(buffer, 0, amount)
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): List<NesCheatDefinition>? {
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return null
        return try {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            if (root.optString("format") != FORMAT || root.has("enabled")) return null
            val values = root.optJSONArray("entries") ?: return null
            if (values.length() > NesCheatRepository.MAX_CHEATS) return null
            val output = ArrayList<NesCheatDefinition>(values.length())
            val seen = HashSet<String>()
            for (i in 0 until values.length()) {
                val item = values.optJSONObject(i) ?: return null
                // Reject unexpected keys that could attempt to smuggle
                // enablement or emulator state into imported preferences.
                if (item.length() != 2 || !item.has("name") || !item.has("code")) return null
                val name = item.optString("name").trim()
                val rawCode = item.optString("code")
                val code = NesCheatCodes.normalize(rawCode) ?: return null
                if (name.length !in 1..64 || !seen.add(code)) return null
                output += NesCheatDefinition(UUID.randomUUID().toString(), name, code)
            }
            output
        } catch (_: Exception) {
            null
        }
    }

    fun merge(
        current: List<NesCheatDefinition>,
        imported: List<NesCheatDefinition>,
    ): List<NesCheatDefinition>? {
        if (current.size > NesCheatRepository.MAX_CHEATS ||
            imported.size > NesCheatRepository.MAX_CHEATS
        ) {
            return null
        }
        val seen = current.mapTo(HashSet()) { it.code }
        val new = imported.filter { seen.add(it.code) }
        return (current + new).takeIf { it.size <= NesCheatRepository.MAX_CHEATS }
    }
}
