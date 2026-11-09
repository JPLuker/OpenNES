package com.swordfish.lemuroid.app.shared.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.library.LibraryScanStatus
import com.swordfish.lemuroid.app.utils.android.displayErrorDialog
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import timber.log.Timber
import javax.inject.Inject

class StorageFrameworkPickerLauncher : RetrogradeActivity() {
    @Inject
    lateinit var directoriesManager: DirectoriesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    this.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    this.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    this.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    this.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                }
            try {
                startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER)
            } catch (e: Exception) {
                showStorageAccessFrameworkNotSupportedDialog()
            }
        }
    }

    private fun showStorageAccessFrameworkNotSupportedDialog() {
        val message = getString(R.string.dialog_saf_not_found, directoriesManager.getInternalRomsDirectory())
        val actionLabel = getString(R.string.ok)
        displayErrorDialog(message, actionLabel) { finish() }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        resultData: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK) {
            val sharedPreferences = SharedPreferencesHelper.getLegacySharedPreferences(this)
            val preferenceKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)

            val currentValue: String? = sharedPreferences.getString(preferenceKey, null)
            val newValue = resultData?.data

            if (newValue != null) {
                val permissionResult = runCatching { updatePersistableUris(newValue) }
                if (permissionResult.isFailure) {
                    Timber.e(permissionResult.exceptionOrNull(), "Could not retain NES folder access")
                    LibraryScanStatus.setError(this, LibraryScanStatus.FOLDER_UNAVAILABLE)
                    displayErrorDialog(
                        getString(R.string.opennes_setup_folder_error_description),
                        getString(R.string.ok),
                    ) { finish() }
                    return
                }

                if (newValue.toString() != currentValue) {
                    sharedPreferences.edit().putString(preferenceKey, newValue.toString()).apply()
                }

                // Selecting the same tree again can restore a revoked grant.
                LibraryScanStatus.setError(this, null)
                startLibraryIndexWork()
            }
        }
        finish()
    }

    private fun updatePersistableUris(uri: Uri) {
        // Obtain the new grant before releasing the old one. A failed picker
        // must not strand the player's existing ROM library.
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.uri != uri }
            .forEach {
                runCatching {
                    contentResolver.releasePersistableUriPermission(
                        it.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }.onFailure { error ->
                    Timber.w(error, "Could not release previous NES folder grant")
                }
            }
    }

    private fun startLibraryIndexWork() {
        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 1

        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, StorageFrameworkPickerLauncher::class.java))
        }
    }
}
