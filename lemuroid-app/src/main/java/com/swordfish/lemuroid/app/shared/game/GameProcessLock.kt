package com.swordfish.lemuroid.app.shared.game

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

object GameProcessLock {
    private const val LOCK_FILE_NAME = "game_process.lock"

    @Volatile
    private var channel: FileChannel? = null

    @Volatile
    private var lock: FileLock? = null

    @Synchronized
    fun acquire(appContext: Context) {
        if (lock?.isValid == true) return
        val lockFile = File(appContext.filesDir, LOCK_FILE_NAME)
        val acquiredChannel = RandomAccessFile(lockFile, "rw").channel
        try {
            // Starting another emulator without ownership would make the
            // library incorrectly show two valid, independent sessions.
            val acquiredLock =
                acquiredChannel.tryLock()
                    ?: error("An OpenNES emulator process already owns the game lock")
            channel = acquiredChannel
            lock = acquiredLock
        } catch (failure: Exception) {
            acquiredChannel.close()
            throw failure
        }
    }

    /**
     * Safely export/import save files only while no emulator owns the process
     * lock. Holding this same lock during the entire I/O transaction also
     * prevents launching a new game halfway through migration.
     */
    fun <T> withNoGameRunning(
        appContext: Context,
        block: () -> T,
    ): T = withExclusiveLockFile(File(appContext.filesDir, LOCK_FILE_NAME), block)

    internal fun <T> withExclusiveLockFile(
        file: File,
        block: () -> T,
    ): T {
        try {
            return RandomAccessFile(file, "rw").channel.use { probe ->
                val owned =
                    probe.tryLock()
                        ?: throw IOException("Close the running NES game before transferring saves")
                owned.use { block() }
            }
        } catch (_: OverlappingFileLockException) {
            throw IOException("Close the running NES game before transferring saves")
        }
    }

    fun isHeldByAnotherProcess(appContext: Context): Boolean = isFileLocked(File(appContext.filesDir, LOCK_FILE_NAME))

    /** Fail closed if the lock cannot be probed; never launch two cores. */
    internal fun isFileLocked(file: File): Boolean {
        return try {
            RandomAccessFile(file, "rw").channel.use { probeChannel ->
                val probeLock = probeChannel.tryLock()
                if (probeLock == null) {
                    true
                } else {
                    probeLock.release()
                    false
                }
            }
        } catch (_: OverlappingFileLockException) {
            // The current process holds the lock; this is still occupied.
            true
        } catch (_: IOException) {
            true
        } catch (_: SecurityException) {
            true
        }
    }
}
