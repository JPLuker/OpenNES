package com.swordfish.lemuroid.app.shared.library

import org.junit.Assert.assertEquals
import org.junit.Test

class RomFolderAccessTest {
    @Test
    fun indexedDocumentWithoutSelectedFolderNeedsRecovery() {
        assertEquals(
            RomFolderAccess.Status.RESELECT_REQUIRED,
            RomFolderAccess.evaluate(isDocumentUri = true, folderSelected = false, persistedReadGrant = false),
        )
    }

    @Test
    fun folderWithRevokedAndroidGrantNeedsRecovery() {
        assertEquals(
            RomFolderAccess.Status.RESELECT_REQUIRED,
            RomFolderAccess.evaluate(isDocumentUri = true, folderSelected = true, persistedReadGrant = false),
        )
    }

    @Test
    fun permittedDocumentAndPrivateLocalRomAreAllowed() {
        assertEquals(
            RomFolderAccess.Status.AVAILABLE,
            RomFolderAccess.evaluate(isDocumentUri = true, folderSelected = true, persistedReadGrant = true),
        )
        assertEquals(
            RomFolderAccess.Status.AVAILABLE,
            RomFolderAccess.evaluate(isDocumentUri = false, folderSelected = false, persistedReadGrant = false),
        )
    }
}
