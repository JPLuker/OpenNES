package com.swordfish.lemuroid.app.shared.settings

import android.content.Context
import com.swordfish.lemuroid.app.mobile.feature.game.NesControllerOnboardingStore
import com.swordfish.lemuroid.app.mobile.feature.main.OpenNesFirstRunStore
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.romm.RommConnectionStore
import com.swordfish.lemuroid.app.shared.romm.RommSaveSyncWork
import com.swordfish.lemuroid.app.shared.romm.RommTwoWaySync
import com.swordfish.lemuroid.app.shared.storage.cache.CacheCleanerWork
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SettingsInteractor(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
) {
    fun changeLocalStorageFolder() {
        StorageFrameworkPickerLauncher.pickFolder(context)
    }

    fun resetAllSettings() {
        SharedPreferencesHelper.getLegacySharedPreferences(context).edit().clear().apply()
        SharedPreferencesHelper.getSharedPreferences(context).edit().clear().apply()
        LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
        CacheCleanerWork.enqueueCleanCacheAll(context.applicationContext)
        deleteDownloadedCores()
        // A factory reset also forgets the RomM server and shows setup again.
        // Saves, save states and the games themselves are never touched.
        val app = context.applicationContext
        OpenNesFirstRunStore(app).requestAgain()
        NesControllerOnboardingStore(app).requestAgain()
        RommSaveSyncWork.cancel(app)
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { RommTwoWaySync.setEnabled(app, false, emptyList()) }
            runCatching { RommConnectionStore(app).disconnect() }
        }
    }

    private fun deleteDownloadedCores() {
        directoriesManager.getCoresDirectory()
            .listFiles()
            ?.forEach { runCatching { it.deleteRecursively() } }
    }
}
