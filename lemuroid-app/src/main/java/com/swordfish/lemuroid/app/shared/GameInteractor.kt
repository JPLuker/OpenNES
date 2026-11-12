package com.swordfish.lemuroid.app.shared

import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.library.RomFolderAccess
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.library.covers.CustomCoverManager
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.R
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GameInteractor(
    private val activity: BusyActivity,
    private val retrogradeDb: RetrogradeDatabase,
    private val useLeanback: Boolean,
    private val shortcutsGenerator: ShortcutsGenerator,
    private val gameLauncher: GameLauncher,
    private val customCoverManager: CustomCoverManager,
) {
    fun onGamePlay(game: Game) {
        if (!ensureRomFolderAccessible(game) || !ensureNotBusy()) {
            return
        }
        if (!ensureNotificationsPermissionAvailable()) {
            return
        }
        gameLauncher.launchGameAsync(activity.activity(), game, true, useLeanback)
    }

    fun onGameRestart(game: Game) {
        if (!ensureRomFolderAccessible(game) || !ensureNotBusy()) {
            return
        }
        if (!ensureNotificationsPermissionAvailable()) {
            return
        }
        gameLauncher.launchGameAsync(activity.activity(), game, false, useLeanback)
    }

    fun onFavoriteToggle(
        game: Game,
        isFavorite: Boolean,
    ) {
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(isFavorite = isFavorite))
        }
    }

    fun onCreateShortcut(game: Game) {
        GlobalScope.launch {
            shortcutsGenerator.pinShortcutForGame(game)
        }
    }

    fun supportShortcuts(): Boolean {
        return shortcutsGenerator.supportShortcuts()
    }

    fun onSetCustomName(
        game: Game,
        customName: String,
    ) {
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(customName = customName))
        }
    }

    fun onClearCustomName(game: Game) {
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(customName = null))
        }
    }

    /**
     * Copies the image at [sourceUri] into app-private storage, then stores
     * the local file URI in the database.  This ensures the cover survives
     * app restarts regardless of where the user picked the file from.
     */
    fun onSetCustomCoverUri(
        game: Game,
        sourceUri: String,
    ) {
        GlobalScope.launch {
            val localUri = customCoverManager.importCover(game.id, Uri.parse(sourceUri))
            retrogradeDb.gameDao().update(game.copy(customCoverUri = localUri))
        }
    }

    /**
     * Removes the custom cover: deletes the file from private storage and
     * clears the database field.
     */
    fun onClearCustomCoverUri(game: Game) {
        GlobalScope.launch {
            customCoverManager.deleteCover(game.id)
            retrogradeDb.gameDao().update(game.copy(customCoverUri = null))
        }
    }

    /** A cached library record is not evidence that Android still grants ROM access. */
    private fun ensureRomFolderAccessible(game: Game): Boolean {
        val context = activity.activity()
        if (RomFolderAccess.check(context.applicationContext, game.fileUri) == RomFolderAccess.Status.AVAILABLE) {
            return true
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.opennes_setup_folder_error_title)
            .setMessage(R.string.opennes_rom_folder_missing_play)
            .setPositiveButton(R.string.opennes_setup_choose_action) { _, _ ->
                StorageFrameworkPickerLauncher.pickFolder(context)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return false
    }

    private fun ensureNotificationsPermissionAvailable(): Boolean {
        if (useLeanback || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        val permissionResult =
            ContextCompat.checkSelfPermission(
                activity.activity(),
                Manifest.permission.POST_NOTIFICATIONS,
            )

        if (permissionResult == PackageManager.PERMISSION_GRANTED) {
            return true
        }

        activity.activity().displayToast(R.string.game_interactor_notification_permission_required)
        return false
    }

    private fun ensureNotBusy(): Boolean {
        if (activity.isBusy()) {
            activity.activity().displayToast(R.string.game_interactory_busy)
            return false
        }
        return true
    }
}
