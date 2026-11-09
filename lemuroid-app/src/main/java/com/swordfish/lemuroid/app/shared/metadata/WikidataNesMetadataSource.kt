package com.swordfish.lemuroid.app.shared.metadata

import android.net.Uri
import com.swordfish.lemuroid.lib.library.metadata.NesGameTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Key-free read-only lookup against Wikidata (CC0) and verified Wikipedia summaries.
 *
 * Never guess between entities. A match must have the exact normalized title,
 * be an instance of video game (Q7889), explicitly list NES (Q172742) as a
 * platform, and be the one and only qualifying entity.
 *
 * Only the game title appears in the query, not ROM bytes or local paths.
 * Like any remote HTTP service, Wikidata can observe the request's IP address
 * and user agent. Automatic lookups are controlled from Library maintenance.
 */
class WikidataNesMetadataSource {
    companion object {
        private const val MAX_SUMMARY_LENGTH = 1050
        private const val USER_AGENT =
            "OpenNES/0.2 (https://github.com/JPLuker/OpenNES; Android NES metadata)"
    }

    sealed interface Result {
        data class Match(
            val entityId: String,
            val genre: String?,
            val releaseYear: Int?,
            val description: String?,
            val wikipediaArticleTitle: String? = null,
        ) : Result

        data object NoMatch : Result

        data object Unavailable : Result
    }

    private val http =
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .build()

    suspend fun lookup(title: String): Result =
        withContext(Dispatchers.IO) {
            val searchTerm = NesGameTitle.clean(title)
            if (searchTerm.length < 3 || searchTerm.length > 120) {
                return@withContext Result.NoMatch
            }

            runCatching { findMatch(searchTerm) }
                .onFailure { Timber.w(it, "Wikidata NES lookup unavailable") }
                .getOrElse { Result.Unavailable }
        }

    private fun findMatch(title: String): Result {
        val search =
            request(
                mapOf(
                    "action" to "wbsearchentities",
                    "search" to title,
                    "language" to "en",
                    "type" to "item",
                    "limit" to "10",
                    "format" to "json",
                    "maxlag" to "5",
                ),
            )
        val names = search.optJSONArray("search") ?: return Result.NoMatch
        val canonical = normalize(title)
        val matchingIds =
            (0 until names.length())
                .mapNotNull { names.optJSONObject(it) }
                .filter { normalize(it.optString("label")) == canonical }
                .map { it.optString("id") }
                .filter { it.matches(Regex("Q[1-9][0-9]*")) }
                .distinct()

        if (matchingIds.isEmpty()) return Result.NoMatch

        val response =
            request(
                mapOf(
                    "action" to "wbgetentities",
                    "ids" to matchingIds.joinToString("|"),
                    "props" to "claims|descriptions|sitelinks",
                    "languages" to "en",
                    "format" to "json",
                    "maxlag" to "5",
                ),
            )
        val entities = response.optJSONObject("entities") ?: return Result.NoMatch
        val eligible =
            matchingIds.mapNotNull { id ->
                entities.optJSONObject(id)?.let { entity -> id to entity }
            }.filter { (_, entity) ->
                val claims = entity.optJSONObject("claims") ?: return@filter false
                claimItemIds(claims, "P31").contains("Q7889") &&
                    claimItemIds(claims, "P400").contains("Q172742")
            }

        // More than one compatible Wikidata item means the user must resolve
        // the ambiguity manually; never pick the first result.
        if (eligible.size != 1) return Result.NoMatch
        val (id, entity) = eligible.single()
        val claims = entity.optJSONObject("claims") ?: return Result.NoMatch

        val years =
            claimStatements(claims, "P577").mapNotNull { statement ->
                val time =
                    statement.optJSONObject("mainsnak")
                        ?.optJSONObject("datavalue")
                        ?.optJSONObject("value")
                        ?.optString("time") ?: return@mapNotNull null
                Regex("^[+-]([0-9]{4})").find(time)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?.takeIf { it in 1950..2100 }
            }
        val genreIds = claimItemIds(claims, "P136").distinct().take(2)
        val genre = genreLabels(genreIds)
        val description =
            entity.optJSONObject("descriptions")
                ?.optJSONObject("en")
                ?.optString("value")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it.length <= 300 }

