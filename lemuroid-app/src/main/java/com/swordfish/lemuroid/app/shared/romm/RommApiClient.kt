package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * RomM adapter. Uses scoped bearer tokens; HTTPS is required outside private IPv4 LANs.
 * Every import is explicitly initiated by the player, staged in app cache,
 * and committed into the already selected NES ROM document tree.
 */
class RommApiClient {
    data class Rom(
        val id: Int,
        val platformId: Int,
        val name: String,
        val fileName: String,
        val sizeBytes: Long,
        val md5: String?,
        val description: String? = null,
        val coverUrl: String? = null,
        val genres: List<String> = emptyList(),
    )

    enum class ImportStage {
        CONNECTING,
        DOWNLOADING,
        VERIFYING,
        IMPORTING,
        INDEXING,
    }

    data class ImportProgress(
        val stage: ImportStage,
        val receivedBytes: Long = 0L,
        val totalBytes: Long? = null,
    )

    data class Page(
        val items: List<Rom>,
        val total: Int,
        val offset: Int,
        val limit: Int,
        // Raw API row count, before filtering unsupported files. Required to
        // advance through every page without silently dropping valid games.
        val rawCount: Int = items.size,
    )

    data class RemoteSave(
        val id: Int,
        val romId: Int,
        val fileName: String,
        val slot: String?,
        val sizeBytes: Long,
        val contentHash: String?,
    )

