package com.swordfish.lemuroid.lib.library.db.dao

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val VERSION_8_9: Migration =
        object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `datafiles`(
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `lastIndexedAt` INTEGER NOT NULL,
                        `path` TEXT, FOREIGN KEY(`gameId`
                    ) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_datafiles_id` ON `datafiles` (`id`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_fileUri` ON `datafiles` (`fileUri`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_gameId` ON `datafiles` (`gameId`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_lastIndexedAt` ON `datafiles` (`lastIndexedAt`)
                    """.trimIndent(),
                )
            }
        }

    val VERSION_9_10: Migration =
        object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                migrateGamesTableToVersion10(database)
                GameSearchDao.migrateFtsForVersion10(database)
            }
        }

    val VERSION_10_11: Migration =
        object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Keep all existing games and saves; historic sessions are unknown.
                database.execSQL(
                    "ALTER TABLE games ADD COLUMN totalPlayTimeMillis INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

    val VERSION_11_12: Migration =
        object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Existing games are eligible for one metadata enrichment check.
                // Preserve their custom names/covers, saves and recorded playtime.
                database.execSQL(
                    "ALTER TABLE games ADD COLUMN lastMetadataCheckedAt INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

    val VERSION_12_13: Migration =
        object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add optional editorial fields; keep every existing row, game save,
                // custom cover/name, favorites, metadata and recorded playtime.
                database.execSQL("ALTER TABLE games ADD COLUMN genre TEXT")
                database.execSQL("ALTER TABLE games ADD COLUMN releaseYear INTEGER")
                database.execSQL("ALTER TABLE games ADD COLUMN description TEXT")
            }
        }

    val VERSION_13_14: Migration =
        object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Optional source reference; do not touch saves, ROMs or custom data.
                database.execSQL("ALTER TABLE games ADD COLUMN wikidataEntityId TEXT")
            }
        }

    private fun migrateGamesTableToVersion10(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE games ADD COLUMN customName TEXT DEFAULT NULL",
        )
        database.execSQL(
            "ALTER TABLE games ADD COLUMN customCoverUri TEXT DEFAULT NULL",
        )
    }
}