        // The sitelink belongs to the uniquely verified NES game item,
        // avoiding unrelated Wikipedia pages with similar names.
        val articleTitle =
            entity.optJSONObject("sitelinks")
                ?.optJSONObject("enwiki")
                ?.optString("title")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it.length <= 160 }
        val summary = articleTitle?.let { wikipediaSummary(it) }

        return Result.Match(
            entityId = id,
            genre = genre,
            releaseYear = years.minOrNull(),
            description = summary ?: description,
            wikipediaArticleTitle = if (summary != null) articleTitle else null,
        )
    }

    /**
     * Wikipedia lead extracts are CC BY-SA content, not Wikidata CC0 values.
     * Store the exact article title so game details can link to the source.
     * A 404 means no summary; transient network or rate-limit failures retry.
     */
    private fun wikipediaSummary(articleTitle: String): String? {
        val url =
            "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                Uri.encode(articleTitle.replace(' ', '_'))
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

        val body =
            http.newCall(request).execute().use { response ->
                if (response.code == 404) return null
                if (!response.isSuccessful) {
                    throw IOException("Wikipedia summary HTTP status: " + response.code)
                }
                response.body?.string() ?: throw IOException("Empty Wikipedia summary response")
            }
        val data = JSONObject(body)
        if (data.optString("type") == "disambiguation") return null

        val extract =
            data.optString("extract").replace(Regex("""\s+"""), " ").trim()
                .takeIf { it.length in 55..10_000 } ?: return null
        if (extract.length <= MAX_SUMMARY_LENGTH) return extract

        val shortened = extract.take(MAX_SUMMARY_LENGTH)
        val sentence = shortened.substringBeforeLast('.')
        return if (sentence.length >= 300) "$sentence." else shortened.trimEnd() + "…"
    }

    private fun genreLabels(ids: List<String>): String? {
        if (ids.isEmpty()) return null
        val labels =
            request(
                mapOf(
                    "action" to "wbgetentities",
                    "ids" to ids.joinToString("|"),
                    "props" to "labels",
                    "languages" to "en",
                    "format" to "json",
                    "maxlag" to "5",
                ),
            ).optJSONObject("entities") ?: return null

        return ids.mapNotNull { id ->
            labels.optJSONObject(id)
                ?.optJSONObject("labels")
                ?.optJSONObject("en")
                ?.optString("value")
                ?.takeIf { it.isNotBlank() }
        }.joinToString(" / ").takeIf { it.isNotBlank() }
    }

    private fun claimItemIds(
        claims: JSONObject,
        property: String,
    ): List<String> =
        claimStatements(claims, property).mapNotNull { statement ->
            statement.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")
                ?.optJSONObject("value")
                ?.optString("id")
                ?.takeIf { it.matches(Regex("Q[1-9][0-9]*")) }
        }

    private fun claimStatements(
        claims: JSONObject,
        property: String,
    ): List<JSONObject> {
        val statements: JSONArray = claims.optJSONArray(property) ?: return emptyList()
        return (0 until statements.length()).mapNotNull { statements.optJSONObject(it) }
    }

    private fun normalize(title: String): String =
        title.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")

    private fun request(params: Map<String, String>): JSONObject {
        val uri =
            Uri.parse("https://www.wikidata.org/w/api.php")
                .buildUpon().apply {
                    params.forEach { (key, value) -> appendQueryParameter(key, value) }
                }.build()
        val request =
            Request.Builder()
                .url(uri.toString())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Wikidata HTTP status: " + response.code)
            }
            val body = response.body?.string() ?: throw IOException("Empty Wikidata response")
            JSONObject(body)
        }
    }
}
