package com.swordfish.lemuroid.app.shared.game.viewmodel

import java.util.ArrayDeque

/** In-memory, bounded history of native NES snapshots, never written to disk. */
internal class BoundedRewindHistory(
    private val maxBytes: Int = 32 * 1024 * 1024,
    private val maxSnapshots: Int = 30,
) {
    init {
        require(maxBytes > 0)
        require(maxSnapshots > 1)
    }

    private data class Entry(val id: Long, val state: ByteArray)

    internal data class Candidate(val id: Long, val state: ByteArray)

    private val entries = ArrayDeque<Entry>()
    private var usedBytes = 0
    private var nextId = 0L

    @Synchronized
    fun record(snapshot: ByteArray): Boolean {
        if (snapshot.isEmpty() || snapshot.size > maxBytes) return false
        val copy = snapshot.copyOf()
        entries.addLast(Entry(++nextId, copy))
        usedBytes += copy.size
        while (entries.size > maxSnapshots || usedBytes > maxBytes) {
            usedBytes -= entries.removeFirst().state.size
        }
        return true
    }

    @Synchronized
    fun canRewind(stepsBack: Int): Boolean = stepsBack > 0 && entries.size > stepsBack

    /** A full number of captured intervals must exist; no fallback to bad data. */
    @Synchronized
    fun candidate(stepsBack: Int): Candidate? {
        if (!canRewind(stepsBack)) return null
        val entry = entries.elementAt(entries.size - stepsBack - 1)
        return Candidate(entry.id, entry.state.copyOf())
    }

    /** Only discard future history after native unserialize actually succeeds. */
    @Synchronized
    fun commit(candidate: Candidate): Boolean {
        if (entries.none { it.id == candidate.id }) {
            clear()
            return false
        }
        while (entries.last.id != candidate.id) {
            usedBytes -= entries.removeLast().state.size
        }
        return true
    }

    @Synchronized
    fun clear() {
        entries.clear()
        usedBytes = 0
    }

    @Synchronized
    fun storedBytes(): Int = usedBytes
}
