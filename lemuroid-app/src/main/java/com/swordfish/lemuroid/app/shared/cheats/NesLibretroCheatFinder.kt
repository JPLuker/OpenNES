package com.swordfish.lemuroid.app.shared.cheats

import com.swordfish.lemuroid.lib.library.metadata.NesGameTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    private data class CheatIndex(
        val commitSha: String,
        val entries: List<FileEntry>,
    )

    private const val API_ROOT = "https://api.github.com/repos/libretro/libretro-database/git/"
    private const val API_TREE = API_ROOT + "trees/"
    private const val API_MASTER_REF = API_ROOT + "ref/heads/master"
    private const val RAW_ROOT = "https://raw.githubusercontent.com/libretro/libretro-database/"
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
    private var cachedIndex: CheatIndex? = null

    suspend fun find(title: String): List<Candidate> =
        withContext(Dispatchers.IO) {
            val query = normalizeTitle(title)
            if (query.length < 3) return@withContext emptyList()
            val index = cachedIndex ?: loadIndex().also { cachedIndex = it }
            val matching =
                index.entries.filter { normalizeTitle(it.name.removeSuffix(".cht")) == query }
                    .take(8)
            matching.flatMap { entry ->
                val url = pinnedCheatUrl(index.commitSha, entry.name)
                parseCheatText(fetch(url), entry.name.removeSuffix(".cht"))
            }.distinctBy { it.code }.take(150)
        }

    private suspend fun loadIndex(): CheatIndex {
        // Resolve the branch once, then fetch the tree AND its cheat files
        // from that immutable commit. Never mix a cached index with a newer
        // moving /master/raw file.
        val ref = JSONObject(fetch(API_MASTER_REF))
        val commitSha = ref.getJSONObject("object").getString("sha")
        require(commitSha.matches(Regex("[a-f0-9]{40}"))) { "Invalid Libretro commit revision" }
        val commit = JSONObject(fetch(API_ROOT + "commits/" + commitSha))
        val rootTreeSha = commit.getJSONObject("tree").getString("sha")
        require(rootTreeSha.matches(Regex("[a-f0-9]{40}")))
        val root = fetchTree(rootTreeSha)
        val cheats = childSha(root, "cht")
        val platforms = fetchTree(cheats)
        val nes = childSha(platforms, NES_DIRECTORY)
        val files = fetchTree(nes)
        require(!files.optBoolean("truncated", false)) { "Incomplete Libretro cheat directory" }
        val entries = files.getJSONArray("tree")
        val filesAtRevision =
            (0 until entries.length()).mapNotNull { index ->
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
        return CheatIndex(commitSha, filesAtRevision)
    }

    /**
     * Only a specific commit and flat .cht filename may be requested from the
     * trusted Libretro NES path. No mutable branch or remote path components.
     */
    internal fun pinnedCheatUrl(
        commitSha: String,
        fileName: String,
    ): String {
        require(commitSha.matches(Regex("[a-f0-9]{40}")))
        require(
            fileName.length in 5..180 &&
                fileName.endsWith(".cht", ignoreCase = true) &&
                !fileName.contains('/') &&
                !fileName.contains('\\'),
        )
        val encoded =
            java.net.URLEncoder.encode(fileName, Charsets.UTF_8.name())
                .replace("+", "%20")
        return RAW_ROOT + commitSha + "/cht/Nintendo%20-%20Nintendo%20Entertainment%20System/" + encoded
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

    private suspend fun fetchTree(reference: String): JSONObject {
        require(reference.matches(Regex("[a-f0-9]{40}")))
        return JSONObject(fetch(API_TREE + reference))
    }

    private suspend fun fetch(url: String): String {
        require(
            url == API_MASTER_REF ||
                url.startsWith(API_TREE) ||
                (
                    url.startsWith(API_ROOT + "commits/") &&
                        url.removePrefix(API_ROOT + "commits/").matches(Regex("[a-f0-9]{40}"))
                ) ||
                (
                    url.startsWith(RAW_ROOT) &&
                        Regex(
                            "^https://raw\\.githubusercontent\\.com/libretro/libretro-database/" +
                                "[a-f0-9]{40}/cht/Nintendo%20-%20Nintendo%20Entertainment%20System/" +
                                "[A-Za-z0-9_.%*\\-]+$",
                        ).matches(url)
                ),
        )
        val request =
            Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OpenNES/0.1 (NES cheat lookup)")
                .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        failure: IOException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        try {
                            val result =
                                response.use {
                                    if (!it.isSuccessful) {
                                        throw IOException("Cheat database unavailable: HTTP ${it.code}")
                                    }
                                    val body = it.body ?: throw IOException("Cheat database response missing")
                                    if (body.contentLength() > MAX_RESPONSE) {
                                        throw IOException("Cheat database response too large")
                                    }
                                    val bytes =
                                        body.byteStream().use { input ->
                                            NesCheatResponseReader.readBounded(input, MAX_RESPONSE)
                                        }
                                    String(bytes, Charsets.UTF_8)
                                }
                            if (continuation.isActive) continuation.resume(result)
                        } catch (failure: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(failure)
                        }
                    }
                },
            )
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
