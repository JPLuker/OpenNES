package com.swordfish.lemuroid.app.shared.migration

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * User-selected, offline migration of NES SRAM and native FCEUmm states
 * between the debug and Play package IDs. Never includes ROMs, settings,
 * credentials, a game-library database, or Hardcore saves.
 *
 * Import stages and verifies the WHOLE archive before creating destination
 * files. Existing saves are never opened for writing or replaced.
 */
internal object OpenNesSaveTransfer {
    data class Item(val path: String, val bytes: Long, val sha256: String)

    data class ImportResult(val imported: Int, val existingPreserved: Int)

    class PreparedImport internal constructor(
        private val staging: File,
        val items: List<Item>,
    ) {
        fun apply(destinationRoot: File): ImportResult {
            val created = ArrayList<File>()
            try {
                // Revalidate every staged file before the first destination
                // write. A stale, replaced or corrupted staged archive fails.
                items.forEach { item ->
                    val source = File(staging, item.path)
                    if (!safePath(item.path) || source.length() != item.bytes ||
                        !hashMatches(source, item)
                    ) {
                        throw IOException("Save migration staging changed")
                    }
                }
                var skipped = 0
                items.forEach { item ->
                    val destination = verifiedDestination(destinationRoot, item.path)
                    if (destination.exists()) {
                        skipped++
                        return@forEach
                    }
                    // Stream into a unique temporary file in the SAME
                    // destination directory and verify before commit. A
                    // process kill must not leave a truncated *.srm or *.state
                    // that the next emulator launch mistakes for a valid save.
                    val temporary =
                        File.createTempFile("opennes-import-", ".partial", destination.parentFile)
                    try {
                        File(staging, item.path).inputStream().buffered().use { input ->
                            FileOutputStream(temporary).use { output ->
                                copyBounded(input, output, MAX_FILE_BYTES)
                                output.fd.sync()
                            }
                        }
                        if (!hashMatches(temporary, item)) {
                            throw IOException("Imported save failed pre-commit verification")
                        }
                        // The caller holds GameProcessLock across this entire
                        // transaction. Never use REPLACE_EXISTING, and always
                        // refuse a destination that appeared meanwhile.
                        if (destination.exists()) {
                            skipped++
                            return@forEach
                        }
                        if (!temporary.renameTo(destination)) {
                            throw IOException("Cannot commit verified migration save")
                        }
                        created += destination
                        if (!hashMatches(destination, item)) {
                            throw IOException("Imported save failed post-commit verification")
                        }
                    } finally {
                        temporary.delete()
                    }
                }
                return ImportResult(created.size, skipped)
            } catch (e: Exception) {
                created.asReversed().forEach { it.delete() }
                throw e
            } finally {
                discard()
            }
        }

        fun discard() {
            // Only the uniquely created app-cache staging directory is ours.
            if (staging.name.startsWith("opennes-save-import-")) {
                staging.deleteRecursively()
            }
        }
    }

