package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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

/**
 * Read-only RomM API adapter. Uses a scoped bearer token and HTTPS exclusively.
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
    )

    data class Page(
        val items: List<Rom>,
        val total: Int,
        val offset: Int,
        val limit: Int,
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

    suspend fun checkAccount(connection: RommConnection): String =
        withContext(Dispatchers.IO) {
            val data = getJson(connection, listOf("api", "users", "me"))
            data.optString("username").takeIf { it.isNotBlank() }
                ?: throw IOException("RomM did not return a signed-in user")
        }

    suspend fun listNesRoms(
        connection: RommConnection,
        offset: Int = 0,
        limit: Int = 40,
    ): Page =
        withContext(Dispatchers.IO) {
            require(offset >= 0 && limit in 1..100) { "Invalid RomM page" }
            val ids = findNesPlatformIds(connection)
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
                        md5 =
                            row.optString("md5_hash")
                                .lowercase(Locale.ROOT)
                                .takeIf { it.matches(Regex("[a-f0-9]{32}")) },
                    )
                }
            Page(
                items = result,
                total = document.optInt("total", offset + items.length()),
                offset = offset,
                limit = limit,
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
            val temporary = downloadVerifiedRemoteSave(context, connection, save)
            try {
                val fingerprint =
                    RommServerIdentity.fingerprint(connection.serverUrl)
                        ?: throw IOException("Invalid RomM server identity")
                privateBackupStore(context).createVerifiedBackup(save.romId, temporary, fingerprint)
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
            if (backup.sourceServerFingerprint != serverFingerprint ||
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
            if (folder.findFile(rom.fileName) != null) {
                throw IOException("A local ROM with this filename already exists; nothing was overwritten")
            }

            val temporary = File.createTempFile("opennes-romm-", ".part", context.cacheDir)
            try {
                downloadToTemp(connection, rom, temporary)
                RommDownloadVerification.checkImportedRom(temporary, rom.fileName)
                coroutineContext.ensureActive()
                if (folder.findFile(rom.fileName) != null) {
                    throw IOException("The local ROM appeared during download; import cancelled")
                }
                val newFile =
                    folder.createFile("application/octet-stream", rom.fileName)
                        ?: throw IOException("Could not create a file in the selected NES folder")
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
                LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
                newFile.uri
            } finally {
                temporary.delete()
            }
        }

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
    ) {
        val request =
            Request.Builder()
                .url(buildUrl(connection, listOf("api", "roms", rom.id.toString(), "content", rom.fileName)))
                .get()
                .header("Authorization", "Bearer " + connection.token)
                .header("User-Agent", "OpenNES/0.1 RomM read-only client")
                .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code)
            val body = response.body ?: throw IOException("Empty RomM ROM response")
            if (body.contentLength() > MAX_ROM_BYTES) {
                throw IOException("RomM ROM exceeds import size limit")
            }
            val digest = MessageDigest.getInstance("MD5")
            var countTotal = 0L
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
                    }
                }
            }
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
                ?: throw IllegalArgumentException("Invalid RomM HTTPS address")
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

        /** HTTPS-only: never expose bearer tokens over cleartext HTTP. */
        fun validatedServerUrl(raw: String): String {
            val candidate = raw.trim()
            val parsed =
                candidate.toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("Enter a valid RomM URL")
            require(
                parsed.scheme == "https" &&
                    parsed.username.isEmpty() &&
                    parsed.password.isEmpty() &&
                    parsed.query == null &&
                    parsed.fragment == null,
            ) {
                "RomM requires an HTTPS URL without login details or query"
            }
            // Accept both https://host and https://host/subpath.
            // The trailing slash is needed before appending /api endpoints.
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
