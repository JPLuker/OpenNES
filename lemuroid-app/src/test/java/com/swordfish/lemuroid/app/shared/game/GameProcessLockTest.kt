package com.swordfish.lemuroid.app.shared.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class GameProcessLockTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun freeLockCanBeProbedRepeatedly() {
        val file = temporaryFolder.newFile("open-nes.lock")
        assertFalse(GameProcessLock.isFileLocked(file))
        assertFalse(GameProcessLock.isFileLocked(file))
    }

    @Test
    fun sameProcessOwnerCannotCrashSessionProbe() {
        val file = temporaryFolder.newFile("open-nes.lock")
        RandomAccessFile(file, "rw").channel.use { channel ->
            val ownedLock = channel.lock()
            try {
                assertTrue(GameProcessLock.isFileLocked(file))
            } finally {
                ownedLock.release()
            }
        }
        assertFalse(GameProcessLock.isFileLocked(file))
    }

    @Test
    fun unreadableLockLocationFailsClosed() {
        val directory = File(temporaryFolder.root, "missing-parent")
        assertTrue(GameProcessLock.isFileLocked(File(directory, "game.lock")))
    }
}