    fun export(
        sourceRoot: File,
        destination: OutputStream,
    ): Int {
        val files = ArrayList<Pair<String, File>>()
        val saves = File(sourceRoot, "saves")
        val states = File(sourceRoot, "states/fceumm")
        saves.listFiles()?.filter { it.isFile && it.name.endsWith(".srm", ignoreCase = true) }
            ?.forEach { files += "saves/" + it.name to it }
        states.listFiles()?.filter { it.isFile && isStateFile(it.name) }
            ?.forEach { files += "states/fceumm/" + it.name to it }
        if (files.isEmpty()) throw IOException("No NES saves or states to export")
        if (files.size > MAX_ENTRIES) throw IOException("Too many save files to export")

        val items = ArrayList<Item>()
        var total = 0L
        ZipOutputStream(destination).use { archive ->
            for ((path, file) in files.sortedBy { it.first }) {
                if (!safePath(path) || !verifiedExistingFile(sourceRoot, path, file)) {
                    throw IOException("Unsafe save source")
                }
                val bytes = file.length()
                if (bytes !in 1..MAX_FILE_BYTES) throw IOException("Save file size is not supported")
                total += bytes
                if (total > MAX_TOTAL_BYTES) throw IOException("Save migration is too large")
                val original = file.readBytes()
                if (original.size.toLong() != bytes) throw IOException("Save changed during export")
                val sha = sha256(original)
                // Read again; never silently export bytes from a game that
                // was saving while the archive was being assembled.
                if (!hashMatches(file, Item(path, bytes, sha))) {
                    throw IOException("Save changed during export")
                }
                archive.putNextEntry(ZipEntry(path))
                archive.write(original)
                archive.closeEntry()
                items += Item(path, bytes, sha)
            }
            val manifest =
                JSONObject()
                    .put("format", "opennes-saves")
                    .put("version", 1)
                    .put(
                        "files",
                        JSONArray().apply {
                            items.forEach { item ->
                                put(
                                    JSONObject()
                                        .put("path", item.path)
                                        .put("bytes", item.bytes)
                                        .put("sha256", item.sha256),
                                )
                            }
                        },
                    )
                    .toString().toByteArray(Charsets.UTF_8)
            archive.putNextEntry(ZipEntry(MANIFEST))
            archive.write(manifest)
            archive.closeEntry()
        }
        return items.size
    }

