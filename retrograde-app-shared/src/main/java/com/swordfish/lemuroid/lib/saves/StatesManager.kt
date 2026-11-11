package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.readBytesUncompressedAtomic
import com.swordfish.lemuroid.common.kotlin.readTextAtomic
import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.common.kotlin.writeBytesCompressedAtomic
import com.swordfish.lemuroid.common.kotlin.writeTextAtomic
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class StatesManager(private val directoriesManager: DirectoriesManager) {
    suspend fun getSlotSave(
        game: Game,
        coreID: CoreID,
        index: Int,
        profile: GameSaveProfile = GameSaveProfile.CASUAL,
    ): SaveState? =
        withContext(Dispatchers.IO) {
            require(index in 0 until MAX_STATES) { "Invalid NES state slot" }
            getSaveState(getSlotSaveFileName(game, index), coreID.coreName, profile)
        }

    suspend fun setSlotSave(
        game: Game,
        saveState: SaveState,
        coreID: CoreID,
        index: Int,
        profile: GameSaveProfile = GameSaveProfile.CASUAL,
    ) = withContext(Dispatchers.IO) {
        require(index in 0 until MAX_STATES) { "Invalid NES state slot" }
        setSaveState(getSlotSaveFileName(game, index), coreID.coreName, saveState, profile)
    }

    suspend fun getAutoSaveInfo(
        game: Game,
        coreID: CoreID,
        profile: GameSaveProfile = GameSaveProfile.CASUAL,
    ): SaveInfo =
        withContext(Dispatchers.IO) {
            val autoSaveFile = getStateFile(getAutoSaveFileName(game), coreID.coreName, profile)
            val autoSaveHasData = autoSaveFile.length() > 0
            SaveInfo(autoSaveFile.exists() && autoSaveHasData, autoSaveFile.lastModified())
        }

    suspend fun getAutoSave(
        game: Game,
        coreID: CoreID,
        profile: GameSaveProfile = GameSaveProfile.CASUAL,
    ) = withContext(Dispatchers.IO) {
        getSaveState(getAutoSaveFileName(game), coreID.coreName, profile)
    }

    suspend fun setAutoSave(
        game: Game,
        coreID: CoreID,
        saveState: SaveState,
        profile: GameSaveProfile = GameSaveProfile.CASUAL,
    ) = withContext(Dispatchers.IO) {
        setSaveState(getAutoSaveFileName(game), coreID.coreName, saveState, profile)
    }

    suspend fun getSavedSlotsInfo(
        game: Game,
        coreID: CoreID,
        profile: GameSaveProfile = GameSaveProfile.CASUAL,
    ): List<SaveInfo> =
        withContext(Dispatchers.IO) {
            (0 until MAX_STATES)
                .map { getStateFile(getSlotSaveFileName(game, it), coreID.coreName, profile) }
                .map { SaveInfo(it.exists() && it.length() > 0L, it.lastModified()) }
                .toList()
        }

    private suspend fun getSaveState(
        fileName: String,
        coreName: String,
        profile: GameSaveProfile,
    ): SaveState? {
        return runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            val saveFile = getStateFile(fileName, coreName, profile)
            val metadataFile = getMetadataStateFile(fileName, coreName, profile)
            if (saveFile.exists()) {
                val byteArray = saveFile.readBytesUncompressedAtomic()
                val stateMetadata =
                    runCatching {
                        Json.Default.decodeFromString(
                            SaveState.Metadata.serializer(),
                            metadataFile.readTextAtomic(),
                        )
                    }
                if (byteArray.isEmpty()) {
                    null
                } else {
                    SaveState(byteArray, stateMetadata.getOrNull() ?: SaveState.Metadata())
                }
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun setSaveState(
        fileName: String,
        coreName: String,
        saveState: SaveState,
        profile: GameSaveProfile,
    ) {
        require(saveState.state.isNotEmpty()) { "Refusing to persist an empty NES save state" }
        runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            writeStateToDisk(fileName, coreName, saveState.state, profile)
            writeMetadataToDisk(fileName, coreName, saveState.metadata, profile)
        }.getOrThrow()
    }

    private fun writeMetadataToDisk(
        fileName: String,
        coreName: String,
        metadata: SaveState.Metadata,
        profile: GameSaveProfile,
    ) {
        val metadataFile = getMetadataStateFile(fileName, coreName, profile)
        metadataFile.writeTextAtomic(Json.encodeToString(SaveState.Metadata.serializer(), metadata))
    }

    private fun writeStateToDisk(
        fileName: String,
        coreName: String,
        stateArray: ByteArray,
        profile: GameSaveProfile,
    ) {
        val saveFile = getStateFile(fileName, coreName, profile)
        saveFile.writeBytesCompressedAtomic(stateArray)
    }

    private fun getStateFile(
        fileName: String,
        coreName: String,
        profile: GameSaveProfile,
    ): File {
        val root = File(directoriesManager.getStatesDirectory(), coreName)
        val directory = SaveProfileDirectories.states(root, profile)
        check(directory.mkdirs() || directory.isDirectory)
        return File(directory, fileName)
    }

    private fun getMetadataStateFile(
        stateFileName: String,
        coreName: String,
        profile: GameSaveProfile,
    ): File {
        val root = File(directoriesManager.getStatesDirectory(), coreName)
        val directory = SaveProfileDirectories.states(root, profile)
        check(directory.mkdirs() || directory.isDirectory)
        return File(directory, "$stateFileName.metadata")
    }

    private fun getAutoSaveFileName(game: Game) = "${game.fileName}.state"

    private fun getSlotSaveFileName(
        game: Game,
        index: Int,
    ) = "${game.fileName}.slot${index + 1}"

    companion object {
        const val MAX_STATES = 4
        private const val FILE_ACCESS_RETRIES = 3
    }
}
