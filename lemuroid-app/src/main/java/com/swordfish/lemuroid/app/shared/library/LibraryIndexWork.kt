package com.swordfish.lemuroid.app.shared.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.shared.metadata.NesAutoMetadataScheduler
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import com.swordfish.lemuroid.lib.library.LemuroidLibrary
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import dagger.android.AndroidInjector
import dagger.Binds
import dagger.multibindings.IntoMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class LibraryIndexWork(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var lemuroidLibrary: LemuroidLibrary

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)

        val notificationsManager = NotificationsManager(applicationContext)

        val foregroundInfo =
            createSyncForegroundInfo(
                NotificationsManager.LIBRARY_INDEXING_NOTIFICATION_ID,
                notificationsManager.libraryIndexingNotification(),
            )

        setForegroundAsync(foregroundInfo)

        // A revoked document-tree grant must not be interpreted as an empty
        // NES folder: the indexer's cleanup could otherwise remove saved games.
        if (!selectedRomFolderIsReadable()) {
            Timber.w("Selected NES folder is no longer accessible")
            LibraryScanStatus.setError(applicationContext, LibraryScanStatus.FOLDER_UNAVAILABLE)
            return Result.failure()
        }

        try {
            withContext(Dispatchers.IO) {
                lemuroidLibrary.indexLibrary()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "NES library indexing failed; existing library retained")
            LibraryScanStatus.setError(applicationContext, LibraryScanStatus.SCAN_FAILED)
            return Result.failure()
        }

        LibraryScanStatus.setError(applicationContext, null)

        // The game library is usable immediately. Metadata is enriched later
        // by a separate WorkManager job with an online constraint.
        NesAutoMetadataScheduler.schedule(applicationContext)
        LibraryIndexScheduler.scheduleCoreUpdate(applicationContext)

        return Result.success()
    }

    private fun selectedRomFolderIsReadable(): Boolean {
        val selectedFolder =
            SharedPreferencesHelper.getLegacySharedPreferences(applicationContext)
                .getString(
                    applicationContext.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder),
                    null,
                )
                ?.takeIf { it.isNotBlank() }
                ?: return true

        return try {
            val uri = Uri.parse(selectedFolder)
            val hasGrant =
                applicationContext.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission
                }
            val folder = DocumentFile.fromTreeUri(applicationContext, uri)
            hasGrant && folder != null && folder.exists() && folder.isDirectory && folder.canRead()
        } catch (e: Exception) {
            Timber.w(e, "Cannot read selected NES document folder")
            false
        }
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(LibraryIndexWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<LibraryIndexWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<LibraryIndexWork>()
    }
}
