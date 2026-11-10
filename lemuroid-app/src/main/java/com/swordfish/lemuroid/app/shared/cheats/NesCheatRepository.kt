package com.swordfish.lemuroid.app.shared.cheats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * User-authored codes only, never enabled state. Nothing from persistent
 * storage is applied to gameplay automatically on launch or resume.
 */
internal class NesCheatRepository(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("opennes_nes_cheats_v1", Context.MODE_PRIVATE)

    fun load(romUri: String): List<NesCheatDefinition> {
        val source = preferences.getString(key(romUri), null) ?: return emptyList()
        return try {
            val array = JSONArray(source)
            if (array.length() > MAX_CHEATS) return emptyList()
            buildList {
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    val id = entry.optString("id")
                    val name = entry.optString("name")
                    val code = NesCheatCodes.normalize(entry.optString("code"))
                    if (id.length !in 1..64 || name.length !in 1..64 || code == null) continue
                    if (any { it.id == id || it.code == code }) continue
                    add(NesCheatDefinition(id, name, code))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(
        romUri: String,
        definitions: List<NesCheatDefinition>,
    ): Boolean {
        if (definitions.size > MAX_CHEATS ||
            definitions.any {
                it.id.length !in 1..64 ||
                    it.name.length !in 1..64 ||
                    NesCheatCodes.normalize(it.code) != it.code
            } ||
            definitions.map { it.id }.distinct().size != definitions.size ||
            definitions.map { it.code }.distinct().size != definitions.size
        ) {
            return false
        }
        val array = JSONArray()
        definitions.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("code", it.code),
            )
        }
        return preferences.edit().putString(key(romUri), array.toString()).commit()
    }

    fun create(
        name: String,
        code: String,
    ): NesCheatDefinition? {
        val cleanName = name.trim().takeIf { it.length in 1..64 } ?: return null
        val cleanCode = NesCheatCodes.normalize(code) ?: return null
        return NesCheatDefinition(UUID.randomUUID().toString(), cleanName, cleanCode)
    }

    private fun key(uri: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
        return "rom_" +
            bytes.joinToString("") { byte ->
                val value = byte.toInt() and 0xFF
                HEX[value ushr 4].toString() + HEX[value and 15]
            }
    }

    companion object {
        const val MAX_CHEATS = 64
        private const val HEX = "0123456789abcdef"
    }
}
