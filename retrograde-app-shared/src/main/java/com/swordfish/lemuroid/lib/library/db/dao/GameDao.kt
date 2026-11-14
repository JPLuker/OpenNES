/*
 * GameDao.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.lib.library.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun selectById(id: Int): Game?

    /** All installed NES ROMs, including already-played games and favorites. */
    @Query("SELECT * FROM games WHERE systemId = 'nes' ORDER BY COALESCE(customName, title) ASC")
    fun observeAllNesGames(): Flow<List<Game>>

    /** One snapshot of the NES library, for RomM save sync. */
    @Query("SELECT * FROM games WHERE systemId = 'nes'")
    suspend fun selectAllNesGames(): List<Game>

    /** Exact local filenames for RomM catalogue deduplication; no ROM bytes are read. */
    @Query("SELECT fileName FROM games WHERE systemId = 'nes'")
    fun observeNesFileNames(): Flow<List<String>>

    @Query("SELECT * FROM games WHERE fileUri = :fileUri")
    fun selectByFileUri(fileUri: String): Game?

    @Query("SELECT * FROM games WHERE lastIndexedAt < :lastIndexedAt")
    fun selectByLastIndexedAtLessThan(lastIndexedAt: Long): List<Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY COALESCE(customName, title) ASC")
    fun selectFavorites(): PagingSource<Int, Game>

    @Query(
        """
        SELECT * FROM games WHERE lastPlayedAt IS NOT NULL AND isFavorite = 0 ORDER BY lastPlayedAt DESC LIMIT :limit
        """,
    )
    fun selectFirstUnfavoriteRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavoritesRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectRecentGames(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun asyncSelectFirstRecents(limit: Int): List<Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavorites(limit: Int): Flow<List<Game>>

    // Keep the Favorites destination hidden until the NES library actually has favorites.
    // A Room Flow updates navigation as soon as a game is favorited/unfavorited.
    @Query("SELECT COUNT(*) FROM games WHERE systemId = 'nes' AND isFavorite = 1")
    fun observeFavoriteGameCount(): Flow<Int>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NULL LIMIT :limit")
    fun selectFirstNotPlayed(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE systemId = :systemId ORDER BY COALESCE(customName, title) ASC, id DESC")
    fun selectBySystem(systemId: String): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE systemId IN (:systemIds) ORDER BY COALESCE(customName, title) ASC, id DESC")
    fun selectBySystems(systemIds: List<String>): PagingSource<Int, Game>

    @Query("SELECT DISTINCT systemId FROM games ORDER BY systemId ASC")
    suspend fun selectSystems(): List<String>

    @Query("SELECT count(*) count, systemId systemId FROM games GROUP BY systemId")
    fun selectSystemsWithCount(): Flow<List<SystemCount>>

    /**
     * The user explicitly starts a bounded online enrichment batch.
     * Only NES titles with empty details and an expired per-game check cache
     * enter the queue. Never scan or upload game files.
     */
    @Query(
        """
        SELECT * FROM games
        WHERE systemId = 'nes'
          AND (genre IS NULL OR TRIM(genre) = '' OR releaseYear IS NULL
               OR description IS NULL OR TRIM(description) = ''
               OR (wikidataEntityId IS NOT NULL AND wikipediaArticleTitle IS NULL))
          AND lastOnlineMetadataCheckedAt <= :retryBefore
        ORDER BY lastOnlineMetadataCheckedAt ASC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun selectOnlineMetadataCandidates(
        retryBefore: Long,
        limit: Int,
    ): List<Game>

    @Query(
        """
        UPDATE games SET lastOnlineMetadataCheckedAt = :checkedAt
        WHERE id = :gameId AND systemId = 'nes'
        """,
    )
    suspend fun markOnlineMetadataChecked(
        gameId: Int,
        checkedAt: Long,
    ): Int

    @Insert
    fun insert(games: List<Game>): List<Long>

    @Delete
    fun delete(games: List<Game>)

    /**
     * Targeted edits avoid overwriting concurrent playtime, favorites, custom
     * artwork, and ROM scan timestamps with a stale Game object.
     */
    @Query(
        """
        UPDATE games
        SET genre = :genre, releaseYear = :releaseYear, description = :description,
            wikidataEntityId = NULL, wikipediaArticleTitle = NULL, lastOnlineMetadataCheckedAt = 0
        WHERE id = :gameId
        """,
    )
    suspend fun updateGameProfile(
        gameId: Int,
        genre: String?,
        releaseYear: Int?,
        description: String?,
    ): Int

    /**
     * A verified external NES game record can fill only missing editorial
     * fields. Never overwrite local edits or other game-state columns.
     */
    @Query(
        """
        UPDATE games
        SET genre = CASE WHEN genre IS NULL OR TRIM(genre) = '' THEN :genre ELSE genre END,
            releaseYear = COALESCE(releaseYear, :releaseYear),
            description = CASE
                WHEN description IS NULL OR TRIM(description) = '' THEN :description
                WHEN wikidataEntityId IS NOT NULL AND wikipediaArticleTitle IS NULL
                     AND :wikipediaTitle IS NOT NULL THEN :description
                ELSE description END,
            wikipediaArticleTitle = CASE
                WHEN :wikipediaTitle IS NOT NULL AND
                     (description IS NULL OR TRIM(description) = ''
                      OR (wikidataEntityId IS NOT NULL AND wikipediaArticleTitle IS NULL))
                THEN :wikipediaTitle ELSE wikipediaArticleTitle END,
            wikidataEntityId = :sourceId,
            lastOnlineMetadataCheckedAt = :checkedAt
        WHERE id = :gameId AND systemId = 'nes'
        """,
    )
    suspend fun fillMissingOnlineGameInfo(
        gameId: Int,
        genre: String?,
        releaseYear: Int?,
        description: String?,
        sourceId: String,
        wikipediaTitle: String?,
        checkedAt: Long,
    ): Int

    @Query(
        """
        UPDATE games
        SET title = :title, developer = :developer,
            coverFrontUrl = :coverFrontUrl, lastMetadataCheckedAt = :lastChecked,
            wikidataEntityId = NULL, wikipediaArticleTitle = NULL, lastOnlineMetadataCheckedAt = 0
        WHERE id = :gameId
        """,
    )
    suspend fun updateCatalogueMatch(
        gameId: Int,
        title: String,
        developer: String?,
        coverFrontUrl: String?,
        lastChecked: Long,
    ): Int

    /**
     * Explicit Fix Match confirmation may replace only the game's editorial
     * metadata, provenance and the selected library display name. The ROM
     * identity, indexed title, favorites, saves and playtime remain unchanged.
     * Matching box art is applied separately by updateCatalogueCover.
     */
    @Query(
        """
        UPDATE games
        SET customName = :selectedTitle,
            genre = COALESCE(:genre, genre),
            releaseYear = COALESCE(:releaseYear, releaseYear),
            description = COALESCE(:description, description),
            wikidataEntityId = :sourceId,
            wikipediaArticleTitle = CASE
                WHEN :description IS NOT NULL THEN :wikipediaTitle
                ELSE wikipediaArticleTitle END,
            lastOnlineMetadataCheckedAt = :checkedAt
        WHERE id = :gameId AND systemId = 'nes'
        """,
    )
    suspend fun applySelectedOnlineMatch(
        gameId: Int,
        selectedTitle: String,
        genre: String?,
        releaseYear: Int?,
        description: String?,
        sourceId: String,
        wikipediaTitle: String?,
        checkedAt: Long,
    ): Int

    /**
     * Details from the player's own RomM server for a game downloaded from
     * it. Never replaces a name the player chose or details already present.
     */
    @Query(
        """
        UPDATE games
        SET customName = COALESCE(customName, :name),
            genre = COALESCE(genre, :genre),
            description = COALESCE(description, :description)
        WHERE id = :gameId AND systemId = 'nes'
        """,
    )
    suspend fun fillRommDetails(
        gameId: Int,
        name: String?,
        genre: String?,
        description: String?,
    ): Int

    /**
     * Catalogue box art for a title confirmed in Fix Match. A cover the
     * player chose themselves (customCoverUri) is stored separately and
     * still takes priority when shown.
     */
    @Query("UPDATE games SET coverFrontUrl = :coverFrontUrl WHERE id = :gameId AND systemId = 'nes'")
    suspend fun updateCatalogueCover(
        gameId: Int,
        coverFrontUrl: String,
    ): Int

    @Update
    suspend fun update(game: Game)

    @Update
    fun update(games: List<Game>)
}

data class SystemCount(val systemId: String, val count: Int)
