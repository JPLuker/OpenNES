package com.swordfish.lemuroid.app.shared.cheats

import com.swordfish.lemuroid.lib.library.metadata.NesGameTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Read-only, unauthenticated lookup of public NES .cht files from Libretro's
 * CC BY-SA 4.0 database. No ROM files, account tokens or gameplay state are sent.
 * The source is attributed in the picker. Unsupported combined/conditional
 * codes are excluded rather than passed unsafely to FCEUmm.
 */
internal object NesLibretroCheatFinder {
    data class Candidate(
        val title: String,
        val code: String,
        val sourceTitle: String,
    )

    private data class FileEntry(
        val name: String,
    )

    private const val API = "https://api.github.com/repos/libretro/libretro-database/git/trees/"
    private const val RAW = "https://raw.githubusercontent.com/libretro/libretro-database/master/cht/"
    private const val NES_DIRECTORY = "Nintendo - Nintendo Entertainment System"
    private const val MAX_RESPONSE = 2_500_000
    private val client =
        OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    @Volatile
    private var cachedIndex: List<FileEntry>? = null

    suspend fun find(title: String): List<Candidate> =
        withContext(Dispatchers.IO) {
            val query = normalizeTitle(title)
            if (query.length < 3) return@withContext emptyList()
            val index = cachedIndex ?: loadIndex().also { cachedIndex = it }
            val matching =
                index.filter { normalizeTitle(it.name.removeSuffix(".cht")) == query }
                    .take(8)
            matching.flatMap { entry ->
                val url =
                    RAW + "Nintendo%20-%20Nintendo%20Entertainment%20System/" +
                        java.net.URLEncoder.encode(entry.name, Charsets.UTF_8.name())
                            .replace("+", "%20")
                parseCheatText(fetch(url), entry.name.removeSuffix(".cht"))
            }.distinctBy { it.code }.take(150)
        }

    private fun loadIndex(): List<FileEntry> {
        val root = fetchTree("master")
        val cheats = childSha(root, "cht")
        val platforms = fetchTree(cheats)
        val nes = childSha(platforms, NES_DIRECTORY)
        val files = fetchTree(nes)
        require(!files.optBoolean("truncated", false)) { "Incomplete Libretro cheat directory" }
        val entries = files.getJSONArray("tree")
        return (0 until entries.length()).mapNotNull { index ->
            val entry = entries.optJSONObject(index) ?: return@mapNotNull null
            if (entry.optString("type") != "blob") return@mapNotNull null
            val name = entry.optString("path")
            name.takeIf {
                it.length in 5..180 &&
                    it.endsWith(".cht", ignoreCase = true) &&
                    !it.contains('/') &&
                    !it.contains('\\')
            }?.let(::FileEntry)
        }.also { require(it.isNotEmpty()) }
    }

    private fun childSha(
        document: JSONObject,
        name: String,
    ): String {
        val tree = document.getJSONArray("tree")
        for (index in 0 until tree.length()) {
            val item = tree.optJSONObject(index) ?: continue
            if (item.optString("type") != "tree" || item.optString("path") != name) continue
            val sha = item.optString("sha")
            require(sha.matches(Regex("[a-f0-9]{40}")))
            return sha
        }
        error("Libretro NES cheat directory not available")
    }

    private fun fetchTree(reference: String): JSONObject {
        require(reference == "master" || reference.matches(Regex("[a-f0-9]{40}")))
        return JSONObject(fetch(API + reference))
    }

    private fun fetch(url: String): String {
        require(
            url.startsWith(API) ||
                url.startsWith(RAW + "Nintendo%20-%20Nintendo%20Entertainment%20System/"),
        )
        val request =
            Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OpenNES/0.1 (NES cheat lookup)")
                .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Cheat database unavailable: HTTP ${response.code}" }
            val body = response.body ?: error("Cheat database response missing")
            require(body.contentLength() <= MAX_RESPONSE) { "Cheat database response too large" }
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            body.byteStream().use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_RESPONSE) { "Cheat database response too large" }
                    output.write(buffer, 0, count)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    fun normalizeTitle(raw: String): String {
        val withoutExtension = raw.removeSuffix(".cht")
        val withoutTags =
            withoutExtension.replace(Regex("""\s*\([^)]*\)"""), "").trim()
        return NesGameTitle.canonicalReleaseName(withoutTags)
            .lowercase(Locale.ROOT)
    }

    /** Libretro .cht parser; never honor upstream cheatN_enable=true. */
    fun parseCheatText(
        text: String,
        sourceTitle: String,
    ): List<Candidate> {
        if (text.length > 250_000) return emptyList()
        val descriptions = mutableMapOf<Int, String>()
        val codes = mutableMapOf<Int, String>()
        val linePattern = Regex("""^cheat(\d+)_(desc|code)\s*=\s*(.*)$""")
        for (line in text.lineSequence().take(1200)) {
            val match = linePattern.matchEntire(line.trim()) ?: continue
            val index = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..500 } ?: continue
            val value = match.groupValues[3].trim().trim('"')
            when (match.groupValues[2]) {
                "desc" -> descriptions[index] = value.take(64)
                "code" -> codes[index] = value
            }
        }
        return codes.mapNotNull { (index, raw) ->
            val normalized = NesCheatCodes.normalize(raw) ?: return@mapNotNull null
            val title = descriptions[index]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Candidate(title, normalized, sourceTitle)
        }.distinctBy { it.code }.take(150)
    }
}
