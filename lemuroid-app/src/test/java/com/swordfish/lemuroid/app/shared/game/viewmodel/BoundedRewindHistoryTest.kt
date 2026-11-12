package com.swordfish.lemuroid.app.shared.game.viewmodel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedRewindHistoryTest {
    @Test
    fun requiresFullHistoryForRewind() {
        val history = BoundedRewindHistory(maxBytes = 32, maxSnapshots = 8)
        assertFalse(history.canRewind(5))
        repeat(5) { history.record(byteArrayOf(it.toByte())) }
        assertNull(history.candidate(5))
        history.record(byteArrayOf(5))
        assertTrue(history.canRewind(5))
        assertArrayEquals(byteArrayOf(0), history.candidate(5)?.state)
    }

    @Test
    fun capsMemoryWithoutExceedingTotalBytes() {
        val history = BoundedRewindHistory(maxBytes = 8, maxSnapshots = 5)
        history.record(byteArrayOf(1, 2, 3))
        history.record(byteArrayOf(4, 5, 6))
        history.record(byteArrayOf(7, 8, 9))
        assertEquals(6, history.storedBytes())
        assertArrayEquals(byteArrayOf(4, 5, 6), history.candidate(1)?.state)
        assertFalse(history.record(ByteArray(9)))
        assertFalse(history.record(byteArrayOf()))
        assertEquals(6, history.storedBytes())
    }

    @Test
    fun snapshotBytesNeverAliasNativeBuffer() {
        val history = BoundedRewindHistory(maxBytes = 10, maxSnapshots = 4)
        val original = byteArrayOf(1, 2)
        history.record(original)
        history.record(byteArrayOf(3, 4))
        original[0] = 99
        val snapshot = history.candidate(1)!!
        assertArrayEquals(byteArrayOf(1, 2), snapshot.state)
        snapshot.state[0] = 88
        assertArrayEquals(byteArrayOf(1, 2), history.candidate(1)?.state)
    }

    @Test
    fun discardsFutureOnlyAfterSuccessfulRestore() {
        val history = BoundedRewindHistory(maxBytes = 100, maxSnapshots = 8)
        repeat(7) { history.record(byteArrayOf(it.toByte())) }
        val candidate = history.candidate(5)!!
        assertTrue(history.canRewind(5))
        assertTrue(history.commit(candidate))
        assertEquals(2, history.storedBytes())
        assertFalse(history.canRewind(5))
        assertNull(history.candidate(5))
    }

    @Test
    fun staleSnapshotCannotBeCommittedAfterClearAndNewTimeline() {
        val history = BoundedRewindHistory(maxBytes = 16, maxSnapshots = 6)
        history.record(byteArrayOf(1))
        history.record(byteArrayOf(2))
        val old = history.candidate(1)!!
        history.clear()
        history.record(byteArrayOf(3))
        history.record(byteArrayOf(4))
        assertFalse(history.commit(old))
        assertEquals(0, history.storedBytes())
    }

    @Test
    fun oversizedOrEmptyCapturePreservesUsablePreviousTimeline() {
        val history = BoundedRewindHistory(maxBytes = 4, maxSnapshots = 4)
        history.record(byteArrayOf(1))
        history.record(byteArrayOf(2))
        assertFalse(history.record(ByteArray(5)))
        assertFalse(history.record(byteArrayOf()))
        assertEquals(2, history.storedBytes())
        assertArrayEquals(byteArrayOf(1), history.candidate(1)?.state)
    }

    @Test
    fun repeatedOneStepRewindsCannotCrossOldestSnapshot() {
        val history = BoundedRewindHistory(maxBytes = 16, maxSnapshots = 8)
        repeat(6) { history.record(byteArrayOf(it.toByte())) }
        repeat(5) {
            val candidate = history.candidate(1)!!
            assertTrue(history.commit(candidate))
        }
        assertEquals(1, history.storedBytes())
        assertFalse(history.canRewind(1))
        assertNull(history.candidate(1))
        assertTrue(history.record(byteArrayOf(9)))
        assertArrayEquals(byteArrayOf(0), history.candidate(1)?.state)
    }

    @Test
    fun rejectedOrEvictedCandidateClearsUnsafeHistory() {
        val history = BoundedRewindHistory(maxBytes = 8, maxSnapshots = 2)
        history.record(byteArrayOf(1))
        history.record(byteArrayOf(2))
        val candidate = history.candidate(1)!!
        history.record(byteArrayOf(3))
        assertFalse(history.commit(candidate))
        assertEquals(0, history.storedBytes())
    }
}
