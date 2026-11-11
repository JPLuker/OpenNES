package com.swordfish.lemuroid.app.shared.metadata

import android.net.Uri
import com.swordfish.lemuroid.lib.library.metadata.NesGameTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
        private const val MAX_METADATA_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_WIKIPEDIA_RESPONSE_BYTES = 512 * 1024
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

    /** User-selected candidates may have a different displayed title, but
     * must still be positively identified as an NES video game. */
    data class Candidate(
        val entityId: String,
        val title: String,
        val shortDescription: String?,
        val releaseYear: Int?,
    )

    sealed interface CandidateResults {
        data class Found(val candidates: List<Candidate>) : CandidateResults

        data object NoMatch : CandidateResults

        data object Unavailable : CandidateResults
    }

    suspend fun searchManualCandidates(query: String): CandidateResults =
        withContext(Dispatchers.IO) {
            val term = NesGameTitle.clean(query)
            if (term.length !in 3..120) return@withContext CandidateResults.NoMatch
            runCatching {
                val queries = linkedSetOf(term)
                queries.addAll(NesMetadataTitleQueries.alternateQueries(term))
                for (fallback in NesManualSearchTerms.fallbacks(term)) {
                    queries += fallback
                    queries.addAll(NesMetadataTitleQueries.alternateQueries(fallback))
                }
                var result: CandidateResults = CandidateResults.NoMatch
                for (query in queries.take(12)) {
                    result = findManualCandidates(query, term)
                    if (result is CandidateResults.Found) break
                }
                result
            }.onFailure {
                if (it is CancellationException) throw it
                Timber.w(it, "Manual NES metadata candidate search unavailable")
            }.getOrElse { CandidateResults.Unavailable }
        }

    suspend fun resolveManualCandidate(entityId: String): Result =
        withContext(Dispatchers.IO) {
            if (!entityId.matches(Regex("Q[1-9][0-9]*"))) return@withContext Result.NoMatch
            runCatching { findManualCandidateDetails(entityId) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Timber.w(it, "Manual NES metadata candidate lookup unavailable")
                }
                .getOrElse { Result.Unavailable }
        }

    private suspend fun findManualCandidates(
        term: String,
        requestedTitle: String,
    ): CandidateResults {
        val search =
            request(
                mapOf(
                    "action" to "wbsearchentities",
                    "search" to term,
                    "language" to "en",
                    "type" to "item",
                    "limit" to "15",
                    "format" to "json",
                    "maxlag" to "5",
                ),
            )
        val names = search.optJSONArray("search") ?: return CandidateResults.NoMatch
        val items =
            (0 until names.length())
                .mapNotNull { names.optJSONObject(it) }
                .filter { it.optString("id").matches(Regex("Q[1-9][0-9]*")) }
                .distinctBy { it.optString("id") }
                .take(15)
        if (items.isEmpty()) return CandidateResults.NoMatch

        val entities =
            request(
                mapOf(
                    "action" to "wbgetentities",
                    "ids" to items.joinToString("|") { it.optString("id") },
                    "props" to "claims|descriptions",
                    "languages" to "en",
                    "format" to "json",
                    "maxlag" to "5",
                ),
            ).optJSONObject("entities") ?: return CandidateResults.NoMatch

        val matches =
            items.mapNotNull { item ->
                val id = item.optString("id")
                val entity = entities.optJSONObject(id) ?: return@mapNotNull null
                if (!isVerifiedNesVideoGame(entity)) return@mapNotNull null
                val label =
                    item.optString("label").trim().takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                if (!NesMetadataTitleQueries.compatibleCandidate(requestedTitle, label)) {
                    return@mapNotNull null
                }
                val claims = entity.optJSONObject("claims")
                val year =
                    claims?.let { gameClaims ->
                        claimStatements(gameClaims, "P577").mapNotNull { statement ->
                            val time =
                                statement.optJSONObject("mainsnak")
                                    ?.optJSONObject("datavalue")
                                    ?.optJSONObject("value")
                                    ?.optString("time") ?: return@mapNotNull null
                            Regex("^[+-]([0-9]{4})").find(time)
                                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                                ?.takeIf { it in 1950..2100 }
                        }.minOrNull()
                    }
                val description =
                    entity.optJSONObject("descriptions")
                        ?.optJSONObject("en")
                        ?.optString("value")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                Candidate(id, label, description, year)
            }.take(8)

        return if (matches.isEmpty()) CandidateResults.NoMatch else CandidateResults.Found(matches)
    }

    private suspend fun findManualCandidateDetails(id: String): Result {
        val response =
            request(
                mapOf(
                    "action" to "wbgetentities",
                    "ids" to id,
                    "props" to "claims|descriptions|sitelinks",
                    "languages" to "en",
                    "format" to "json",
                    "maxlag" to "5",
                ),
            )
        val entity = response.optJSONObject("entities")?.optJSONObject(id) ?: return Result.NoMatch
        if (!isVerifiedNesVideoGame(entity)) return Result.NoMatch
        return buildVerifiedMatch(id, entity) ?: Result.NoMatch
    }

    private fun isVerifiedNesVideoGame(entity: JSONObject): Boolean {
        val claims = entity.optJSONObject("claims") ?: return false
        return claimItemIds(claims, "P31").contains("Q7889") &&
            claimItemIds(claims, "P400").contains("Q172742")
    }

    private val http =
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    suspend fun lookup(title: String): Result =
        withContext(Dispatchers.IO) {
            val searchTerm = NesGameTitle.clean(title)
            if (searchTerm.length < 3 || searchTerm.length > 120) {
                return@withContext Result.NoMatch
            }

            runCatching {
                var result = findMatch(searchTerm)
                if (result is Result.NoMatch) {
                    for (variant in NesMetadataTitleQueries.alternateQueries(searchTerm)) {
                        result = findMatch(variant)
                        if (result !is Result.NoMatch) break
                    }
                }
                result
            }.onFailure {
                if (it is CancellationException) throw it
                Timber.w(it, "Wikidata NES lookup unavailable")
            }
                .getOrElse { Result.Unavailable }
        }

    private suspend fun findMatch(title: String): Result {
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
        val canonical = NesMetadataTitleQueries.normalized(title)
        val matchingIds =
            (0 until names.length())
                .mapNotNull { names.optJSONObject(it) }
                .filter { NesMetadataTitleQueries.normalized(it.optString("label")) == canonical }
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
        return buildVerifiedMatch(id, entity) ?: Result.NoMatch
    }

    private suspend fun buildVerifiedMatch(
        id: String,
        entity: JSONObject,
    ): Result.Match? {
        val claims = entity.optJSONObject("claims") ?: return null

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
    private suspend fun wikipediaSummary(articleTitle: String): String? {
        val url =
            "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                Uri.encode(articleTitle.replace(' ', '_'))
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

        val data =
            fetchJson(request, MAX_WIKIPEDIA_RESPONSE_BYTES, allowNotFound = true)
                ?: return null
        if (data.optString("type") == "disambiguation") return null

        val extract =
            data.optString("extract").replace(Regex("""\s+"""), " ").trim()
                .takeIf { it.length in 55..10_000 } ?: return null
        if (extract.length <= MAX_SUMMARY_LENGTH) return extract

        val shortened = extract.take(MAX_SUMMARY_LENGTH)
        val sentence = shortened.substringBeforeLast('.')
        return if (sentence.length >= 300) "$sentence." else shortened.trimEnd() + "…"
    }

    private suspend fun genreLabels(ids: List<String>): String? {
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

    private suspend fun request(params: Map<String, String>): JSONObject {
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

        return fetchJson(request, MAX_METADATA_RESPONSE_BYTES)
            ?: throw IOException("Empty Wikidata response")
    }

    /** Search cancellation closes the underlying request, not only the UI. */
    private suspend fun fetchJson(
        request: Request,
        maxBytes: Int,
        allowNotFound: Boolean = false,
    ): JSONObject? =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        try {
                            val parsed =
                                response.use {
                                    if (allowNotFound && it.code == 404) return@use null
                                    if (!it.isSuccessful) {
                                        throw IOException("Metadata HTTP status: " + it.code)
                                    }
                                    val body = it.body ?: throw IOException("Empty metadata response")
                                    val bytes =
                                        body.byteStream().use { stream ->
                                            WikidataResponseReader.readBounded(stream, maxBytes)
                                        }
                                    JSONObject(String(bytes, Charsets.UTF_8))
                                }
                            if (continuation.isActive) continuation.resume(parsed)
                        } catch (failure: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(failure)
                        }
                    }
                },
            )
        }
}
