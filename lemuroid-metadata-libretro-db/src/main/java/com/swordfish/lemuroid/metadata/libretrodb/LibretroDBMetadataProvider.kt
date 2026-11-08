package com.swordfish.lemuroid.metadata.libretrodb

import com.swordfish.lemuroid.common.kotlin.filterNullable
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.StorageFile
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDBManager
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDatabase
import com.swordfish.lemuroid.metadata.libretrodb.db.entity.LibretroRom
import timber.log.Timber
import java.util.Locale

class LibretroDBMetadataProvider(private val ovgdbManager: LibretroDBManager) :
    GameMetadataProvider {
    companion object {
        private val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|]")
    }

    private val sortedSystemIds = listOf(SystemID.NES.dbname)

    override suspend fun searchMetadata(title: String): List<GameMetadata> {
        val query = title.trim()
        if (query.length < 3) return emptyList()

        return runCatching {
            ovgdbManager.dbInstance.gameDao().searchNesTitles(query)
                .distinctBy { it.name?.lowercase(Locale.ROOT) }
                .take(20)
                .map { convertToGameMetadata(it) }
        }.onFailure {
            Timber.w(it, "Could not search the local NES metadata catalogue")
        }.getOrDefault(emptyList())
    }

    override suspend fun retrieveMetadata(storageFile: StorageFile): GameMetadata? {
        val db = ovgdbManager.dbInstance

        Timber.d("Looking metadata for file: $storageFile")

        val metadata =
            runCatching {
                findByCRC(storageFile, db)
                    ?: findBySerial(storageFile, db)
                    ?: findByFilename(db, storageFile)
                    ?: findByTitleCandidates(db, storageFile)
                    ?: findByPathAndFilename(db, storageFile)
                    ?: findByUniqueExtension(storageFile)
                    ?: findByKnownSystem(storageFile)
                    ?: findByPathAndSupportedExtension(storageFile)
            }.getOrElse {
                Timber.e("Error in retrieving $storageFile metadata: $it... Skipping.")
                null
            }

        metadata?.let { Timber.d("Metadata retrieved for item: $it") }

        return metadata
    }

    private fun convertToGameMetadata(rom: LibretroRom): GameMetadata {
        val system = GameSystem.findById(rom.system!!)
        return GameMetadata(
            name = rom.name,
            romName = rom.romName,
            thumbnail = computeCoverUrl(system, rom.name),
            system = rom.system,
            developer = rom.developer,
        )
    }

    private suspend fun findByFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        return db.gameDao().findByFileName(file.name)
            .filterNullable { it.system == SystemID.NES.dbname }
            .filterNullable { extractGameSystem(it).scanOptions.scanByFilename }
            ?.let { convertToGameMetadata(it) }
    }

    /**
     * No-Intro-style ROM filenames often carry a region or revision tag that
     * differs from the catalogue's box-art title. Only accept an unambiguous
     * title match; never guess between similarly named games.
     */
    private suspend fun findByTitleCandidates(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        val title = file.extensionlessName
        val prefix =
            title.substringBefore('(')
                .substringBefore('[')
                .trim()

        if (prefix.length < 4 || prefix.contains('%') || prefix.contains('_')) return null

        val canonicalTitle = canonicalNesTitle(title)
        val matches =
            db.gameDao().findByTitlePrefix(prefix)
                .filter { candidate ->
                    candidate.name?.let { canonicalNesTitle(it) == canonicalTitle } == true
                }
        val uniqueMatch =
            when (matches.size) {
                0 -> null
                1 -> matches.first()
                else -> matches.singleOrNull { it.romName.equals(file.name, ignoreCase = true) }
            }

        return uniqueMatch?.let { convertToGameMetadata(it) }
    }

    private fun canonicalNesTitle(raw: String): String {
        val noExtension = raw.replace(Regex("\\.(nes|zip|7z)$", RegexOption.IGNORE_CASE), "")
        val noTags = noExtension.replace(Regex("\\([^)]*\\)|\\[[^]]*\\]"), " ")
        return noTags.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
    }

    private suspend fun findByPathAndFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        return db.gameDao().findByFileName(file.name)
            .filterNullable { it.system == SystemID.NES.dbname }
            .filterNullable { extractGameSystem(it).scanOptions.scanByPathAndFilename }
            .filterNullable { parentContainsSystem(file.path, extractGameSystem(it).id.dbname) }
            ?.let { convertToGameMetadata(it) }
    }

    private fun findByPathAndSupportedExtension(file: StorageFile): GameMetadata? {
        val system =
            sortedSystemIds
                .filter { parentContainsSystem(file.path, it) }
                .map { GameSystem.findById(it) }
                .filter { it.scanOptions.scanByPathAndSupportedExtensions }
                .firstOrNull { it.supportedExtensions.contains(file.extension) }

        return system?.let {
            GameMetadata(
                name = file.extensionlessName,
                romName = file.name,
                thumbnail = null,
                system = it.id.dbname,
                developer = null,
            )
        }
    }

    private fun parentContainsSystem(
        parent: String?,
        dbname: String,
    ): Boolean {
        return parent?.toLowerCase(Locale.getDefault())?.contains(dbname) == true
    }

    private suspend fun findByCRC(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {
        if (file.crc == null || file.crc == "0") return null
        return file.crc?.let { crc32 -> db.gameDao().findByCRC(crc32) }
            .filterNullable { it.system == SystemID.NES.dbname }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findBySerial(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {
        if (file.serial == null) return null
        return db.gameDao().findBySerial(file.serial!!)
            .filterNullable { it.system == SystemID.NES.dbname }
            ?.let { convertToGameMetadata(it) }
    }

    private fun findByKnownSystem(file: StorageFile): GameMetadata? {
        if (file.systemID != SystemID.NES) return null

        return GameMetadata(
            name = file.extensionlessName,
            romName = file.name,
            thumbnail = null,
            system = file.systemID!!.dbname,
            developer = null,
        )
    }

    private fun findByUniqueExtension(file: StorageFile): GameMetadata? {
        val system = GameSystem.findByUniqueFileExtension(file.extension)

        if (system?.scanOptions?.scanByUniqueExtension == false) {
            return null
        }

        val result =
            system?.let {
                GameMetadata(
                    name = file.extensionlessName,
                    romName = file.name,
                    thumbnail = null,
                    system = it.id.dbname,
                    developer = null,
                )
            }

        return result
    }

    private fun extractGameSystem(rom: LibretroRom): GameSystem {
        return GameSystem.findById(rom.system!!)
    }

    private fun computeCoverUrl(
        system: GameSystem,
        name: String?,
    ): String? {
        val systemName = system.libretroFullName

        if (name == null) {
            return null
        }

        val imageType = "Named_Boxarts"

        val thumbGameName = name.replace(THUMB_REPLACE, "_")

        return "https://thumbnails.libretro.com/$systemName/$imageType/$thumbGameName.png"
    }
}
