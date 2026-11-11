package com.swordfish.lemuroid.app.shared.romm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RommCatalogueCodecTest {
    private val source = "a".repeat(64)
    private val revision = "local-credential-revision"

    private fun rom(
        id: Int = 7,
        description: String? = "A game summary from RomM",
    ) = RommApiClient.Rom(
        id = id,
        platformId = 1,
        name = "A NES Game",
        fileName = "A NES Game (USA).nes",
        sizeBytes = 24576L,
        md5 = "1".repeat(32),
        description = description,
    )

    @Test
    fun metadataSurvivesRecreationWithoutROMBytesOrSecrets() {
        val bytes =
            RommCatalogueCodec.encode(
                listOf(rom()),
                source,
                revision,
                1_000_000L,
            )!!
        val saved = RommCatalogueCodec.decode(bytes, source, revision, 2_000_000L)!!
        assertEquals(1_000_000L, saved.fetchedAt)
        assertEquals(rom(), saved.games.single())
        assertTrue(String(bytes).contains("A game summary from RomM"))
        assertTrue(bytes.size < 2_000)
    }

    @Test
    fun remoteGenreMetadataSurvivesOfflineRestart() {
        val withGenres = rom().copy(genres = listOf("Platform", "Action"))
        val payload =
            RommCatalogueCodec.encode(listOf(withGenres), source, revision, 1_000_000L)!!
        val recovered = RommCatalogueCodec.decode(payload, source, revision, 2_000_000L)!!
        assertEquals(listOf("Platform", "Action"), recovered.games.single().genres)
        val invalid = rom().copy(genres = listOf("x".repeat(100)))
        assertNull(RommCatalogueCodec.encode(listOf(invalid), source, revision, 1_000_000L))
    }

    @Test
    fun wrongAccountOrServerCannotReadAnotherAccountCatalogue() {
        val bytes = RommCatalogueCodec.encode(listOf(rom()), source, revision, 1_000_000L)!!
        assertNull(RommCatalogueCodec.decode(bytes, source, "another-account", 2_000_000L))
        assertNull(RommCatalogueCodec.decode(bytes, "b".repeat(64), revision, 2_000_000L))
        assertNull(RommCatalogueCodec.decode(bytes, source, revision, 200L))
    }

    @Test
    fun malformedUnsafeOrOverlargeEntriesFailClosed() {
        val original = RommCatalogueCodec.encode(listOf(rom()), source, revision, 1_000_000L)!!
        assertNull(RommCatalogueCodec.decode(byteArrayOf(0), source, revision))
        assertNull(
            RommCatalogueCodec.decode(
                original.copyOf(RommCatalogueCodec.MAX_BYTES + 1),
                source,
                revision,
            ),
        )
        val unsafe = JSONObject(String(original))
        unsafe.getJSONArray("games").getJSONObject(0).put("filename", "../escape.nes")
        assertNull(RommCatalogueCodec.decode(unsafe.toString().toByteArray(), source, revision, 2_000_000L))
        val duplicate = JSONObject(String(original))
        duplicate.getJSONArray("games").put(JSONObject(duplicate.getJSONArray("games").getJSONObject(0).toString()))
        assertNull(RommCatalogueCodec.decode(duplicate.toString().toByteArray(), source, revision, 2_000_000L))
    }

    @Test
    fun excessiveSummaryIsTruncatedAndInvalidNamesAreRejected() {
        val encoded = RommCatalogueCodec.encode(listOf(rom(description = "x".repeat(9000))), source, revision, 1234L)!!
        val parsed = RommCatalogueCodec.decode(encoded, source, revision, 1234L)!!
        assertEquals(8000, parsed.games.single().description!!.length)
        assertNull(
            RommCatalogueCodec.encode(
                listOf(rom().copy(fileName = "not a NES game.exe")),
                source,
                revision,
                1234L,
            ),
        )
    }
}
