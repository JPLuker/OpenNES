package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.common.kotlin.writeBytesAtomic
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.migrators.getSavesMigrator
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SavesManager(private val directoriesManager: DirectoriesManager) {
    suspend fun getSaveRAM(
        game: Game,
        systemCoreConfig: SystemCoreConfig,
        profile: GameSaveProfile = GameSaveProfile.CASUAL,
    ): ByteArray? {
        return withContext(Dispatchers.IO) {
            val result =
                runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                    val saveFile = getSaveFile(getSaveRAMFileName(game), profile)
                    if (saveFile.exists() && saveFile.length() > 0) {
                        saveFile.readBytes()
                    } else {
                        if (profile == GameSaveProfile.CASUAL) {
                            val savesMigrator = systemCoreConfig.getSavesMigrator()
                            savesMigrator?.loadPreviousSaveForGame(game, directoriesManager)
                        } else {
                            null
                        }
                    }
                }
            result.getOrNull()
        }
    }

    suspend fun setSaveRAM(
        game: Game,
        data: ByteArray,
        profile: GameSaveProfile = GameSaveProfile.CASUAL,
    ) {
        withContext(Dispatchers.IO) {
            val result =
                runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                    if (data.isEmpty()) {
                        return@runCatchingWithRetry
                    }

                    val saveFile = getSaveFile(getSaveRAMFileName(game), profile)
                    saveFile.writeBytesAtomic(data)
                }
            // A failed SRAM write must propagate to the quit flow. Otherwise
            // the app can report a successful exit even when progress was lost.
            result.getOrThrow()
        }
    }

    suspend fun getSaveRAMInfo(
        game: Game,
        profile: GameSaveProfile = GameSaveProfile.CASUAL,
    ): SaveInfo {
        return withContext(Dispatchers.IO) {
            val saveFile = getSaveFile(getSaveRAMFileName(game), profile)
            val fileExists = saveFile.exists() && saveFile.length() > 0
            SaveInfo(fileExists, saveFile.lastModified())
        }
    }

    private suspend fun getSaveFile(
        fileName: String,
        profile: GameSaveProfile,
    ): File =
        withContext(Dispatchers.IO) {
            val root = directoriesManager.getSavesDirectory()
            val directory = SaveProfileDirectories.sram(root, profile)
            if (profile == GameSaveProfile.HARDCORE) check(directory.mkdirs() || directory.isDirectory)
            File(directory, fileName)
        }

    /** This name should make it compatible with RetroArch so that users can freely sync saves across the two application. */
    private fun getSaveRAMFileName(game: Game) = "${game.fileName.substringBeforeLast(".")}.srm"

    companion object {
        private const val FILE_ACCESS_RETRIES = 3
    }
}
