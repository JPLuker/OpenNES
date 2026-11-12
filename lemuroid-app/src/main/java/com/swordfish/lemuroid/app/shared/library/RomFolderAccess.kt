package com.swordfish.lemuroid.app.shared.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/**
 * Android can discard a document-tree grant while keeping the game's cover
 * and metadata in Room. Never treat an indexed game as a valid ROM permission.
 * File-based games (including privately imported RomM games) are unaffected.
 */
object RomFolderAccess {
    enum class Status {
        AVAILABLE,
        RESELECT_REQUIRED,
    }

    internal fun evaluate(
        isDocumentUri: Boolean,
        folderSelected: Boolean,
        persistedReadGrant: Boolean,
    ): Status =
        if (isDocumentUri && (!folderSelected || !persistedReadGrant)) {
            Status.RESELECT_REQUIRED
        } else {
            Status.AVAILABLE
        }

    fun check(
        context: Context,
        gameFileUri: String,
    ): Status {
        val isDocumentUri = Uri.parse(gameFileUri).scheme == ContentResolver.SCHEME_CONTENT
        if (!isDocumentUri) return Status.AVAILABLE

        val selected =
            SharedPreferencesHelper.getLegacySharedPreferences(context)
                .getString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder), null)
                .orEmpty()
        val hasGrant =
            runCatching {
                val tree = Uri.parse(selected)
                context.contentResolver.persistedUriPermissions.any {
                    it.uri == tree && it.isReadPermission
                }
            }.getOrDefault(false)
        return evaluate(isDocumentUri, selected.isNotBlank(), hasGrant)
    }
}