    fun prepare(
        archiveStream: InputStream,
        cacheRoot: File,
    ): PreparedImport {
        if (!cacheRoot.isDirectory && !cacheRoot.mkdirs()) {
            throw IOException("Cannot create temporary migration directory")
        }
        val staging = File(cacheRoot, "opennes-save-import-" + UUID.randomUUID())
        if (!staging.mkdir()) throw IOException("Cannot create migration staging")
        try {
            var manifest: String? = null
            var total = 0L
            val seen = HashSet<String>()
            val staged = HashMap<String, Item>()
            ZipInputStream(archiveStream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = entry.name
                    if (!seen.add(path) || entry.isDirectory ||
                        (path != MANIFEST && !safePath(path)) ||
                        seen.size > MAX_ENTRIES + 1
                    ) {
                        throw IOException("Unsafe or duplicate save archive entry")
                    }
                    if (path == MANIFEST) {
                        val content = readBounded(zip, MAX_MANIFEST_BYTES)
                        manifest = String(content, Charsets.UTF_8)
                    } else {
                        val dest = File(staging, path)
                        val parent = dest.parentFile ?: throw IOException("Invalid archive path")
                        if (!parent.isDirectory && !parent.mkdirs()) {
                            throw IOException("Cannot stage migration file")
                        }
                        val digest = MessageDigest.getInstance("SHA-256")
                        var length = 0L
                        FileOutputStream(dest).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var idleReads = 0
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                if (count == 0) {
                                    if (++idleReads > 8) throw IOException("Stalled save archive")
                                    continue
                                }
                                idleReads = 0
                                length += count
                                total += count
                                if (length > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) {
                                    throw IOException("Save archive exceeds safe size")
                                }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                        if (length == 0L) throw IOException("Empty save in migration archive")
                        staged[path] = Item(path, length, hex(digest.digest()))
                    }
                    zip.closeEntry()
                }
            }
            val parsed = manifest?.let(::parseManifest)
                ?: throw IOException("Missing save migration manifest")
            if (parsed.size != staged.size || parsed.toSet() != staged.values.toSet()) {
                throw IOException("Save migration manifest does not match the archive")
            }
            return PreparedImport(staging, parsed)
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw e
        }
    }

    private fun parseManifest(json: String): List<Item> {
        if (json.length !in 2..MAX_MANIFEST_BYTES) throw IOException("Invalid migration manifest")
        val root = JSONObject(json)
        if (root.optString("format") != "opennes-saves" || root.optInt("version") != 1) {
            throw IOException("Unsupported save migration format")
        }
        val entries = root.optJSONArray("files") ?: throw IOException("Missing save manifest files")
        if (entries.length() !in 1..MAX_ENTRIES) throw IOException("Invalid save manifest count")
        val items = ArrayList<Item>()
        val seen = HashSet<String>()
        for (i in 0 until entries.length()) {
            val row = entries.getJSONObject(i)
            val path = row.getString("path")
            val bytes = row.getLong("bytes")
            val sha = row.getString("sha256")
            if (!safePath(path) || bytes !in 1..MAX_FILE_BYTES ||
                !sha.matches(Regex("[0-9a-f]{64}")) || !seen.add(path)
            ) {
                throw IOException("Unsafe save migration manifest")
            }
            items += Item(path, bytes, sha)
        }
        return items
    }

    private fun verifiedExistingFile(root: File, path: String, file: File): Boolean {
        val parent = file.parentFile ?: return false
        val expected = File(root, path.substringBeforeLast('/'))
        return file.isFile && !file.isDirectory &&
            parent.canonicalFile == expected.canonicalFile &&
            file.canonicalFile.parentFile == parent.canonicalFile &&
            file.canonicalFile.path.startsWith(root.canonicalFile.path + File.separator)
    }

    private fun verifiedDestination(root: File, path: String): File {
        if (!safePath(path)) throw IOException("Invalid save destination")
        val destination = File(root, path)
        val parent = destination.parentFile ?: throw IOException("Invalid save destination")
        val normalized = root.canonicalFile
        val segments = path.split('/')
        // Reject a pre-existing symlink in the saves/states directories.
        var current = normalized
        for (segment in segments.dropLast(1)) {
            current = File(current, segment)
            if (current.exists() && current.canonicalFile != current.absoluteFile) {
                throw IOException("Save destination is redirected")
            }
            if (!current.isDirectory && !current.mkdirs()) {
                throw IOException("Cannot initialize save destination")
            }
        }
        if (parent.canonicalFile != current.canonicalFile ||
            destination.canonicalFile.parentFile != current.canonicalFile
        ) {
            throw IOException("Save destination escaped app storage")
        }
        return destination
    }

    private fun safePath(path: String): Boolean {
        if (path.startsWith("/") || path.contains('\\') ||
            path.any { it.isISOControl() } || path.length !in 1..255 ||
            path.split('/').any { it.isEmpty() || it == "." || it == ".." }
        ) {
            return false
        }
        return when {
            path.startsWith("saves/") -> {
                val name = path.removePrefix("saves/")
                '/' !in name && name.endsWith(".srm", ignoreCase = true) && name.length > 4
            }
            path.startsWith("states/fceumm/") -> {
                val name = path.removePrefix("states/fceumm/")
                '/' !in name && isStateFile(name)
            }
            else -> false
        }
    }

    private fun isStateFile(name: String): Boolean {
        val value = name.removeSuffix(".metadata")
        return (value.endsWith(".state") && value.length > ".state".length) ||
            (1..4).any { value.endsWith(".slot" + it) && value.length > ".slot1".length }
    }

    private fun hashMatches(file: File, item: Item): Boolean {
        if (!file.isFile || file.length() != item.bytes) return false
        return sha256(file.readBytes()) == item.sha256 && file.length() == item.bytes
    }

    private fun sha256(bytes: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private fun copyBounded(source: InputStream, dest: OutputStream, limit: Long): Long {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            if (read == 0) throw IOException("Stalled save migration")
            total += read
            if (total > limit) throw IOException("Save migration exceeded safe size")
            dest.write(buffer, 0, read)
        }
        return total
    }

    private fun readBounded(stream: InputStream, limit: Int): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        copyBounded(stream, result, limit.toLong())
        return result.toByteArray()
    }

    private const val MANIFEST = "opennes-save-manifest.json"
    private const val MAX_MANIFEST_BYTES = 128 * 1024
    private const val MAX_FILE_BYTES = 16L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
    private const val MAX_ENTRIES = 512
}