    private val http =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            // Do not forward a bearer token to a redirected origin.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    // Explicit ROM imports can exceed the metadata API's 60-second timeout,
    // but a 15-minute absolute cap plus read-stall and size limits avoids an
    // indefinitely draining transfer on a hostile or extremely slow server.
    private val importHttp =
        http.newBuilder()
            .callTimeout(15, TimeUnit.MINUTES)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    /**
     * RomM client-token QR pairing: exchange an expiring code exactly once.
     * Never put the token in a URL, display it or persist it until /users/me
     * verifies the returned account in the screen's explicit Connect action.
     */
    suspend fun exchangePairingCode(
        serverUrl: String,
        pairingCode: String,
    ): RommConnection =
        withContext(Dispatchers.IO) {
            val root =
                validatedServerUrl(serverUrl).toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("Invalid RomM server URL")
            val code = RommPairing.normalizeCode(pairingCode)
            val url = root.newBuilder().addPathSegments("api/client-tokens/exchange").build()
            val json = JSONObject().put("code", code).toString()
            val request =
                Request.Builder()
                    .url(url)
                    .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("Accept", "application/json")
                    .header("User-Agent", "OpenNES/0.1 RomM pairing")
                    .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw httpError(response.code)
                val body = response.body ?: throw IOException("Empty RomM pairing response")
                val payload = body.byteStream().use { readBounded(it, 16 * 1024L) }
                val rawToken = JSONObject(String(payload, Charsets.UTF_8)).optString("raw_token")
                if (!rawToken.matches(Regex("rmm_[0-9a-f]{64}"))) {
                    throw IOException("RomM returned an invalid client token")
                }
                RommConnection(root.toString(), rawToken)
            }
        }

    internal suspend fun checkAccountProfile(connection: RommConnection): RommAccountIdentity.Profile =
        withContext(Dispatchers.IO) {
            RommAccountIdentity.parseProfile(getJson(connection, listOf("api", "users", "me")))
        }

    /**
     * Explicit, account-bound device registration for future RomM 5.4 sync.
     * Registration adds only an anonymous device entry to this user's RomM;
     * no saves are downloaded, uploaded, deleted or replaced here.
     *
     * The token and pinned account are checked again after the HTTP response
     * so a logout/login during a pending registration cannot bind the wrong
     * account to this device.
     */
    internal suspend fun registerDevice(
        context: Context,
        connection: RommConnection,
    ): RommDeviceRegistration.Registered {
        val store = RommConnectionStore(context)
        val saved =
            withContext(Dispatchers.IO) { store.load() }
                ?: throw IOException("Reconnect to RomM to register this device")
        val initialRevision = store.credentialRevision() ?: throw IOException("RomM token not verified")
        if (!sameConnection(saved, connection)) throw IOException("RomM login changed")
        val profile = checkAccountProfile(connection)
        val userId = profile.userId ?: throw IOException("RomM account ID not available")
        if (store.lastVerifiedUserId() != userId) {
            throw IOException("RomM account is different from the verified login")
        }
        val fingerprint =
            RommServerIdentity.fingerprint(connection.serverUrl)
                ?: throw IOException("Invalid RomM server")
        val identity = RommDeviceIdentityStore(context)
        val alias = withContext(Dispatchers.IO) { identity.alias(fingerprint, userId) }
        val requestBody =
            RommDeviceRegistration.request(
                hostAlias = alias,
                version = BuildConfig.VERSION_NAME.substringBefore('-'),
            ).toString()
        val result =
            RommDeviceRegistration.parse(
                postCancellableJson(
                    connection = connection,
                    path = listOf("api", "devices"),
                    json = requestBody,
                    maxBytes = 16 * 1024,
                ),
            )
        withContext(Dispatchers.IO) {
            val latest = store.load() ?: throw IOException("RomM login changed")
            if (!sameConnection(latest, connection) ||
                store.credentialRevision() != initialRevision ||
                store.lastVerifiedUserId() != userId
            ) {
                throw IOException("RomM account changed during device registration")
            }
            identity.pin(fingerprint, initialRevision, userId, result.deviceId)
        }
        return result
    }

    /**
     * Explicit comparison only. RomM creates a short-lived sync session while
     * negotiating; this method immediately completes it as a dry run with no
     * save transfers. Every upload/download/delete/conflict stays a display
     * item; no file bytes, local saves or remote saves are mutated.
     */
    internal suspend fun previewDeviceSaveSync(
        context: Context,
        connection: RommConnection,
        romId: Int,
        selectedSave: RommDeviceSyncNegotiationRequest.LocalSave,
    ): RommDeviceSyncProtocol.Negotiation {
        require(romId > 0 && selectedSave.romId == romId) { "Unexpected RomM save game" }
        val store = RommConnectionStore(context)
        val initialConnection =
            withContext(Dispatchers.IO) { store.load() }
                ?: throw IOException("Reconnect before comparing saves")
        val revision = store.credentialRevision() ?: throw IOException("RomM credentials not verified")
        if (!sameConnection(initialConnection, connection)) throw IOException("RomM login changed")
        val user = checkAccountProfile(connection)
        val accountId = user.userId ?: throw IOException("RomM account ID unavailable")
        if (store.lastVerifiedUserId() != accountId) {
            throw IOException("RomM account needs to be verified again")
        }
        val fingerprint =
            RommServerIdentity.fingerprint(connection.serverUrl)
                ?: throw IOException("Invalid RomM server identity")
        val deviceId =
            RommDeviceIdentityStore(context).pinnedDevice(fingerprint, revision, accountId)
                ?: throw IOException("Register this RomM device before comparing saves")
        val request =
            RommDeviceSyncNegotiationRequest.build(
                deviceId = deviceId,
                requestedRomIds = setOf(romId),
                localSaves = listOf(selectedSave),
                currentServerFingerprint = fingerprint,
                currentCredentialRevision = revision,
                currentAccountId = accountId,
                pinnedServerFingerprint = fingerprint,
                pinnedCredentialRevision = revision,
                pinnedAccountId = accountId,
            )
        // Server requires assets.read + devices.read for negotiation.
        // The current account owns the device, and saves are included only
        // as fingerprints of the one explicitly user-selected local file.
        val json =
            postCancellableJson(
                connection,
                listOf("api", "sync", "negotiate"),
                request,
                128 * 1024,
            )
        val result = RommDeviceSyncProtocol.parseNegotiation(json, setOf(romId))
        val accepted =
            result as? RommDeviceSyncProtocol.ParseResult.Accepted
                ?: throw IOException("Unsupported RomM device sync response")
        val negotiation = accepted.negotiation
        // Mark the preview session as finished without claiming that any
        // transfer occurred. Future full sync must use a separate session.
        val notTransferred =
            negotiation.operations.count {
                it.action != RommDeviceSyncProtocol.Action.NO_OP
            }
        val unchanged = negotiation.operations.size - notTransferred
        val completion =
            JSONObject()
                .put("operations_completed", unchanged)
                .put("operations_failed", notTransferred)
                .toString()
        postCancellableJson(
            connection,
            listOf("api", "sync", "sessions", negotiation.sessionId.toString(), "complete"),
            completion,
            16 * 1024,
        )
        return negotiation
    }

    // Two-way save sync (RomM 5.4 device sync). The engine decides what to
    // do; these calls only move one save or one session record each.

    /** Starts a sync session. Requires assets.read and devices.read. */
    internal suspend fun negotiateSaveSync(
        connection: RommConnection,
        requestJson: String,
        allowedRomIds: Set<Int>,
    ): RommDeviceSyncProtocol.Negotiation {
        val (code, text) =
            syncCall(
                connection,
                buildUrl(connection, listOf("api", "sync", "negotiate")),
                requestJson.toRequestBody(JSON_MEDIA_TYPE),
                MAX_NEGOTIATION_BYTES,
            )
        if (code !in 200..299) throw RommSyncHttpException(code)
        val result = RommDeviceSyncProtocol.parseNegotiation(String(text, Charsets.UTF_8), allowedRomIds)
        return (result as? RommDeviceSyncProtocol.ParseResult.Accepted)?.negotiation
            ?: throw IOException("Unsupported RomM sync response")
    }

    /** Closes a session with the real counts. Requires devices.write. */
    internal suspend fun completeSaveSyncSession(
        connection: RommConnection,
        sessionId: Long,
        completed: Int,
        failed: Int,
    ) {
        require(sessionId > 0) { "Invalid RomM sync session" }
        val body =
            JSONObject()
                .put("operations_completed", completed.coerceAtLeast(0))
                .put("operations_failed", failed.coerceAtLeast(0))
                .toString()
        val (code, _) =
            syncCall(
                connection,
                buildUrl(connection, listOf("api", "sync", "sessions", sessionId.toString(), "complete")),
                body.toRequestBody(JSON_MEDIA_TYPE),
                16 * 1024,
            )
        if (code !in 200..299) throw RommSyncHttpException(code)
    }

    /**
     * Sends one battery save to the RomM "autosave" slot. Returns false when
     * RomM refuses because its copy changed since this device last synced
     * (HTTP 409); the caller turns that into a conflict, never a retry with
     * overwrite. overwrite is only true for the player's explicit choice.
     * Requires assets.write and devices.write.
     */
    internal suspend fun uploadSyncedSave(
        connection: RommConnection,
        romId: Int,
        fileName: String,
        content: ByteArray,
        md5: String,
        deviceId: String,
        sessionId: Long?,
        overwrite: Boolean,
    ): Boolean {
        require(romId > 0 && content.isNotEmpty() && content.size <= MAX_SYNC_SAVE_BYTES) { "Invalid save" }
        require(safeAttachmentFileName(fileName) && fileName.endsWith(".srm")) { "Invalid save name" }
        require(md5.matches(Regex("[0-9a-f]{32}"))) { "Invalid save hash" }
        val params =
            mutableListOf(
                "rom_id" to romId.toString(),
                "slot" to SYNC_SLOT,
                "emulator" to SYNC_EMULATOR,
                "device_id" to deviceId,
                "content_hash" to md5,
                "overwrite" to overwrite.toString(),
            )
        sessionId?.let { params += "session_id" to it.toString() }
        val body =
            okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart(
                    "saveFile",
                    fileName,
                    content.toRequestBody("application/octet-stream".toMediaType()),
                )
                .build()
        val (code, _) =
            syncCall(connection, buildUrl(connection, listOf("api", "saves"), params), body, MAX_NEGOTIATION_BYTES)
        if (code == 409) return false
        if (code !in 200..299) throw RommSyncHttpException(code)
        return true
    }

    /** Downloads one server save's bytes, bounded. Requires assets.read. */
    internal suspend fun downloadSyncedSave(
        connection: RommConnection,
        saveId: Long,
        deviceId: String,
        sessionId: Long?,
    ): ByteArray {
        require(saveId > 0) { "Invalid RomM save" }
        val params = mutableListOf("device_id" to deviceId)
        sessionId?.let { params += "session_id" to it.toString() }
        val (code, bytes) =
            syncCall(
                connection,
                buildUrl(connection, listOf("api", "saves", saveId.toString(), "content"), params),
                null,
                MAX_SYNC_SAVE_BYTES,
            )
        if (code !in 200..299) throw RommSyncHttpException(code)
        if (bytes.isEmpty()) throw IOException("RomM returned an empty save")
        return bytes
    }

    /** Tells RomM this device now holds the save. Requires devices.write. */
    internal suspend fun confirmSyncedSaveDownloaded(
        connection: RommConnection,
        saveId: Long,
        deviceId: String,
        md5: String,
    ) {
        val body = JSONObject().put("device_id", deviceId).put("content_hash", md5).toString()
        val (code, _) =
            syncCall(
                connection,
                buildUrl(connection, listOf("api", "saves", saveId.toString(), "downloaded")),
                body.toRequestBody(JSON_MEDIA_TYPE),
                MAX_NEGOTIATION_BYTES,
            )
        if (code !in 200..299) throw RommSyncHttpException(code)
    }

    /** The newest save state per (ROM, slot 1-4) for these ROMs. Requires assets.read. */
    internal suspend fun listSlotStates(
        connection: RommConnection,
        romIds: Collection<Int>,
    ): Map<Pair<Int, Int>, RommStateSlotPlan.Remote> {
        if (romIds.isEmpty()) return emptyMap()
        val params = romIds.map { "rom_ids" to it.toString() }
        val (code, bytes) =
            syncCall(connection, buildUrl(connection, listOf("api", "states"), params), null, MAX_JSON_BYTES.toInt())
        if (code !in 200..299) throw RommSyncHttpException(code)
        return RommStateSlotPlan.parseRemote(String(bytes, Charsets.UTF_8))
    }

    /** Downloads one save state's bytes, bounded. Requires assets.read. */
    internal suspend fun downloadSlotState(
        connection: RommConnection,
        stateId: Long,
    ): ByteArray {
        require(stateId > 0) { "Invalid RomM state" }
        val (code, bytes) =
            syncCall(
                connection,
                buildUrl(connection, listOf("api", "states", stateId.toString(), "content")),
                null,
                MAX_SYNC_SAVE_BYTES,
            )
        if (code !in 200..299) throw RommSyncHttpException(code)
        if (bytes.isEmpty()) throw IOException("RomM returned an empty state")
        return bytes
    }

    /**
     * Stores one save-state slot on RomM, replacing the state of the same
     * file name. Returns RomM's record of it when it could be read.
     * Requires assets.write.
     */
    internal suspend fun uploadSlotState(
        connection: RommConnection,
        romId: Int,
        fileName: String,
        content: ByteArray,
    ): RommStateSlotPlan.Remote? {
        require(romId > 0 && content.isNotEmpty() && content.size <= MAX_SYNC_SAVE_BYTES) { "Invalid state" }
        require(safeAttachmentFileName(fileName)) { "Invalid state name" }
        val body =
            okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart(
                    "stateFile",
                    fileName,
                    content.toRequestBody("application/octet-stream".toMediaType()),
                )
                .build()
        val params = listOf("rom_id" to romId.toString(), "emulator" to SYNC_EMULATOR)
        val (code, bytes) =
            syncCall(connection, buildUrl(connection, listOf("api", "states"), params), body, MAX_NEGOTIATION_BYTES)
        if (code !in 200..299) throw RommSyncHttpException(code)
        return runCatching { RommStateSlotPlan.parseOne(JSONObject(String(bytes, Charsets.UTF_8))) }.getOrNull()
    }

    /**
     * One cancellable request that returns the status and a bounded body
     * instead of throwing on every non-2xx code, so a 409 can be told apart.
     * A null body sends a GET. Never follows redirects.
     */
    private suspend fun syncCall(
        connection: RommConnection,
        url: HttpUrl,
        body: okhttp3.RequestBody?,
        maxBytes: Int,
    ): Pair<Int, ByteArray> =
        suspendCancellableCoroutine { continuation ->
            val request =
                Request.Builder()
                    .url(url)
                    .apply { if (body == null) get() else post(body) }
                    .header("Authorization", "Bearer " + connection.token)
                    .header("User-Agent", "OpenNES RomM save sync")
                    .build()
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
                            val result =
                                response.use {
                                    if (!it.isSuccessful) return@use it.code to ByteArray(0)
                                    val responseBody = it.body ?: return@use it.code to ByteArray(0)
                                    if (responseBody.contentLength() > maxBytes) {
                                        throw IOException("RomM response too large")
                                    }
                                    it.code to
                                        responseBody.byteStream().use { input ->
                                            RommSafeStreamRead.readBounded(input, maxBytes)
                                        }
                                }
                            if (continuation.isActive) continuation.resume(result)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                },
            )
        }

    private fun sameConnection(
        first: RommConnection,
        second: RommConnection,
    ): Boolean =
        first.serverUrl == second.serverUrl &&
            MessageDigest.isEqual(
                first.token.toByteArray(Charsets.UTF_8),
                second.token.toByteArray(Charsets.UTF_8),
            )

    /**
     * Cancels the underlying OkHttp call on coroutine cancellation. Never
     * follows redirects; bearer tokens must stay on the configured origin.
     */
    private suspend fun postCancellableJson(
        connection: RommConnection,
        path: List<String>,
        json: String,
        maxBytes: Int,
    ): String =
        suspendCancellableCoroutine { continuation ->
            val request =
                Request.Builder()
                    .url(buildUrl(connection, path))
                    .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("Authorization", "Bearer " + connection.token)
                    .header("Accept", "application/json")
                    .header("User-Agent", "OpenNES RomM device registration")
                    .build()
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
                            val text =
                                response.use {
                                    if (!it.isSuccessful) throw httpError(it.code)
                                    val body = it.body ?: throw IOException("Empty RomM device response")
                                    if (body.contentLength() > maxBytes) {
                                        throw IOException("RomM device response too large")
                                    }
                                    val bytes =
                                        body.byteStream().use { input ->
                                            RommSafeStreamRead.readBounded(input, maxBytes)
                                        }
                                    String(bytes, Charsets.UTF_8)
                                }
                            if (continuation.isActive) continuation.resume(text)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                },
            )
        }

    suspend fun checkAccount(connection: RommConnection): String = checkAccountProfile(connection).username

    // An API adapter is scoped to one active connection during a catalogue
    // refresh. Avoid re-requesting /api/platforms for every page.
    private var platformCacheConnection: RommConnection? = null
    private var platformCacheIds: Set<Int>? = null

    suspend fun listNesRoms(
        connection: RommConnection,
        offset: Int = 0,
        limit: Int = 40,
    ): Page =
        withContext(Dispatchers.IO) {
            require(offset >= 0 && limit in 1..100) { "Invalid RomM page" }
            val ids =
                if (platformCacheConnection === connection) {
                    platformCacheIds ?: findNesPlatformIds(connection)
                } else {
                    findNesPlatformIds(connection)
                }
            platformCacheConnection = connection
            platformCacheIds = ids
            if (ids.isEmpty()) return@withContext Page(emptyList(), 0, offset, limit)
            val params =
                mutableListOf(
                    "offset" to offset.toString(),
                    "limit" to limit.toString(),
                    "with_total" to "true",
                    "with_char_index" to "false",
                    "with_filter_values" to "false",
                )
            ids.forEach { id -> params += "platform_ids" to id.toString() }
            val document = getJson(connection, listOf("api", "roms"), params)
            val items = document.optJSONArray("items") ?: JSONArray()
            val result =
                (0 until items.length()).mapNotNull { index ->
                    val row = items.optJSONObject(index) ?: return@mapNotNull null
                    val platformId = row.optInt("platform_id", -1)
                    if (platformId !in ids) return@mapNotNull null
                    val id = row.optInt("id", -1)
                    val fileName = row.optString("fs_name")
                    if (id <= 0 || !safeRomFileName(fileName)) return@mapNotNull null
                    Rom(
                        id = id,
                        platformId = platformId,
                        name = row.optString("name").takeIf { it.isNotBlank() } ?: fileName,
                        fileName = fileName,
                        sizeBytes = row.optLong("fs_size_bytes", 0L),
                        description =
                            row.optString("summary")
                                .trim()
                                .takeIf { it.isNotBlank() && it != "null" }
                                ?.take(8_000),
                        coverUrl = RommCoverUrl.trusted(row.optString("url_cover")),
                        genres =
                            row.optJSONObject("metadatum")?.optJSONArray("genres")?.let { values ->
                                (0 until minOf(values.length(), 12))
                                    .mapNotNull { index ->
                                        values.optString(index).trim().takeIf { it.length in 2..60 }
                                    }
                                    .distinct()
                            } ?: emptyList(),
                        md5 =
                            row.optString("md5_hash")
                                .lowercase(Locale.ROOT)
                                .takeIf { it.matches(Regex("[a-f0-9]{32}")) },
                    )
                }
            Page(
                items = result,
                total =
                    if (document.has("total") && !document.isNull("total")) {
                        document.optInt("total", -1)
                    } else {
                        -1
                    },
                offset = offset,
                limit = limit,
                rawCount = items.length(),
            )
        }

    /** Requires an optional RomM token scope: assets.read. No writes to RomM. */
    suspend fun listRemoteSaves(
        connection: RommConnection,
        romId: Int,
    ): List<RemoteSave> =
        withContext(Dispatchers.IO) {
            require(romId > 0) { "Invalid RomM game" }
            val response =
                fetchJsonText(
                    connection,
                    listOf("api", "saves"),
                    listOf("rom_id" to romId.toString()),
                )
            // RomM 5.x exposes a bare SaveSchema array rather than a ROM page.
            val array =
                if (response.trimStart().startsWith("[")) {
                    JSONArray(response)
                } else {
                    JSONObject(response).optJSONArray("items") ?: JSONArray()
                }
            (0 until array.length()).mapNotNull { index ->
                val row = array.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optInt("id", -1)
                if (id <= 0 || row.optInt("rom_id", -1) != romId) return@mapNotNull null
                val filename = row.optString("file_name")
                if (!safeAttachmentFileName(filename)) return@mapNotNull null
                RemoteSave(
                    id = id,
                    romId = romId,
                    fileName = filename,
                    slot = row.optString("slot").takeIf { it.isNotBlank() && it != "null" },
                    sizeBytes = row.optLong("file_size_bytes", 0L),
                    contentHash = row.optString("content_hash").takeIf { it.isNotBlank() },
                )
            }
        }

    /**
     * Exports a copy to a user-selected SAF document, never to OpenNES's live
     * SRAM or save-state storage. The destination chooser is responsible for
     * explicit overwrite confirmation if the player picks an existing file.
     */
    suspend fun exportSaveBackup(
        context: Context,
        connection: RommConnection,
        save: RemoteSave,
        destination: Uri,
    ) = withContext(Dispatchers.IO) {
        require(save.id > 0 && save.romId > 0 && safeAttachmentFileName(save.fileName)) {
            "Invalid RomM save metadata"
        }
        val temporary = downloadVerifiedRemoteSave(context, connection, save)
        try {
            coroutineContext.ensureActive()
            val output =
                context.contentResolver.openOutputStream(destination, "w")
                    ?: throw IOException("Cannot write the selected backup document")
            output.use { stream ->
                temporary.inputStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = readProgressing(input, buffer)
                        if (count < 0) break
                        stream.write(buffer, 0, count)
                    }
                }
                stream.flush()
            }
        } finally {
            temporary.delete()
        }
    }

    /**
     * One shared, bounded save-download path for user exports and private
     * archives. It verifies the optional RomM content hash before returning
     * a temporary file, deleting partial downloads on any exception.
     */
    private suspend fun downloadVerifiedRemoteSave(
        context: Context,
        connection: RommConnection,
        save: RemoteSave,
    ): File =
        withContext(Dispatchers.IO) {
            require(save.id > 0 && save.romId > 0 && safeAttachmentFileName(save.fileName)) {
                "Invalid RomM save metadata"
            }
            val temporary = File.createTempFile("opennes-romm-save-", ".part", context.cacheDir)
            try {
                val request =
                    Request.Builder()
                        .url(buildUrl(connection, listOf("api", "saves", save.id.toString(), "content")))
                        .get()
                        .header("Authorization", "Bearer " + connection.token)
                        .header("User-Agent", "OpenNES/0.1 RomM backup export")
                        .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw httpError(response.code)
                    val body = response.body ?: throw IOException("Empty RomM save response")
                    if (body.contentLength() > MAX_SAVE_BYTES) {
                        throw IOException("RomM save exceeds supported backup size")
                    }
                    var total = 0L
                    body.byteStream().use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = readProgressing(input, buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_SAVE_BYTES) {
                                    throw IOException("RomM save exceeds supported backup size")
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (total == 0L) throw IOException("RomM returned an empty save")
                    RommDownloadVerification.checkRemoteSaveHash(temporary, save.contentHash)
                }
                coroutineContext.ensureActive()
                temporary
            } catch (e: Exception) {
                temporary.delete()
                throw e
            }
        }

    /** Save a user-selected remote save into an immutable app-private archive. */
    internal suspend fun keepRemoteSaveBackup(
        context: Context,
        connection: RommConnection,
        save: RemoteSave,
    ): RommVersionedSaveBackupStore.Backup =
        withContext(Dispatchers.IO) {
            // A server URL is not an account identity: check the token owner
            // before producing a new save that might later be synced.
            val userId =
                checkAccountProfile(connection).userId
                    ?: throw IOException("RomM account identity unavailable for save backup")
            val temporary = downloadVerifiedRemoteSave(context, connection, save)
            try {
                val fingerprint =
                    RommServerIdentity.fingerprint(connection.serverUrl)
                        ?: throw IOException("Invalid RomM server identity")
                privateBackupStore(context).createVerifiedBackup(
                    romId = save.romId,
                    original = temporary,
                    sourceServerFingerprint = fingerprint,
                    sourceAccountId = userId,
                )
            } finally {
                temporary.delete()
            }
        }

    internal suspend fun listOfflineSaveBackups(
        context: Context,
        romId: Int? = null,
    ): List<RommVersionedSaveBackupStore.Backup> =
        withContext(Dispatchers.IO) {
            privateBackupStore(context).listVerifiedBackups(romId)
        }

    /**
     * Read-only integrity counts for local offline backup files. Files with
     * corrupt or missing manifests are shown as unverified, not deleted.
     */
    internal suspend fun auditOfflineSaveBackups(context: Context): RommVersionedSaveBackupStore.ArchiveAudit =
        withContext(Dispatchers.IO) {
            privateBackupStore(context).audit()
        }

    /**
     * Explicit local cleanup only. Never touches live OpenNES saves or
     * makes an HTTP request, and never deletes an unverified archive.
     */
    internal suspend fun deleteOfflineSaveBackup(
        context: Context,
        backup: RommVersionedSaveBackupStore.Backup,
    ): Boolean =
        withContext(Dispatchers.IO) {
            privateBackupStore(context).deleteVerifiedBackup(backup)
        }

    /**
     * Record a non-executing offline transfer request. A verified archived
     * backup MUST originate from the currently configured HTTPS server.
     * No tokens, selected-file URIs, save contents or approval are persisted.
     * The user will have to reselect and reverify the local save later.
     */
    internal suspend fun prepareOfflineSaveTransfer(
        context: Context,
        backup: RommVersionedSaveBackupStore.Backup,
        localDocument: Uri,
        direction: RommSaveSyncPlanner.Direction,
        serverUrl: String?,
        journal: RommPendingSyncJournal,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val serverFingerprint = RommServerIdentity.fingerprint(serverUrl) ?: return@withContext false
            val pinnedUserId = RommConnectionStore(context).lastVerifiedUserId()
            if (backup.sourceServerFingerprint != serverFingerprint ||
                backup.sourceAccountId == null ||
                backup.sourceAccountId != pinnedUserId ||
                direction == RommSaveSyncPlanner.Direction.NONE
            ) {
                return@withContext false
            }
            val backups = privateBackupStore(context)
            if (!backups.isCurrentlyArchived(backup)) return@withContext false
            val local =
                RommSaveSnapshotter.fromReopenableStream {
                    context.contentResolver.openInputStream(localDocument)
                }
            if (!backups.isCurrentlyArchived(backup)) return@withContext false
            journal.enqueue(
                romId = backup.romId,
                direction = direction,
                local = local,
                remote = RommSaveSyncPlanner.Snapshot(backup.sha256, backup.bytes),
                serverFingerprint = serverFingerprint,
                sourceAccountId = backup.sourceAccountId,
            )
        }

    /**
     * Recheck only the local half of a pending offline intent. The picker
     * grants access for this read, not for future uploads or restoration.
     * A matching hash is NOT transfer approval: the remote save and server
     * permissions have not been revalidated.
     */
    internal suspend fun reviewPendingLocalSave(
        context: Context,
        pending: RommPendingSyncJournal.Pending,
        localDocument: Uri,
        serverUrl: String?,
        journal: RommPendingSyncJournal,
    ): RommPendingSyncJournal.LocalReview =
        withContext(Dispatchers.IO) {
            val serverFingerprint = RommServerIdentity.fingerprint(serverUrl)
            if (serverFingerprint == null || serverFingerprint != pending.serverFingerprint) {
                return@withContext RommPendingSyncJournal.LocalReview.DIFFERENT_SERVER
            }
            val local =
                RommSaveSnapshotter.fromReopenableStream {
                    context.contentResolver.openInputStream(localDocument)
                }
            journal.reviewLocal(pending, serverFingerprint, local)
        }

    /**
     * Compare a user-selected local document against an immutable archived
     * RomM save. The comparison never reads or modifies live emulator state,
     * and does not imply that merging or replacing saves would be safe.
     */
    internal suspend fun compareOfflineSaveBackup(
        context: Context,
        backup: RommVersionedSaveBackupStore.Backup,
        localDocument: Uri,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val backups = privateBackupStore(context)
            if (!backups.verify(backup)) throw IOException("Archive failed integrity check")
            val local =
                RommSaveSnapshotter.fromReopenableStream {
                    context.contentResolver.openInputStream(localDocument)
                }
            if (!backups.verify(backup)) throw IOException("Archive changed during comparison")
            local.bytes == backup.bytes && local.sha256 == backup.sha256
        }

    /** User-chosen SAF destination; never replaces emulator SRAM/state files. */
    internal suspend fun exportOfflineSaveBackup(
        context: Context,
        backup: RommVersionedSaveBackupStore.Backup,
        destination: Uri,
    ) = withContext(Dispatchers.IO) {
        val store = privateBackupStore(context)
        if (!store.verify(backup)) throw IOException("Local save backup failed integrity verification")
        val output =
            context.contentResolver.openOutputStream(destination, "w")
                ?: throw IOException("Cannot write selected export document")
        output.use { sink ->
            backup.file.inputStream().buffered().use { source ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val length = readProgressing(source, buffer)
                    if (length < 0) break
                    sink.write(buffer, 0, length)
                }
                sink.flush()
            }
        }
        if (!store.verify(backup)) throw IOException("Local backup changed during export")
    }

    private fun privateBackupStore(context: Context): RommVersionedSaveBackupStore =
        RommVersionedSaveBackupStore(File(context.noBackupFilesDir, "romm-save-archive-v1"))

    /** A RomM import never overwrites an existing local file or save. */
    suspend fun importIntoSelectedNesFolder(
        context: Context,
        connection: RommConnection,
        rom: Rom,
        onProgress: (ImportProgress) -> Unit = {},
    ): Uri =
        withContext(Dispatchers.IO) {
            require(rom.id > 0 && safeRomFileName(rom.fileName)) {
                "This RomM game is not a supported standalone NES file"
            }
            @Suppress("DEPRECATION")
            val legacyPrefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
            val treeKey = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
            val treeString =
                legacyPrefs.getString(treeKey, null)
                    ?: throw IOException("Choose an OpenNES ROM folder in Settings first")
            val folder =
                DocumentFile.fromTreeUri(context, Uri.parse(treeString))
                    ?: throw IOException("The selected NES ROM folder is unavailable")
            if (!folder.isDirectory || !folder.canWrite()) {
                throw IOException("The selected NES ROM folder is not writable")
            }
            if (romFileExists(folder, rom.fileName)) {
                throw IOException("A local ROM with this filename already exists; nothing was overwritten")
            }

            val temporary = File.createTempFile("opennes-romm-", ".part", context.cacheDir)
            try {
                onProgress(ImportProgress(ImportStage.CONNECTING))
                downloadToTemp(connection, rom, temporary, onProgress)
                onProgress(ImportProgress(ImportStage.VERIFYING))
                RommDownloadVerification.checkImportedRom(temporary, rom.fileName)
                coroutineContext.ensureActive()
                if (romFileExists(folder, rom.fileName)) {
                    throw IOException("The local ROM appeared during download; import cancelled")
                }
                onProgress(ImportProgress(ImportStage.IMPORTING))
                val newFile =
                    folder.createFile("application/octet-stream", rom.fileName)
                        ?: throw IOException("Could not create a file in the selected NES folder")
                if (newFile.name != rom.fileName) {
                    // Some providers append a MIME extension. Do not leave an
                    // unindexable or wrongly named ROM and claim success.
                    newFile.delete()
                    throw IOException("Selected document provider renamed the imported ROM")
                }
                try {
                    val stream =
                        context.contentResolver.openOutputStream(newFile.uri, "w")
                            ?: throw IOException("Cannot write to selected NES folder")
                    stream.use { output ->
                        temporary.inputStream().use { input ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = readProgressing(input, buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                        output.flush()
                    }
                } catch (e: Exception) {
                    // This file was freshly created by OpenNES, never an old ROM.
                    newFile.delete()
                    throw e
                }
                // The ROM has already been durably written at this point.
                // If WorkManager cannot enqueue, leave it untouched and let
                // the details screen offer Retry library scan after a delay.
                runCatching {
                    LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
                }
                onProgress(ImportProgress(ImportStage.INDEXING))
                newFile.uri
            } finally {
                temporary.delete()
            }
        }

    private fun romFileExists(
        folder: DocumentFile,
        fileName: String,
    ): Boolean =
        folder.findFile(fileName) != null ||
            folder.listFiles().any { it.name.equals(fileName, ignoreCase = true) }

    private fun findNesPlatformIds(connection: RommConnection): Set<Int> {
        // RomM's GET /api/platforms returns a bare JSON array, unlike
        // the paginated {items, total} response of GET /api/roms.
        val payload = fetchJsonText(connection, listOf("api", "platforms"))
        val array =
            if (payload.trimStart().startsWith("[")) {
                JSONArray(payload)
            } else {
                val page = JSONObject(payload)
                page.optJSONArray("items") ?: page.optJSONArray("platforms") ?: JSONArray()
            }
        val matching = mutableSetOf<Int>()
        for (index in 0 until array.length()) {
            val platform = array.optJSONObject(index) ?: continue
            val slug = platform.optString("slug").lowercase(Locale.ROOT)
            val fsSlug = platform.optString("fs_slug").lowercase(Locale.ROOT)
            if (slug in NES_SLUGS || fsSlug in NES_SLUGS) {
                platform.optInt("id").takeIf { it > 0 }?.let { matching += it }
            }
        }
        return matching
    }

    private fun getJson(
        connection: RommConnection,
        path: List<String>,
        params: List<Pair<String, String>> = emptyList(),
    ): JSONObject = JSONObject(fetchJsonText(connection, path, params))

    private fun fetchJsonText(
        connection: RommConnection,
        path: List<String>,
        params: List<Pair<String, String>> = emptyList(),
    ): String {
        val url = buildUrl(connection, path, params)
        val request =
            Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer " + connection.token)
                .header("Accept", "application/json")
                .header("User-Agent", "OpenNES/0.1 RomM read-only client")
                .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code)
            val body = response.body ?: throw IOException("Empty RomM response")
            val bytes = body.byteStream().use { readBounded(it, MAX_JSON_BYTES) }
            String(bytes, Charsets.UTF_8)
        }
    }

    private suspend fun downloadToTemp(
        connection: RommConnection,
        rom: Rom,
        target: File,
        onProgress: (ImportProgress) -> Unit,
    ) {
        val request =
            Request.Builder()
                .url(buildUrl(connection, listOf("api", "roms", rom.id.toString(), "content", rom.fileName)))
                .get()
                .header("Authorization", "Bearer " + connection.token)
                .header("User-Agent", "OpenNES/0.1 RomM read-only client")
                .build()
        importHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code)
            val body = response.body ?: throw IOException("Empty RomM ROM response")
            if (rom.sizeBytes > MAX_ROM_BYTES) {
                throw IOException("RomM ROM exceeds import size limit")
            }
            if (body.contentLength() > MAX_ROM_BYTES) {
                throw IOException("RomM ROM exceeds import size limit")
            }
            val digest = MessageDigest.getInstance("MD5")
            val totalBytes =
                body.contentLength().takeIf { it > 0 }
                    ?: rom.sizeBytes.takeIf { it > 0 }
            var countTotal = 0L
            var lastNotified = 0L
            onProgress(ImportProgress(ImportStage.DOWNLOADING, 0L, totalBytes))
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = readProgressing(input, buffer)
                        if (count < 0) break
                        countTotal += count
                        if (countTotal > MAX_ROM_BYTES) {
                            throw IOException("RomM ROM exceeds import size limit")
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        if (countTotal - lastNotified >= 16 * 1024L) {
                            onProgress(ImportProgress(ImportStage.DOWNLOADING, countTotal, totalBytes))
                            lastNotified = countTotal
                        }
                    }
                }
            }
            onProgress(ImportProgress(ImportStage.DOWNLOADING, countTotal, totalBytes))
            if (countTotal == 0L) throw IOException("RomM returned an empty ROM")
            rom.md5?.let { expected ->
                val actual =
                    digest.digest().joinToString("") { byte ->
                        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
                    }
                if (actual != expected) throw IOException("RomM ROM checksum mismatch")
            }
        }
    }

    private fun buildUrl(
        connection: RommConnection,
        path: List<String>,
        params: List<Pair<String, String>> = emptyList(),
    ): HttpUrl {
        val root =
            validatedServerUrl(connection.serverUrl).toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Invalid RomM server URL")
        return root.newBuilder().apply {
            path.forEach { addPathSegment(it) }
            params.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
    }

    private fun httpError(code: Int): IOException =
        IOException(
            when (code) {
                401 -> "RomM token expired or invalid (401)"
                403 -> "RomM token lacks required read scope (403)"
                404 -> "RomM API endpoint unavailable (404); check server version"
                in 300..399 -> "RomM redirected the request; a token was not forwarded"
                else -> "RomM server returned HTTP " + code
            },
        )

    companion object {
        private const val MAX_JSON_BYTES = 3 * 1024 * 1024L
        private const val MAX_ROM_BYTES = 96 * 1024 * 1024L
        private const val MAX_SAVE_BYTES = 16 * 1024 * 1024L
        private val NES_SLUGS = setOf("nes", "famicom", "fds", "famicom-disk-system")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_NEGOTIATION_BYTES = 128 * 1024
        const val MAX_SYNC_SAVE_BYTES = 4 * 1024 * 1024

        // RomM's names for an NES battery save, shared with its web player
        // and RetroArch sync so the same save continues on any of them.
        const val SYNC_SLOT = "autosave"
        const val SYNC_EMULATOR = "fceumm"

        /**
         * Allow cleartext only to literal RFC1918 IPv4 addresses. Hostnames
         * and public IPs must use HTTPS; never trust DNS to prove a LAN.
         * Plain HTTP exposes a bearer token to observers on that network.
         */
        fun isPrivateLanHttp(raw: String): Boolean {
            val url = raw.trim().toHttpUrlOrNull() ?: return false
            if (url.scheme != "http") return false
            val octets = url.host.split('.')
            if (octets.size != 4 || octets.any { it.isEmpty() || it.length > 3 }) return false
            val parts = octets.map { it.toIntOrNull() ?: return false }
            if (parts.any { it !in 0..255 }) return false
            return parts[0] == 10 ||
                (parts[0] == 172 && parts[1] in 16..31) ||
                (parts[0] == 192 && parts[1] == 168)
        }

        fun validatedServerUrl(raw: String): String {
            val parsed =
                raw.trim().toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("Enter a valid RomM URL")
            require(
                (parsed.scheme == "https" || isPrivateLanHttp(raw)) &&
                    parsed.username.isEmpty() &&
                    parsed.password.isEmpty() &&
                    parsed.query == null &&
                    parsed.fragment == null,
            ) {
                "RomM requires HTTPS, or an explicit private-LAN HTTP address, without credentials or query"
            }
            return if (parsed.encodedPath.endsWith("/")) {
                parsed.toString()
            } else {
                parsed.newBuilder().addPathSegment("").build().toString()
            }
        }

        fun safeRomFileName(fileName: String): Boolean {
            if (!safeAttachmentFileName(fileName)) return false
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return ext in setOf("nes", "fds", "zip")
        }

        private fun safeAttachmentFileName(fileName: String): Boolean {
            if (fileName.isBlank() || fileName.length > 230 || fileName.startsWith(".")) return false
            return fileName.none { it == '/' || it == '\\' || it.isISOControl() }
        }

        /**
         * A bulk read returning zero is not EOF. Consume one byte instead
         * of retrying indefinitely; every caller retains its own limits.
         */
        private fun readProgressing(
            input: InputStream,
            buffer: ByteArray,
        ): Int {
            require(buffer.isNotEmpty())
            val count = input.read(buffer)
            if (count != 0) return count
            val one = input.read()
            if (one < 0) return -1
            buffer[0] = one.toByte()
            return 1
        }

        private fun readBounded(
            input: InputStream,
            maxBytes: Long,
        ): ByteArray {
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val amount = readProgressing(input, buffer)
                if (amount < 0) break
                total += amount
                if (total > maxBytes) throw IOException("RomM JSON response exceeds limit")
                result.write(buffer, 0, amount)
            }
            return result.toByteArray()
        }
    }
}

/** A RomM save-sync call returned [code]; the engine maps it to a message. */
internal class RommSyncHttpException(val code: Int) : IOException("RomM returned HTTP " + code)
