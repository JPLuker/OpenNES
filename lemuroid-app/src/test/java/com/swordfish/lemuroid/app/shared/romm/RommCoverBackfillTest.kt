package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Test

class RommCoverBackfillTest {
    private val cover = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1abc.jpg"

    private fun rom(
        fileName: String,
        coverUrl: String? = cover,
    ) = RommApiClient.Rom(7, 1, "Kirby", fileName, 1L, null, coverUrl = coverUrl)

    private fun local(
        fileName: String,
        coverFrontUrl: String? = null,
        customCoverUri: String? = null,
    ) = RommCoverBackfill.LocalGame(1, fileName, coverFrontUrl, customCoverUri)

    @Test
    fun `game without a cover borrows RomM's cover for the same file`() {
        assertEquals(
            mapOf(1 to cover),
            RommCoverBackfill.covers(listOf(local("Kirby's Adventure.nes")), listOf(rom("kirby's adventure.NES"))),
        )
    }

    @Test
    fun `existing or custom covers are never replaced`() {
        val games =
            listOf(
                local("a.nes", coverFrontUrl = "https://thumbnails.libretro.com/x.png"),
                local("a.nes", customCoverUri = "content://cover"),
            )
        assertEquals(emptyMap<Int, String>(), RommCoverBackfill.covers(games, listOf(rom("a.nes"))))
    }

    @Test
    fun `untrusted or missing RomM covers are ignored`() {
        val roms = listOf(rom("a.nes", coverUrl = "https://example.com/a.jpg"), rom("b.nes", coverUrl = null))
        assertEquals(
            emptyMap<Int, String>(),
            RommCoverBackfill.covers(listOf(local("a.nes"), local("b.nes")), roms),
        )
    }

    @Test
    fun `different files are not matched`() {
        assertEquals(
            emptyMap<Int, String>(),
            RommCoverBackfill.covers(listOf(local("Kirby (USA).nes")), listOf(rom("Kirby.nes"))),
        )
    }
}
