package com.swordfish.lemuroid.lib.saves

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class StatesPreviewManager(private val directoriesManager: DirectoriesManager) {
    suspend fun getPreviewForSlot(
        game: Game,
        coreID: CoreID,
        index: Int,
        size: Int,
        profile: GameSaveProfile = GameSaveProfile.CASUAL,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val screenshotName = getSlotScreenshotName(game, index)
            val file = getPreviewFile(screenshotName, coreID.coreName, profile)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ThumbnailUtils.extractThumbnail(bitmap, size, size)
        }

    suspend fun setPreviewForSlot(
        game: Game,
        bitmap: Bitmap,
        coreID: CoreID,
        index: Int,
        profile: GameSaveProfile = GameSaveProfile.CASUAL,
    ) = withContext(Dispatchers.IO) {
        val screenshotName = getSlotScreenshotName(game, index)
        val file = getPreviewFile(screenshotName, coreID.coreName, profile)
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
    }

    private fun getPreviewFile(
        fileName: String,
        coreName: String,
        profile: GameSaveProfile,
    ): File {
        val root = File(directoriesManager.getStatesPreviewDirectory(), coreName)
        val directory = SaveProfileDirectories.states(root, profile)
        check(directory.mkdirs() || directory.isDirectory)
        return File(directory, fileName)
    }

    private fun getSlotScreenshotName(
        game: Game,
        index: Int,
    ) = "${game.fileName}.slot${index + 1}.jpg"

    companion object {
        val PREVIEW_SIZE_DP = 96f
    }
}
