package com.swordfish.lemuroid.metadata.libretrodb.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.swordfish.lemuroid.metadata.libretrodb.db.entity.LibretroRom

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE romName LIKE :romName")
    suspend fun findByName(romName: String): List<LibretroRom>?

    // Scope every lookup inside the database, not after LIMIT 1.
    // The untrimmed development SQLite source also contains other consoles.
    @Query("SELECT * FROM games WHERE system = 'nes' AND romName = :romName LIMIT 1")
    suspend fun findByFileName(romName: String): LibretroRom?

    @Query("SELECT * FROM games WHERE system = 'nes' AND crc32 = :crc LIMIT 1")
    suspend fun findByCRC(crc: String): LibretroRom?

    @Query("SELECT * FROM games WHERE system = 'nes' AND serial = :serial LIMIT 1")
    suspend fun findBySerial(serial: String): LibretroRom?

    // Used only as a cautious fallback when exact ROM/CRC matching fails.
    @Query("SELECT * FROM games WHERE system = 'nes' AND name LIKE :prefix || '%' LIMIT 80")
    suspend fun findByTitlePrefix(prefix: String): List<LibretroRom>
}
